package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

import java.util.List;
import java.util.Map;

/**
 * Schema-driven protobuf binary codec over {@link ProtoSchema}.
 *
 * <p>Decoding is defensive: unknown field numbers are skipped (and
 * counted), a field whose wire type does not match the schema is skipped
 * as unknown, packed and unpacked repeated scalars are both accepted, and
 * nesting is capped so a hostile payload cannot blow the stack. Truncated
 * input throws {@link ProtoException}.</p>
 */
public final class ProtoCodec {

        public static final String ANY = "google.protobuf.Any";
        static final int MAX_DEPTH = 48;

        private ProtoCodec() {
        }

        /** Decodes {@code data} as {@code typeName}; null if the type is unknown. */
        public static ProtoMessage decode(String typeName, byte[] data) {
                ProtoSchema.MessageType t = ProtoSchema.message(typeName);
                if (t == null) {
                        return null;
                }
                return decode(t, new ProtoReader(data), 0);
        }

        /**
         * Unpacks a serialized google.protobuf.Any (the lunar:apollo
         * envelope). Returns the inner message, or a type-only message with
         * no fields when the inner type is unknown to this schema (the
         * router logs and ignores those).
         */
        public static ProtoMessage decodeAny(byte[] data) {
                ProtoMessage any = decode(ANY, data);
                String url = any.getString("typeUrl");
                if (url.isEmpty()) {
                        throw new ProtoException("Any without type_url");
                }
                String inner = ProtoSchema.typeNameFromUrl(url);
                ProtoMessage msg = decode(inner, any.getBytes("value"));
                return msg != null ? msg : new ProtoMessage(inner);
        }

        /** Serializes {@code msg} wrapped in a google.protobuf.Any. */
        public static byte[] encodeAny(ProtoMessage msg) {
                ProtoMessage any = new ProtoMessage(ANY);
                any.set("typeUrl", ProtoSchema.TYPE_URL_PREFIX + msg.type());
                any.set("value", encode(msg));
                return encode(any);
        }

        private static ProtoMessage decode(ProtoSchema.MessageType t, ProtoReader r, int depth) {
                if (depth > MAX_DEPTH) {
                        throw new ProtoException("message nesting too deep");
                }
                ProtoMessage msg = new ProtoMessage(t.name);
                while (r.hasMore()) {
                        int tag = r.readTag();
                        int wt = tag & 7;
                        ProtoSchema.Field f = t.field(tag >>> 3);
                        if (f == null) {
                                r.skip(wt);
                                msg.unknownFields++;
                                continue;
                        }
                        if (f.isMap()) {
                                if (wt != ProtoReader.WT_LEN) {
                                        r.skip(wt);
                                        msg.unknownFields++;
                                        continue;
                                }
                                decodeMapEntry(f, r.readSub(), msg, depth);
                        } else if (f.repeated && f.isPackable() && wt == ProtoReader.WT_LEN) {
                                ProtoReader packed = r.readSub();
                                while (packed.hasMore()) {
                                        msg.add(f.name, readScalar(f, packed));
                                }
                        } else if (wt != f.wireType()) {
                                r.skip(wt);
                                msg.unknownFields++;
                        } else {
                                Object v = f.isMessage() ? decodeSub(f.typeName, r.readSub(), depth) : readScalar(f, r);
                                if (f.repeated) {
                                        msg.add(f.name, v);
                                } else {
                                        msg.set(f.name, v); // last one wins (proto3 rule)
                                }
                        }
                }
                return msg;
        }

        private static ProtoMessage decodeSub(String typeName, ProtoReader r, int depth) {
                ProtoSchema.MessageType st = ProtoSchema.message(typeName);
                if (st == null) {
                        throw new ProtoException("unknown nested type " + typeName);
                }
                return decode(st, r, depth + 1);
        }

        private static void decodeMapEntry(ProtoSchema.Field f, ProtoReader e, ProtoMessage msg, int depth) {
                String key = "";
                Object value = null;
                while (e.hasMore()) {
                        int tag = e.readTag();
                        int num = tag >>> 3;
                        int wt = tag & 7;
                        if (num == 1 && wt == ProtoReader.WT_LEN) {
                                key = e.readString();
                        } else if (num == 2 && wt == ProtoReader.WT_LEN) {
                                value = decodeSub(f.typeName, e.readSub(), depth);
                        } else {
                                e.skip(wt);
                        }
                }
                if (value == null) {
                        value = new ProtoMessage(f.typeName);
                }
                msg.put(f.name, key, value);
        }

        private static Object readScalar(ProtoSchema.Field f, ProtoReader r) {
                switch (f.kind) {
                case 's':
                        return r.readString();
                case 'y':
                        return r.readBytes();
                case 'b':
                        return r.readBool();
                case 'i':
                case 'I':
                case 'e':
                        return r.readVarint32();
                case 'l':
                case 'L':
                        return r.readVarint64();
                case 'z':
                        return r.readSint32();
                case 'Z':
                        return r.readSint64();
                case 'f':
                        return r.readFloat();
                case 'd':
                        return r.readDouble();
                case 'x':
                case 'w':
                        return r.readFixed32();
                case 'X':
                case 'W':
                        return r.readFixed64();
                default:
                        throw new ProtoException("bad kind " + f.kind);
                }
        }

        // ==================== encode ====================

        public static byte[] encode(ProtoMessage msg) {
                ProtoSchema.MessageType t = ProtoSchema.message(msg.type());
                if (t == null) {
                        throw new ProtoException("unknown type " + msg.type());
                }
                ProtoWriter w = new ProtoWriter();
                encode(t, msg, w, 0);
                return w.toByteArray();
        }

        private static void encode(ProtoSchema.MessageType t, ProtoMessage msg, ProtoWriter w, int depth) {
                if (depth > MAX_DEPTH) {
                        throw new ProtoException("message nesting too deep");
                }
                for (ProtoSchema.Field f : t.fields()) {
                        Object v = msg.get(f.name);
                        if (v == null) {
                                continue;
                        }
                        if (f.isMap()) {
                                for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                                        ProtoWriter entry = new ProtoWriter();
                                        entry.writeTag(1, ProtoReader.WT_LEN).writeString(String.valueOf(e.getKey()));
                                        entry.writeTag(2, ProtoReader.WT_LEN).writeBytes(encodeNested(f.typeName, e.getValue(), depth));
                                        w.writeTag(f.number, ProtoReader.WT_LEN).writeBytes(entry.toByteArray());
                                }
                        } else if (f.repeated) {
                                List<?> list = (List<?>) v;
                                if (list.isEmpty()) {
                                        continue;
                                }
                                if (f.isPackable()) {
                                        ProtoWriter packed = new ProtoWriter();
                                        for (Object o : list) {
                                                writeScalar(f, o, packed);
                                        }
                                        w.writeTag(f.number, ProtoReader.WT_LEN).writeBytes(packed.toByteArray());
                                } else {
                                        for (Object o : list) {
                                                writeOne(f, o, w, depth);
                                        }
                                }
                        } else {
                                writeOne(f, v, w, depth);
                        }
                }
        }

        private static byte[] encodeNested(String typeName, Object v, int depth) {
                ProtoSchema.MessageType st = ProtoSchema.message(typeName);
                if (st == null || !(v instanceof ProtoMessage)) {
                        throw new ProtoException("bad nested value for " + typeName);
                }
                ProtoWriter sub = new ProtoWriter();
                encode(st, (ProtoMessage) v, sub, depth + 1);
                return sub.toByteArray();
        }

        private static void writeOne(ProtoSchema.Field f, Object v, ProtoWriter w, int depth) {
                w.writeTag(f.number, f.wireType());
                if (f.isMessage()) {
                        w.writeBytes(encodeNested(f.typeName, v, depth));
                } else {
                        writeScalar(f, v, w);
                }
        }

        private static void writeScalar(ProtoSchema.Field f, Object v, ProtoWriter w) {
                switch (f.kind) {
                case 's':
                        w.writeString(String.valueOf(v));
                        break;
                case 'y':
                        w.writeBytes((byte[]) v);
                        break;
                case 'b':
                        w.writeVarint64(Boolean.TRUE.equals(v) ? 1 : 0);
                        break;
                case 'i':
                case 'e':
                        w.writeInt32(((Number) v).intValue());
                        break;
                case 'I':
                        w.writeUint32(((Number) v).intValue());
                        break;
                case 'l':
                case 'L':
                        w.writeVarint64(((Number) v).longValue());
                        break;
                case 'z':
                        w.writeSint32(((Number) v).intValue());
                        break;
                case 'Z':
                        w.writeSint64(((Number) v).longValue());
                        break;
                case 'f':
                        w.writeFloat(((Number) v).floatValue());
                        break;
                case 'd':
                        w.writeDouble(((Number) v).doubleValue());
                        break;
                case 'x':
                case 'w':
                        w.writeFixed32(((Number) v).intValue());
                        break;
                case 'X':
                case 'W':
                        w.writeFixed64(((Number) v).longValue());
                        break;
                default:
                        throw new ProtoException("bad kind " + f.kind);
                }
        }
}
