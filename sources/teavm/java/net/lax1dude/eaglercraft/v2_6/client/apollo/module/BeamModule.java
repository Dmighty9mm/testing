package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;

/** beam: colored beacon-style beams at block positions. */
public final class BeamModule extends ApolloModule {

        public static final class Beam {
                public final String id;
                public final Ids.Pos pos;
                public final int argb;

                Beam(String id, Ids.Pos pos, int argb) {
                        this.id = id;
                        this.pos = pos;
                        this.argb = argb;
                }
        }

        private final Map<String, Beam> beams = new LinkedHashMap<>();

        public BeamModule() {
                super("beam", "Beams");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("beam.v1.DisplayBeaconBeamMessage", m -> {
                        String id = m.getString("id");
                        if (beams.size() < 256 || beams.containsKey(id)) {
                                beams.put(id, new Beam(id, Ids.blockLocation(m.getMessage("location")),
                                                ApolloText.argb(m.getMessage("color"), 0xFFFFFFFF)));
                        }
                });
                r.on("beam.v1.RemoveBeaconBeamMessage", m -> beams.remove(m.getString("id")));
                r.on("beam.v1.ResetBeaconBeamsMessage", m -> reset());
        }

        public List<Beam> all() {
                return new ArrayList<>(beams.values());
        }

        @Override
        public void reset() {
                beams.clear();
        }

        @Override
        public String describeState() {
                return beams.size() + " beam(s)";
        }
}
