package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.HashSet;
import java.util.Set;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** entity: cosmetic per-entity overrides (upside-down "flipped" entities, rainbow sheep). */
public final class EntityModule extends ApolloModule {

        private final Set<Integer> flipped = new HashSet<>();
        private final Set<Integer> rainbowSheep = new HashSet<>();

        public EntityModule() {
                super("entity", "Entities");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("entity.v1.FlipEntityMessage", m -> ids(m, flipped, true));
                r.on("entity.v1.ResetFlipedEntityMessage", m -> ids(m, flipped, false));
                r.on("entity.v1.OverrideRainbowSheepMessage", m -> ids(m, rainbowSheep, true));
                r.on("entity.v1.ResetRainbowSheepMessage", m -> ids(m, rainbowSheep, false));
        }

        private static void ids(ProtoMessage m, Set<Integer> set, boolean add) {
                for (ProtoMessage e : m.getMessages("entityIds")) {
                        if (add) {
                                if (set.size() < 4096) {
                                        set.add(Ids.entityId(e));
                                }
                        } else {
                                set.remove(Ids.entityId(e));
                        }
                }
        }

        public boolean isFlipped(int entityId) {
                return flipped.contains(entityId);
        }

        public boolean isRainbowSheep(int entityId) {
                return rainbowSheep.contains(entityId);
        }

        @Override
        public void reset() {
                flipped.clear();
                rainbowSheep.clear();
        }

        @Override
        public String describeState() {
                return flipped.size() + " flipped, " + rainbowSheep.size() + " rainbow";
        }
}
