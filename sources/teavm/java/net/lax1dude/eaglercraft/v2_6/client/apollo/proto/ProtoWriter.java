package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

import java.nio.charset.StandardCharsets;

/** Protobuf wire-format writer; the counterpart of {@link ProtoReader}. */
public final class ProtoWriter {

        private byte[] buf = new byte[64];
        private int len;

        private void ensure(int n) {
                if (len + n > buf.length) {
                        byte[] nb = new byte[Math.max(buf.length * 2, len + n)];
                        System.arraycopy(buf, 0, nb, 0, len);
                        buf = nb;
                }
        }

        public ProtoWriter writeRawByte(int b) {
                ensure(1);
                buf[len++] = (byte) b;
                return this;
        }

        public ProtoWriter writeTag(int field, int wireType) {
                return writeVarint64(((long) field << 3) | wireType);
        }

        public ProtoWriter writeVarint64(long v) {
                ensure(10);
                while ((v & ~0x7FL) != 0) {
                        buf[len++] = (byte) ((v & 0x7F) | 0x80);
                        v >>>= 7;
                }
                buf[len++] = (byte) v;
                return this;
        }

        /** int32 / enum: negative values are sign-extended to 10 bytes (protobuf rule). */
        public ProtoWriter writeInt32(int v) {
                return writeVarint64(v);
        }

        public ProtoWriter writeUint32(int v) {
                return writeVarint64(v & 0xFFFFFFFFL);
        }

        public ProtoWriter writeSint32(int v) {
                return writeUint32((v << 1) ^ (v >> 31));
        }

        public ProtoWriter writeSint64(long v) {
                return writeVarint64((v << 1) ^ (v >> 63));
        }

        public ProtoWriter writeFixed32(int v) {
                ensure(4);
                buf[len++] = (byte) v;
                buf[len++] = (byte) (v >>> 8);
                buf[len++] = (byte) (v >>> 16);
                buf[len++] = (byte) (v >>> 24);
                return this;
        }

        public ProtoWriter writeFixed64(long v) {
                writeFixed32((int) v);
                return writeFixed32((int) (v >>> 32));
        }

        public ProtoWriter writeFloat(float v) {
                return writeFixed32(Float.floatToIntBits(v));
        }

        public ProtoWriter writeDouble(double v) {
                return writeFixed64(Double.doubleToLongBits(v));
        }

        public ProtoWriter writeBytes(byte[] b) {
                writeVarint64(b.length);
                ensure(b.length);
                System.arraycopy(b, 0, buf, len, b.length);
                len += b.length;
                return this;
        }

        public ProtoWriter writeString(String s) {
                return writeBytes(s.getBytes(StandardCharsets.UTF_8));
        }

        public int size() {
                return len;
        }

        public byte[] toByteArray() {
                byte[] out = new byte[len];
                System.arraycopy(buf, 0, out, 0, len);
                return out;
        }
}
