package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/**
 * rich_presence: what the server says the player is doing (game, mode,
 * map, team size). A browser has no Discord bridge, so this client shows
 * it in the pause menu status line and the Apollo debug screen.
 */
public final class RichPresenceModule extends ApolloModule {

        private ProtoMessage current;

        public RichPresenceModule() {
                super("rich_presence", "Rich presence");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("richpresence.v1.OverrideServerRichPresenceMessage", m -> current = m);
                r.on("richpresence.v1.ResetServerRichPresenceMessage", m -> reset());
        }

        /** "BedWars - Solo - Lighthouse (1/4)" style line, or "" when unset. */
        public String summary() {
                if (current == null) {
                        return "";
                }
                StringBuilder sb = new StringBuilder();
                append(sb, current.getString("gameName"));
                append(sb, current.getString("gameVariantName"));
                append(sb, current.getString("mapName"));
                append(sb, current.getString("gameState"));
                append(sb, current.getString("playerState"));
                int max = current.getInt("teamMaxSize");
                if (max > 0) {
                        sb.append(" (").append(current.getInt("teamCurrentSize")).append('/').append(max).append(')');
                }
                return sb.toString();
        }

        private static void append(StringBuilder sb, String s) {
                if (s != null && !s.isEmpty()) {
                        if (sb.length() > 0) {
                                sb.append(" - ");
                        }
                        sb.append(s);
                }
        }

        public ProtoMessage current() {
                return current;
        }

        @Override
        public void reset() {
                current = null;
        }

        @Override
        public String describeState() {
                String s = summary();
                return s.isEmpty() ? "none" : s;
        }
}
