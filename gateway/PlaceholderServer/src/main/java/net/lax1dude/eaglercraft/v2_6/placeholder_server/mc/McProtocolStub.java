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
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * EaglerCraft 26.1.2 - minimal Minecraft protocol backend for gateway smoke
 * tests.
 *
 * <p>Speaks the same protocol-775 subset as {@link McBridge}: handshake,
 * login (with Set Compression to exercise the zlib path), the vanilla
 * configuration phase (brand, a {@code stub:config} plugin message, known
 * packs, keepalive, ping, finish), keepalives on a timer, chat echo (every
 * unsigned client chat comes back as a system chat message; {@code !reconfig}
 * triggers Start Configuration), and plugin-message echo: every client
 * plugin message comes back on the same channel, {@code minecraft:register}
 * is answered on {@code stub:registered}, and {@code stub:status} reports
 * how the configuration phase was answered. This is the other end of the e2e multiplayer smoke test:
 * browser &rarr; Eagler WS listener &rarr; {@link McBridge} &rarr; this stub.</p>
 *
 * <h2>Usage</h2>
 * <pre>
 *   java -cp PlaceholderServer-1.0.0.jar \
 *        net.lax1dude.eaglercraft.v2_6.placeholder_server.mc.McProtocolStub \
 *        [--port 25577] [--interval-ms 3000] [--no-echo] [--verbose]
 * </pre>
 */
public final class McProtocolStub {

    private static final int COMPRESSION_THRESHOLD = 256;

    private final int port;
    private final long keepAliveIntervalMs;
    private final boolean echoChat;
    private final boolean verbose;
    private final AtomicInteger sessions = new AtomicInteger();

    private McProtocolStub(int port, long keepAliveIntervalMs, boolean echoChat, boolean verbose) {
        this.port = port;
        this.keepAliveIntervalMs = keepAliveIntervalMs;
        this.echoChat = echoChat;
        this.verbose = verbose;
    }

    public static void main(String[] args) throws Exception {
        int port = 25577;
        long interval = 3000;
        boolean echo = true;
        boolean verbose = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port": port = Integer.parseInt(args[++i]); break;
                case "--interval-ms": interval = Long.parseLong(args[++i]); break;
                case "--no-echo": echo = false; break;
                case "--verbose": verbose = true; break;
                case "--help":
                case "-h":
                    System.out.println("Usage: McProtocolStub [--port N] [--interval-ms N] [--no-echo] [--verbose]");
                    return;
                default:
                    System.err.println("Unknown argument: " + args[i] + " (try --help)");
                    System.exit(2);
            }
        }
        new McProtocolStub(port, interval, echo, verbose).run();
    }

    private void run() throws IOException {
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(port));
        System.out.println("[mc-stub] listening on 0.0.0.0:" + port
                + " (compression=" + COMPRESSION_THRESHOLD + ", keepalive=" + keepAliveIntervalMs + "ms)");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                ss.close();
            } catch (IOException ignored) {
            }
        }, "mc-stub-shutdown"));
        while (true) {
            Socket s = ss.accept();
            int n = sessions.incrementAndGet();
            Thread t = new Thread(() -> handle(s, n), "mc-stub-session-" + n);
            t.setDaemon(true);
            t.start();
        }
    }

    // ==================== one session ====================

    private void handle(Socket socket, int sessionNo) {
        int threshold = -1;
        try {
            socket.setTcpNoDelay(true);
            socket.setSoTimeout(60_000);
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream(), 1 << 14));
            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), 1 << 14);

            // ===== login state =====
            DataInputStream pkt = readPacket(in, threshold);
            if (pkt == null) {
                return;
            }
            int id = McVarInt.readVarInt(pkt);
            if (id != 0x00) {
                log(sessionNo, "first packet not handshake (id=" + id + ") - dropping");
                return;
            }
            int protocol = McVarInt.readVarInt(pkt);
            String addr = McVarInt.readString(pkt, 255);
            pkt.readUnsignedShort();
            int nextState = McVarInt.readVarInt(pkt);
            log(sessionNo, "handshake protocol=" + protocol + " addr=" + addr + " nextState=" + nextState);
            if (nextState != 2) {
                return; // status pings unsupported
            }

            pkt = readPacket(in, threshold);
            if (pkt == null) {
                return;
            }
            id = McVarInt.readVarInt(pkt);
            if (id != 0x00) {
                log(sessionNo, "expected login start, got id=" + id);
                return;
            }
            String name = McVarInt.readString(pkt, 16);
            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
            log(sessionNo, "login start name=" + name + " uuid=" + uuid);

            sendSetCompression(out, COMPRESSION_THRESHOLD);
            threshold = COMPRESSION_THRESHOLD;
            sendLoginSuccess(out, uuid, name);

            // vanilla 26.1.2: the client acknowledges, then CONFIGURATION
            pkt = readPacket(in, threshold);
            if (pkt == null) {
                return;
            }
            id = McVarInt.readVarInt(pkt);
            if (id != 0x03) {
                log(sessionNo, "expected login acknowledged (0x03), got id=0x" + Integer.toHexString(id));
                return;
            }

            final Object writeLock = new Object();
            final int[] stats = new int[3]; // [0]=known packs echoed, [1]=last pong, [2]=config keepalive answered
            if (!runConfiguration(in, out, writeLock, sessionNo, stats)) {
                return;
            }
            sendPluginMessage(out, writeLock, true, "stub:status",
                    ("packs=" + stats[0] + " pong=" + stats[1] + " keepalive=" + stats[2]
                            + " phase=play").getBytes(StandardCharsets.UTF_8));

            // ===== play state =====
            AtomicInteger keepAliveCounter = new AtomicInteger();
            Thread ka = new Thread(() -> {
                while (true) {
                    try {
                        Thread.sleep(keepAliveIntervalMs);
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        synchronized (writeLock) {
                            sendKeepAlive(out, keepAliveCounter.getAndIncrement());
                        }
                    } catch (IOException e) {
                        return;
                    }
                }
            }, "mc-stub-keepalive-" + sessionNo);
            ka.setDaemon(true);
            ka.start();

            while (true) {
                DataInputStream p = readPacket(in, threshold);
                if (p == null) {
                    return;
                }
                int pid = McVarInt.readVarInt(p);
                if (pid == 0x1C) { // keep alive response
                    long cookie = p.readLong();
                    log(sessionNo, "keepalive response " + cookie);
                } else if (pid == 0x09) { // chat message (ServerboundChatPacket)
                    String msg = McVarInt.readString(p, 256);
                    p.skipBytes(16); // timestamp + salt
                    if (p.readUnsignedByte() != 0) {
                        p.skipBytes(256); // message signature
                    }
                    McVarInt.readVarInt(p); // last-seen offset
                    p.skipBytes(3 + 1);     // 20-bit acknowledged set + checksum
                    log(sessionNo, "chat: " + msg);
                    if ("!reconfig".equals(msg)) {
                        // exercise Start Configuration -> ack -> config again
                        synchronized (writeLock) {
                            ByteArrayOutputStream sc = new ByteArrayOutputStream();
                            McVarInt.writeVarInt(sc, 0x76);
                            writeFrame(out, sc.toByteArray(), COMPRESSION_THRESHOLD);
                        }
                        DataInputStream ack;
                        do { // drain play packets in flight until the ack
                            ack = readPacket(in, threshold);
                            if (ack == null) {
                                return;
                            }
                        } while (McVarInt.readVarInt(ack) != 0x10);
                        if (!runConfiguration(in, out, writeLock, sessionNo, stats)) {
                            return;
                        }
                        sendPluginMessage(out, writeLock, true, "stub:status",
                                ("reconfigured packs=" + stats[0] + " pong=" + stats[1]
                                        + " phase=play").getBytes(StandardCharsets.UTF_8));
                    } else if (echoChat) {
                        synchronized (writeLock) {
                            sendSystemChat(out, name + " echoed: " + msg);
                        }
                    }
                } else if (pid == 0x07) { // chat command (unsigned)
                    String cmd = McVarInt.readString(p, 32767);
                    log(sessionNo, "command: /" + cmd);
                    synchronized (writeLock) {
                        sendSystemChat(out, "ran command: /" + cmd);
                    }
                } else if (pid == 0x16) { // custom payload (plugin message)
                    handleClientPluginMessage(p, out, writeLock, true, sessionNo);
                } else {
                    log(sessionNo, "ignoring play packet id=0x" + Integer.toHexString(pid));
                }
            }
        } catch (IOException e) {
            log(sessionNo, "session ended: " + e);
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * One vanilla CONFIGURATION phase: brand + a config-phase plugin message,
     * known packs, keepalive and ping, then finish; returns once the client
     * acknowledged the finish. Client plugin messages are echoed (config ids).
     */
    private boolean runConfiguration(DataInputStream in, OutputStream out, Object writeLock,
            int sessionNo, int[] stats) throws IOException {
        int threshold = COMPRESSION_THRESHOLD;
        synchronized (writeLock) {
            ByteArrayOutputStream brand = new ByteArrayOutputStream();
            McVarInt.writeString(brand, "mc-stub", 32767);
            sendPluginMessage(out, writeLock, false, "minecraft:brand", brand.toByteArray());
            sendPluginMessage(out, writeLock, false, "stub:config",
                    "hello-config".getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream kp = new ByteArrayOutputStream();
            McVarInt.writeVarInt(kp, 0x0E); // select known packs
            McVarInt.writeVarInt(kp, 1);
            McVarInt.writeString(kp, "minecraft", 32767);
            McVarInt.writeString(kp, "core", 32767);
            McVarInt.writeString(kp, "26.1.2", 32767);
            writeFrame(out, kp.toByteArray(), threshold);
            ByteArrayOutputStream cka = new ByteArrayOutputStream();
            McVarInt.writeVarInt(cka, 0x04); // config keep alive
            writeLong(cka, 0xC0FFEEL);
            writeFrame(out, cka.toByteArray(), threshold);
            ByteArrayOutputStream ping = new ByteArrayOutputStream();
            McVarInt.writeVarInt(ping, 0x05); // config ping
            ping.write(0);
            ping.write(0);
            ping.write(0);
            ping.write(42);
            writeFrame(out, ping.toByteArray(), threshold);
        }
        // like vanilla/Paper, finish only once the client answered the
        // known packs (the registry burst depends on that answer)
        while (true) {
            DataInputStream p = readPacket(in, threshold);
            if (p == null) {
                return false;
            }
            int pid = McVarInt.readVarInt(p);
            if (pid == 0x07) { // select known packs
                stats[0] = McVarInt.readVarInt(p);
                synchronized (writeLock) {
                    ByteArrayOutputStream fin = new ByteArrayOutputStream();
                    McVarInt.writeVarInt(fin, 0x03); // finish configuration
                    writeFrame(out, fin.toByteArray(), threshold);
                }
            } else if (pid == 0x05) { // pong
                stats[1] = p.readInt();
            } else if (pid == 0x04) { // keep alive response
                stats[2] = p.readLong() == 0xC0FFEEL ? 1 : -1;
            } else if (pid == 0x02) { // custom payload
                handleClientPluginMessage(p, out, writeLock, false, sessionNo);
            } else if (pid == 0x03) { // finish ack
                log(sessionNo, "configuration finished: packs=" + stats[0] + " pong=" + stats[1]);
                return true;
            } else {
                log(sessionNo, "ignoring config packet id=0x" + Integer.toHexString(pid));
            }
        }
    }

    /**
     * {@code minecraft:register} is answered on {@code stub:registered} with
     * the registered channel list; any other channel is echoed back verbatim
     * on the same channel (the round-trip the gateway relay tests assert).
     */
    private void handleClientPluginMessage(DataInputStream p, OutputStream out, Object writeLock,
            boolean play, int sessionNo) throws IOException {
        String channel = McVarInt.readString(p, 32767);
        byte[] data = p.readAllBytes();
        log(sessionNo, (play ? "play" : "config") + " plugin message " + channel + " (" + data.length + " bytes)");
        if ("minecraft:register".equals(channel)) {
            sendPluginMessage(out, writeLock, play, "stub:registered", data);
        } else if (!"minecraft:brand".equals(channel)) {
            sendPluginMessage(out, writeLock, play, channel, data);
        }
    }

    private void sendPluginMessage(OutputStream out, Object writeLock, boolean play,
            String channel, byte[] data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(data.length + 32);
        McVarInt.writeVarInt(buf, play ? 0x18 : 0x01);
        McVarInt.writeString(buf, channel, 32767);
        buf.write(data, 0, data.length);
        synchronized (writeLock) {
            writeFrame(out, buf.toByteArray(), COMPRESSION_THRESHOLD);
        }
    }

    // ==================== outbound ====================

    private void sendSetCompression(OutputStream out, int threshold) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        McVarInt.writeVarInt(buf, 0x03);
        McVarInt.writeVarInt(buf, threshold);
        writeFrame(out, buf.toByteArray(), -1);
    }

    private void sendLoginSuccess(OutputStream out, UUID uuid, String name) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        McVarInt.writeVarInt(buf, 0x02);
        writeLong(buf, uuid.getMostSignificantBits());
        writeLong(buf, uuid.getLeastSignificantBits());
        McVarInt.writeString(buf, name, 16);
        McVarInt.writeVarInt(buf, 0); // properties
        writeFrame(out, buf.toByteArray(), COMPRESSION_THRESHOLD);
    }

    private void sendKeepAlive(OutputStream out, int counter) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        McVarInt.writeVarInt(buf, 0x2C); // play keep alive (26.1.2)
        writeLong(buf, 0x5EED000000000000L | counter);
        writeFrame(out, buf.toByteArray(), COMPRESSION_THRESHOLD);
    }

    private void sendSystemChat(OutputStream out, String text) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        McVarInt.writeVarInt(buf, 0x79); // system chat (26.1.2)
        McVarInt.writeNbtString(buf, text);
        buf.write(0); // overlay = false
        writeFrame(out, buf.toByteArray(), COMPRESSION_THRESHOLD);
    }

    // ==================== framing ====================

    /** Reads one framed packet honoring (optional) compression. */
    private static DataInputStream readPacket(DataInputStream in, int threshold) throws IOException {
        int length = McVarInt.readVarInt(in);
        if (length <= 0 || length > (1 << 21)) {
            throw new IOException("client frame length insane: " + length);
        }
        if (threshold >= 0) {
            int dataLen = McVarInt.readVarInt(in);
            int payloadLen = length - McVarInt.varIntSize(dataLen);
            byte[] compressed = new byte[payloadLen];
            in.readFully(compressed);
            if (dataLen == 0) {
                return new DataInputStream(new ByteArrayInputStream(compressed));
            }
            Inflater inf = new Inflater();
            inf.setInput(compressed);
            byte[] raw = new byte[dataLen];
            try {
                int produced = 0;
                while (!inf.finished() && produced < dataLen) {
                    produced += inf.inflate(raw, produced, dataLen - produced);
                    if (produced == 0 && inf.needsInput()) {
                        throw new IOException("zlib stream truncated");
                    }
                }
                if (produced != dataLen) {
                    throw new IOException("zlib size mismatch");
                }
            } catch (java.util.zip.DataFormatException e) {
                throw new IOException("zlib corrupt: " + e.getMessage());
            } finally {
                inf.end();
            }
            return new DataInputStream(new ByteArrayInputStream(raw));
        }
        byte[] raw = new byte[length];
        in.readFully(raw);
        return new DataInputStream(new ByteArrayInputStream(raw));
    }

    private static void writeFrame(OutputStream out, byte[] packet, int threshold) throws IOException {
        ByteArrayOutputStream frame = new ByteArrayOutputStream(packet.length + 16);
        if (threshold >= 0) {
            if (packet.length >= threshold) {
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
        out.write(frame.toByteArray());
        out.flush();
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

    private void log(int sessionNo, String msg) {
        if (verbose) {
            System.out.println("[mc-stub#" + sessionNo + "] " + msg);
        }
    }
}
