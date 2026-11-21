package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;

/** vignette: a full-screen overlay texture at an opacity. */
public final class VignetteModule extends ApolloModule {

        private String resource = "";
        private float opacity;

        public VignetteModule() {
                super("vignette", "Vignette");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("vignette.v1.DisplayVignetteMessage", m -> {
                        resource = m.getString("resourceLocation");
                        float o = m.getFloat("opacity");
                        opacity = Float.isNaN(o) ? 0f : Math.max(0f, Math.min(1f, o));
                });
                r.on("vignette.v1.ResetVignetteMessage", m -> reset());
        }

        public boolean isActive() {
                return opacity > 0f;
        }

        public String resource() {
                return resource;
        }

        public float opacity() {
                return opacity;
        }

        @Override
        public void reset() {
                resource = "";
                opacity = 0f;
        }

        @Override
        public String describeState() {
                return isActive() ? resource + " @" + opacity : "off";
        }
}
