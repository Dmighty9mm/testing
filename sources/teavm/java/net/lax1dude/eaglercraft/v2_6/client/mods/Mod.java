package net.lax1dude.eaglercraft.v2_6.client.mods;

import java.util.Collections;
import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.ModSettingModule;

/**
 * One client mod. {@link #id} doubles as the Apollo mod_setting key, so
 * a server can force it off with "&lt;id&gt;.enabled" = false (for example a
 * server blocking zoom) - that always wins over the player's toggle.
 */
public abstract class Mod {

        private final String id;
        private final String name;
        private final String description;
        private boolean enabled;
        /** Top-left as a fraction of the screen (HUD mods). */
        float fx;
        float fy;

        protected Mod(String id, String name, String description, boolean enabledByDefault, float fx, float fy) {
                this.id = id;
                this.name = name;
                this.description = description;
                this.enabled = enabledByDefault;
                this.fx = fx;
                this.fy = fy;
        }

        public final String id() {
                return id;
        }

        public final String name() {
                return name;
        }

        public final String description() {
                return description;
        }

        /** The player's own toggle. */
        public final boolean isEnabled() {
                return enabled;
        }

        public final void setEnabled(boolean on) {
                enabled = on;
        }

        /** True while the connected server forbids this mod. */
        public boolean isBlockedByServer() {
                ModSettingModule ms = Apollo.module(ModSettingModule.class);
                return ms != null && ms.isModBlocked(id);
        }

        /** Enabled by the player and allowed by the server. */
        public final boolean isActive() {
                return enabled && !isBlockedByServer();
        }

        /** HUD mods draw a box; functional mods (zoom, fullbright) return false. */
        public boolean hasHud() {
                return false;
        }

        /** The HUD element, or null when there is nothing to show. */
        public HudBox layout(ClientView view, HudBox.Measure m) {
                return null;
        }

        /** Representative content for the HUD editor when the live view has none. */
        public HudBox preview(ClientView view, HudBox.Measure m) {
                return layout(view, m);
        }

        protected static List<String> one(String s) {
                return Collections.singletonList(s);
        }

        public final float fx() {
                return fx;
        }

        public final float fy() {
                return fy;
        }

        public final void setPosition(float x, float y) {
                fx = Math.max(0f, Math.min(1f, x));
                fy = Math.max(0f, Math.min(1f, y));
        }
}
