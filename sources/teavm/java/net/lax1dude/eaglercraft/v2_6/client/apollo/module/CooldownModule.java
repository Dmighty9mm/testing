package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** cooldown: named countdown timers with an item or texture icon. */
public final class CooldownModule extends ApolloModule {

        public static final class Cooldown {
                public final String name;
                public final long start;
                public final long durationMs;
                /** Item name ("ENDER_PEARL") or texture path; "" when none. */
                public final String icon;

                Cooldown(String name, long start, long durationMs, String icon) {
                        this.name = name;
                        this.start = start;
                        this.durationMs = durationMs;
                        this.icon = icon;
                }

                public long remaining(long now) {
                        return Math.max(0L, start + durationMs - now);
                }

                /** 1 at start, 0 when done. */
                public float fraction(long now) {
                        return durationMs <= 0 ? 0f : remaining(now) / (float) durationMs;
                }
        }

        private final Map<String, Cooldown> active = new LinkedHashMap<>();

        public CooldownModule() {
                super("cooldown", "Cooldowns");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("cooldown.v1.DisplayCooldownMessage", this::display);
                r.on("cooldown.v1.RemoveCooldownMessage", m -> active.remove(m.getString("name")));
                r.on("cooldown.v1.ResetCooldownsMessage", m -> reset());
        }

        private void display(ProtoMessage m) {
                String name = m.getString("name");
                long d = Math.min(m.getDurationMillis("duration"), 24L * 3600000L);
                active.remove(name);
                active.put(name, new Cooldown(name, Apollo.now(), Math.max(0L, d), iconName(m.getMessage("icon"))));
        }

        /** Icon oneof: item stack name, else the resource location path. */
        public static String iconName(ProtoMessage icon) {
                if (icon == null) {
                        return "";
                }
                ProtoMessage item = icon.getMessage("itemStack");
                if (item != null) {
                        return !item.getString("itemName").isEmpty() ? item.getString("itemName") : "#" + item.getInt("itemId");
                }
                ProtoMessage s = icon.getMessage("simpleResourceLocation");
                if (s == null) {
                        s = icon.getMessage("advancedResourceLocation");
                }
                return s == null ? "" : s.getString("resourceLocation");
        }

        @Override
        public void tick(long now) {
                for (Iterator<Cooldown> it = active.values().iterator(); it.hasNext();) {
                        if (it.next().remaining(now) <= 0) {
                                it.remove();
                        }
                }
        }

        public List<Cooldown> active() {
                return new ArrayList<>(active.values());
        }

        @Override
        public void reset() {
                active.clear();
        }

        @Override
        public String describeState() {
                return active.size() + " active";
        }
}
