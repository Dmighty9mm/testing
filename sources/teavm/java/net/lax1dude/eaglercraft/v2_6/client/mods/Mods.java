package net.lax1dude.eaglercraft.v2_6.client.mods;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.ModSettingModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.ServerRuleModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoValues;

/**
 * The built-in mods. Ids match the option keys servers use in Apollo
 * mod_setting overrides ("zoom", "fps", "toggle-sneak", "lighting", ...),
 * which is what lets a server block one. QoL only: nothing here reads
 * hidden information or automates combat.
 */
public final class Mods {

        private Mods() {
        }

        static ModSettingModule settings() {
                return Apollo.module(ModSettingModule.class);
        }

        /** Server override of one option, or {@code def}. */
        static boolean serverOption(String id, String option, boolean def) {
                ModSettingModule ms = settings();
                return ms == null ? def : ms.optionBool(id, option, def);
        }

        // ==================== HUD text mods ====================

        /** A mod whose HUD is a plain text panel. */
        public abstract static class TextMod extends Mod {
                protected TextMod(String id, String name, String desc, boolean on, float fx, float fy) {
                        super(id, name, desc, on, fx, fy);
                }

                @Override
                public boolean hasHud() {
                        return true;
                }

                protected abstract List<String> lines(ClientView v);

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        List<String> l = lines(v);
                        return l.isEmpty() ? null : HudBox.lines(l, m);
                }
        }

        public static final class Fps extends TextMod {
                public Fps() {
                        super("fps", "FPS", "Frames per second", true, 0.005f, 0.01f);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        return one(v.fps() + " FPS");
                }
        }

        public static final class Cps extends TextMod {
                public Cps() {
                        super("cps", "CPS", "Clicks per second (left | right)", true, 0.005f, 0.05f);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        return one(v.cps(0) + " | " + v.cps(1) + " CPS");
                }
        }

        public static final class Coordinates extends TextMod {
                public Coordinates() {
                        super("coordinates", "Coordinates", "Position and facing", true, 0.005f, 0.09f);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        List<String> l = new ArrayList<>();
                        l.add("X: " + fmt(v.x()));
                        l.add("Y: " + fmt(v.y()));
                        l.add("Z: " + fmt(v.z()));
                        l.add("Facing: " + facing(v.yaw()));
                        return l;
                }

                static String fmt(double d) {
                        long tenths = Math.round(d * 10.0);
                        String sign = tenths < 0 ? "-" : "";
                        tenths = Math.abs(tenths);
                        return sign + (tenths / 10) + "." + (tenths % 10);
                }

                /** Vanilla compass: yaw 0 = south (+Z), 90 = west (-X), 180 = north, 270 = east. */
                public static String facing(float yaw) {
                        float y = ((yaw % 360f) + 360f) % 360f;
                        int q = (int) Math.floor((y + 45f) / 90f) & 3;
                        switch (q) {
                        case 0:
                                return "South (+Z)";
                        case 1:
                                return "West (-X)";
                        case 2:
                                return "North (-Z)";
                        default:
                                return "East (+X)";
                        }
                }
        }

        public static final class Ping extends TextMod {
                public Ping() {
                        super("ping", "Ping", "Connection latency", false, 0.005f, 0.22f);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        int p = v.pingMs();
                        return one(p < 0 ? "Ping: n/a" : "Ping: " + p + " ms");
                }
        }

        public static final class Clock extends TextMod {
                public Clock() {
                        super("clock", "Clock", "Local time", false, 0.90f, 0.01f);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        return one(v.localTime());
                }
        }

        public static final class Armor extends TextMod {
                public Armor() {
                        super("armorstatus", "Armor", "Worn armor and durability", false, 0.005f, 0.70f);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        List<String> l = new ArrayList<>();
                        for (String s : v.armor()) {
                                if (!s.isEmpty()) {
                                        l.add(s);
                                }
                        }
                        return l;
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        HudBox b = layout(v, m);
                        if (b != null) {
                                return b;
                        }
                        List<String> l = new ArrayList<>();
                        l.add("Helmet 363/363");
                        l.add("Chestplate 528/528");
                        l.add("Leggings 495/495");
                        l.add("Boots 429/429");
                        return HudBox.lines(l, m);
                }
        }

        public static final class Potions extends TextMod {
                public Potions() {
                        super("potion-effects", "Potion effects", "Active effects and time left", false, 0.80f, 0.30f);
                }

                @Override
                protected List<String> lines(ClientView v) {
                        return new ArrayList<>(v.effects());
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        HudBox b = layout(v, m);
                        if (b != null) {
                                return b;
                        }
                        List<String> l = new ArrayList<>();
                        l.add("Speed II 1:30");
                        l.add("Strength 0:45");
                        return HudBox.lines(l, m);
                }
        }

        // ==================== keystrokes ====================

        public static final class Keystrokes extends Mod {
                static final int KEY = 20;
                static final int GAP = 2;

                public Keystrokes() {
                        super("keystrokes", "Keystrokes", "WASD, mouse buttons and jump", true, 0.005f, 0.30f);
                }

                @Override
                public boolean hasHud() {
                        return true;
                }

                @Override
                public HudBox layout(ClientView v, HudBox.Measure m) {
                        HudBox b = new HudBox();
                        int col = KEY + GAP;
                        key(b, "W", col, 0, KEY, v.keyDown("forward"));
                        key(b, "A", 0, col, KEY, v.keyDown("left"));
                        key(b, "S", col, col, KEY, v.keyDown("back"));
                        key(b, "D", col * 2, col, KEY, v.keyDown("right"));
                        int mw = (col * 3 - GAP - GAP) / 2;
                        key(b, "LMB " + v.cps(0), 0, col * 2, mw, v.mouseDown(0));
                        key(b, "RMB " + v.cps(1), mw + GAP, col * 2, mw, v.mouseDown(1));
                        b.rect(0, col * 3, col * 3 - GAP, 8, v.keyDown("jump") ? HudBox.BG_PRESSED : HudBox.BG);
                        b.rect(col * 3 / 2 - 10, col * 3 + 3, 20, 1, v.keyDown("jump") ? HudBox.TEXT_PRESSED : HudBox.TEXT);
                        return b;
                }

                private static void key(HudBox b, String label, int x, int y, int w, boolean down) {
                        b.rect(x, y, w, KEY, down ? HudBox.BG_PRESSED : HudBox.BG);
                        b.text(label, x + w / 2, y + (KEY - 8) / 2, down ? HudBox.TEXT_PRESSED : HudBox.TEXT, true);
                }
        }

        // ==================== functional mods ====================

        /** Toggle sprint: tap the sprint key to keep sprinting (Apollo id "toggle-sneak", option "toggle-sprint"). */
        public static final class ToggleSprint extends TextMod {
                private boolean toggled;

                public ToggleSprint() {
                        super("toggle-sneak", "Toggle Sprint", "Tap sprint to keep sprinting", false, 0.005f, 0.95f);
                }

                @Override
                public boolean isBlockedByServer() {
                        return super.isBlockedByServer() || !serverOption(id(), "toggle-sprint", true);
                }

                /** Sprint key pressed (not held): flips the toggle. */
                public void onSprintKey() {
                        if (isActive()) {
                                toggled = !toggled;
                        }
                }

                public boolean sprintHeld() {
                        return isActive() && toggled;
                }

                public void reset() {
                        toggled = false;
                }

                @Override
                protected List<String> lines(ClientView v) {
                        return sprintHeld() ? one("[Sprinting (Toggled)]") : new ArrayList<String>();
                }

                @Override
                public HudBox preview(ClientView v, HudBox.Measure m) {
                        return HudBox.lines(one("[Sprinting (Toggled)]"), m);
                }
        }

        /** Zoom: hold the zoom key (C) to divide the field of view. */
        public static final class Zoom extends Mod {
                public static final float DEFAULT_DIVISOR = 4f;

                public Zoom() {
                        super("zoom", "Zoom", "Hold C to zoom", true, 0f, 0f);
                }

                public float fov(float baseFov, boolean keyDown) {
                        if (!keyDown || !isActive()) {
                                return baseFov;
                        }
                        ModSettingModule ms = settings();
                        float div = DEFAULT_DIVISOR;
                        if (ms != null) {
                                double d = ProtoValues.asNumber(ms.option(id(), "zoom-divisor"), DEFAULT_DIVISOR);
                                if (d >= 1.0 && d <= 32.0) {
                                        div = (float) d;
                                }
                        }
                        return baseFov / div;
                }
        }

        /** Fullbright (Apollo id "lighting", option "full-bright"); a server brightness rule wins. */
        public static final class Fullbright extends Mod {
                public Fullbright() {
                        super("lighting", "Fullbright", "See in the dark", false, 0f, 0f);
                }

                @Override
                public boolean isBlockedByServer() {
                        return super.isBlockedByServer() || !serverOption(id(), "full-bright", true);
                }

                /** Minimum light level 0..1 for the world shader. */
                public float minLight() {
                        ServerRuleModule rules = Apollo.module(ServerRuleModule.class);
                        float forced = rules == null ? -1f : rules.brightnessOverride();
                        if (forced >= 0f) {
                                return forced;
                        }
                        return isActive() ? 1f : 0f;
                }
        }
}
