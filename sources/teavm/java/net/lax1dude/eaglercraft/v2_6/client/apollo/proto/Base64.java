package net.lax1dude.eaglercraft.v2_6.client.apollo.proto;

/** RFC 4648 base64 (standard and URL-safe alphabets on decode), for JSON bytes fields. */
final class Base64 {

        private static final char[] ALPHABET =
                        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

        private Base64() {
        }

        static String encode(byte[] data) {
                StringBuilder sb = new StringBuilder((data.length + 2) / 3 * 4);
                for (int i = 0; i < data.length; i += 3) {
                        int b0 = data[i] & 0xFF;
                        int b1 = i + 1 < data.length ? data[i + 1] & 0xFF : 0;
                        int b2 = i + 2 < data.length ? data[i + 2] & 0xFF : 0;
                        sb.append(ALPHABET[b0 >> 2]);
                        sb.append(ALPHABET[(b0 & 3) << 4 | b1 >> 4]);
                        sb.append(i + 1 < data.length ? ALPHABET[(b1 & 15) << 2 | b2 >> 6] : '=');
                        sb.append(i + 2 < data.length ? ALPHABET[b2 & 63] : '=');
                }
                return sb.toString();
        }

        static byte[] decode(String s) {
                byte[] out = new byte[s.length() * 3 / 4 + 3];
                int n = 0;
                int acc = 0;
                int bits = 0;
                for (int i = 0; i < s.length(); i++) {
                        char c = s.charAt(i);
                        int v;
                        if (c >= 'A' && c <= 'Z') {
                                v = c - 'A';
                        } else if (c >= 'a' && c <= 'z') {
                                v = c - 'a' + 26;
                        } else if (c >= '0' && c <= '9') {
                                v = c - '0' + 52;
                        } else if (c == '+' || c == '-') {
                                v = 62;
                        } else if (c == '/' || c == '_') {
                                v = 63;
                        } else if (c == '=' || c == '\n' || c == '\r' || c == ' ') {
                                continue;
                        } else {
                                throw new ProtoException("bad base64");
                        }
                        acc = acc << 6 | v;
                        bits += 6;
                        if (bits >= 8) {
                                bits -= 8;
                                out[n++] = (byte) (acc >> bits);
                        }
                }
                byte[] r = new byte[n];
                System.arraycopy(out, 0, r, 0, n);
                return r;
        }
}
