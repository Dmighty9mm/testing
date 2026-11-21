package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;

/** chat: "live" chat lines the server can update in place or remove by id. */
public final class ChatModule extends ApolloModule {

        public static final int MAX_LINES = 20;

        private final Map<Integer, List<ApolloText.Run>> lines = new LinkedHashMap<>();

        public ChatModule() {
                super("chat", "Live chat");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("chat.v1.DisplayLiveChatMessageMessage", m -> {
                        int id = m.getInt("messageId");
                        if (!lines.containsKey(id)) {
                                while (lines.size() >= MAX_LINES) {
                                        lines.remove(lines.keySet().iterator().next());
                                }
                        }
                        lines.put(id, ApolloText.fromJson(m.getString("adventureJsonLines")));
                });
                r.on("chat.v1.RemoveLiveChatMessageMessage", m -> lines.remove(m.getInt("messageId")));
        }

        public List<List<ApolloText.Run>> lines() {
                return new ArrayList<>(lines.values());
        }

        @Override
        public void reset() {
                lines.clear();
        }

        @Override
        public String describeState() {
                return lines.size() + " live line(s)";
        }
}
