import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.lax1dude.eaglercraft.v2_6.placeholder_server.mc.McBridge;
import net.lax1dude.eaglercraft.v2_6.placeholder_server.mc.McPacketIds;
import net.lax1dude.eaglercraft.v2_6.placeholder_server.mc.McProtocolStub;
import net.lax1dude.eaglercraft.v2_6.placeholder_server.mc.McVarInt;

/**
 * JVM test for the gateway's backend bridge: the plugin-message (custom
 * payload) relay that server APIs such as Apollo ride on.
 *
 * Runs McBridge against McProtocolStub in-process over real TCP (with
 * compression on, so both the raw and the zlib framing paths carry
 * payloads) and asserts:
 *
 *  1. LOGIN + CONFIGURATION: the bridge acknowledges login, echoes the
 *     known packs, answers config keepalive + ping, acks finish and
 *     reaches PLAY (the stub reports how each was answered on
 *     stub:status).
 *  2. CONFIG-PHASE DELIVERY: plugin messages the backend sends during
 *     configuration (minecraft:brand, stub:config) reach the listener.
 *  3. EARLY QUEUE: a plugin message sent before login completes is queued
 *     and flushed in order once the backend can carry it.
 *  4. PLAY ROUND TRIP: minecraft:register (NUL-separated channel list),
 *     binary lunar:apollo payloads (all 256 byte values) and a payload
 *     above the compression threshold come back byte-for-byte.
 *  5. LIMITS: a payload above the vanilla 32767-byte serverbound cap is
 *     refused locally (false), and the bridge keeps working.
 *  6. CHAT + RECONFIGURE: chat still relays, and Start Configuration
 *     (proxy server switch) is acked, configuration walked again, and the
 *     relay keeps working afterwards.
 *
 * Exit 0 = the relay holds; 1 = violation.
 */
public class BridgeRelayTest {

        static int failures = 0;
        static int checks = 0;

        static void check(boolean ok, String what) {
                checks++;
                if (!ok) {
                        System.out.println("FAIL: " + what);
                        failures++;
                }
        }

        static final class Msg {
                final String channel;
                final byte[] data;

                Msg(String channel, byte[] data) {
                        this.channel = channel;
                        this.data = data;
                }

                String text() {
                        return new String(data, StandardCharsets.UTF_8);
                }
        }

        static final BlockingQueue<Msg> plugin = new LinkedBlockingQueue<>();
        static final BlockingQueue<String> chat = new LinkedBlockingQueue<>();
        static final BlockingQueue<String> errors = new LinkedBlockingQueue<>();

        /** Next plugin message on {@code channel} within 5s (others are skipped). */
        static Msg await(String channel) throws InterruptedException {
                long deadline = System.currentTimeMillis() + 5000;
                while (true) {
                        long left = deadline - System.currentTimeMillis();
                        if (left <= 0) {
                                return null;
                        }
                        Msg m = plugin.poll(left, TimeUnit.MILLISECONDS);
                        if (m == null) {
                                return null;
                        }
                        if (m.channel.equals(channel)) {
                                return m;
                        }
                }
        }

        static int freePort() throws Exception {
                try (ServerSocket s = new ServerSocket(0)) {
                        return s.getLocalPort();
                }
        }

        static void waitForPort(int port) throws Exception {
                long deadline = System.currentTimeMillis() + 10000;
                while (System.currentTimeMillis() < deadline) {
                        try (Socket s = new Socket()) {
                                s.connect(new InetSocketAddress("127.0.0.1", port), 200);
                                return;
                        } catch (Exception e) {
                                Thread.sleep(50);
                        }
                }
                throw new IllegalStateException("stub did not start on " + port);
        }

        public static void main(String[] args) throws Exception {
                int port = freePort();
                Thread stub = new Thread(() -> {
                        try {
                                McProtocolStub.main(new String[] { "--port", String.valueOf(port),
                                                "--interval-ms", "200" });
                        } catch (Exception e) {
                                errors.add("stub crashed: " + e);
                        }
                }, "stub");
                stub.setDaemon(true);
                stub.start();
                waitForPort(port);

                McBridge.Listener listener = new McBridge.Listener() {
                        @Override
                        public void onStateChange(McBridge b, McBridge.State s, String detail) {
                        }

                        @Override
                        public void onChat(McBridge b, String text) {
                                chat.add(text);
                        }

                        @Override
                        public void onKicked(McBridge b, String reason) {
                                errors.add("kicked: " + reason);
                        }

                        @Override
                        public void onError(McBridge b, String message) {
                                errors.add(message);
                        }

                        @Override
                        public void onPluginMessage(McBridge b, String channel, byte[] data) {
                                plugin.add(new Msg(channel, data));
                        }
                };
                String name = "RelayTester";
                McBridge bridge = new McBridge(new InetSocketAddress("127.0.0.1", port),
                                new McPacketIds(), "127.0.0.1", name,
                                UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)),
                                5000, listener);

                // 3. queued before the connection even starts
                check(bridge.sendPluginMessage("test:early", "early-1".getBytes(StandardCharsets.UTF_8)),
                                "early plugin message is queued");
                check(bridge.sendPluginMessage("test:early", "early-2".getBytes(StandardCharsets.UTF_8)),
                                "second early plugin message is queued");
                bridge.start();

                // 2. config-phase delivery
                Msg brand = await("minecraft:brand");
                check(brand != null, "minecraft:brand delivered in configuration");
                Msg cfg = await("stub:config");
                check(cfg != null && "hello-config".equals(cfg.text()),
                                "stub:config delivered in configuration: " + (cfg == null ? null : cfg.text()));
                Msg e1 = await("test:early");
                Msg e2 = await("test:early");
                check(e1 != null && "early-1".equals(e1.text()), "early message 1 echoed in order");
                check(e2 != null && "early-2".equals(e2.text()), "early message 2 echoed in order");

                // 1. login + configuration answered correctly
                Msg status = await("stub:status");
                check(status != null && status.text().contains("packs=1")
                                && status.text().contains("pong=42")
                                && status.text().contains("keepalive=1")
                                && status.text().contains("phase=play"),
                                "configuration answered: " + (status == null ? null : status.text()));
                check(bridge.isConnected() && bridge.getPhase() == McBridge.Phase.PLAY,
                                "bridge reached PLAY (state=" + bridge.getState() + " phase=" + bridge.getPhase() + ")");

                // 4. play round trips
                byte[] register = "lunar:apollo\0apollo:json".getBytes(StandardCharsets.UTF_8);
                check(bridge.sendPluginMessage("minecraft:register", register), "register sent");
                Msg reg = await("stub:registered");
                check(reg != null && Arrays.equals(reg.data, register),
                                "minecraft:register reached the backend intact");

                byte[] binary = new byte[256];
                for (int i = 0; i < 256; i++) {
                        binary[i] = (byte) i;
                }
                check(bridge.sendPluginMessage("lunar:apollo", binary), "binary payload sent");
                Msg bin = await("lunar:apollo");
                check(bin != null && Arrays.equals(bin.data, binary),
                                "binary lunar:apollo payload round-trips byte-for-byte");

                byte[] big = new byte[20000];
                for (int i = 0; i < big.length; i++) {
                        big[i] = (byte) (i * 31 + 7);
                }
                check(bridge.sendPluginMessage("apollo:json", big), "large payload sent");
                Msg bigBack = await("apollo:json");
                check(bigBack != null && Arrays.equals(bigBack.data, big),
                                "20000-byte payload round-trips through compressed framing");

                // 5. limits
                check(!bridge.sendPluginMessage("lunar:apollo", new byte[32768]),
                                "payload above the 32767-byte serverbound cap is refused");
                check(!bridge.sendPluginMessage("", new byte[1]), "empty channel is refused");
                check(bridge.isConnected(), "bridge still connected after refused sends");

                // 6. chat + reconfigure
                check(bridge.sendChat("hi"), "chat sent");
                String echoed = chat.poll(5, TimeUnit.SECONDS);
                check(echoed != null && echoed.endsWith("echoed: hi"), "chat relays: " + echoed);

                check(bridge.sendChat("!reconfig"), "reconfigure requested");
                Msg brand2 = await("minecraft:brand");
                check(brand2 != null, "configuration walked again after Start Configuration");
                Msg status2 = await("stub:status");
                check(status2 != null && status2.text().startsWith("reconfigured")
                                && status2.text().contains("packs=1") && status2.text().contains("pong=42"),
                                "reconfiguration answered: " + (status2 == null ? null : status2.text()));
                check(bridge.getPhase() == McBridge.Phase.PLAY, "bridge back in PLAY after reconfigure");
                byte[] after = "after-reconfig".getBytes(StandardCharsets.UTF_8);
                check(bridge.sendPluginMessage("lunar:apollo", after), "post-reconfig payload sent");
                Msg afterBack = await("lunar:apollo");
                check(afterBack != null && Arrays.equals(afterBack.data, after),
                                "relay works after reconfiguration");

                // keepalives kept flowing the whole time (200ms interval)
                Thread.sleep(600);
                check(bridge.isConnected(), "bridge survives keepalives");
                check(errors.isEmpty(), "no bridge errors: " + errors);

                bridge.close("test done");
                check(!bridge.sendPluginMessage("lunar:apollo", new byte[1]),
                                "closed bridge refuses plugin messages");

                System.out.println("BridgeRelayTest: " + checks + " checks, failures=" + failures);
                System.exit(failures > 0 ? 1 : 0);
        }
}
