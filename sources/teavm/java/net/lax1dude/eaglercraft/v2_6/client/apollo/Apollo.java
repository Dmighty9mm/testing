package net.lax1dude.eaglercraft.v2_6.client.apollo;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.module.ApolloModules;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoCodec;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoJson;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoValues;
import net.lax1dude.eaglercraft.v2_6.client.network.PluginChannels;

/**
 * Client side of the Apollo server API: the {@code lunar:apollo}
 * (protobuf {@code google.protobuf.Any}) and {@code apollo:json} (JSON
 * with "@type") plugin channels, the player handshake, the message router
 * and the module enable/option state servers drive with
 * OverrideConfigurableSettingsMessage.
 *
 * <p>The channel names and message type names are protocol identifiers
 * required for compatibility with servers running Apollo. This client
 * never identifies itself as any other client: the handshake carries no
 * client brand or client version, and the minecraft:brand stays this
 * client's own.</p>
 *
 * <p>Pure Java, single-threaded (the game thread), testable on the JVM.
 * Every inbound message is decoded defensively: malformed payloads,
 * unknown types and messages for disabled modules are logged to the
 * debug log and dropped, never thrown.</p>
 */
public final class Apollo {

        public static final String CHANNEL_PROTO = "lunar:apollo";
        public static final String CHANNEL_JSON = "apollo:json";

        static final String CONFIGURABLE = "lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage";
        static final String HANDSHAKE = "lunarclient.apollo.player.v1.PlayerHandshakeMessage";

        /** Minecraft version string sent in the handshake (Apollo maps unknown versions to UNKNOWN). */
        public static final String MINECRAFT_VERSION = "v26_1_2";

        /** Wall clock; replaceable in tests. */
        public interface Clock {
                long now();
        }

        /** Message type to handler registry, filled by the modules. */
        public static final class Router {
                private final Map<String, ApolloModule> owners = new HashMap<>();
                private final Map<String, ApolloModule.Handler> handlers = new HashMap<>();
                private ApolloModule current;

                /** {@code type} may be a full name or "package.v1.Name" relative to lunarclient.apollo. */
                public void on(String type, ApolloModule.Handler handler) {
                        String full = type.startsWith("lunarclient.") ? type : "lunarclient.apollo." + type;
                        owners.put(full, current);
                        handlers.put(full, handler);
                }

                public boolean handles(String fullType) {
                        return handlers.containsKey(fullType);
                }
        }

        private static final Router router = new Router();
        private static final List<ApolloModule> modules = new ArrayList<>();
        private static final Map<String, ApolloModule> modulesById = new HashMap<>();
        private static final ApolloDebugLog debugLog = new ApolloDebugLog();

        private static boolean initialized;
        private static boolean debug;
        private static boolean jsonMode;
        private static boolean handshakeSent;
        private static int received;
        private static int dropped;
        private static Clock clock = new Clock() {
                @Override
                public long now() {
                        return System.currentTimeMillis();
                }
        };
        private static PluginChannels.Log log = new PluginChannels.Log() {
                @Override
                public void info(String msg) {
                        System.out.println(msg);
                }

                @Override
                public void warn(String msg) {
                        System.out.println(msg);
                }
        };

        private Apollo() {
        }

        // ==================== setup ====================

        /**
         * Registers the Apollo channels and every module. Idempotent; call
         * once at client start-up (before the first connection).
         */
        public static void init() {
                if (initialized) {
                        return;
                }
                initialized = true;
                for (ApolloModule m : ApolloModules.create()) {
                        addModule(m);
                }
                PluginChannels.register(CHANNEL_PROTO, new PluginChannels.Handler() {
                        @Override
                        public void onMessage(String channel, byte[] data) {
                                receive(channel, data);
                        }
                });
                PluginChannels.register(CHANNEL_JSON, new PluginChannels.Handler() {
                        @Override
                        public void onMessage(String channel, byte[] data) {
                                receive(channel, data);
                        }
                });
                PluginChannels.addSessionListener(new PluginChannels.SessionListener() {
                        @Override
                        public void onJoin() {
                                sendHandshake();
                        }

                        @Override
                        public void onReset() {
                                resetSession();
                        }
                });
        }

        static void addModule(ApolloModule m) {
                modules.add(m);
                modulesById.put(m.id(), m);
                router.current = m;
                m.register(router);
                router.current = null;
        }

        public static void setLog(PluginChannels.Log l) {
                if (l != null) {
                        log = l;
                }
        }

        public static void setDebug(boolean on) {
                debug = on;
        }

        public static boolean isDebug() {
                return debug;
        }

        public static void setClock(Clock c) {
                clock = c;
        }

        public static long now() {
                return clock.now();
        }

        // ==================== inbound ====================

        /** One payload from the server on lunar:apollo or apollo:json. Never throws. */
        static void receive(String channel, byte[] data) {
                received++;
                ProtoMessage msg;
                try {
                        if (CHANNEL_JSON.equals(channel)) {
                                msg = ProtoJson.parse(new String(data, StandardCharsets.UTF_8));
                                jsonMode = true;
                        } else {
                                msg = ProtoCodec.decodeAny(data);
                                jsonMode = false;
                        }
                } catch (Throwable t) {
                        dropped++;
                        record(channel, "?", ApolloDebugLog.Status.ERROR, "decode failed: " + t.getMessage());
                        log.warn("[Apollo] dropped malformed " + channel + " payload (" + data.length + " B): " + t.getMessage());
                        return;
                }
                route(channel, msg);
        }

        static void route(String channel, ProtoMessage msg) {
                String type = msg.type();
                try {
                        if (CONFIGURABLE.equals(type)) {
                                applyConfigurable(msg);
                                record(channel, msg.simpleType(), ApolloDebugLog.Status.HANDLED, "");
                                return;
                        }
                        ApolloModule.Handler h = router.handlers.get(type);
                        ApolloModule owner = router.owners.get(type);
                        if (h == null) {
                                record(channel, msg.simpleType(), ApolloDebugLog.Status.IGNORED_UNKNOWN, type);
                                if (debug) {
                                        log.info("[Apollo] ignored unsupported message " + type);
                                }
                                return;
                        }
                        if (owner != null && !owner.isEnabled()) {
                                record(channel, msg.simpleType(), ApolloDebugLog.Status.IGNORED_DISABLED,
                                                "module " + owner.id() + " is disabled by the server");
                                return;
                        }
                        if (owner != null) {
                                owner.countReceived();
                        }
                        h.handle(msg);
                        record(channel, msg.simpleType(), ApolloDebugLog.Status.HANDLED,
                                        msg.unknownFieldCount() > 0 ? msg.unknownFieldCount() + " unknown field(s) skipped" : "");
                } catch (PolicyException p) {
                        record(channel, msg.simpleType(), ApolloDebugLog.Status.IGNORED_POLICY, p.getMessage());
                        log.info("[Apollo] " + p.getMessage());
                } catch (Throwable t) {
                        dropped++;
                        record(channel, msg.simpleType(), ApolloDebugLog.Status.ERROR, String.valueOf(t));
                        log.warn("[Apollo] handler for " + type + " failed: " + t);
                }
        }

        /**
         * OverrideConfigurableSettingsMessage: per module enable flag plus
         * option overrides. A null option value rolls the override back.
         */
        static void applyConfigurable(ProtoMessage msg) {
                for (ProtoMessage s : msg.getMessages("configurableSettings")) {
                        String moduleId = s.getString("apolloModule");
                        String mod = s.getString("lunarClientMod");
                        ApolloModule m;
                        String prefix = "";
                        if (!moduleId.isEmpty()) {
                                m = modulesById.get(moduleId);
                        } else if (!mod.isEmpty()) {
                                // per-mod form: options keyed relative to the mod
                                m = modulesById.get(ApolloModules.MOD_SETTING);
                                prefix = mod + ".";
                        } else {
                                continue; // plugin root ("apollo") settings: nothing client-side
                        }
                        if (m == null) {
                                record("config", "ConfigurableSettings", ApolloDebugLog.Status.IGNORED_UNKNOWN,
                                                "module " + moduleId);
                                continue;
                        }
                        if (prefix.isEmpty()) {
                                m.setEnabled(s.getBool("enable"));
                        }
                        for (Map.Entry<String, Object> e : s.getMap("properties").entrySet()) {
                                m.putProperty(prefix + e.getKey(), ProtoValues.toJava((ProtoMessage) e.getValue()));
                        }
                        m.onPropertiesChanged();
                }
        }

        // ==================== outbound ====================

        /**
         * Sends a message to the server on the channel it speaks (JSON when
         * the server last talked apollo:json, protobuf otherwise).
         */
        public static boolean send(ProtoMessage msg) {
                boolean ok;
                String channel;
                try {
                        if (jsonMode) {
                                channel = CHANNEL_JSON;
                                ok = PluginChannels.send(channel, ProtoJson.toJson(msg).getBytes(StandardCharsets.UTF_8));
                        } else {
                                channel = CHANNEL_PROTO;
                                ok = PluginChannels.send(channel, ProtoCodec.encodeAny(msg));
                        }
                } catch (Throwable t) {
                        log.warn("[Apollo] could not encode " + msg.type() + ": " + t);
                        return false;
                }
                record(channel, msg.simpleType(), ApolloDebugLog.Status.SENT, ok ? "" : "not sent");
                return ok;
        }

        /** The handshake: Minecraft version only, no client brand or client version. */
        public static ProtoMessage buildHandshake() {
                ProtoMessage version = new ProtoMessage("lunarclient.apollo.common.v1.MinecraftVersion");
                version.set("enum", MINECRAFT_VERSION);
                ProtoMessage hs = new ProtoMessage(HANDSHAKE);
                hs.set("minecraftVersion", version);
                hs.set("embeddedCheckoutSupport", 0); // UNSPECIFIED: no embedded store checkout
                return hs;
        }

        static void sendHandshake() {
                // always protobuf: the plugin listens for the handshake on lunar:apollo
                boolean wasJson = jsonMode;
                jsonMode = false;
                handshakeSent = send(buildHandshake());
                jsonMode = wasJson;
        }

        // ==================== lifecycle ====================

        /** Client tick (game thread): module timers and expiry. */
        public static void tick() {
                long t = now();
                for (ApolloModule m : modules) {
                        try {
                                m.tick(t);
                        } catch (Throwable e) {
                                log.warn("[Apollo] tick of " + m.id() + " failed: " + e);
                        }
                }
        }

        /** Disconnect / server switch: every module back to a clean state. */
        static void resetSession() {
                for (ApolloModule m : modules) {
                        try {
                                m.clearForSession();
                        } catch (Throwable e) {
                                log.warn("[Apollo] reset of " + m.id() + " failed: " + e);
                        }
                }
                jsonMode = false;
                handshakeSent = false;
                received = 0;
                dropped = 0;
        }

        static void record(String channel, String type, ApolloDebugLog.Status status, String detail) {
                debugLog.add(now(), channel, type, status, detail);
        }

        // ==================== queries ====================

        public static List<ApolloModule> modules() {
                return Collections.unmodifiableList(modules);
        }

        @SuppressWarnings("unchecked")
        public static <T extends ApolloModule> T module(Class<T> type) {
                for (ApolloModule m : modules) {
                        if (type.isInstance(m)) {
                                return (T) m;
                        }
                }
                return null;
        }

        public static ApolloModule module(String id) {
                return modulesById.get(id);
        }

        public static ApolloDebugLog debugLog() {
                return debugLog;
        }

        /** True once the server spoke Apollo on this connection or announced the channel. */
        public static boolean isServerApollo() {
                return received > 0 || PluginChannels.serverListensOn(CHANNEL_PROTO)
                                || PluginChannels.serverListensOn(CHANNEL_JSON);
        }

        public static boolean isJsonMode() {
                return jsonMode;
        }

        public static boolean isHandshakeSent() {
                return handshakeSent;
        }

        public static int receivedCount() {
                return received;
        }

        public static int droppedCount() {
                return dropped;
        }

        public static boolean isRouted(String fullType) {
                return CONFIGURABLE.equals(fullType) || router.handles(fullType);
        }

        /** Test hook: back to a fresh process state. */
        public static void resetAllForTests() {
                modules.clear();
                modulesById.clear();
                router.owners.clear();
                router.handlers.clear();
                debugLog.clear();
                initialized = false;
                debug = false;
                jsonMode = false;
                handshakeSent = false;
                received = 0;
                dropped = 0;
        }
}
