package net.lax1dude.eaglercraft.v2_6.client.mods;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.Json;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoValues;

/**
 * The mod list plus its persistence: one JSON document
 * {"fps":{"on":true,"x":0.005,"y":0.01}, ...} under {@link #STORE_KEY}.
 * Unknown ids and bad values in stored data are ignored.
 */
public final class ModRegistry {

        public static final String STORE_KEY = "mods";

        private final List<Mod> mods = new ArrayList<>();
        private final Map<String, Mod> byId = new LinkedHashMap<>();

        public final Mods.Fps fps = add(new Mods.Fps());
        public final Mods.Cps cps = add(new Mods.Cps());
        public final Mods.Keystrokes keystrokes = add(new Mods.Keystrokes());
        public final Mods.Coordinates coordinates = add(new Mods.Coordinates());
        public final Mods.Armor armor = add(new Mods.Armor());
        public final Mods.Potions potions = add(new Mods.Potions());
        public final Mods.Ping ping = add(new Mods.Ping());
        public final Mods.Clock clock = add(new Mods.Clock());
        public final Mods.ToggleSprint toggleSprint = add(new Mods.ToggleSprint());
        public final Mods.Zoom zoom = add(new Mods.Zoom());
        public final Mods.Fullbright fullbright = add(new Mods.Fullbright());

        private <T extends Mod> T add(T m) {
                mods.add(m);
                byId.put(m.id(), m);
                return m;
        }

        public List<Mod> all() {
                return Collections.unmodifiableList(mods);
        }

        public Mod byId(String id) {
                return byId.get(id);
        }

        public void load(SettingsStore store) {
                String raw = store == null ? null : store.get(STORE_KEY);
                if (raw == null || raw.isEmpty()) {
                        return;
                }
                Object root;
                try {
                        root = Json.parse(raw);
                } catch (RuntimeException e) {
                        return; // corrupt settings: keep defaults
                }
                if (!(root instanceof Map)) {
                        return;
                }
                for (Map.Entry<?, ?> e : ((Map<?, ?>) root).entrySet()) {
                        Mod m = byId.get(String.valueOf(e.getKey()));
                        if (m == null || !(e.getValue() instanceof Map)) {
                                continue;
                        }
                        Map<?, ?> s = (Map<?, ?>) e.getValue();
                        m.setEnabled(ProtoValues.asBool(s.get("on"), m.isEnabled()));
                        double x = ProtoValues.asNumber(s.get("x"), m.fx());
                        double y = ProtoValues.asNumber(s.get("y"), m.fy());
                        if (!Double.isNaN(x) && !Double.isNaN(y)) {
                                m.setPosition((float) x, (float) y);
                        }
                }
        }

        public String serialize() {
                Map<String, Object> root = new LinkedHashMap<>();
                for (Mod m : mods) {
                        Map<String, Object> s = new LinkedHashMap<>();
                        s.put("on", m.isEnabled());
                        if (m.hasHud()) {
                                s.put("x", (double) m.fx());
                                s.put("y", (double) m.fy());
                        }
                        root.put(m.id(), s);
                }
                return Json.write(root);
        }

        public void save(SettingsStore store) {
                if (store != null) {
                        store.set(STORE_KEY, serialize());
                }
        }

        /** Top-left pixel of a HUD box, clamped fully on screen. */
        public static int[] place(Mod m, int boxW, int boxH, int screenW, int screenH) {
                int x = Math.round(m.fx() * screenW);
                int y = Math.round(m.fy() * screenH);
                x = Math.max(0, Math.min(x, screenW - boxW));
                y = Math.max(0, Math.min(y, screenH - boxH));
                return new int[] { x, y };
        }
}
