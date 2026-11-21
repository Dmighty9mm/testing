package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** hologram: floating multi-line text in the world. */
public final class HologramModule extends ApolloModule {

        public static final class Hologram {
                public final String id;
                public final Ids.Pos pos;
                public final List<List<ApolloText.Run>> lines;
                public final boolean throughWalls;
                public final boolean shadow;
                public final boolean background;

                Hologram(String id, Ids.Pos pos, List<List<ApolloText.Run>> lines, boolean throughWalls, boolean shadow,
                                boolean background) {
                        this.id = id;
                        this.pos = pos;
                        this.lines = lines;
                        this.throughWalls = throughWalls;
                        this.shadow = shadow;
                        this.background = background;
                }
        }

        private final Map<String, Hologram> holograms = new LinkedHashMap<>();

        public HologramModule() {
                super("hologram", "Holograms");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("hologram.v1.DisplayHologramMessage", this::display);
                r.on("hologram.v1.RemoveHologramMessage", m -> holograms.remove(m.getString("id")));
                r.on("hologram.v1.ResetHologramsMessage", m -> reset());
        }

        private void display(ProtoMessage m) {
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
                while (lines.size() > 32) {
                        lines.remove(lines.size() - 1);
                }
                String id = m.getString("id");
                if (holograms.size() < 256 || holograms.containsKey(id)) {
                        holograms.put(id, new Hologram(id, Ids.location(m.getMessage("location")), lines,
                                        m.getBool("showThroughWalls"), m.getBool("showShadow"), m.getBool("showBackground")));
                }
        }

        public List<Hologram> all() {
                return new ArrayList<>(holograms.values());
        }

        @Override
        public void reset() {
                holograms.clear();
        }

        @Override
        public String describeState() {
                return holograms.size() + " hologram(s)";
        }
}
