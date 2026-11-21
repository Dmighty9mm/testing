package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** limb: hide armor pieces / body parts of specific players (BodyPart / ArmorPiece enums). */
public final class LimbModule extends ApolloModule {

        private final Map<String, Set<Integer>> hiddenArmor = new HashMap<>();
        private final Map<String, Set<Integer>> hiddenParts = new HashMap<>();

        public LimbModule() {
                super("limb", "Limbs");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("limb.v1.HideArmorPiecesMessage", m -> change(hiddenArmor, m, "armorPieces", true));
                r.on("limb.v1.ResetArmorPiecesMessage", m -> change(hiddenArmor, m, "armorPieces", false));
                r.on("limb.v1.HideBodyPartMessage", m -> change(hiddenParts, m, "bodyParts", true));
                r.on("limb.v1.ResetBodyPartMessage", m -> change(hiddenParts, m, "bodyParts", false));
        }

        private static void change(Map<String, Set<Integer>> map, ProtoMessage m, String field, boolean hide) {
                String uuid = Ids.uuid(m.getMessage("playerUuid"));
                Set<Integer> set = map.get(uuid);
                if (set == null) {
                        if (!hide || map.size() >= 1024) {
                                return;
                        }
                        set = new HashSet<>();
                        map.put(uuid, set);
                }
                for (Object o : m.getList(field)) {
                        if (o instanceof Integer) {
                                if (hide) {
                                        set.add((Integer) o);
                                } else {
                                        set.remove(o);
                                }
                        }
                }
                if (set.isEmpty()) {
                        map.remove(uuid);
                }
        }

        /** BodyPart: 1 head, 2 torso, 3 left arm, 4 right arm, 5 left leg, 6 right leg. */
        public boolean isBodyPartHidden(String uuid, int part) {
                Set<Integer> s = hiddenParts.get(uuid);
                return s != null && s.contains(part);
        }

        /** ArmorPiece: 1 helmet, 2 chestplate, 3 leggings, 4 boots. */
        public boolean isArmorHidden(String uuid, int piece) {
                Set<Integer> s = hiddenArmor.get(uuid);
                return s != null && s.contains(piece);
        }

        @Override
        public void reset() {
                hiddenArmor.clear();
                hiddenParts.clear();
        }

        @Override
        public String describeState() {
                return hiddenParts.size() + " body / " + hiddenArmor.size() + " armor override(s)";
        }
}
