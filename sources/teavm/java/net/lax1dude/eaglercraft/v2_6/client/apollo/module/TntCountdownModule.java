package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;

/**
 * tnt_countdown: fuse-time labels above primed TNT. Default fuse comes
 * from the "tnt-ticks" option (vanilla 80); the server can set a specific
 * entity's fuse.
 */
public final class TntCountdownModule extends ApolloModule {

        private static final class Fuse {
                final int ticks;
                final long setAt;

                Fuse(int ticks, long setAt) {
                        this.ticks = ticks;
                        this.setAt = setAt;
                }
        }

        private final Map<Integer, Fuse> fuses = new HashMap<>();

        public TntCountdownModule() {
                super("tnt_countdown", "TNT countdown");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("tntcountdown.v1.SetTntCountdownMessage", m -> {
                        if (fuses.size() < 4096) {
                                fuses.put(Ids.entityId(m.getMessage("entityId")),
                                                new Fuse(Math.max(0, Math.min(m.getInt("durationTicks"), 72000)), Apollo.now()));
                        }
                });
        }

        public int defaultFuseTicks() {
                return (int) Math.max(0, Math.min(72000, numberProperty("tnt-ticks", 80)));
        }

        /**
         * Remaining fuse in ticks for a TNT entity first seen at
         * {@code spawnedAt}: the server-set fuse if any, else the default.
         */
        public int remainingTicks(int entityId, long spawnedAt, long now) {
                Fuse f = fuses.get(entityId);
                long since = f != null ? f.setAt : spawnedAt;
                int total = f != null ? f.ticks : defaultFuseTicks();
                return (int) Math.max(0, total - (now - since) / 50);
        }

        @Override
        public void tick(long now) {
                for (Iterator<Fuse> it = fuses.values().iterator(); it.hasNext();) {
                        Fuse f = it.next();
                        if (now - f.setAt > f.ticks * 50L + 5000L) {
                                it.remove();
                        }
                }
        }

        @Override
        public void reset() {
                fuses.clear();
        }

        @Override
        public String describeState() {
                return "fuse " + defaultFuseTicks() + "t, " + fuses.size() + " set";
        }
}
