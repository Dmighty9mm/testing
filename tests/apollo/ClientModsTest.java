import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.mods.ClickCounter;
import net.lax1dude.eaglercraft.v2_6.client.mods.ClientView;
import net.lax1dude.eaglercraft.v2_6.client.mods.HudBox;
import net.lax1dude.eaglercraft.v2_6.client.mods.Mod;
import net.lax1dude.eaglercraft.v2_6.client.mods.ModRegistry;
import net.lax1dude.eaglercraft.v2_6.client.mods.Mods;
import net.lax1dude.eaglercraft.v2_6.client.mods.SettingsStore;
import net.lax1dude.eaglercraft.v2_6.client.network.PluginChannels;

/**
 * JVM test for the client's own mods: HUD layouts, saved layout/toggle
 * persistence (the HUD editor's storage layer), and - the key rule - that
 * a server's Apollo mod_setting / server_rule overrides force-disable or
 * override the player's mods (zoom, toggle sprint, fullbright).
 *
 * Exit 0 = holds; 1 = violation.
 */
public class ClientModsTest {

        static int failures = 0;
        static int checks = 0;

        static void check(boolean ok, String what) {
                checks++;
                if (!ok) {
                        System.out.println("FAIL: " + what);
                        failures++;
                }
        }

        static final class FakeView implements ClientView {
                boolean w;
                boolean lmb;

                @Override
                public int fps() {
                        return 144;
                }

                @Override
                public double x() {
                        return -12.34;
                }

                @Override
                public double y() {
                        return 64;
                }

                @Override
                public double z() {
                        return 1000.06;
                }

                @Override
                public float yaw() {
                        return 180f;
                }

                @Override
                public int pingMs() {
                        return -1;
                }

                @Override
                public String localTime() {
                        return "13:37";
                }

                @Override
                public boolean keyDown(String key) {
                        return "forward".equals(key) && w;
                }

                @Override
                public boolean mouseDown(int button) {
                        return button == 0 && lmb;
                }

                @Override
                public int cps(int button) {
                        return button == 0 ? 9 : 2;
                }

                @Override
                public List<String> armor() {
                        return Collections.emptyList();
                }

                @Override
                public List<String> effects() {
                        return Collections.emptyList();
                }
        }

        static final HudBox.Measure M = s -> s.length() * 6;

        static void json(String json) {
                PluginChannels.dispatch(Apollo.CHANNEL_JSON, json.getBytes(StandardCharsets.UTF_8));
        }

        static String modSetting(String props) {
                return "{\"@type\":\"type.googleapis.com/lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage\","
                                + "\"configurable_settings\":[{\"apollo_module\":\"mod_setting\",\"enable\":true,\"properties\":" + props
                                + "}]}";
        }

        public static void main(String[] args) {
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
                Apollo.init();
                PluginChannels.attach((ch, d) -> true);
                PluginChannels.onJoin();

                ModRegistry reg = new ModRegistry();
                FakeView v = new FakeView();

                // HUD layouts
                HudBox fps = reg.fps.layout(v, M);
                check(fps != null && fps.texts.get(0).text.equals("144 FPS"), "FPS text");
                HudBox coords = reg.coordinates.layout(v, M);
                check(coords.texts.get(0).text.equals("X: -12.3") && coords.texts.get(2).text.equals("Z: 1000.1")
                                && coords.texts.get(3).text.equals("Facing: North (-Z)"), "coordinates + facing");
                check(Mods.Coordinates.facing(0).startsWith("South") && Mods.Coordinates.facing(90).startsWith("West")
                                && Mods.Coordinates.facing(-90).startsWith("East") && Mods.Coordinates.facing(44).startsWith("South"),
                                "vanilla compass directions");
                check(reg.cps.layout(v, M).texts.get(0).text.equals("9 | 2 CPS"), "CPS text");
                check(reg.ping.layout(v, M).texts.get(0).text.equals("Ping: n/a"), "unknown ping shows n/a");
                v.w = true;
                v.lmb = true;
                HudBox keys = reg.keystrokes.layout(v, M);
                check(keys.rects.get(0).argb == HudBox.BG_PRESSED && keys.rects.get(1).argb == HudBox.BG,
                                "keystrokes: W pressed, A not");
                check(keys.texts.get(4).text.equals("LMB 9") && keys.texts.get(4).argb == HudBox.TEXT_PRESSED, "LMB cell shows CPS");
                check(reg.armor.layout(v, M) == null && reg.armor.preview(v, M) != null,
                                "armor hides with no armor but previews in the editor");

                // persistence (HUD editor storage layer)
                Map<String, String> mem = new HashMap<>();
                SettingsStore store = new SettingsStore() {
                        @Override
                        public String get(String key) {
                                return mem.get(key);
                        }

                        @Override
                        public void set(String key, String value) {
                                mem.put(key, value);
                        }
                };
                reg.fps.setPosition(0.5f, 0.25f);
                reg.zoom.setEnabled(false);
                reg.clock.setEnabled(true);
                reg.save(store);
                ModRegistry again = new ModRegistry();
                again.load(store);
                check(again.fps.fx() == 0.5f && again.fps.fy() == 0.25f, "HUD position persists: " + mem.get("mods"));
                check(!again.zoom.isEnabled() && again.clock.isEnabled(), "toggles persist");
                again.fps.setPosition(4f, -2f);
                check(again.fps.fx() == 1f && again.fps.fy() == 0f, "positions clamp to the screen");
                mem.put("mods", "{\"fps\":{\"on\":\"yes\",\"x\":\"bad\"},\"nope\":1,\"cps\":[1]");
                ModRegistry corrupt = new ModRegistry();
                corrupt.load(store);
                check(corrupt.fps.isEnabled() && corrupt.cps.isEnabled(), "corrupt settings keep defaults");
                mem.put("mods", "{\"fps\":{\"on\":false,\"x\":\"bad\",\"y\":0.5}}");
                corrupt.load(store);
                check(!corrupt.fps.isEnabled() && corrupt.fps.fy() == 0.5f, "partially bad entry applies the good fields");
                int[] p = ModRegistry.place(reg.fps, 60, 12, 320, 240);
                check(p[0] == 160 && p[1] == 60, "place() maps fractions to pixels");
                reg.fps.setPosition(1f, 1f);
                p = ModRegistry.place(reg.fps, 60, 12, 320, 240);
                check(p[0] == 260 && p[1] == 228, "place() keeps the box on screen");

                // server overrides
                reg.zoom.setEnabled(true);
                check(reg.zoom.fov(70f, true) == 17.5f && reg.zoom.fov(70f, false) == 70f, "zoom divides FOV by 4 while held");
                json(modSetting("{\"zoom.enabled\":false}"));
                check(reg.zoom.isBlockedByServer() && !reg.zoom.isActive() && reg.zoom.fov(70f, true) == 70f,
                                "server blocks zoom: no FOV change even with the key held");
                json(modSetting("{\"zoom.enabled\":null,\"zoom.zoom-divisor\":2}"));
                check(reg.zoom.isActive() && reg.zoom.fov(70f, true) == 35f, "override rolled back; server divisor honored");

                reg.toggleSprint.setEnabled(true);
                reg.toggleSprint.onSprintKey();
                check(reg.toggleSprint.sprintHeld(), "toggle sprint latches");
                json(modSetting("{\"toggle-sneak.toggle-sprint\":false}"));
                check(!reg.toggleSprint.sprintHeld(), "server option toggle-sprint=false stops toggle sprint");
                json(modSetting("{\"toggle-sneak.toggle-sprint\":null}"));
                check(reg.toggleSprint.sprintHeld(), "and it resumes once the override is rolled back");

                reg.fullbright.setEnabled(true);
                check(reg.fullbright.minLight() == 1f, "fullbright raises the light floor");
                json(modSetting("{\"lighting.full-bright\":false}"));
                check(reg.fullbright.minLight() == 0f, "server can turn fullbright off");
                json("{\"@type\":\"type.googleapis.com/lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage\","
                                + "\"configurable_settings\":[{\"apollo_module\":\"server_rule\",\"enable\":true,\"properties\":"
                                + "{\"override-brightness\":true,\"brightness\":25}}]}");
                check(Math.abs(reg.fullbright.minLight() - 0.25f) < 1e-6, "server brightness rule wins over the mod");

                // every HUD mod can be blocked by "<id>.enabled" = false
                List<String> ids = new ArrayList<>();
                for (Mod m : reg.all()) {
                        ids.add(m.id());
                }
                StringBuilder props = new StringBuilder("{");
                for (int i = 0; i < ids.size(); i++) {
                        props.append(i == 0 ? "" : ",").append('"').append(ids.get(i)).append(".enabled\":false");
                }
                json(modSetting(props.append('}').toString()));
                boolean allBlocked = true;
                for (Mod m : reg.all()) {
                        allBlocked &= m.isBlockedByServer() && !m.isActive();
                }
                check(allBlocked, "every mod honours a server block: " + ids);
                check(ids.containsAll(Arrays.asList("fps", "cps", "keystrokes", "coordinates", "armorstatus", "potion-effects",
                                "ping", "toggle-sneak", "zoom", "lighting", "clock")), "mod ids match the server option keys");

                // disconnect drops the overrides
                PluginChannels.reset();
                check(reg.zoom.isActive() && reg.fullbright.minLight() == 1f, "overrides end with the connection");

                ClickCounter cc = new ClickCounter();
                for (int i = 0; i < 12; i++) {
                        cc.click(0, 1000 + i * 50);
                }
                cc.click(1, 1500);
                check(cc.cps(0, 1599) == 12 && cc.cps(0, 2400) == 3 && cc.cps(1, 2400) == 1 && cc.cps(1, 2600) == 0,
                                "click counter window: " + cc.cps(0, 1599) + "/" + cc.cps(0, 2400));

                System.out.println("ClientModsTest: " + checks + " checks, failures=" + failures);
                System.exit(failures > 0 ? 1 : 0);
        }
}
