package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A decoded protobuf message: its type name plus field name to value.
 * Modules read fields by name with typed getters that return proto3
 * defaults for absent fields, so a server that omits a field (or an
 * older/newer schema) never breaks a module.
 *
 * <p>Value types: String, byte[], Boolean, Integer (32-bit ints and
 * enums), Long (64-bit ints), Float, Double, ProtoMessage, List (repeated)
 * and Map&lt;String, Object&gt; (map fields).</p>
 */
public final class ProtoMessage {

        private final String type;
        private final Map<String, Object> values = new LinkedHashMap<>();
        int unknownFields;

        public ProtoMessage(String type) {
                this.type = type;
        }

        public String type() {
                return type;
        }

        public String simpleType() {
                return type.substring(type.lastIndexOf('.') + 1);
        }

        /** Fields present on the wire whose numbers this schema does not know. */
        public int unknownFieldCount() {
                return unknownFields;
        }

        public boolean has(String field) {
                return values.containsKey(field);
        }

        public Object get(String field) {
                return values.get(field);
        }

        public ProtoMessage set(String field, Object value) {
                if (value == null) {
                        values.remove(field);
                } else {
                        values.put(field, value);
                }
                return this;
        }

        /** Appends to a repeated field. */
        @SuppressWarnings("unchecked")
        public ProtoMessage add(String field, Object value) {
                Object cur = values.get(field);
                List<Object> list;
                if (cur instanceof List) {
                        list = (List<Object>) cur;
                } else {
                        list = new ArrayList<>();
                        values.put(field, list);
                }
                list.add(value);
                return this;
        }

        /** Puts one map-field entry. */
        @SuppressWarnings("unchecked")
        public ProtoMessage put(String field, String key, Object value) {
                Object cur = values.get(field);
                Map<String, Object> map;
                if (cur instanceof Map) {
                        map = (Map<String, Object>) cur;
                } else {
                        map = new LinkedHashMap<>();
                        values.put(field, map);
                }
                map.put(key, value);
                return this;
        }

        public Map<String, Object> values() {
                return Collections.unmodifiableMap(values);
        }

        public String getString(String field) {
                Object v = values.get(field);
                return v instanceof String ? (String) v : "";
        }

        public byte[] getBytes(String field) {
                Object v = values.get(field);
                return v instanceof byte[] ? (byte[]) v : new byte[0];
        }

        public boolean getBool(String field) {
                Object v = values.get(field);
                return v instanceof Boolean && (Boolean) v;
        }

        public int getInt(String field) {
                Object v = values.get(field);
                return v instanceof Number ? ((Number) v).intValue() : 0;
        }

        public long getLong(String field) {
                Object v = values.get(field);
                return v instanceof Number ? ((Number) v).longValue() : 0L;
        }

        public float getFloat(String field) {
                Object v = values.get(field);
                return v instanceof Number ? ((Number) v).floatValue() : 0.0f;
        }

        public double getDouble(String field) {
                Object v = values.get(field);
                return v instanceof Number ? ((Number) v).doubleValue() : 0.0D;
        }

        /** Sub-message, or null when absent. */
        public ProtoMessage getMessage(String field) {
                Object v = values.get(field);
                return v instanceof ProtoMessage ? (ProtoMessage) v : null;
        }

        @SuppressWarnings("unchecked")
        public List<Object> getList(String field) {
                Object v = values.get(field);
                return v instanceof List ? (List<Object>) v : Collections.emptyList();
        }

        /** Repeated sub-messages. */
        public List<ProtoMessage> getMessages(String field) {
                List<ProtoMessage> out = new ArrayList<>();
                for (Object o : getList(field)) {
                        if (o instanceof ProtoMessage) {
                                out.add((ProtoMessage) o);
                        }
                }
                return out;
        }

        public List<String> getStrings(String field) {
                List<String> out = new ArrayList<>();
                for (Object o : getList(field)) {
                        if (o instanceof String) {
                                out.add((String) o);
                        }
                }
                return out;
        }

        @SuppressWarnings("unchecked")
        public Map<String, Object> getMap(String field) {
                Object v = values.get(field);
                return v instanceof Map ? (Map<String, Object>) v : Collections.<String, Object>emptyMap();
        }

        /** A google.protobuf.Duration field in milliseconds (0 when absent). */
        public long getDurationMillis(String field) {
                ProtoMessage d = getMessage(field);
                if (d == null) {
                        return 0L;
                }
                return d.getLong("seconds") * 1000L + d.getInt("nanos") / 1000000;
        }

        /** Enum field as its constant name ("" when unknown). */
        public String getEnumName(String field) {
                ProtoSchema.MessageType t = ProtoSchema.message(type);
                ProtoSchema.Field f = t == null ? null : t.field(field);
                ProtoSchema.EnumType e = f == null ? null : ProtoSchema.enumType(f.typeName);
                String n = e == null ? null : e.name(getInt(field));
                return n == null ? "" : n;
        }

        @Override
        public String toString() {
                return simpleType() + values;
        }
}
