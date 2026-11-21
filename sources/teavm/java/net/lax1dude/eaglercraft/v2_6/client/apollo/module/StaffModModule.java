package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.PolicyException;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/**
 * staff_mod: staff-only client mods a server can switch on for its own
 * staff. The only one Apollo defines is STAFF_MOD_XRAY (1). This client
 * has a strict no-cheats policy and does not implement X-ray, even when a
 * server asks: the request is recorded and refused (IGNORED_POLICY in the
 * debug log). Nothing is ever enabled without an explicit server message.
 */
public final class StaffModModule extends ApolloModule {

        public static final int XRAY = 1;

        private final List<Integer> requested = new ArrayList<>();

        public StaffModModule() {
                super("staff_mod", "Staff mods");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("staffmod.v1.EnableStaffModsMessage", this::enable);
                r.on("staffmod.v1.DisableStaffModsMessage", m -> requested.removeAll(m.getList("staffMods")));
        }

        private void enable(ProtoMessage m) {
                for (Object o : m.getList("staffMods")) {
                        if (o instanceof Integer && !requested.contains(o)) {
                                requested.add((Integer) o);
                        }
                }
                if (requested.contains(XRAY)) {
                        throw new PolicyException("server requested the X-ray staff mod; refused (no-cheats policy)");
                }
        }

        /** Staff mods the server asked for (none are ever active in this client). */
        public List<Integer> requested() {
                return new ArrayList<>(requested);
        }

        @Override
        public void reset() {
                requested.clear();
        }

        @Override
        public String describeState() {
                return requested.isEmpty() ? "none requested" : "requested " + requested + " (not supported)";
        }
}
