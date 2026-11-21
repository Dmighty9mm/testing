package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/**
 * border: extra rectangular borders (combat-tag walls, arenas). The
 * client draws them and keeps the player from crossing a border that
 * cancels entry/exit (the server enforces it too).
 */
public final class BorderModule extends ApolloModule {

        public static final class Border {
                public final String id;
                public final String world;
                public final boolean cancelEntry;
                public final boolean cancelExit;
                public final boolean canShrinkOrExpand;
                public final int argb;
                public final double minX;
                public final double minZ;
                public final double maxX;
                public final double maxZ;
                public final int durationTicks;
                public final long start;

                Border(String id, String world, boolean cancelEntry, boolean cancelExit, boolean canShrinkOrExpand, int argb,
                                double minX, double minZ, double maxX, double maxZ, int durationTicks, long start) {
                        this.id = id;
                        this.world = world;
                        this.cancelEntry = cancelEntry;
                        this.cancelExit = cancelExit;
                        this.canShrinkOrExpand = canShrinkOrExpand;
                        this.argb = argb;
                        this.minX = Math.min(minX, maxX);
                        this.minZ = Math.min(minZ, maxZ);
                        this.maxX = Math.max(minX, maxX);
                        this.maxZ = Math.max(minZ, maxZ);
                        this.durationTicks = durationTicks;
                        this.start = start;
                }

                public boolean contains(double x, double z) {
                        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                }

                /** Distance from (x, z) to the nearest wall. */
                public double distanceToEdge(double x, double z) {
                        if (contains(x, z)) {
                                return Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
                        }
                        double dx = Math.max(Math.max(minX - x, 0), x - maxX);
                        double dz = Math.max(Math.max(minZ - z, 0), z - maxZ);
                        return Math.sqrt(dx * dx + dz * dz);
                }
        }

        private final Map<String, Border> borders = new LinkedHashMap<>();

        public BorderModule() {
                super("border", "Borders");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("border.v1.DisplayBorderMessage", this::display);
                r.on("border.v1.RemoveBorderMessage", m -> borders.remove(m.getString("id")));
                r.on("border.v1.ResetBordersMessage", m -> reset());
        }

        private void display(ProtoMessage m) {
                ProtoMessage b = m.getMessage("bounds");
                if (b == null) {
                        return;
                }
                String id = m.getString("id");
                if (borders.size() >= 64 && !borders.containsKey(id)) {
                        return;
                }
                borders.put(id, new Border(id, m.getString("world"), m.getBool("cancelEntry"), m.getBool("cancelExit"),
                                m.getBool("canShrinkOrExpand"), ApolloText.argb(m.getMessage("color"), 0xFF55FFFF),
                                b.getDouble("minX"), b.getDouble("minZ"), b.getDouble("maxX"), b.getDouble("maxZ"),
                                m.getInt("durationTicks"), Apollo.now()));
        }

        /**
         * Would moving from (fromX, fromZ) to (toX, toZ) cross a border that
         * cancels that direction? Used by the movement hook.
         */
        public boolean blocksMove(String world, double fromX, double fromZ, double toX, double toZ) {
                for (Border b : borders.values()) {
                        if (!b.world.isEmpty() && world != null && !world.isEmpty() && !b.world.equals(world)) {
                                continue;
                        }
                        boolean inFrom = b.contains(fromX, fromZ);
                        boolean inTo = b.contains(toX, toZ);
                        if ((b.cancelExit && inFrom && !inTo) || (b.cancelEntry && !inFrom && inTo)) {
                                return true;
                        }
                }
                return false;
        }

        public List<Border> all() {
                return new ArrayList<>(borders.values());
        }

        @Override
        public void reset() {
                borders.clear();
        }

        @Override
        public String describeState() {
                return borders.size() + " border(s)";
        }
}
