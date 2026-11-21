package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;

/**
 * server_rule: server-wide client rules, sent as module options
 * (competitive-game, override-brightness/brightness,
 * override-nametag-render-distance, override-max-chat-length, ...).
 */
public final class ServerRuleModule extends ApolloModule {

        public ServerRuleModule() {
                super("server_rule", "Server rules");
        }

        @Override
        protected void register(Apollo.Router r) {
                // options only, no messages
        }

        public boolean competitiveGame() {
                return isEnabled() && boolProperty("competitive-game", false);
        }

        @SuppressWarnings("unchecked")
        public boolean isCompetitiveCommand(String command) {
                Object o = properties().get("competitive-commands");
                if (!competitiveGame() || !(o instanceof List)) {
                        return false;
                }
                for (Object c : (List<Object>) o) {
                        if (c instanceof String && command.toLowerCase().startsWith(((String) c).toLowerCase())) {
                                return true;
                        }
                }
                return false;
        }

        /** Forced brightness 0..1, or -1 when the server does not override it. */
        public float brightnessOverride() {
                if (!isEnabled() || !boolProperty("override-brightness", false)) {
                        return -1f;
                }
                // Apollo's brightness option is a percentage (default 50)
                return (float) Math.max(0, Math.min(1, numberProperty("brightness", 50) / 100.0));
        }

        /** Forced nametag render distance in blocks, or -1. */
        public int nametagRenderDistance() {
                if (!isEnabled() || !boolProperty("override-nametag-render-distance", false)) {
                        return -1;
                }
                return (int) Math.max(0, Math.min(512, numberProperty("nametag-render-distance", 64)));
        }

        /** Forced max chat message length, or -1. */
        public int maxChatLength() {
                if (!isEnabled() || !boolProperty("override-max-chat-length", false)) {
                        return -1;
                }
                return (int) Math.max(1, Math.min(32767, numberProperty("max-chat-length", 256)));
        }

        public boolean disableShaders() {
                return isEnabled() && boolProperty("disable-shaders", false);
        }

        public boolean antiPortalTraps() {
                return isEnabled() && boolProperty("anti-portal-traps", false);
        }

        @Override
        public void reset() {
                // options live in properties(), cleared by the session reset
        }

        @Override
        public String describeState() {
                return properties().isEmpty() ? "no rules" : properties().toString();
        }
}
