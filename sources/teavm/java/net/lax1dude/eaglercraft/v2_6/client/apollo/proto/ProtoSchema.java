package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime view of the Apollo message tables ({@link ApolloSchemaData}):
 * message types with their fields, and enums. Built lazily on first use.
 */
public final class ProtoSchema {

        public static final String TYPE_URL_PREFIX = "type.googleapis.com/";

        /** One field of a message type. */
        public static final class Field {
                public final int number;
                public final String name;
                /** Kind letter (see {@link ApolloSchemaData}). */
                public final char kind;
                public final boolean repeated;
                /** Message / enum / map-value type name, or null for scalars. */
                public final String typeName;

                Field(int number, String name, char kind, boolean repeated, String typeName) {
                        this.number = number;
                        this.name = name;
                        this.kind = kind;
                        this.repeated = repeated;
                        this.typeName = typeName;
                }

                public boolean isMessage() {
                        return kind == 'm';
                }

                public boolean isMap() {
                        return kind == 'M';
                }

                public boolean isEnum() {
                        return kind == 'e';
                }

                /** Scalar numeric kinds that proto3 packs when repeated. */
                public boolean isPackable() {
                        return kind != 's' && kind != 'y' && kind != 'm' && kind != 'M';
                }

                /** The wire type a value of this field is written with. */
                public int wireType() {
                        switch (kind) {
                        case 'd':
                        case 'X':
                        case 'W':
                                return ProtoReader.WT_FIXED64;
                        case 'f':
                        case 'x':
                        case 'w':
                                return ProtoReader.WT_FIXED32;
                        case 's':
                        case 'y':
                        case 'm':
                        case 'M':
                                return ProtoReader.WT_LEN;
                        default:
                                return ProtoReader.WT_VARINT;
                        }
                }

                @Override
                public String toString() {
                        return number + ":" + name;
                }
        }

        /** One message type. */
        public static final class MessageType {
                public final String name;
                private final Map<Integer, Field> byNumber = new HashMap<>();
                private final Map<String, Field> byName = new LinkedHashMap<>();
                private final Map<String, Field> byJsonKey = new HashMap<>();

                MessageType(String name) {
                        this.name = name;
                }

                void add(Field f) {
                        byNumber.put(f.number, f);
                        byName.put(f.name, f);
                        byJsonKey.put(jsonKey(f.name), f);
                }

                public Field field(int number) {
                        return byNumber.get(number);
                }

                public Field field(String name) {
                        return byName.get(name);
                }

                /** JSON lookup: snake_case and lowerCamelCase both match. */
                public Field fieldForJsonKey(String key) {
                        return byJsonKey.get(jsonKey(key));
                }

                public Collection<Field> fields() {
                        return byName.values();
                }

                /** Short name ("DisplayTitleMessage"). */
                public String simpleName() {
                        return name.substring(name.lastIndexOf('.') + 1);
                }
        }

        /** One enum type. */
        public static final class EnumType {
                public final String name;
                private final Map<String, Integer> byName = new LinkedHashMap<>();
                private final Map<Integer, String> byNumber = new HashMap<>();

                EnumType(String name) {
                        this.name = name;
                }

                public Integer number(String constant) {
                        return byName.get(constant);
                }

                public String name(int number) {
                        return byNumber.get(number);
                }
        }

        private static Map<String, MessageType> messages;
        private static Map<String, EnumType> enums;

        private ProtoSchema() {
        }

        static String jsonKey(String key) {
                StringBuilder sb = new StringBuilder(key.length());
                for (int i = 0; i < key.length(); i++) {
                        char c = key.charAt(i);
                        if (c != '_') {
                                sb.append(Character.toLowerCase(c));
                        }
                }
                return sb.toString();
        }

        private static synchronized void load() {
                if (messages != null) {
                        return;
                }
                Map<String, MessageType> m = new HashMap<>();
                String[] rows = ApolloSchemaData.MESSAGES;
                for (int i = 0; i + 1 < rows.length; i += 2) {
                        MessageType t = new MessageType(rows[i]);
                        if (!rows[i + 1].isEmpty()) {
                                for (String spec : rows[i + 1].split(";")) {
                                        String[] p = spec.split(":");
                                        String kind = p[2];
                                        boolean rep = kind.charAt(0) == 'r';
                                        char k = kind.charAt(rep ? 1 : 0);
                                        t.add(new Field(Integer.parseInt(p[0]), p[1], k, rep, p.length > 3 ? p[3] : null));
                                }
                        }
                        m.put(t.name, t);
                }
                Map<String, EnumType> e = new HashMap<>();
                rows = ApolloSchemaData.ENUMS;
                for (int i = 0; i + 1 < rows.length; i += 2) {
                        EnumType t = new EnumType(rows[i]);
                        for (String spec : rows[i + 1].split(";")) {
                                int eq = spec.indexOf('=');
                                int n = Integer.parseInt(spec.substring(eq + 1));
                                String name = spec.substring(0, eq);
                                t.byName.put(name, n);
                                t.byNumber.put(n, name);
                        }
                        e.put(t.name, t);
                }
                enums = e;
                messages = m;
        }

        public static MessageType message(String fullName) {
                load();
                return fullName == null ? null : messages.get(fullName);
        }

        public static EnumType enumType(String fullName) {
                load();
                return fullName == null ? null : enums.get(fullName);
        }

        public static int messageCount() {
                load();
                return messages.size();
        }

        public static int enumCount() {
                load();
                return enums.size();
        }

        /** "type.googleapis.com/a.b.C" (or any "host/a.b.C") to "a.b.C". */
        public static String typeNameFromUrl(String typeUrl) {
                if (typeUrl == null) {
                        return null;
                }
                int slash = typeUrl.lastIndexOf('/');
                return slash >= 0 ? typeUrl.substring(slash + 1) : typeUrl;
        }
}
