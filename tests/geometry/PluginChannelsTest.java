import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.network.PluginChannels;

/**
 * JVM test for the client's plugin-message channel layer
 * (PluginChannels): the minecraft:register convention, dispatch, and the
 * safety rules server APIs such as Apollo depend on.
 *
 *  1. CODEC: NUL-joined channel lists round-trip; empty/invalid entries
 *     are dropped; Identifier validation matches the vanilla charset.
 *  2. JOIN: entering play announces every registered channel in ONE
 *     minecraft:register; a channel registered later is announced
 *     immediately.
 *  3. DISPATCH: payloads reach their handler byte-for-byte; a throwing
 *     handler never propagates; unknown channels are ignored; the
 *     server's own register/unregister lists are tracked.
 *  4. LIMITS: sends above the 32767-byte serverbound cap, on invalid ids
 *     or while detached are refused.
 *  5. RESET: disconnect clears per-connection state, fires every reset
 *     hook (even after one throws) and detaches the sender.
 *
 * Exit 0 = holds; 1 = violation.
 */
public class PluginChannelsTest {

        static int failures = 0;
        static int checks = 0;

        static void check(boolean ok, String what) {
                checks++;
                if (!ok) {
                        System.out.println("FAIL: " + what);
                        failures++;
                }
        }

        static final List<String> sentChannels = new ArrayList<>();
        static final List<byte[]> sentData = new ArrayList<>();
        static final List<String> warnings = new ArrayList<>();

        static byte[] utf8(String s) {
                return s.getBytes(StandardCharsets.UTF_8);
        }

        public static void main(String[] args) {
                PluginChannels.resetAllForTests();
                PluginChannels.setLog(new PluginChannels.Log() {
                        @Override
                        public void info(String msg) {
                        }

                        @Override
                        public void warn(String msg) {
                                warnings.add(msg);
                        }
                });

                // 1. codec
                byte[] enc = PluginChannels.encodeChannelList(Arrays.asList("lunar:apollo", "apollo:json"));
                check(Arrays.equals(enc, utf8("lunar:apollo\0apollo:json")), "register body is NUL-joined UTF-8");
                check(PluginChannels.decodeChannelList(utf8("a:b\0\0c:d\0Bad:Caps\0nocolon\0e:f/g"))
                                .equals(Arrays.asList("a:b", "c:d", "e:f/g")),
                                "decode drops empty and invalid entries: "
                                                + PluginChannels.decodeChannelList(utf8("a:b\0\0c:d\0Bad:Caps\0nocolon\0e:f/g")));
                check(PluginChannels.isValidChannel("lunar:apollo"), "lunar:apollo is valid");
                check(PluginChannels.isValidChannel("minecraft:register"), "minecraft:register is valid");
                check(!PluginChannels.isValidChannel("Lunar:Apollo"), "upper case is invalid");
                check(!PluginChannels.isValidChannel(":x") && !PluginChannels.isValidChannel("x:")
                                && !PluginChannels.isValidChannel("x"), "namespace and path are required");
                check(!PluginChannels.isValidChannel("a/b:c"), "'/' is only legal in the path");

                // registration
                final List<String> got = new ArrayList<>();
                final byte[][] lastPayload = new byte[1][];
                PluginChannels.register("lunar:apollo", (ch, data) -> {
                        got.add(ch);
                        lastPayload[0] = data;
                });
                PluginChannels.register("apollo:json", (ch, data) -> {
                        throw new IllegalStateException("handler bug");
                });
                boolean threw = false;
                try {
                        PluginChannels.register("NOT VALID", (ch, data) -> { });
                } catch (IllegalArgumentException e) {
                        threw = true;
                }
                check(threw, "registering an invalid channel id throws at startup");

                final int[] joins = new int[1];
                final int[] resets = new int[1];
                PluginChannels.addSessionListener(new PluginChannels.SessionListener() {
                        @Override
                        public void onJoin() {
                                throw new RuntimeException("join hook bug");
                        }

                        @Override
                        public void onReset() {
                                throw new RuntimeException("reset hook bug");
                        }
                });
                PluginChannels.addSessionListener(new PluginChannels.SessionListener() {
                        @Override
                        public void onJoin() {
                                joins[0]++;
                        }

                        @Override
                        public void onReset() {
                                resets[0]++;
                        }
                });

                // 4. detached sends are refused
                check(!PluginChannels.send("lunar:apollo", new byte[1]), "send while detached is refused");

                // 2. join
                PluginChannels.attach((ch, data) -> {
                        sentChannels.add(ch);
                        sentData.add(data);
                        return true;
                });
                PluginChannels.onJoin();
                check(sentChannels.size() == 1 && "minecraft:register".equals(sentChannels.get(0)),
                                "join sends exactly one minecraft:register: " + sentChannels);
                check(sentData.size() == 1 && Arrays.equals(sentData.get(0), utf8("lunar:apollo\0apollo:json")),
                                "register lists every channel in registration order");
                check(joins[0] == 1, "join hooks run even after one throws");
                check(PluginChannels.isJoined(), "joined flag set");

                PluginChannels.register("plebmc:test", (ch, data) -> { });
                check(sentChannels.size() == 2 && Arrays.equals(sentData.get(1), utf8("plebmc:test")),
                                "late registration is announced immediately");
                PluginChannels.register("plebmc:test", (ch, data) -> { });
                check(sentChannels.size() == 2, "re-registering an existing channel is not re-announced");

                // 3. dispatch
                byte[] bin = new byte[300];
                for (int i = 0; i < bin.length; i++) {
                        bin[i] = (byte) i;
                }
                PluginChannels.dispatch("lunar:apollo", bin);
                check(got.size() == 1 && Arrays.equals(lastPayload[0], bin), "payload reaches its handler intact");
                int warnBefore = warnings.size();
                PluginChannels.dispatch("apollo:json", utf8("{}"));
                check(warnings.size() == warnBefore + 1, "a throwing handler is logged, not propagated");
                PluginChannels.dispatch("unknown:channel", utf8("x"));
                check(got.size() == 1, "unknown channels are ignored");
                PluginChannels.dispatch("minecraft:register", utf8("lunar:apollo\0apollo:json"));
                check(PluginChannels.serverListensOn("lunar:apollo") && PluginChannels.serverListensOn("apollo:json"),
                                "server register list is tracked");
                PluginChannels.dispatch("minecraft:unregister", utf8("apollo:json"));
                check(PluginChannels.serverListensOn("lunar:apollo") && !PluginChannels.serverListensOn("apollo:json"),
                                "server unregister is honoured");
                PluginChannels.dispatch(null, null);
                check(PluginChannels.getReceivedCount() == 5, "received counter: " + PluginChannels.getReceivedCount());
                List<PluginChannels.Traffic> recent = PluginChannels.getRecent();
                check(recent.size() == 7 && recent.get(recent.size() - 1).inbound
                                && !recent.get(4).handled, "recent traffic recorded: " + recent);

                // 4. limits
                check(PluginChannels.send("lunar:apollo", new byte[32767]), "32767-byte payload is allowed");
                check(!PluginChannels.send("lunar:apollo", new byte[32768]), "32768-byte payload is refused");
                check(!PluginChannels.send("Bad Channel", new byte[1]), "invalid channel id is refused");
                check(!PluginChannels.send("lunar:apollo", null), "null payload is refused");
                PluginChannels.attach((ch, data) -> {
                        throw new RuntimeException("socket gone");
                });
                check(!PluginChannels.send("lunar:apollo", new byte[1]), "a throwing sender reports false");

                for (int i = 0; i < 200; i++) {
                        PluginChannels.dispatch("unknown:spam", new byte[1]);
                }
                check(PluginChannels.getRecent().size() == PluginChannels.RECENT_LIMIT,
                                "recent ring is capped at " + PluginChannels.RECENT_LIMIT);

                // 5. reset
                PluginChannels.reset();
                check(resets[0] == 1, "reset hooks run even after one throws");
                check(!PluginChannels.isJoined(), "reset clears joined");
                check(PluginChannels.getServerChannels().isEmpty(), "reset clears the server channel list");
                check(!PluginChannels.send("lunar:apollo", new byte[1]), "reset detaches the sender");
                check(PluginChannels.getChannels().equals(Arrays.asList("lunar:apollo", "apollo:json", "plebmc:test")),
                                "handlers survive reset (registered once at startup)");

                System.out.println("PluginChannelsTest: " + checks + " checks, failures=" + failures);
                System.exit(failures > 0 ? 1 : 0);
        }
}
