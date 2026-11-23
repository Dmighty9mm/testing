package net.lax1dude.eaglercraft.v2_6.client.apollo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.Json;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/**
 * Text as Apollo sends it - Adventure/vanilla chat-component JSON strings
 * ("adventure_json_*" fields) or the legacy common.v1.Component message -
 * flattened to colored runs the HUD can draw. Unknown component kinds
 * fall back to their plain text; malformed JSON is shown verbatim.
 */
public final class ApolloText {

        public static final int WHITE = 0xFFFFFFFF;

        /** One run of same-colored text. */
        public static final class Run {
                public final String text;
                public final int argb;
                public final boolean bold;

                public Run(String text, int argb, boolean bold) {
                        this.text = text;
                        this.argb = argb;
                        this.bold = bold;
                }

                @Override
                public String toString() {
                        return text;
                }
        }

        private static final String[] NAMES = { "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
                        "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple",
                        "yellow", "white" };
        private static final int[] RGB = { 0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00,
                        0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF };

        private ApolloText() {
        }

        /** Named or "#RRGGBB" color to ARGB; {@code def} when unknown. */
        public static int color(String name, int def) {
                if (name == null) {
                        return def;
                }
                if (name.startsWith("#") && name.length() == 7) {
                        try {
                                return 0xFF000000 | Integer.parseInt(name.substring(1), 16);
                        } catch (NumberFormatException e) {
                                return def;
                        }
                }
                for (int i = 0; i < NAMES.length; i++) {
                        if (NAMES[i].equals(name)) {
                                return 0xFF000000 | RGB[i];
                        }
                }
                return def;
        }

        /** Parses an Adventure JSON component (or a plain string) into runs. */
        public static List<Run> fromJson(String json) {
                List<Run> out = new ArrayList<>();
                if (json == null || json.isEmpty()) {
                        return out;
                }
                Object node;
                try {
                        node = Json.parse(json);
                } catch (RuntimeException e) {
                        legacy(json, WHITE, false, out);
                        return out;
                }
                walk(node, WHITE, false, out, 0);
                return out;
        }

        private static void walk(Object node, int color, boolean bold, List<Run> out, int depth) {
                if (depth > 32 || node == null || node == Json.NULL) {
                        return;
                }
                if (node instanceof String) {
                        legacy((String) node, color, bold, out);
                } else if (node instanceof List) {
                        // vanilla: an array is the first element with the rest as extras
                        List<?> l = (List<?>) node;
                        for (Object o : l) {
                                walk(o, color, bold, out, depth + 1);
                        }
                } else if (node instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) node;
                        int c = color(m.get("color") instanceof String ? (String) m.get("color") : null, color);
                        boolean b = m.get("bold") instanceof Boolean ? (Boolean) m.get("bold") : bold;
                        Object text = m.get("text");
                        if (text == null) {
                                text = m.get("translate"); // no client-side translations: show the key
                        }
                        if (text == null && m.get("keybind") != null) {
                                text = m.get("keybind");
                        }
                        if (text == null && m.get("selector") != null) {
                                text = m.get("selector");
                        }
                        if (text instanceof String) {
                                legacy((String) text, c, b, out);
                        } else if (text != null) {
                                legacy(String.valueOf(text), c, b, out);
                        }
                        Object extra = m.get("extra");
                        if (extra instanceof List) {
                                for (Object o : (List<?>) extra) {
                                        walk(o, c, b, out, depth + 1);
                                }
                        }
                } else {
                        legacy(String.valueOf(node), color, bold, out);
                }
        }

        /** Plain text that may still carry legacy section-sign color codes. */
        static void legacy(String s, int color, boolean bold, List<Run> out) {
                StringBuilder cur = new StringBuilder();
                int c = color;
                boolean b = bold;
                for (int i = 0; i < s.length(); i++) {
                        char ch = s.charAt(i);
                        if (ch == '§' && i + 1 < s.length()) {
                                if (cur.length() > 0) {
                                        out.add(new Run(cur.toString(), c, b));
                                        cur.setLength(0);
                                }
                                char code = Character.toLowerCase(s.charAt(++i));
                                int idx = "0123456789abcdef".indexOf(code);
                                if (idx >= 0) {
                                        c = 0xFF000000 | RGB[idx];
                                        b = false;
                                } else if (code == 'l') {
                                        b = true;
                                } else if (code == 'r') {
                                        c = color;
                                        b = bold;
                                }
                                continue;
                        }
                        cur.append(ch);
                }
                if (cur.length() > 0) {
                        out.add(new Run(cur.toString(), c, b));
                }
        }

        /** Legacy common.v1.Component message (content, color, decorators, children). */
        public static List<Run> fromComponent(ProtoMessage comp) {
                List<Run> out = new ArrayList<>();
                component(comp, WHITE, out, 0);
                return out;
        }

        private static void component(ProtoMessage comp, int color, List<Run> out, int depth) {
                if (comp == null || depth > 32) {
                        return;
                }
                ProtoMessage col = comp.getMessage("color");
                int c = col != null ? 0xFF000000 | col.getInt("color") : color;
                boolean bold = comp.getList("decorators").contains(2);
                String content = comp.getString("content");
                if (!content.isEmpty()) {
                        legacy(content, c, bold, out);
                }
                for (ProtoMessage child : comp.getMessages("children")) {
                        component(child, c, out, depth + 1);
                }
        }

        /** Prefer the Adventure JSON field, fall back to the legacy Component field. */
        public static List<Run> pick(String adventureJson, ProtoMessage legacyComponent) {
                if (adventureJson != null && !adventureJson.isEmpty()) {
                        return fromJson(adventureJson);
                }
                return fromComponent(legacyComponent);
        }

        public static String plain(List<Run> runs) {
                StringBuilder sb = new StringBuilder();
                for (Run r : runs) {
                        sb.append(r.text);
                }
                return sb.toString();
        }

        /** A plain string as one white run list. */
        public static List<Run> of(String s) {
                List<Run> out = new ArrayList<>();
                if (s != null && !s.isEmpty()) {
                        legacy(s, WHITE, false, out);
                }
                return out;
        }

        /** ARGB from an Apollo common.v1.Color (RGB int, alpha forced opaque unless set). */
        public static int argb(ProtoMessage color, int def) {
                if (color == null || !color.has("color")) {
                        return def;
                }
                int v = color.getInt("color");
                return (v >>> 24) == 0 ? 0xFF000000 | v : v;
        }
}
