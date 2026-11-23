package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * google.protobuf.Value / Struct / ListValue to and from plain Java
 * (null, Boolean, Double, String, Map, List). Server rules and mod
 * settings arrive as {@code map<string, Value>}.
 */
public final class ProtoValues {

        private static final String VALUE = "google.protobuf.Value";
        private static final String STRUCT = "google.protobuf.Struct";
        private static final String LIST_VALUE = "google.protobuf.ListValue";

        private ProtoValues() {
        }

        /** A Value message as plain Java; Struct to Map, ListValue to List. */
        public static Object toJava(ProtoMessage v) {
                if (v == null) {
                        return null;
                }
                if (STRUCT.equals(v.type())) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> e : v.getMap("fields").entrySet()) {
                                m.put(e.getKey(), toJava((ProtoMessage) e.getValue()));
                        }
                        return m;
                }
                if (LIST_VALUE.equals(v.type())) {
                        List<Object> l = new ArrayList<>();
                        for (ProtoMessage item : v.getMessages("values")) {
                                l.add(toJava(item));
                        }
                        return l;
                }
                if (v.has("numberValue")) {
                        return v.getDouble("numberValue");
                }
                if (v.has("stringValue")) {
                        return v.getString("stringValue");
                }
                if (v.has("boolValue")) {
                        return v.getBool("boolValue");
                }
                if (v.has("structValue")) {
                        return toJava(v.getMessage("structValue"));
                }
                if (v.has("listValue")) {
                        return toJava(v.getMessage("listValue"));
                }
                return null;
        }

        /** Plain Java (or a parsed JSON node) to a Value message. */
        public static ProtoMessage fromJava(Object o) {
                return fromJava(o, 0);
        }

        static ProtoMessage fromJava(Object o, int depth) {
                if (depth > ProtoCodec.MAX_DEPTH) {
                        throw new ProtoException("value nesting too deep");
                }
                ProtoMessage v = new ProtoMessage(VALUE);
                if (o == null || o == Json.NULL) {
                        v.set("nullValue", 0);
                } else if (o instanceof Boolean) {
                        v.set("boolValue", o);
                } else if (o instanceof Number) {
                        v.set("numberValue", ((Number) o).doubleValue());
                } else if (o instanceof String) {
                        v.set("stringValue", o);
                } else if (o instanceof Map) {
                        v.set("structValue", struct((Map<?, ?>) o, depth + 1));
                } else if (o instanceof List) {
                        v.set("listValue", list((List<?>) o, depth + 1));
                } else {
                        v.set("stringValue", String.valueOf(o));
                }
                return v;
        }

        static ProtoMessage struct(Map<?, ?> m, int depth) {
                ProtoMessage s = new ProtoMessage(STRUCT);
                for (Map.Entry<?, ?> e : m.entrySet()) {
                        s.put("fields", String.valueOf(e.getKey()), fromJava(e.getValue(), depth + 1));
                }
                return s;
        }

        static ProtoMessage list(List<?> l, int depth) {
                ProtoMessage lv = new ProtoMessage(LIST_VALUE);
                lv.set("values", new ArrayList<Object>());
                for (Object o : l) {
                        lv.add("values", fromJava(o, depth + 1));
                }
                return lv;
        }

        // ==================== typed reads of plain values ====================

        public static boolean asBool(Object o, boolean def) {
                if (o instanceof Boolean) {
                        return (Boolean) o;
                }
                if (o instanceof String) {
                        String s = (String) o;
                        if ("true".equalsIgnoreCase(s)) {
                                return true;
                        }
                        if ("false".equalsIgnoreCase(s)) {
                                return false;
                        }
                }
                return def;
        }

        public static double asNumber(Object o, double def) {
                if (o instanceof Number) {
                        return ((Number) o).doubleValue();
                }
                if (o instanceof String) {
                        try {
                                return Double.parseDouble((String) o);
                        } catch (NumberFormatException e) {
                                return def;
                        }
                }
                return def;
        }

        public static String asString(Object o, String def) {
                return o instanceof String ? (String) o : o == null ? def : String.valueOf(o);
        }
}
