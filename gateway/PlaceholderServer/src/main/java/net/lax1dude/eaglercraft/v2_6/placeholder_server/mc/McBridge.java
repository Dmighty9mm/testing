package net.lax1dude.eaglercraft.v2_6.placeholder_server.mc;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * EaglerCraft 26.1.2 - Minecraft protocol TCP bridge from a WebSocket Eagler
 * session into a backend server.
 *
 * <p>This is gateway milestone #2: the bridge is the MC-protocol half of
 * forwarding an Eagler player into the proxy's server registry. One bridge
 * instance owns one vanilla TCP connection to a registered backend
 * {@code ServerInfo} address and performs the vanilla 26.1.2 login dance (handshake
 * with next-state 2, Login Start, Set Compression, Login Finished, Login
 * Acknowledged), walks the CONFIGURATION phase (known packs,
 * keepalive/ping, code of conduct, finish + ack) and then runs a minimal
 * play-state relay:</p>
 *
 * <ul>
 *   <li>server keepalive / ping is answered immediately (config + play);</li>
 *   <li>plugin messages (custom payloads) are relayed BOTH ways in config
 *       and play - the side channel server APIs such as Apollo
 *       ({@code lunar:apollo}, {@code apollo:json}) ride on;</li>
 *   <li>Start Configuration (proxy server switch / reconfigure) is
 *       acknowledged and the configuration phase is walked again;</li>
 *   <li>system chat is decoded (NBT) and handed to the {@link Listener};</li>
 *   <li>unsigned client chat is forwarded to the backend;</li>
 *   <li>disconnect packets (login, config, play) end the bridge;</li>
 *   <li>login plugin requests and cookie requests are politely refused;</li>
 *   <li>encryption requests abort the bridge (offline-mode gateway);</li>
 *   <li>every other server packet is skipped by length framing.</li>
 * </ul>
 *
 * <p>Plugin messages sent before the backend reaches a phase that can
 * carry them are queued and flushed in order once it does.</p>
 *
 * <p>The class is pure JDK (no netty, no slf4j) so both proxy plugins and
 * standalone harnesses can embed it. All public methods are safe to call
 * from any thread; I/O happens on dedicated daemon threads.</p>
 */
public final class McBridge {

    /** Bridge lifecycle states, reported through {@link Listener#onStateChange}. */
    public enum State {
        NEW, CONNECTING, LOGGING_IN, CONNECTED, CLOSED
    }

    /** The backend protocol phase while {@link State#CONNECTED}. */
    public enum Phase {
        LOGIN, CONFIGURATION, PLAY
    }

    /** Vanilla serverbound custom payload cap (ServerboundCustomPayloadPacket). */
    public static final int MAX_SERVERBOUND_PAYLOAD = 32767;
    /** Vanilla clientbound custom payload cap (ClientboundCustomPayloadPacket). */
    public static final int MAX_CLIENTBOUND_PAYLOAD = 1048576;
    /** Plugin messages queued while the backend cannot carry them yet. */
    public static final int MAX_PENDING_PLUGIN_MESSAGES = 256;

    /** Callbacks from the bridge; invoked on the bridge I/O threads. */
    public interface Listener {
        void onStateChange(McBridge bridge, State newState, String detail);

        /** A decoded system-chat text arrived from the backend. */
        void onChat(McBridge bridge, String text);

        /** The backend disconnected the player (login or play state). */
        void onKicked(McBridge bridge, String reason);

        default void onError(McBridge bridge, String message) {
        }

        /**
         * A plugin message (custom payload) arrived from the backend, in the
         * configuration or the play phase. {@code data} is the raw payload
         * after the channel identifier.
         */
        default void onPluginMessage(McBridge bridge, String channel, byte[] data) {
        }
    }

    private final InetSocketAddress target;
    private final McPacketIds ids;
    private final String virtualHost;
    private final String username;
    private final UUID uuid;
    private final int connectTimeoutMs;
    private final Listener listener;

    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private final Object writeLock = new Object();

    private volatile Socket socket;
    private volatile DataInputStream in;
    private volatile OutputStream out;
    private volatile int compressionThreshold = -1; // -1 = uncompressed
    private volatile Phase phase = Phase.LOGIN;
    /** {channel, data} pairs waiting for the backend to reach CONFIGURATION/PLAY. */
    private final ArrayDeque<Object[]> pendingPluginMessages = new ArrayDeque<>();

    public McBridge(InetSocketAddress target, McPacketIds ids, String virtualHost,
            String username, UUID uuid, int connectTimeoutMs, Listener listener) {
        this.target = target;
        this.ids = ids.copy();
        this.virtualHost = virtualHost;
        this.username = username;
        this.uuid = uuid;
        this.connectTimeoutMs = connectTimeoutMs;
        this.listener = listener;
    }

    // ==================== lifecycle ====================

    /** Starts the connection + login on a daemon thread. */
    public void start() {
        Thread t = new Thread(this::runLogin, "eagler-mcbridge-" + username);
        t.setDaemon(true);
        t.start();
    }

    public State getState() {
        return state.get();
    }

    public boolean isConnected() {
        return state.get() == State.CONNECTED;
    }

    /** The backend phase; meaningful while {@link #isConnected()}. */
    public Phase getPhase() {
        return phase;
    }

    public String getUsername() {
        return username;
    }

    public UUID getUuid() {
        return uuid;
    }

    public InetSocketAddress getTarget() {
        return target;
    }

    /** Closes the bridge; safe to call from any thread, idempotent. */
    public void close(String reason) {
        // CLOSED first: the reader thread's "socket closed" exception is
        // then recognised as our own close, not reported as an error
        transition(State.CLOSED, reason);
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    // ==================== login ====================

    private void runLogin() {
        transition(State.CONNECTING, "connecting " + target);
        try {
            socket = new Socket();
            // resolve per connect (unresolved targets come from host:port config)
            InetSocketAddress addr = target.isUnresolved()
                    ? new InetSocketAddress(target.getHostString(), target.getPort())
                    : new InetSocketAddress(target.getAddress(), target.getPort());
            socket.connect(addr,
                    Math.max(1, connectTimeoutMs));
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(30_000);
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 1 << 15));
            out = new BufferedOutputStream(socket.getOutputStream(), 1 << 15);
        } catch (Exception e) {
            fail("connect failed: " + e.getMessage());
            return;
        }

        transition(State.LOGGING_IN, "login handshake");
        try {
            // handshake (state 0): protocol, addr, port, nextState=2
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            McVarInt.writeVarInt(hs, 0x00);
            McVarInt.writeVarInt(hs, ids.protocolVersion);
            McVarInt.writeString(hs, virtualHost, 255);
            hs.write((target.getPort() >>> 8) & 0xFF);
            hs.write(target.getPort() & 0xFF);
            McVarInt.writeVarInt(hs, 2);
            sendFrame(hs.toByteArray());

            // login start: name + profile uuid (26.1.2 ServerboundHelloPacket:
            // writeUtf(name, 16) + writeUUID - no presence flag)
            ByteArrayOutputStream ls = new ByteArrayOutputStream();
            McVarInt.writeVarInt(ls, 0x00);
            McVarInt.writeString(ls, username, 16);
            writeUuid(ls, uuid);
            sendFrame(ls.toByteArray());
        } catch (Exception e) {
            fail("login write failed: " + e.getMessage());
            return;
        }

        try {
            while (state.get() == State.LOGGING_IN) {
                DataInputStream payload = readPacket();
                if (payload == null) {
                    return; // closed
                }
                int id = McVarInt.readVarInt(payload);
                if (id == ids.loginSetCompression) {
                    int threshold = McVarInt.readVarInt(payload);
                    compressionThreshold = threshold;
                } else if (id == ids.loginSuccess) {
                    byte[] rawUuid = new byte[16];
                    payload.readFully(rawUuid);
                    String name = McVarInt.readString(payload, 16);
                    int props = McVarInt.readVarInt(payload);
                    if (props != 0) {
                        fail("backend sent properties (online mode?) - refusing");
                        return;
                    }
                    // vanilla: Login Acknowledged moves both sides into
                    // CONFIGURATION (ServerboundLoginAcknowledgedPacket)
                    synchronized (pendingPluginMessages) {
                        ByteArrayOutputStream ack = new ByteArrayOutputStream();
                        McVarInt.writeVarInt(ack, ids.loginAcknowledged);
                        sendFrame(ack.toByteArray());
                        phase = Phase.CONFIGURATION;
                    }
                    transition(State.CONNECTED, "login success: " + name);
                    flushPendingPluginMessages();
                    if (runConfiguration()) {
                        runPlay();
                    }
                    return;
                } else if (id == ids.loginDisconnect) {
                    kicked(McNbt.readChatText(payload));
                    return;
                } else if (id == ids.loginEncryptionRequest) {
                    kicked("backend requires online-mode encryption; the Eagler gateway is offline-mode");
                    return;
                } else if (id == ids.loginPluginRequest) {
                    int msgId = McVarInt.readVarInt(payload);
                    ByteArrayOutputStream resp = new ByteArrayOutputStream();
                    McVarInt.writeVarInt(resp, ids.loginPluginResponse);
                    McVarInt.writeVarInt(resp, msgId);
                    resp.write(0); // successful=false
                    sendFrame(resp.toByteArray());
                } else if (id == ids.loginCookieRequest) {
                    sendCookieMiss(payload, ids.loginCookieResponse);
                } else {
                    // skip: already fully read into payload
                }
            }
        } catch (IOException e) {
            fail("login read failed: " + e.getMessage());
        }
    }

    // ==================== play relay ====================

    /**
     * Walks one CONFIGURATION phase. Returns true when the backend finished
     * configuration and the bridge entered PLAY, false when it ended.
     */
    private boolean runConfiguration() {
        try {
            while (state.get() == State.CONNECTED) {
                DataInputStream payload = readPacket();
                if (payload == null) {
                    return false;
                }
                int id = McVarInt.readVarInt(payload);
                if (id == ids.configKeepAlive) {
                    long cookie = payload.readLong();
                    ByteArrayOutputStream ka = new ByteArrayOutputStream();
                    McVarInt.writeVarInt(ka, ids.configKeepAliveResponse);
                    writeLong(ka, cookie);
                    sendFrame(ka.toByteArray());
                } else if (id == ids.configPing) {
                    int token = payload.readInt();
                    ByteArrayOutputStream pong = new ByteArrayOutputStream();
                    McVarInt.writeVarInt(pong, ids.configPong);
                    writeInt(pong, token);
                    sendFrame(pong.toByteArray());
                } else if (id == ids.configCustomPayload) {
                    deliverPluginMessage(payload);
                } else if (id == ids.configSelectKnownPacks) {
                    // Echo the server's pack list: the gateway resolves
                    // registries itself and never parses registry NBT, so
                    // claiming the packs keeps the registry burst small.
                    int n = McVarInt.readVarInt(payload);
                    if (n < 0 || n > 64) {
                        fail("known packs count insane: " + n);
                        return false;
                    }
                    ByteArrayOutputStream kp = new ByteArrayOutputStream();
                    McVarInt.writeVarInt(kp, ids.configSelectKnownPacksC2S);
                    McVarInt.writeVarInt(kp, n);
                    for (int i = 0; i < n * 3; i++) { // namespace, id, version
                        McVarInt.writeString(kp, McVarInt.readString(payload, 32767), 32767);
                    }
                    sendFrame(kp.toByteArray());
                } else if (id == ids.configCodeOfConduct) {
                    ByteArrayOutputStream coc = new ByteArrayOutputStream();
                    McVarInt.writeVarInt(coc, ids.configAcceptCodeOfConduct);
                    sendFrame(coc.toByteArray());
                } else if (id == ids.configCookieRequest) {
                    sendCookieMiss(payload, ids.configCookieResponse);
                } else if (id == ids.configFinish) {
                    // ack + phase flip under the send lock: no config-id
                    // custom payload may slip out after the ack
                    synchronized (pendingPluginMessages) {
                        ByteArrayOutputStream fin = new ByteArrayOutputStream();
                        McVarInt.writeVarInt(fin, ids.configFinishAck);
                        sendFrame(fin.toByteArray());
                        phase = Phase.PLAY;
                    }
                    return true;
                } else if (id == ids.configDisconnect) {
                    kicked(McNbt.readChatText(payload));
                    return false;
                } else {
                    // registry data, tags, feature flags, resource packs,
                    // links, dialogs...: payload already buffered, skip
                }
            }
        } catch (IOException e) {
            fail("configuration read failed: " + e.getMessage());
        }
        return false;
    }

    private void runPlay() {
        try {
            while (state.get() == State.CONNECTED) {
                DataInputStream payload = readPacket();
                if (payload == null) {
                    return;
                }
                int id = McVarInt.readVarInt(payload);
                if (id == ids.playKeepAlive) {
                    long cookie = payload.readLong();
                    ByteArrayOutputStream ka = new ByteArrayOutputStream();
                    McVarInt.writeVarInt(ka, ids.playKeepAliveResponse);
                    writeLong(ka, cookie);
                    sendFrame(ka.toByteArray());
                } else if (id == ids.playPing) {
                    int token = payload.readInt();
                    ByteArrayOutputStream pong = new ByteArrayOutputStream();
                    McVarInt.writeVarInt(pong, ids.playPong);
                    writeInt(pong, token);
                    sendFrame(pong.toByteArray());
                } else if (id == ids.playCustomPayload) {
                    deliverPluginMessage(payload);
                } else if (id == ids.playSystemChat) {
                    String text = McNbt.readChatText(payload);
                    payload.readUnsignedByte(); // overlay flag
                    try {
                        listener.onChat(this, text);
                    } catch (Exception e) {
                        listener.onError(this, "chat listener error: " + e);
                    }
                } else if (id == ids.playStartConfiguration) {
                    // proxy server switch / reconfigure: acknowledge and
                    // walk configuration again; plugin messages sent
                    // meanwhile go out as configuration custom payloads
                    synchronized (pendingPluginMessages) {
                        phase = Phase.CONFIGURATION;
                        ByteArrayOutputStream ack = new ByteArrayOutputStream();
                        McVarInt.writeVarInt(ack, ids.playConfigurationAck);
                        sendFrame(ack.toByteArray());
                    }
                    if (!runConfiguration()) {
                        return;
                    }
                } else if (id == ids.playCookieRequest) {
                    sendCookieMiss(payload, ids.playCookieResponse);
                } else if (id == ids.playDisconnect) {
                    kicked(McNbt.readChatText(payload));
                    return;
                } else {
                    // unknown packet: payload already buffered, nothing to do
                }
            }
        } catch (IOException e) {
            fail("play read failed: " + e.getMessage());
        }
    }

    /** Decodes a clientbound custom payload body and hands it to the listener. */
    private void deliverPluginMessage(DataInputStream payload) throws IOException {
        String channel = McVarInt.readString(payload, 32767);
        byte[] data = payload.readAllBytes();
        if (data.length > MAX_CLIENTBOUND_PAYLOAD) {
            listener.onError(this, "dropping oversized plugin message on " + channel
                    + " (" + data.length + " bytes)");
            return;
        }
        try {
            listener.onPluginMessage(this, channel, data);
        } catch (Exception e) {
            listener.onError(this, "plugin message listener error: " + e);
        }
    }

    /** Answers a cookie request with "no cookie stored" (identifier + absent payload). */
    private void sendCookieMiss(DataInputStream payload, int responseId) throws IOException {
        String key = McVarInt.readString(payload, 32767);
        ByteArrayOutputStream resp = new ByteArrayOutputStream();
        McVarInt.writeVarInt(resp, responseId);
        McVarInt.writeString(resp, key, 32767);
        resp.write(0); // payload absent
        sendFrame(resp.toByteArray());
    }

    // ==================== outbound ====================

    /**
     * Sends an unsigned chat message to the backend. Returns false when the
     * bridge is not in the play state or the write failed.
     */
    public boolean sendChat(String message) {
        if (state.get() != State.CONNECTED || phase != Phase.PLAY) {
            return false;
        }
        if (message == null || message.isEmpty() || message.length() > 256) {
            return false;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        McVarInt.writeVarInt(buf, ids.playChat);
        McVarInt.writeString(buf, message, 256);
        // signature-less chat: timestamp, salt, no signature, empty acknowledgements
        writeLong(buf, System.currentTimeMillis());
        writeLong(buf, 0L);
        buf.write(0); // has signature = false
        // LastSeenMessages.Update: offset varint, fixed 20-bit set (3 bytes), checksum byte
        McVarInt.writeVarInt(buf, 0);
        buf.write(0);
        buf.write(0);
        buf.write(0);
        buf.write(0);
        try {
            sendFrame(buf.toByteArray());
            return true;
        } catch (IOException e) {
            listener.onError(this, "chat write failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends an unsigned slash command (ServerboundChatCommandPacket) to the
     * backend; {@code command} is given without the leading '/'. Returns
     * false when the bridge is not in the play state or the write failed.
     */
    public boolean sendCommand(String command) {
        if (state.get() != State.CONNECTED || phase != Phase.PLAY) {
            return false;
        }
        if (command == null || command.isEmpty() || command.length() > 32767) {
            return false;
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        McVarInt.writeVarInt(buf, ids.playChatCommand);
        McVarInt.writeString(buf, command, 32767);
        try {
            sendFrame(buf.toByteArray());
            return true;
        } catch (IOException e) {
            listener.onError(this, "command write failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Sends a plugin message (custom payload) to the backend. While the
     * backend is still connecting/logging in, the message is queued (up to
     * {@link #MAX_PENDING_PLUGIN_MESSAGES}) and flushed in order once it
     * reaches CONFIGURATION or PLAY; the packet id follows the current
     * phase. Returns false when the bridge is closed, the channel/payload is
     * invalid, the queue is full, or the write failed.
     */
    public boolean sendPluginMessage(String channel, byte[] data) {
        if (channel == null || channel.isEmpty() || channel.length() > 32767
                || data == null || data.length > MAX_SERVERBOUND_PAYLOAD) {
            return false;
        }
        synchronized (pendingPluginMessages) {
            State st = state.get();
            if (st == State.CLOSED) {
                return false;
            }
            if (st != State.CONNECTED || phase == Phase.LOGIN) {
                if (pendingPluginMessages.size() >= MAX_PENDING_PLUGIN_MESSAGES) {
                    return false;
                }
                pendingPluginMessages.add(new Object[] { channel, data });
                return true;
            }
            return writePluginMessage(channel, data);
        }
    }

    /** Writes one custom payload with the current phase's id; caller holds the queue lock. */
    private boolean writePluginMessage(String channel, byte[] data) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(data.length + channel.length() + 8);
        McVarInt.writeVarInt(buf, phase == Phase.PLAY ? ids.playCustomPayloadC2S : ids.configCustomPayloadC2S);
        McVarInt.writeString(buf, channel, 32767);
        buf.write(data, 0, data.length);
        try {
            sendFrame(buf.toByteArray());
            return true;
        } catch (IOException e) {
            listener.onError(this, "plugin message write failed: " + e.getMessage());
            return false;
        }
    }

    private void flushPendingPluginMessages() {
        synchronized (pendingPluginMessages) {
            Object[] m;
            while ((m = pendingPluginMessages.poll()) != null) {
                writePluginMessage((String) m[0], (byte[]) m[1]);
            }
        }
    }

    // ==================== framing ====================

    /**
     * Reads one packet payload with compression handling. Returns null when
     * the connection closed. The returned stream contains exactly one packet
     * body (packet id already included, NOT yet consumed).
     */
    private DataInputStream readPacket() throws IOException {
        DataInputStream i = in;
        int length = McVarInt.readVarInt(i);
        if (length <= 0 || length > (1 << 21)) {
            fail("backend frame length insane: " + length);
            return null;
        }
        if (compressionThreshold >= 0) {
            int dataLen = McVarInt.readVarInt(i);
            int payloadLen = length - McVarInt.varIntSize(dataLen);
            if (payloadLen < 0) {
                fail("compressed frame framing broken");
                return null;
            }
            byte[] compressed = new byte[payloadLen];
            i.readFully(compressed);
            if (dataLen == 0) {
                return new DataInputStream(new ByteArrayInputStream(compressed));
            }
            if (dataLen > (1 << 21)) {
                fail("uncompressed size insane: " + dataLen);
                return null;
            }
            Inflater inf = new Inflater();
            inf.setInput(compressed);
            byte[] raw = new byte[dataLen];
            try {
                int produced = 0;
                while (!inf.finished() && produced < dataLen) {
                    produced += inf.inflate(raw, produced, dataLen - produced);
                    if (produced == 0 && inf.needsInput()) {
                        fail("zlib stream truncated");
                        return null;
                    }
                }
                if (produced != dataLen) {
                    fail("zlib size mismatch: " + produced + " != " + dataLen);
                    return null;
                }
            } catch (DataFormatException e) {
                fail("zlib corrupt: " + e.getMessage());
                return null;
            } finally {
                inf.end();
            }
            return new DataInputStream(new ByteArrayInputStream(raw));
        }
        byte[] raw = new byte[length];
        i.readFully(raw);
        return new DataInputStream(new ByteArrayInputStream(raw));
    }

    /** Frames and writes one packet (compression applied when active). */
    private void sendFrame(byte[] packet) throws IOException {
        synchronized (writeLock) {
            OutputStream o = out;
            if (o == null || state.get() == State.CLOSED) {
                throw new IOException("bridge closed");
            }
            ByteArrayOutputStream frame = new ByteArrayOutputStream(packet.length + 16);
            if (compressionThreshold >= 0) {
                if (packet.length >= compressionThreshold) {
                    Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
                    def.setInput(packet);
                    def.finish();
                    ByteArrayOutputStream zipped = new ByteArrayOutputStream(packet.length / 2 + 32);
                    byte[] tmp = new byte[8192];
                    while (!def.finished()) {
                        int n = def.deflate(tmp);
                        zipped.write(tmp, 0, n);
                    }
                    def.end();
                    byte[] z = zipped.toByteArray();
                    McVarInt.writeVarInt(frame, McVarInt.varIntSize(packet.length) + z.length);
                    McVarInt.writeVarInt(frame, packet.length);
                    frame.write(z, 0, z.length);
                } else {
                    McVarInt.writeVarInt(frame, McVarInt.varIntSize(0) + packet.length);
                    McVarInt.writeVarInt(frame, 0);
                    frame.write(packet, 0, packet.length);
                }
            } else {
                McVarInt.writeVarInt(frame, packet.length);
                frame.write(packet, 0, packet.length);
            }
            o.write(frame.toByteArray());
            o.flush();
        }
    }

    // ==================== helpers ====================

    private void transition(State newState, String detail) {
        State old = state.getAndSet(newState);
        if (old == newState) {
            return; // never notify the same transition twice
        }
        try {
            listener.onStateChange(this, newState, detail);
        } catch (Exception ignored) {
        }
    }

    private void fail(String detail) {
        if (state.get() == State.CLOSED) {
            return; // closed on purpose; the read error is the consequence
        }
        transition(State.CLOSED, detail);
        try {
            listener.onError(this, detail);
        } catch (Exception ignored) {
        }
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void kicked(String reason) {
        try {
            listener.onKicked(this, reason);
        } catch (Exception ignored) {
        }
        close("kicked: " + reason);
    }

    private static void writeUuid(ByteArrayOutputStream out, UUID u) {
        writeLong(out, u.getMostSignificantBits());
        writeLong(out, u.getLeastSignificantBits());
    }

    private static void writeInt(ByteArrayOutputStream out, int v) {
        out.write(v >>> 24);
        out.write(v >>> 16);
        out.write(v >>> 8);
        out.write(v);
    }

    private static void writeLong(ByteArrayOutputStream out, long v) {
        out.write((int) (v >>> 56));
        out.write((int) (v >>> 48));
        out.write((int) (v >>> 40));
        out.write((int) (v >>> 32));
        out.write((int) (v >>> 24));
        out.write((int) (v >>> 16));
        out.write((int) (v >>> 8));
        out.write((int) v);
    }
}
