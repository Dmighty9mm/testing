package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** waypoint: named, colored world markers (beam + label + distance). */
public final class WaypointModule extends ApolloModule {

        public static final class Waypoint {
                public final String name;
                public final Ids.Pos pos;
                public final int argb;
                public final boolean preventRemoval;
                public boolean hidden;

                Waypoint(String name, Ids.Pos pos, int argb, boolean preventRemoval, boolean hidden) {
                        this.name = name;
                        this.pos = pos;
                        this.argb = argb;
                        this.preventRemoval = preventRemoval;
                        this.hidden = hidden;
                }
        }

        private final Map<String, Waypoint> waypoints = new LinkedHashMap<>();

        public WaypointModule() {
                super("waypoint", "Waypoints");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("waypoint.v1.DisplayWaypointMessage", this::display);
                r.on("waypoint.v1.RemoveWaypointMessage", m -> waypoints.remove(m.getString("name")));
                r.on("waypoint.v1.ResetWaypointsMessage", m -> reset());
        }

        private void display(ProtoMessage m) {
                String name = m.getString("name");
                if (waypoints.size() >= 256 && !waypoints.containsKey(name)) {
                        return; // hostile flood guard
                }
                waypoints.put(name, new Waypoint(name, Ids.blockLocation(m.getMessage("location")),
                                ApolloText.argb(m.getMessage("color"), 0xFFFFFFFF), m.getBool("preventRemoval"), m.getBool("hidden")));
        }

        /** Player-initiated removal (waypoint menu); refused for prevent_removal waypoints. */
        public boolean removeByPlayer(String name) {
                Waypoint w = waypoints.get(name);
                if (w == null || w.preventRemoval) {
                        return false;
                }
                waypoints.remove(name);
                return true;
        }

        /** Waypoints to draw in {@code world} ("" matches any: the server may omit it). */
        public List<Waypoint> visible(String world) {
                List<Waypoint> out = new ArrayList<>();
                for (Waypoint w : waypoints.values()) {
                        if (!w.hidden && (world == null || w.pos.world.isEmpty() || world.isEmpty() || w.pos.world.equals(world))) {
                                out.add(w);
                        }
                }
                return out;
        }

        public List<Waypoint> all() {
                return new ArrayList<>(waypoints.values());
        }

        @Override
        public void reset() {
                waypoints.clear();
        }

        @Override
        public String describeState() {
                return waypoints.size() + " waypoint(s)";
        }
}
