package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** nametag: multi-line nametags that replace a player's vanilla nametag. */
public final class NametagModule extends ApolloModule {

        private final Map<String, List<List<ApolloText.Run>>> overrides = new LinkedHashMap<>();

        public NametagModule() {
                super("nametag", "Nametags");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("nametag.v1.OverrideNametagMessage", this::override);
                r.on("nametag.v1.ResetNametagMessage", m -> overrides.remove(Ids.uuid(m.getMessage("playerUuid"))));
                r.on("nametag.v1.ResetNametagsMessage", m -> reset());
        }

        private void override(ProtoMessage m) {
                List<List<ApolloText.Run>> lines = new ArrayList<>();
                List<String> json = m.getStrings("adventureJsonLines");
                if (!json.isEmpty()) {
                        for (String s : json) {
                                lines.add(ApolloText.fromJson(s));
                        }
                } else {
                        for (ProtoMessage c : m.getMessages("lines")) {
                                lines.add(ApolloText.fromComponent(c));
                        }
                }
                while (lines.size() > 16) {
                        lines.remove(lines.size() - 1);
                }
                String uuid = Ids.uuid(m.getMessage("playerUuid"));
                if (overrides.size() < 1024 || overrides.containsKey(uuid)) {
                        overrides.put(uuid, lines);
                }
        }

        /** The override lines for a player (top line first), or null for the vanilla nametag. */
        public List<List<ApolloText.Run>> lines(String uuid) {
                return overrides.get(uuid);
        }

        @Override
        public void reset() {
                overrides.clear();
        }

        @Override
        public String describeState() {
                return overrides.size() + " override(s)";
        }
}
