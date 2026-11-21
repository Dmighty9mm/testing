import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloDebugLog;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.BorderModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.CooldownModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.HologramModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.Ids;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.LimbModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.ModSettingModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.NotificationModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.RichPresenceModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.ServerRuleModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.StaffModModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.StopwatchModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.TeamModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.TitleModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.WaypointModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoCodec;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoJson;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;
import net.lax1dude.eaglercraft.v2_6.client.network.PluginChannels;

/**
 * JVM test for the Apollo client core end to end through the real
 * plugin-channel layer: channel registration, the handshake, routing of
 * real serialized messages (tests/apollo/fixtures.txt) and JSON payloads
 * into the modules, server enable/disable and option overrides (a server
 * blocking a client mod), policy refusals, malformed-input safety, timer
 * expiry and per-connection cleanup.
 *
 * Exit 0 = holds; 1 = violation.
 */
public class ApolloClientTest {

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
        static long now = 1_000_000L;

        static byte[] hex(String h) {
                byte[] b = new byte[h.length() / 2];
                for (int i = 0; i < b.length; i++) {
                        b[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
                }
                return b;
        }

        static byte[] utf8(String s) {
                return s.getBytes(StandardCharsets.UTF_8);
        }

        static ApolloDebugLog.Entry lastLog() {
                List<ApolloDebugLog.Entry> e = Apollo.debugLog().entries();
                return e.isEmpty() ? null : e.get(e.size() - 1);
        }

        static void json(String json) {
                PluginChannels.dispatch(Apollo.CHANNEL_JSON, utf8(json));
        }

        public static void main(String[] args) throws Exception {
                Map<String, byte[]> fx = new HashMap<>();
                for (String line : Files.readAllLines(Paths.get(args.length > 0 ? args[0] : "tests/apollo/fixtures.txt"))) {
                        if (!line.isEmpty()) {
                                String[] f = line.split("\t");
                                fx.put(f[0], hex(f[2]));
                        }
                }
                PluginChannels.resetAllForTests();
                Apollo.resetAllForTests();
                PluginChannels.Log quiet = new PluginChannels.Log() {
                        @Override
                        public void info(String msg) {
                        }

                        @Override
                        public void warn(String msg) {
                        }
                };
                PluginChannels.setLog(quiet);
                Apollo.setLog(quiet);
                Apollo.setClock(() -> now);
                Apollo.init();
                Apollo.init(); // idempotent
                check(PluginChannels.getChannels().equals(java.util.Arrays.asList(Apollo.CHANNEL_PROTO, Apollo.CHANNEL_JSON)),
                                "both Apollo channels registered once: " + PluginChannels.getChannels());

                // join: register + handshake
                PluginChannels.attach((ch, data) -> {
                        sentChannels.add(ch);
                        sentData.add(data);
                        return true;
                });
                PluginChannels.onJoin();
                check(sentChannels.size() == 2 && "minecraft:register".equals(sentChannels.get(0))
                                && new String(sentData.get(0), StandardCharsets.UTF_8).equals("lunar:apollo\0apollo:json"),
                                "join announces lunar:apollo + apollo:json: " + sentChannels);
                check(Apollo.CHANNEL_PROTO.equals(sentChannels.get(1)), "handshake goes out on lunar:apollo");
                ProtoMessage hs = ProtoCodec.decodeAny(sentData.get(1));
                check(hs.simpleType().equals("PlayerHandshakeMessage")
                                && "v26_1_2".equals(hs.getMessage("minecraftVersion").getString("enum")),
                                "handshake = PlayerHandshakeMessage with the MC version: " + hs);
                check(!hs.has("lunarClientVersion") && !hs.has("installedMods"),
                                "handshake carries no other client's brand or version");
                check(Apollo.isHandshakeSent(), "handshake flagged as sent");

                // Tier 1 via real protobuf bytes
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("notification"));
                NotificationModule notes = Apollo.module(NotificationModule.class);
                check(notes.active().size() == 1 && ApolloText.plain(notes.active().get(0).title).equals("Hello"),
                                "notification displayed");
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("title"));
                TitleModule titles = Apollo.module(TitleModule.class);
                check(titles.active().size() == 1 && titles.active().get(0).type == TitleModule.TYPE_SUBTITLE
                                && ApolloText.plain(titles.active().get(0).text).equals("Round 1"), "subtitle displayed");
                TitleModule.Title t = titles.active().get(0);
                check(t.alpha(now) == 0f && t.alpha(now + 250) == 0.5f && t.alpha(now + 1000) == 1f, "title fade-in curve");
                check(Math.abs(t.scaleAt(now) - 1.5f) < 1e-6 && t.scaleAt(now + 50_000) < 1.5f, "title scale interpolates toward target");
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("waypoint"));
                WaypointModule wps = Apollo.module(WaypointModule.class);
                check(wps.all().size() == 1 && wps.all().get(0).pos.x == -99.5 && wps.all().get(0).argb == -32768,
                                "waypoint stored at the block centre");
                check(!wps.removeByPlayer("KoTH"), "prevent_removal waypoint cannot be removed by the player");
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("cooldown"));
                CooldownModule cds = Apollo.module(CooldownModule.class);
                check(cds.active().size() == 1 && cds.active().get(0).icon.equals("ENDER_PEARL")
                                && cds.active().get(0).remaining(now + 5000) == 10000, "cooldown with icon and countdown");
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("border"));
                BorderModule borders = Apollo.module(BorderModule.class);
                check(borders.all().size() == 1, "border stored");
                check(borders.blocksMove("world", 100, 0, 0, 0) && !borders.blocksMove("world", 0, 0, 10, 10)
                                && !borders.blocksMove("world", 0, 0, 100, 0),
                                "cancel_entry border blocks entering, not moving inside or leaving");
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("richpresence"));
                check(Apollo.module(RichPresenceModule.class).summary().startsWith("BedWars - Solo - Lighthouse"),
                                "rich presence summary: " + Apollo.module(RichPresenceModule.class).summary());
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("team"));
                TeamModule team = Apollo.module(TeamModule.class);
                check(team.members().size() == 1 && team.members().get(0).hasPos
                                && ApolloText.plain(team.members().get(0).name).equals("Ally")
                                && team.members().get(0).uuid.equals("a0000000-0000-0000-1234-567890abcdef"),
                                "team member with uuid: " + (team.members().isEmpty() ? "-" : team.members().get(0).uuid));
                check(Ids.uuid(Ids.uuidMessage("a0000000-0000-0000-1234-567890abcdef")).equals("a0000000-0000-0000-1234-567890abcdef"),
                                "uuid string round-trip");
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("limb"));
                check(Apollo.module(LimbModule.class).isBodyPartHidden("a0000000-0000-0000-1234-567890abcdef", 2)
                                && !Apollo.module(LimbModule.class).isBodyPartHidden("a0000000-0000-0000-1234-567890abcdef", 5),
                                "limb hide state");
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("hologram"));
                check(Apollo.module(HologramModule.class).all().get(0).lines.size() == 2, "hologram lines");

                // configurable: server rules, mod settings, module disable
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("configurable"));
                ServerRuleModule rules = Apollo.module(ServerRuleModule.class);
                ModSettingModule mods = Apollo.module(ModSettingModule.class);
                check(rules.competitiveGame() && rules.isCompetitiveCommand("/hub lobby") && !rules.isCompetitiveCommand("/msg"),
                                "server_rule competitive-game + commands");
                check(rules.brightnessOverride() == -1f, "brightness only forced with override-brightness");
                check(mods.isModBlocked("zoom") && !mods.isModBlocked("fps"), "server blocks the zoom mod only");
                check(!wps.isEnabled() && wps.all().isEmpty(), "disabling the waypoint module clears its state");
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("waypoint"));
                check(wps.all().isEmpty() && lastLog().status == ApolloDebugLog.Status.IGNORED_DISABLED,
                                "messages for a disabled module are ignored");

                // JSON channel: rollback a mod override with null, re-enable a module, force brightness
                json("{\"@type\":\"type.googleapis.com/lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage\","
                                + "\"configurable_settings\":[{\"apollo_module\":\"mod_setting\",\"enable\":true,\"properties\":{\"zoom.enabled\":null}},"
                                + "{\"apollo_module\":\"waypoint\",\"enable\":true},"
                                + "{\"apollo_module\":\"server_rule\",\"enable\":true,\"properties\":{\"override-brightness\":true,\"brightness\":80}}]}");
                check(!mods.isModBlocked("zoom"), "null rolls the zoom override back");
                check(wps.isEnabled(), "module re-enabled");
                check(Math.abs(rules.brightnessOverride() - 0.8f) < 1e-6, "brightness forced to 80%");
                check(Apollo.isJsonMode(), "server spoke JSON");
                json("{\"@type\":\"type.googleapis.com/lunarclient.apollo.stopwatch.v1.StartStopwatchMessage\"}");
                now += 61_230;
                StopwatchModule sw = Apollo.module(StopwatchModule.class);
                check(sw.isRunning() && StopwatchModule.format(sw.elapsedMs(now)).equals("1:01.23"), "stopwatch runs: "
                                + StopwatchModule.format(sw.elapsedMs(now)));

                // replies follow the server's encoding (JSON now)
                int before = sentChannels.size();
                ProtoMessage ping = new ProtoMessage("lunarclient.apollo.transfer.v1.PingRequest");
                ping.set("requestId", new byte[] { 1, 2, 3 });
                ping.add("serverIps", "play.example.net");
                json(ProtoJson.toJson(ping));
                check(sentChannels.size() == before + 1 && Apollo.CHANNEL_JSON.equals(sentChannels.get(before)),
                                "transfer ping answered on apollo:json");
                ProtoMessage pong = ProtoJson.parse(new String(sentData.get(before), StandardCharsets.UTF_8));
                check(pong.simpleType().equals("PingResponse") && pong.getBytes("requestId").length == 3
                                && pong.getMessages("pingData").get(0).getInt("status") == 2,
                                "ping response echoes the request id, TIMED_OUT: " + pong);

                // policy: X-ray staff mod is refused
                json("{\"@type\":\"type.googleapis.com/lunarclient.apollo.staffmod.v1.EnableStaffModsMessage\",\"staff_mods\":[\"STAFF_MOD_XRAY\"]}");
                check(lastLog().status == ApolloDebugLog.Status.IGNORED_POLICY, "X-ray request logged as refused by policy");
                check(Apollo.module(StaffModModule.class).requested().contains(1), "request recorded for the debug screen");
                json("{\"@type\":\"type.googleapis.com/lunarclient.apollo.tebex.v1.OpenTebexEmbeddedCheckoutMessage\",\"basket_ident\":\"x\"}");
                check(lastLog().status == ApolloDebugLog.Status.IGNORED_POLICY, "store checkout declined");

                // safety
                int dropped = Apollo.droppedCount();
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, new byte[] { 0x0a, 0x7f, 1, 2 });
                json("{not json");
                check(Apollo.droppedCount() == dropped + 2 && lastLog().status == ApolloDebugLog.Status.ERROR,
                                "malformed payloads are dropped and logged, never thrown");
                json("{\"@type\":\"type.googleapis.com/lunarclient.apollo.future.v9.NewThingMessage\"}");
                check(lastLog().status == ApolloDebugLog.Status.IGNORED_UNKNOWN, "unknown message types are ignored");
                json("{\"@type\":\"type.googleapis.com/lunarclient.apollo.title.v1.DisplayTitleMessage\",\"scale\":\"oops\"}");
                check(lastLog().status == ApolloDebugLog.Status.ERROR, "a bad field value is an error entry, not a crash");

                // expiry
                PluginChannels.dispatch(Apollo.CHANNEL_PROTO, fx.get("notification"));
                Apollo.tick();
                check(notes.active().size() == 1, "fresh notification still shown (old one expired)");
                now += NotificationModule.DEFAULT_MS + 1;
                Apollo.tick();
                check(notes.active().isEmpty() && titles.active().isEmpty() && cds.active().isEmpty(),
                                "notifications, titles and cooldowns expire on tick");
                check(Apollo.debugLog().entries().size() <= ApolloDebugLog.LIMIT, "debug log bounded");

                // disconnect: everything per-connection is gone
                PluginChannels.reset();
                check(team.members().isEmpty() && borders.all().isEmpty() && !sw.isShown()
                                && Apollo.module(HologramModule.class).all().isEmpty(), "reset clears every module");
                check(!mods.isModBlocked("zoom") && !rules.competitiveGame() && rules.properties().isEmpty(),
                                "reset drops server rules and mod overrides");
                check(wps.isEnabled() && !Apollo.isJsonMode() && !Apollo.isHandshakeSent() && Apollo.receivedCount() == 0,
                                "reset restores defaults");

                System.out.println("ApolloClientTest: " + checks + " checks, failures=" + failures);
                System.exit(failures > 0 ? 1 : 0);
        }
}
