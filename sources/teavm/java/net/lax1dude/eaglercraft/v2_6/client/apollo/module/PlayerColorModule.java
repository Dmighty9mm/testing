package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.LinkedHashMap;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;

/**
 * Per-player color overrides: glow (outline color) and colored_fire
 * (burning overlay tint) share this shape - override one, reset one,
 * reset all.
 */
public final class PlayerColorModule extends ApolloModule {

        private final String pkg;
        private final String override;
        private final String resetOne;
        private final String resetAll;
        private final Map<String, Integer> colors = new LinkedHashMap<>();

        private PlayerColorModule(String id, String name, String pkg, String override, String resetOne, String resetAll) {
                super(id, name);
                this.pkg = pkg;
                this.override = override;
                this.resetOne = resetOne;
                this.resetAll = resetAll;
        }

        public static PlayerColorModule glow() {
                return new PlayerColorModule("glow", "Glow", "glow.v1.", "OverrideGlowEffectMessage", "ResetGlowEffectMessage",
                                "ResetGlowEffectsMessage");
        }

        public static PlayerColorModule coloredFire() {
                return new PlayerColorModule("colored_fire", "Colored fire", "coloredfire.v1.", "OverrideColoredFireMessage",
                                "ResetColoredFireMessage", "ResetColoredFiresMessage");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on(pkg + override, m -> {
                        String uuid = Ids.uuid(m.getMessage("playerUuid"));
                        if (colors.size() < 1024 || colors.containsKey(uuid)) {
                                colors.put(uuid, ApolloText.argb(m.getMessage("color"), 0xFFFFFFFF));
                        }
                });
                r.on(pkg + resetOne, m -> colors.remove(Ids.uuid(m.getMessage("playerUuid"))));
                r.on(pkg + resetAll, m -> reset());
        }

        /** ARGB override for a player, or 0 when none. */
        public int color(String uuid) {
                Integer c = colors.get(uuid);
                return c == null ? 0 : c;
        }

        public int size() {
                return colors.size();
        }

        @Override
        public void reset() {
                colors.clear();
        }

        @Override
        public String describeState() {
                return colors.size() + " player(s)";
        }
}
