package net.lax1dude.eaglercraft.v2_6.client.apollo;

import java.util.ArrayList;
import java.util.List;

/** Ring buffer of recent Apollo traffic for the debug screen. */
public final class ApolloDebugLog {

        public static final int LIMIT = 100;

        /** Outcome of one message. */
        public enum Status {
                HANDLED, IGNORED_UNKNOWN, IGNORED_DISABLED, IGNORED_POLICY, ERROR, SENT
        }

        /** One recent message. */
        public static final class Entry {
                public final long timeMs;
                public final String channel;
                public final String type;
                public final Status status;
                public final String detail;

                Entry(long timeMs, String channel, String type, Status status, String detail) {
                        this.timeMs = timeMs;
                        this.channel = channel;
                        this.type = type;
                        this.status = status;
                        this.detail = detail;
                }

                @Override
                public String toString() {
                        return (status == Status.SENT ? "-> " : "<- ") + channel + " " + type + " [" + status + "]"
                                        + (detail == null || detail.isEmpty() ? "" : " " + detail);
                }
        }

        private final List<Entry> entries = new ArrayList<>();

        void add(long timeMs, String channel, String type, Status status, String detail) {
                entries.add(new Entry(timeMs, channel, type, status, detail));
                if (entries.size() > LIMIT) {
                        entries.remove(0);
                }
        }

        public List<Entry> entries() {
                return new ArrayList<>(entries);
        }

        void clear() {
                entries.clear();
        }
}
