package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;

/** nick_hider: the server supplies the player's nick, which the client shows instead of their own name. */
public final class NickHiderModule extends ApolloModule {

        private String nick = "";

        public NickHiderModule() {
                super("nick_hider", "Nick hider");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("nickhider.v1.OverrideNickHiderMessage", m -> {
                        String n = m.getString("nick");
                        nick = n.length() > 64 ? n.substring(0, 64) : n;
                });
                r.on("nickhider.v1.ResetNickHiderMessage", m -> reset());
        }

        /** Replaces the player's own name in a line of text when a nick is set. */
        public String apply(String text, String ownName) {
                if (nick.isEmpty() || ownName == null || ownName.isEmpty() || text == null) {
                        return text;
                }
                return text.replace(ownName, nick);
        }

        public String nick() {
                return nick;
        }

        @Override
        public void reset() {
                nick = "";
        }

        @Override
        public String describeState() {
                return nick.isEmpty() ? "off" : "nick=" + nick;
        }
}
