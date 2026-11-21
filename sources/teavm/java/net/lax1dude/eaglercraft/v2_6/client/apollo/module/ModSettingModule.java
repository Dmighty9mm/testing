package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoValues;

/**
 * mod_setting: server overrides of client mod options, keyed
 * "&lt;mod id&gt;.&lt;option&gt;" ("zoom.enabled" = false blocks zoom). A null
 * value rolls the override back. The client's own mods consult
 * {@link #isModBlocked} / {@link #option} before every use.
 */
public final class ModSettingModule extends ApolloModule {

        public ModSettingModule() {
                super(ApolloModules.MOD_SETTING, "Mod settings");
        }

        @Override
        protected void register(Apollo.Router r) {
                // options only, no messages
        }

        /** The server forced this mod off ("&lt;modId&gt;.enabled" = false). */
        public boolean isModBlocked(String modId) {
                return isEnabled() && !ProtoValues.asBool(properties().get(modId + ".enabled"), true);
        }

        /** Server override of one mod option, or null when the server does not override it. */
        public Object option(String modId, String option) {
                return isEnabled() ? properties().get(modId + "." + option) : null;
        }

        public boolean optionBool(String modId, String option, boolean def) {
                return ProtoValues.asBool(option(modId, option), def);
        }

        @Override
        public void reset() {
        }

        @Override
        public String describeState() {
                return properties().isEmpty() ? "no overrides" : properties().toString();
        }
}
