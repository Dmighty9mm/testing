package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

import java.nio.charset.StandardCharsets;

/**
 * Protobuf wire-format reader (varint, zigzag, fixed32/64,
 * length-delimited). Hand-written, java.lang only, so it runs unchanged
 * under TeaVM and in JVM unit tests. Every read is bounds-checked and
 * throws {@link ProtoException} on truncated or malformed input.
 */
public final class ProtoReader {

        public static final int WT_VARINT = 0;
        public static final int WT_FIXED64 = 1;
        public static final int WT_LEN = 2;
        public static final int WT_START_GROUP = 3;
        public static final int WT_END_GROUP = 4;
        public static final int WT_FIXED32 = 5;

        private final byte[] buf;
        private int pos;
        private final int limit;

        public ProtoReader(byte[] buf) {
                this(buf, 0, buf.length);
        }

        public ProtoReader(byte[] buf, int offset, int length) {
                if (offset < 0 || length < 0 || offset + length > buf.length) {
                        throw new ProtoException("bad slice");
                }
                this.buf = buf;
                this.pos = offset;
                this.limit = offset + length;
        }

        public boolean hasMore() {
                return pos < limit;
        }

        public int position() {
                return pos;
        }

        /** Field tag (field number << 3 | wire type); 0 is invalid. */
        public int readTag() {
                long tag = readVarint64();
                if (tag <= 0 || tag > 0xFFFFFFFFL || (tag >>> 3) == 0) {
                        throw new ProtoException("invalid tag " + tag);
                }
                return (int) tag;
        }

        public long readVarint64() {
                long result = 0;
                for (int shift = 0; shift < 64; shift += 7) {
                        if (pos >= limit) {
                                throw new ProtoException("truncated varint");
                        }
                        byte b = buf[pos++];
                        result |= (long) (b & 0x7F) << shift;
                        if ((b & 0x80) == 0) {
                                return result;
                        }
                }
                throw new ProtoException("varint longer than 10 bytes");
        }

        /** int32 / uint32 / enum: the low 32 bits of the varint. */
        public int readVarint32() {
                return (int) readVarint64();
        }

        public int readSint32() {
                int n = readVarint32();
                return (n >>> 1) ^ -(n & 1);
        }

        public long readSint64() {
                long n = readVarint64();
                return (n >>> 1) ^ -(n & 1);
        }

        public boolean readBool() {
                return readVarint64() != 0;
        }

        public int readFixed32() {
                if (limit - pos < 4) {
                        throw new ProtoException("truncated fixed32");
                }
                int v = (buf[pos] & 0xFF) | (buf[pos + 1] & 0xFF) << 8 | (buf[pos + 2] & 0xFF) << 16
                                | (buf[pos + 3] & 0xFF) << 24;
                pos += 4;
                return v;
        }

        public long readFixed64() {
                long lo = readFixed32() & 0xFFFFFFFFL;
                long hi = readFixed32() & 0xFFFFFFFFL;
                return lo | hi << 32;
        }

        public float readFloat() {
                return Float.intBitsToFloat(readFixed32());
        }

        public double readDouble() {
                return Double.longBitsToDouble(readFixed64());
        }

        /** Length prefix of a length-delimited field, checked against the buffer. */
        public int readLength() {
                long len = readVarint64();
                if (len < 0 || len > limit - pos) {
                        throw new ProtoException("length " + len + " exceeds remaining " + (limit - pos));
                }
                return (int) len;
        }

        public byte[] readBytes() {
                int len = readLength();
                byte[] out = new byte[len];
                System.arraycopy(buf, pos, out, 0, len);
                pos += len;
                return out;
        }

        public String readString() {
                int len = readLength();
                String s = new String(buf, pos, len, StandardCharsets.UTF_8);
                pos += len;
                return s;
        }

        /** A reader over the next length-delimited field (sub-message, packed run). */
        public ProtoReader readSub() {
                int len = readLength();
                ProtoReader sub = new ProtoReader(buf, pos, len);
                pos += len;
                return sub;
        }

        /** Skips one field value of the given wire type (unknown fields). */
        public void skip(int wireType) {
                switch (wireType) {
                case WT_VARINT:
                        readVarint64();
                        break;
                case WT_FIXED64:
                        advance(8);
                        break;
                case WT_LEN:
                        advance(readLength());
                        break;
                case WT_FIXED32:
                        advance(4);
                        break;
                case WT_START_GROUP:
                        // deprecated groups: skip until the matching end tag
                        for (int depth = 1; depth > 0;) {
                                int tag = readTag();
                                int wt = tag & 7;
                                if (wt == WT_START_GROUP) {
                                        depth++;
                                } else if (wt == WT_END_GROUP) {
                                        depth--;
                                } else {
                                        skip(wt);
                                }
                        }
                        break;
                default:
                        throw new ProtoException("bad wire type " + wireType);
                }
        }

        private void advance(int n) {
                if (n < 0 || limit - pos < n) {
                        throw new ProtoException("truncated field");
                }
                pos += n;
        }
}
