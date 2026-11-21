package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/** notification: toast popups (title, description, optional icon) for a set time. */
public final class NotificationModule extends ApolloModule {

        public static final long DEFAULT_MS = 5000L;
        public static final long FADE_MS = 300L;
        public static final int MAX_ACTIVE = 8;

        public static final class Notification {
                public final List<ApolloText.Run> title;
                public final List<ApolloText.Run> description;
                public final String resource;
                public final long start;
                public final long durationMs;

                Notification(List<ApolloText.Run> title, List<ApolloText.Run> description, String resource, long start,
                                long durationMs) {
                        this.title = title;
                        this.description = description;
                        this.resource = resource;
                        this.start = start;
                        this.durationMs = durationMs;
                }

                /** 0..1 slide/fade factor at {@code now}. */
                public float visibility(long now) {
                        long t = now - start;
                        if (t < 0) {
                                return 0f;
                        }
                        if (t < FADE_MS) {
                                return t / (float) FADE_MS;
                        }
                        long left = start + durationMs - now;
                        if (left < FADE_MS) {
                                return Math.max(0f, left / (float) FADE_MS);
                        }
                        return 1f;
                }
        }

        private final List<Notification> active = new ArrayList<>();

        public NotificationModule() {
                super("notification", "Notifications");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("notification.v1.DisplayNotificationMessage", this::display);
                r.on("notification.v1.ResetNotificationsMessage", m -> reset());
        }

        private void display(ProtoMessage m) {
                long d = m.getDurationMillis("displayTime");
                List<ApolloText.Run> title = m.getString("titleAdventureJsonLines").isEmpty() ? ApolloText.of(m.getString("title"))
                                : ApolloText.fromJson(m.getString("titleAdventureJsonLines"));
                List<ApolloText.Run> desc = m.getString("descriptionAdventureJsonLines").isEmpty()
                                ? ApolloText.of(m.getString("description"))
                                : ApolloText.fromJson(m.getString("descriptionAdventureJsonLines"));
                active.add(new Notification(title, desc, m.getString("resourceLocation"), Apollo.now(),
                                d > 0 ? Math.min(d, 600000L) : DEFAULT_MS));
                while (active.size() > MAX_ACTIVE) {
                        active.remove(0);
                }
        }

        @Override
        public void tick(long now) {
                for (Iterator<Notification> it = active.iterator(); it.hasNext();) {
                        Notification n = it.next();
                        if (now >= n.start + n.durationMs) {
                                it.remove();
                        }
                }
        }

        public List<Notification> active() {
                return new ArrayList<>(active);
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
