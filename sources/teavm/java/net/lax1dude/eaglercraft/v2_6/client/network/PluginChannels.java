package net.lax1dude.eaglercraft.v2_6.client.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * EaglerCraft 26.1.2 - the client's plugin-message (custom payload)
 * channel layer: the vanilla {@code minecraft:register} /
 * {@code minecraft:unregister} convention plus dispatch of incoming
 * payloads to per-channel handlers.
 *
 * <p>Server APIs such as Apollo detect a capable client by the channels
 * it registers ({@code lunar:apollo}, {@code apollo:json}) and talk to it
 * over plugin messages. Features call {@link #register} once at startup;
 * every time the client enters the play phase it announces the whole set
 * with one {@code minecraft:register} message (NUL-separated channel ids,
 * the Bukkit/Paper convention).</p>
 *
 * <p>Pure Java (java.util only), so it compiles under TeaVM and runs in
 * JVM unit tests unchanged. Single-threaded by design: everything runs on
 * the game thread ({@code Connection#tick} drains the socket there).</p>
 *
 * <p>Safety rules: a handler that throws is logged and the message
 * dropped - a bad payload never disconnects the client. Payloads on
 * channels nobody registered are ignored (debug log only).</p>
 */
public final class PluginChannels {

        public static final String REGISTER = "minecraft:register";
        public static final String UNREGISTER = "minecraft:unregister";
        public static final String BRAND = "minecraft:brand";

        /** Vanilla serverbound custom payload cap (ServerboundCustomPayloadPacket). */
        public static final int MAX_SERVERBOUND_PAYLOAD = 32767;
        /** Vanilla clientbound custom payload cap (ClientboundCustomPayloadPacket). */
        public static final int MAX_CLIENTBOUND_PAYLOAD = 1048576;
        /** Entries kept in the recent-traffic ring (debug screens). */
        public static final int RECENT_LIMIT = 64;

        /** Receives the payloads of one channel. */
        public interface Handler {
                void onMessage(String channel, byte[] data);
        }

        /** Sends one plugin message on the live connection; false = not sent. */
        public interface Sender {
                boolean send(String channel, byte[] data);
        }

        /** Lifecycle hooks for features that keep per-connection state. */
        public interface SessionListener {
                /** The client entered play and announced its channels. */
                void onJoin();

                /** Disconnect or server switch: drop every per-connection state. */
                void onReset();
        }

        /** Log sink (ClientMain.log in the browser, stdout in tests). */
        public interface Log {
                void info(String msg);

                void warn(String msg);
        }

        /** One recent plugin message, for debug screens. */
        public static final class Traffic {
                public final boolean inbound;
                public final String channel;
                public final int size;
                public final boolean handled;

                Traffic(boolean inbound, String channel, int size, boolean handled) {
                        this.inbound = inbound;
                        this.channel = channel;
                        this.size = size;
                        this.handled = handled;
                }

                @Override
                public String toString() {
                        return (inbound ? "<- " : "-> ") + channel + " (" + size + " B"
                                        + (inbound && !handled ? ", unhandled" : "") + ")";
                }
        }

        private static final Map<String, Handler> handlers = new LinkedHashMap<>();
        private static final List<SessionListener> sessionListeners = new ArrayList<>();
        /** Channels the server announced it listens on (per connection). */
        private static final Set<String> serverChannels = new LinkedHashSet<>();
        private static final List<Traffic> recent = new ArrayList<>();

        private static Sender sender;
        private static boolean joined;
        private static boolean debug;
        private static int receivedCount;
        private static int sentCount;
        private static Log log = new Log() {
                @Override
                public void info(String msg) {
                        System.out.println(msg);
                }

                @Override
                public void warn(String msg) {
                        System.out.println(msg);
                }
        };

        private PluginChannels() {
        }

        // ==================== setup ====================

        public static void setLog(Log l) {
                if (l != null) {
                        log = l;
                }
        }

        /** Debug mode logs every plugin message, including ignored ones. */
        public static void setDebug(boolean on) {
                debug = on;
        }

        public static boolean isDebug() {
                return debug;
        }

        /**
         * Registers a handler for {@code channel}. Registering while already in
         * play announces the new channel immediately.
         */
        public static void register(String channel, Handler handler) {
                if (!isValidChannel(channel) || handler == null) {
                        throw new IllegalArgumentException("invalid plugin channel: " + channel);
                }
                boolean isNew = !handlers.containsKey(channel);
                handlers.put(channel, handler);
                if (isNew && joined) {
                        sendRaw(REGISTER, encodeChannelList(Collections.singletonList(channel)));
                }
        }

        public static void addSessionListener(SessionListener l) {
                if (l != null && !sessionListeners.contains(l)) {
                        sessionListeners.add(l);
                }
        }

        /** Channels this client handles, in registration order. */
        public static List<String> getChannels() {
                return new ArrayList<>(handlers.keySet());
        }

        // ==================== connection lifecycle ====================

        /** A connection reached a phase that can carry plugin messages. */
        public static void attach(Sender s) {
                sender = s;
        }

        /**
         * The client entered play: announce every registered channel in one
         * {@code minecraft:register} message, then notify session listeners.
         */
        public static void onJoin() {
                joined = true;
                if (!handlers.isEmpty()) {
                        sendRaw(REGISTER, encodeChannelList(handlers.keySet()));
                }
                for (SessionListener l : new ArrayList<>(sessionListeners)) {
                        try {
                                l.onJoin();
                        } catch (Throwable t) {
                                log.warn("[PluginChannels] join hook failed: " + t);
                        }
                }
        }

        /** Disconnect / server switch: forget everything per-connection. */
        public static void reset() {
                sender = null;
                joined = false;
                serverChannels.clear();
                for (SessionListener l : new ArrayList<>(sessionListeners)) {
                        try {
                                l.onReset();
                        } catch (Throwable t) {
                                log.warn("[PluginChannels] reset hook failed: " + t);
                        }
                }
        }

        public static boolean isJoined() {
                return joined;
        }

        // ==================== traffic ====================

        /**
         * Sends a plugin message to the server. Refused (false) when not
         * connected, the channel id is invalid, or the payload exceeds the
         * vanilla serverbound cap.
         */
        public static boolean send(String channel, byte[] data) {
                if (!isValidChannel(channel) || data == null || data.length > MAX_SERVERBOUND_PAYLOAD) {
                        log.warn("[PluginChannels] refused to send on " + channel
                                        + " (" + (data == null ? "null" : data.length + " B") + ")");
                        return false;
                }
                return sendRaw(channel, data);
        }

        private static boolean sendRaw(String channel, byte[] data) {
                Sender s = sender;
                if (s == null) {
                        return false;
                }
                boolean ok;
                try {
                        ok = s.send(channel, data);
                } catch (Throwable t) {
                        log.warn("[PluginChannels] send on " + channel + " failed: " + t);
                        ok = false;
                }
                if (ok) {
                        sentCount++;
                        record(new Traffic(false, channel, data.length, true));
                        if (debug) {
                                log.info("[PluginChannels] -> " + channel + " (" + data.length + " B)");
                        }
                }
                return ok;
        }

        /**
         * An incoming plugin message (configuration or play phase). Never
         * throws: handler failures are logged and the message dropped.
         */
        public static void dispatch(String channel, byte[] data) {
                if (channel == null || data == null) {
                        return;
                }
                receivedCount++;
                if (data.length > MAX_CLIENTBOUND_PAYLOAD) {
                        log.warn("[PluginChannels] dropped oversized payload on " + channel);
                        record(new Traffic(true, channel, data.length, false));
                        return;
                }
                boolean handled = true;
                if (REGISTER.equals(channel)) {
                        serverChannels.addAll(decodeChannelList(data));
                } else if (UNREGISTER.equals(channel)) {
                        serverChannels.removeAll(decodeChannelList(data));
                } else {
                        Handler h = handlers.get(channel);
                        if (h == null) {
                                handled = false;
                        } else {
                                try {
                                        h.onMessage(channel, data);
                                } catch (Throwable t) {
                                        log.warn("[PluginChannels] handler for " + channel + " failed: " + t);
                                }
                        }
                }
                record(new Traffic(true, channel, data.length, handled));
                if (debug) {
                        log.info("[PluginChannels] <- " + channel + " (" + data.length + " B"
                                        + (handled ? "" : ", no handler - ignored") + ")");
                }
        }

        /** True when the server announced it listens on {@code channel}. */
        public static boolean serverListensOn(String channel) {
                return serverChannels.contains(channel);
        }

        public static List<String> getServerChannels() {
                return new ArrayList<>(serverChannels);
        }

        public static List<Traffic> getRecent() {
                return new ArrayList<>(recent);
        }

        public static int getReceivedCount() {
                return receivedCount;
        }

        public static int getSentCount() {
                return sentCount;
        }

        private static void record(Traffic t) {
                recent.add(t);
                if (recent.size() > RECENT_LIMIT) {
                        recent.remove(0);
                }
        }

        // ==================== codec ====================

        /** {@code minecraft:register} body: UTF-8 channel ids joined by NUL. */
        public static byte[] encodeChannelList(Iterable<String> channels) {
                StringBuilder sb = new StringBuilder();
                for (String c : channels) {
                        if (sb.length() > 0) {
                                sb.append('\0');
                        }
                        sb.append(c);
                }
                return sb.toString().getBytes(StandardCharsets.UTF_8);
        }

        public static List<String> decodeChannelList(byte[] data) {
                List<String> out = new ArrayList<>();
                String all = new String(data, StandardCharsets.UTF_8);
                int start = 0;
                for (int i = 0; i <= all.length(); i++) {
                        if (i == all.length() || all.charAt(i) == '\0') {
                                if (i > start) {
                                        String c = all.substring(start, i);
                                        if (isValidChannel(c)) {
                                                out.add(c);
                                        }
                                }
                                start = i + 1;
                        }
                }
                return out;
        }

        /**
         * Vanilla {@code Identifier} rules: {@code namespace:path}, namespace
         * [a-z0-9_.-], path [a-z0-9_.-/], at most 32767 chars overall.
         */
        public static boolean isValidChannel(String c) {
                if (c == null || c.isEmpty() || c.length() > 32767) {
                        return false;
                }
                int colon = c.indexOf(':');
                if (colon <= 0 || colon == c.length() - 1) {
                        return false;
                }
                for (int i = 0; i < c.length(); i++) {
                        char ch = c.charAt(i);
                        boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')
                                        || ch == '_' || ch == '.' || ch == '-'
                                        || (i > colon && ch == '/') || i == colon;
                        if (!ok) {
                                return false;
                        }
                }
                return true;
        }

        /** Test hook: back to a fresh process state. */
        public static void resetAllForTests() {
                handlers.clear();
                sessionListeners.clear();
                serverChannels.clear();
                recent.clear();
                sender = null;
                joined = false;
                debug = false;
                receivedCount = 0;
                sentCount = 0;
        }
}
