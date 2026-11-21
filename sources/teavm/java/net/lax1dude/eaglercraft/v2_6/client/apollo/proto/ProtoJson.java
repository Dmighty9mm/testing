package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * proto3 JSON mapping over {@link ProtoSchema}, for the {@code apollo:json}
 * channel (one JSON object per plugin message, typed by its
 * {@code "@type"} URL).
 *
 * <p>Lenient like the reference parsers: keys match in snake_case or
 * lowerCamelCase, enums accept names or numbers, 64-bit ints accept
 * strings or numbers, unknown keys are skipped. Well-known types follow
 * the proto3 JSON rules: Duration "1.5s", Timestamp RFC 3339, Value /
 * Struct / ListValue as plain JSON, Any as an object with "@type".</p>
 */
public final class ProtoJson {

        private static final String DURATION = "google.protobuf.Duration";
        private static final String TIMESTAMP = "google.protobuf.Timestamp";
        private static final String VALUE = "google.protobuf.Value";
        private static final String STRUCT = "google.protobuf.Struct";
        private static final String LIST_VALUE = "google.protobuf.ListValue";

        private ProtoJson() {
        }

        /**
         * Parses an apollo:json payload. Returns the message; for a type this
         * schema does not know, a type-only message with no fields.
         */
        public static ProtoMessage parse(String text) {
                Object root = Json.parse(text);
                if (!(root instanceof Map)) {
                        throw new ProtoException("apollo:json payload is not an object");
                }
                Object type = ((Map<?, ?>) root).get("@type");
                if (!(type instanceof String) || ((String) type).isEmpty()) {
                        throw new ProtoException("apollo:json payload without @type");
                }
                String name = ProtoSchema.typeNameFromUrl((String) type);
                ProtoSchema.MessageType t = ProtoSchema.message(name);
                if (t == null) {
                        return new ProtoMessage(name);
                }
                return fromJson(t, root, 0);
        }

        /** Converts a parsed JSON node to a message of {@code typeName}. */
        public static ProtoMessage fromJson(String typeName, Object node) {
                ProtoSchema.MessageType t = ProtoSchema.message(typeName);
                if (t == null) {
                        throw new ProtoException("unknown type " + typeName);
                }
                return fromJson(t, node, 0);
        }

        private static ProtoMessage fromJson(ProtoSchema.MessageType t, Object node, int depth) {
                if (depth > ProtoCodec.MAX_DEPTH) {
                        throw new ProtoException("message nesting too deep");
                }
                switch (t.name) {
                case DURATION:
                        return duration(node);
                case TIMESTAMP:
                        return timestamp(node);
                case VALUE:
                        return ProtoValues.fromJava(node, depth);
                case STRUCT:
                        return ProtoValues.struct(asObject(node), depth);
                case LIST_VALUE:
                        return ProtoValues.list(asArray(node), depth);
                case ProtoCodec.ANY:
                        return any(node, depth);
                default:
                        break;
                }
                Map<?, ?> obj = asObject(node);
                ProtoMessage msg = new ProtoMessage(t.name);
                for (Map.Entry<?, ?> e : obj.entrySet()) {
                        String key = String.valueOf(e.getKey());
                        if (key.equals("@type")) {
                                continue;
                        }
                        ProtoSchema.Field f = t.fieldForJsonKey(key);
                        Object v = e.getValue();
                        if (f == null) {
                                msg.unknownFields++;
                                continue;
                        }
                        if (v == Json.NULL && !VALUE.equals(f.typeName)) {
                                continue; // null = field absent
                        }
                        if (f.isMap()) {
                                for (Map.Entry<?, ?> me : asObject(v).entrySet()) {
                                        msg.put(f.name, String.valueOf(me.getKey()),
                                                        fromJson(ProtoSchema.message(f.typeName), me.getValue(), depth + 1));
                                }
                        } else if (f.repeated) {
                                for (Object item : asArray(v)) {
                                        msg.add(f.name, one(f, item, depth));
                                }
                        } else {
                                msg.set(f.name, one(f, v, depth));
                        }
                }
                return msg;
        }

        private static Object one(ProtoSchema.Field f, Object v, int depth) {
                if (f.isMessage()) {
                        ProtoSchema.MessageType st = ProtoSchema.message(f.typeName);
                        if (st == null) {
                                throw new ProtoException("unknown nested type " + f.typeName);
                        }
                        return fromJson(st, v, depth + 1);
                }
                switch (f.kind) {
                case 's':
                        if (!(v instanceof String)) {
                                throw new ProtoException(f.name + ": expected string");
                        }
                        return v;
                case 'y':
                        return Base64.decode(String.valueOf(v));
                case 'b':
                        if (v instanceof Boolean) {
                                return v;
                        }
                        if ("true".equals(v) || "false".equals(v)) {
                                return Boolean.valueOf((String) v);
                        }
                        throw new ProtoException(f.name + ": expected bool");
                case 'e':
                        if (v instanceof String) {
                                ProtoSchema.EnumType et = ProtoSchema.enumType(f.typeName);
                                Integer n = et == null ? null : et.number((String) v);
                                if (n != null) {
                                        return n;
                                }
                                // unknown enum name: proto3 parsers reject; map to 0 (UNSPECIFIED)
                                try {
                                        return Integer.parseInt((String) v);
                                } catch (NumberFormatException ex) {
                                        return 0;
                                }
                        }
                        return (int) toLong(f, v);
                case 'i':
                case 'I':
                case 'z':
                case 'x':
                case 'w':
                        return (int) toLong(f, v);
                case 'l':
                case 'L':
                case 'Z':
                case 'X':
                case 'W':
                        return toLong(f, v);
                case 'f':
                        return (float) toDouble(f, v);
                case 'd':
                        return toDouble(f, v);
                default:
                        throw new ProtoException("bad kind " + f.kind);
                }
        }

        private static long toLong(ProtoSchema.Field f, Object v) {
                if (v instanceof Long) {
                        return (Long) v;
                }
                if (v instanceof Number) {
                        double d = ((Number) v).doubleValue();
                        if (d != Math.rint(d)) {
                                throw new ProtoException(f.name + ": not an integer");
                        }
                        return (long) d;
                }
                if (v instanceof String) {
                        String str = (String) v;
                        try {
                                return Long.parseLong(str);
                        } catch (NumberFormatException e) {
                                // unsigned 64-bit values above Long.MAX_VALUE
                                return parseUnsigned64(f, str);
                        }
                }
                throw new ProtoException(f.name + ": expected number");
        }

        /** Unsigned decimal to its 64-bit pattern (TeaVM has no Long.parseUnsignedLong). */
        static long parseUnsigned64(ProtoSchema.Field f, String str) {
                if (str.isEmpty() || str.length() > 20
                                || (str.length() == 20 && str.compareTo("18446744073709551615") > 0)) {
                        throw new ProtoException(f.name + ": bad integer " + str);
                }
                long v = 0;
                for (int i = 0; i < str.length(); i++) {
                        int d = str.charAt(i) - '0';
                        if (d < 0 || d > 9) {
                                throw new ProtoException(f.name + ": bad integer " + str);
                        }
                        v = v * 10 + d; // wraps mod 2^64 = the unsigned bit pattern
                }
                return v;
        }

        private static double toDouble(ProtoSchema.Field f, Object v) {
                if (v instanceof Number) {
                        return ((Number) v).doubleValue();
                }
                if (v instanceof String) {
                        String str = (String) v;
                        if ("NaN".equals(str)) {
                                return Double.NaN;
                        }
                        if ("Infinity".equals(str)) {
                                return Double.POSITIVE_INFINITY;
                        }
                        if ("-Infinity".equals(str)) {
                                return Double.NEGATIVE_INFINITY;
                        }
                        try {
                                return Double.parseDouble(str);
                        } catch (NumberFormatException e) {
                                throw new ProtoException(f.name + ": bad number " + str);
                        }
                }
                throw new ProtoException(f.name + ": expected number");
        }

        private static Map<?, ?> asObject(Object v) {
                if (!(v instanceof Map)) {
                        throw new ProtoException("expected JSON object");
                }
                return (Map<?, ?>) v;
        }

        private static List<?> asArray(Object v) {
                if (!(v instanceof List)) {
                        throw new ProtoException("expected JSON array");
                }
                return (List<?>) v;
        }

        /** "1.5s" / "-0.25s" / "3s" (also {"seconds":..,"nanos":..} and bare numbers of seconds). */
        static ProtoMessage duration(Object node) {
                ProtoMessage d = new ProtoMessage(DURATION);
                long seconds;
                int nanos;
                if (node instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) node;
                        Object s = m.get("seconds");
                        Object n = m.get("nanos");
                        seconds = s instanceof Number ? ((Number) s).longValue()
                                        : s instanceof String ? Long.parseLong((String) s) : 0L;
                        nanos = n instanceof Number ? ((Number) n).intValue() : 0;
                } else if (node instanceof Number) {
                        double secs = ((Number) node).doubleValue();
                        seconds = (long) secs;
                        nanos = (int) Math.round((secs - seconds) * 1e9);
                } else if (node instanceof String) {
                        String str = ((String) node).trim();
                        if (!str.endsWith("s")) {
                                throw new ProtoException("bad duration " + str);
                        }
                        str = str.substring(0, str.length() - 1);
                        boolean neg = str.startsWith("-");
                        if (neg) {
                                str = str.substring(1);
                        }
                        int dot = str.indexOf('.');
                        try {
                                seconds = Long.parseLong(dot < 0 ? str : str.substring(0, dot).isEmpty() ? "0" : str.substring(0, dot));
                                nanos = 0;
                                if (dot >= 0) {
                                        String frac = str.substring(dot + 1);
                                        if (frac.length() > 9) {
                                                frac = frac.substring(0, 9);
                                        }
                                        StringBuilder sb = new StringBuilder(frac);
                                        while (sb.length() < 9) {
                                                sb.append('0');
                                        }
                                        nanos = Integer.parseInt(sb.toString());
                                }
                        } catch (NumberFormatException e) {
                                throw new ProtoException("bad duration " + node);
                        }
                        if (neg) {
                                seconds = -seconds;
                                nanos = -nanos;
                        }
                } else {
                        throw new ProtoException("bad duration " + node);
                }
                if (seconds != 0) {
                        d.set("seconds", seconds);
                }
                if (nanos != 0) {
                        d.set("nanos", nanos);
                }
                return d;
        }

        static String durationToJson(ProtoMessage d) {
                long s = d.getLong("seconds");
                int n = d.getInt("nanos");
                StringBuilder sb = new StringBuilder();
                if (s < 0 || n < 0) {
                        sb.append('-');
                        s = Math.abs(s);
                        n = Math.abs(n);
                }
                sb.append(s);
                if (n != 0) {
                        String frac = Integer.toString(n);
                        sb.append('.');
                        for (int i = frac.length(); i < 9; i++) {
                                sb.append('0');
                        }
                        String digits = frac;
                        int end = digits.length();
                        while (end > 0 && digits.charAt(end - 1) == '0') {
                                end--;
                        }
                        sb.append(digits, 0, end);
                }
                return sb.append('s').toString();
        }

        /** RFC 3339 ("2024-05-01T12:30:00.5Z", offsets allowed) or {"seconds":..}. */
        static ProtoMessage timestamp(Object node) {
                ProtoMessage ts = new ProtoMessage(TIMESTAMP);
                if (node instanceof Map) {
                        Map<?, ?> m = (Map<?, ?>) node;
                        Object s = m.get("seconds");
                        Object n = m.get("nanos");
                        long sec = s instanceof Number ? ((Number) s).longValue()
                                        : s instanceof String ? Long.parseLong((String) s) : 0L;
                        if (sec != 0) {
                                ts.set("seconds", sec);
                        }
                        if (n instanceof Number && ((Number) n).intValue() != 0) {
                                ts.set("nanos", ((Number) n).intValue());
                        }
                        return ts;
                }
                if (!(node instanceof String)) {
                        throw new ProtoException("bad timestamp " + node);
                }
                String str = (String) node;
                try {
                        int y = Integer.parseInt(str.substring(0, 4));
                        int mo = Integer.parseInt(str.substring(5, 7));
                        int d = Integer.parseInt(str.substring(8, 10));
                        int h = Integer.parseInt(str.substring(11, 13));
                        int mi = Integer.parseInt(str.substring(14, 16));
                        int se = Integer.parseInt(str.substring(17, 19));
                        int i = 19;
                        int nanos = 0;
                        if (i < str.length() && str.charAt(i) == '.') {
                                int start = ++i;
                                while (i < str.length() && Character.isDigit(str.charAt(i))) {
                                        i++;
                                }
                                StringBuilder frac = new StringBuilder(str.substring(start, Math.min(i, start + 9)));
                                while (frac.length() < 9) {
                                        frac.append('0');
                                }
                                nanos = Integer.parseInt(frac.toString());
                        }
                        long offset = 0;
                        char z = str.charAt(i);
                        if (z == '+' || z == '-') {
                                int oh = Integer.parseInt(str.substring(i + 1, i + 3));
                                int om = Integer.parseInt(str.substring(i + 4, i + 6));
                                offset = (oh * 3600L + om * 60L) * (z == '+' ? 1 : -1);
                        } else if (z != 'Z' && z != 'z') {
                                throw new ProtoException("bad timestamp zone " + str);
                        }
                        long secs = daysFromCivil(y, mo, d) * 86400L + h * 3600L + mi * 60L + se - offset;
                        if (secs != 0) {
                                ts.set("seconds", secs);
                        }
                        if (nanos != 0) {
                                ts.set("nanos", nanos);
                        }
                        return ts;
                } catch (RuntimeException e) {
                        if (e instanceof ProtoException) {
                                throw e;
                        }
                        throw new ProtoException("bad timestamp " + str);
                }
        }

        /** Days since 1970-01-01 (H. Hinnant's civil-date algorithm). */
        static long daysFromCivil(int y, int m, int d) {
                y -= m <= 2 ? 1 : 0;
                long era = (y >= 0 ? y : y - 399) / 400;
                long yoe = y - era * 400;
                long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
                long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
                return era * 146097 + doe - 719468;
        }

        private static ProtoMessage any(Object node, int depth) {
                Map<?, ?> obj = asObject(node);
                Object type = obj.get("@type");
                if (!(type instanceof String)) {
                        throw new ProtoException("Any without @type");
                }
                String name = ProtoSchema.typeNameFromUrl((String) type);
                ProtoSchema.MessageType t = ProtoSchema.message(name);
                ProtoMessage any = new ProtoMessage(ProtoCodec.ANY);
                any.set("typeUrl", type);
                if (t != null) {
                        // well-known types inside Any carry their JSON under "value"
                        Object inner = obj.containsKey("value") && name.startsWith("google.protobuf.") ? obj.get("value") : obj;
                        any.set("value", ProtoCodec.encode(fromJson(t, inner, depth + 1)));
                }
                return any;
        }

        // ==================== to JSON ====================

        /** Serializes {@code msg} as an apollo:json payload (snake_case keys, "@type" first). */
        public static String toJson(ProtoMessage msg) {
                Map<String, Object> obj = new LinkedHashMap<>();
                obj.put("@type", ProtoSchema.TYPE_URL_PREFIX + msg.type());
                obj.putAll(toJsonObject(msg));
                return Json.write(obj);
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> toJsonObject(ProtoMessage msg) {
                Object node = toJsonNode(msg);
                if (node instanceof Map) {
                        return (Map<String, Object>) node;
                }
                throw new ProtoException(msg.type() + " has no JSON object form");
        }

        static Object toJsonNode(ProtoMessage msg) {
                switch (msg.type()) {
                case DURATION:
                        return durationToJson(msg);
                case VALUE:
                case STRUCT:
                case LIST_VALUE:
                        return ProtoValues.toJava(msg);
                default:
                        break;
                }
                ProtoSchema.MessageType t = ProtoSchema.message(msg.type());
                Map<String, Object> out = new LinkedHashMap<>();
                for (ProtoSchema.Field f : t.fields()) {
                        Object v = msg.get(f.name);
                        if (v == null) {
                                continue;
                        }
                        String key = snake(f.name);
                        if (f.isMap()) {
                                Map<String, Object> m = new LinkedHashMap<>();
                                for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                                        m.put(String.valueOf(e.getKey()), toJsonNode((ProtoMessage) e.getValue()));
                                }
                                out.put(key, m);
                        } else if (f.repeated) {
                                List<Object> l = new ArrayList<>();
                                for (Object o : (List<?>) v) {
                                        l.add(scalarToJson(f, o));
                                }
                                out.put(key, l);
                        } else {
                                out.put(key, scalarToJson(f, v));
                        }
                }
                return out;
        }

        private static Object scalarToJson(ProtoSchema.Field f, Object v) {
                if (v instanceof ProtoMessage) {
                        return toJsonNode((ProtoMessage) v);
                }
                switch (f.kind) {
                case 'y':
                        return Base64.encode((byte[]) v);
                case 'e':
                        ProtoSchema.EnumType et = ProtoSchema.enumType(f.typeName);
                        String n = et == null ? null : et.name(((Number) v).intValue());
                        return n != null ? n : v;
                case 'l':
                case 'Z':
                case 'W':
                        return String.valueOf(((Number) v).longValue());
                case 'L':
                case 'X':
                        return Long.toUnsignedString(((Number) v).longValue());
                default:
                        return v;
                }
        }

        static String snake(String camel) {
                StringBuilder sb = new StringBuilder(camel.length() + 4);
                for (int i = 0; i < camel.length(); i++) {
                        char c = camel.charAt(i);
                        if (Character.isUpperCase(c)) {
                                sb.append('_').append(Character.toLowerCase(c));
                        } else {
                                sb.append(c);
                        }
                }
                return sb.toString();
        }
}
