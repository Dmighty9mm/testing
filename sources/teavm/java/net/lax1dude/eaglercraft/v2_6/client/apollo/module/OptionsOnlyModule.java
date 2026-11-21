package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.PolicyException;

/**
 * A module the server configures by options only (combat, glint, ...),
 * or one this client recognizes but declines (store checkout): its
 * messages are acknowledged in the debug log as IGNORED_POLICY instead
 * of "unknown".
 */
public class OptionsOnlyModule extends ApolloModule {

        private final String[] declinedTypes;
        private final String declineReason;

        public OptionsOnlyModule(String id, String name) {
                this(id, name, null);
        }

        public OptionsOnlyModule(String id, String name, String declineReason, String... declinedTypes) {
                super(id, name);
                this.declineReason = declineReason;
                this.declinedTypes = declinedTypes;
        }

        @Override
        protected void register(Apollo.Router r) {
                for (String t : declinedTypes) {
                        r.on(t, m -> {
                                throw new PolicyException(m.simpleType() + " not supported: " + declineReason);
                        });
                }
        }

        public boolean option(String key, boolean def) {
                return isEnabled() && boolProperty(key, def);
        }

        @Override
        public void reset() {
        }

        @Override
        public String describeState() {
                return properties().isEmpty() ? "-" : properties().toString();
        }
}
