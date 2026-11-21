package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** Conversions of Apollo's common.v1 id / location messages. */
public final class Ids {

        private Ids() {
        }

        /** common.v1.Uuid (high64/low64) to the canonical "xxxxxxxx-xxxx-..." string. */
        public static String uuid(ProtoMessage u) {
                if (u == null) {
                        return "";
                }
                return uuid(u.getLong("high64"), u.getLong("low64"));
        }

        public static String uuid(long hi, long lo) {
                return hex(hi >>> 32, 8) + "-" + hex(hi >>> 16, 4) + "-" + hex(hi, 4) + "-" + hex(lo >>> 48, 4) + "-"
                                + hex(lo, 12);
        }

        private static String hex(long v, int digits) {
                StringBuilder sb = new StringBuilder();
                for (int i = digits - 1; i >= 0; i--) {
                        sb.append("0123456789abcdef".charAt((int) (v >>> (i * 4)) & 15));
                }
                return sb.toString();
        }

        /** common.v1.Uuid from a canonical string (for outbound messages and tests). */
        public static ProtoMessage uuidMessage(String s) {
                String h = s.replace("-", "");
                ProtoMessage u = new ProtoMessage("lunarclient.apollo.common.v1.Uuid");
                u.set("high64", parseHex(h.substring(0, 16)));
                u.set("low64", parseHex(h.substring(16, 32)));
                return u;
        }

        private static long parseHex(String h) {
                long v = 0;
                for (int i = 0; i < h.length(); i++) {
                        v = v << 4 | Character.digit(h.charAt(i), 16);
                }
                return v;
        }

        /** EntityId: the numeric network id (uuid is a fallback key). */
        public static int entityId(ProtoMessage e) {
                return e == null ? 0 : e.getInt("entityId");
        }

        /** A world position (Location doubles or BlockLocation ints, block centre for blocks). */
        public static final class Pos {
                public final String world;
                public final double x;
                public final double y;
                public final double z;

                public Pos(String world, double x, double y, double z) {
                        this.world = world;
                        this.x = x;
                        this.y = y;
                        this.z = z;
                }

                @Override
                public String toString() {
                        return world + "(" + x + ", " + y + ", " + z + ")";
                }
        }

        public static Pos location(ProtoMessage l) {
                if (l == null) {
                        return new Pos("", 0, 0, 0);
                }
                return new Pos(l.getString("world"), l.getDouble("x"), l.getDouble("y"), l.getDouble("z"));
        }

        public static Pos blockLocation(ProtoMessage l) {
                if (l == null) {
                        return new Pos("", 0, 0, 0);
                }
                return new Pos(l.getString("world"), l.getInt("x") + 0.5, l.getInt("y"), l.getInt("z") + 0.5);
        }
}
