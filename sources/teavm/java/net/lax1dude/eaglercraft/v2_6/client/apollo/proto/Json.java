package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal strict JSON parser and writer (RFC 8259), java.util only, for
 * the {@code apollo:json} channel. Objects parse to LinkedHashMap, arrays
 * to ArrayList, integers that fit a long to Long (exact, so 64-bit ids
 * survive), other numbers to Double, and null to {@link #NULL}.
 */
public final class Json {

        /** JSON null (a map value of Java null would be ambiguous). */
        public static final Object NULL = new Object() {
                @Override
                public String toString() {
                        return "null";
                }
        };

        static final int MAX_DEPTH = 64;

        private final String s;
        private int pos;

        private Json(String s) {
                this.s = s;
        }

        public static Object parse(String text) {
                if (text == null) {
                        throw new ProtoException("null JSON");
                }
                Json p = new Json(text);
                p.ws();
                Object v = p.value(0);
                p.ws();
                if (p.pos != text.length()) {
                        throw p.err("trailing data");
                }
                return v;
        }

        private ProtoException err(String what) {
                return new ProtoException("JSON " + what + " at " + pos);
        }

        private void ws() {
                while (pos < s.length()) {
                        char c = s.charAt(pos);
                        if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                                pos++;
                        } else {
                                break;
                        }
                }
        }

        private Object value(int depth) {
                if (depth > MAX_DEPTH) {
                        throw err("nesting too deep");
                }
                if (pos >= s.length()) {
                        throw err("unexpected end");
                }
                char c = s.charAt(pos);
                switch (c) {
                case '{':
                        return object(depth);
                case '[':
                        return array(depth);
                case '"':
                        return string();
                case 't':
                        literal("true");
                        return Boolean.TRUE;
                case 'f':
                        literal("false");
                        return Boolean.FALSE;
                case 'n':
                        literal("null");
                        return NULL;
                default:
                        if (c == '-' || (c >= '0' && c <= '9')) {
                                return number();
                        }
                        throw err("unexpected '" + c + "'");
                }
        }

        private void literal(String lit) {
                if (!s.startsWith(lit, pos)) {
                        throw err("bad literal");
                }
                pos += lit.length();
        }

        private Map<String, Object> object(int depth) {
                Map<String, Object> m = new LinkedHashMap<>();
                pos++;
                ws();
                if (peek() == '}') {
                        pos++;
                        return m;
                }
                while (true) {
                        ws();
                        if (peek() != '"') {
                                throw err("expected key");
                        }
                        String k = string();
                        ws();
                        if (peek() != ':') {
                                throw err("expected ':'");
                        }
                        pos++;
                        ws();
                        m.put(k, value(depth + 1));
                        ws();
                        char c = peek();
                        pos++;
                        if (c == '}') {
                                return m;
                        }
                        if (c != ',') {
                                throw err("expected ',' or '}'");
                        }
                }
        }

        private List<Object> array(int depth) {
                List<Object> l = new ArrayList<>();
                pos++;
                ws();
                if (peek() == ']') {
                        pos++;
                        return l;
                }
                while (true) {
                        ws();
                        l.add(value(depth + 1));
                        ws();
                        char c = peek();
                        pos++;
                        if (c == ']') {
                                return l;
                        }
                        if (c != ',') {
                                throw err("expected ',' or ']'");
                        }
                }
        }

        private char peek() {
                if (pos >= s.length()) {
                        throw err("unexpected end");
                }
                return s.charAt(pos);
        }

        private String string() {
                pos++; // opening quote
                StringBuilder sb = new StringBuilder();
                while (true) {
                        char c = peek();
                        pos++;
                        if (c == '"') {
                                return sb.toString();
                        }
                        if (c == '\\') {
                                char e = peek();
                                pos++;
                                switch (e) {
                                case '"':
                                case '\\':
                                case '/':
                                        sb.append(e);
                                        break;
                                case 'b':
                                        sb.append('\b');
                                        break;
                                case 'f':
                                        sb.append('\f');
                                        break;
                                case 'n':
                                        sb.append('\n');
                                        break;
                                case 'r':
                                        sb.append('\r');
                                        break;
                                case 't':
                                        sb.append('\t');
                                        break;
                                case 'u':
                                        if (pos + 4 > s.length()) {
                                                throw err("bad \\u escape");
                                        }
                                        int cp = 0;
                                        for (int i = 0; i < 4; i++) {
                                                int d = Character.digit(s.charAt(pos + i), 16);
                                                if (d < 0) {
                                                        throw err("bad \\u escape");
                                                }
                                                cp = cp * 16 + d;
                                        }
                                        pos += 4;
                                        sb.append((char) cp);
                                        break;
                                default:
                                        throw err("bad escape");
                                }
                        } else if (c < 0x20) {
                                throw err("control character in string");
                        } else {
                                sb.append(c);
                        }
                }
        }

        private Object number() {
                int start = pos;
                if (peek() == '-') {
                        pos++;
                }
                boolean integral = true;
                while (pos < s.length()) {
                        char c = s.charAt(pos);
                        if (c >= '0' && c <= '9') {
                                pos++;
                        } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                                integral = false;
                                pos++;
                        } else {
                                break;
                        }
                }
                String num = s.substring(start, pos);
                try {
                        if (integral && num.length() <= 19) {
                                return Long.parseLong(num);
                        }
                        return Double.parseDouble(num);
                } catch (NumberFormatException e) {
                        try {
                                return Double.parseDouble(num);
                        } catch (NumberFormatException e2) {
                                throw err("bad number " + num);
                        }
                }
        }

        // ==================== writer ====================

        public static String write(Object v) {
                StringBuilder sb = new StringBuilder();
                write(v, sb);
                return sb.toString();
        }

        static void write(Object v, StringBuilder sb) {
                if (v == null || v == NULL) {
                        sb.append("null");
                } else if (v instanceof String) {
                        quote((String) v, sb);
                } else if (v instanceof Boolean) {
                        sb.append(v.toString());
                } else if (v instanceof Double || v instanceof Float) {
                        double d = ((Number) v).doubleValue();
                        if (Double.isNaN(d) || Double.isInfinite(d)) {
                                quote(Double.isNaN(d) ? "NaN" : d > 0 ? "Infinity" : "-Infinity", sb);
                        } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                                sb.append((long) d);
                        } else {
                                sb.append(v instanceof Float ? Float.toString((Float) v) : Double.toString(d));
                        }
                } else if (v instanceof Number) {
                        sb.append(v.toString());
                } else if (v instanceof Map) {
                        sb.append('{');
                        boolean first = true;
                        for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                                if (!first) {
                                        sb.append(',');
                                }
                                first = false;
                                quote(String.valueOf(e.getKey()), sb);
                                sb.append(':');
                                write(e.getValue(), sb);
                        }
                        sb.append('}');
                } else if (v instanceof List) {
                        sb.append('[');
                        boolean first = true;
                        for (Object o : (List<?>) v) {
                                if (!first) {
                                        sb.append(',');
                                }
                                first = false;
                                write(o, sb);
                        }
                        sb.append(']');
                } else {
                        quote(v.toString(), sb);
                }
        }

        static void quote(String str, StringBuilder sb) {
                sb.append('"');
                for (int i = 0; i < str.length(); i++) {
                        char c = str.charAt(i);
                        switch (c) {
                        case '"':
                                sb.append("\\\"");
                                break;
                        case '\\':
                                sb.append("\\\\");
                                break;
                        case '\n':
                                sb.append("\\n");
                                break;
                        case '\r':
                                sb.append("\\r");
                                break;
                        case '\t':
                                sb.append("\\t");
                                break;
                        default:
                                if (c < 0x20) {
                                        String hex = Integer.toHexString(c);
                                        sb.append("\\u");
                                        for (int k = hex.length(); k < 4; k++) {
                                                sb.append('0');
                                        }
                                        sb.append(hex);
                                } else {
                                        sb.append(c);
                                }
                        }
                }
                sb.append('"');
        }
}
