package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** team: teammates with a marker color and (optionally) their position for off-screen markers. */
public final class TeamModule extends ApolloModule {

        public static final class Member {
                public final String uuid;
                public final List<ApolloText.Run> name;
                public final Ids.Pos pos;
                public final boolean hasPos;
                public final int argb;

                Member(String uuid, List<ApolloText.Run> name, Ids.Pos pos, boolean hasPos, int argb) {
                        this.uuid = uuid;
                        this.name = name;
                        this.pos = pos;
                        this.hasPos = hasPos;
                        this.argb = argb;
                }
        }

        private final Map<String, Member> members = new LinkedHashMap<>();

        public TeamModule() {
                super("team", "Teams");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("team.v1.UpdateTeamMembersMessage", this::update);
                r.on("team.v1.ResetTeamMembersMessage", m -> reset());
        }

        private void update(ProtoMessage m) {
                // each update carries the full current team
                members.clear();
                for (ProtoMessage t : m.getMessages("members")) {
                        if (members.size() >= 128) {
                                break;
                        }
                        String uuid = Ids.uuid(t.getMessage("playerUuid"));
                        members.put(uuid, new Member(uuid,
                                        ApolloText.pick(t.getString("adventureJsonPlayerName"), t.getMessage("playerDisplayName")),
                                        Ids.location(t.getMessage("location")), t.getMessage("location") != null,
                                        ApolloText.argb(t.getMessage("markerColor"), 0xFF55FF55)));
                }
        }

        public List<Member> members() {
                return new ArrayList<>(members.values());
        }

        public Member member(String uuid) {
                return members.get(uuid);
        }

        @Override
        public void reset() {
                members.clear();
        }

        @Override
        public String describeState() {
                return members.size() + " member(s)";
        }
}
