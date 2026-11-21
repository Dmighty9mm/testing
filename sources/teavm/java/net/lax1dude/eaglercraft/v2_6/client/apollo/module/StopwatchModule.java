package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;

/** stopwatch: a server-controlled HUD stopwatch (start / stop / reset). */
public final class StopwatchModule extends ApolloModule {

        private boolean running;
        private boolean shown;
        private long startedAt;
        private long accumulated;

        public StopwatchModule() {
                super("stopwatch", "Stopwatch");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("stopwatch.v1.StartStopwatchMessage", m -> {
                        if (!running) {
                                running = true;
                                startedAt = Apollo.now();
                        }
                        shown = true;
                });
                r.on("stopwatch.v1.StopStopwatchMessage", m -> {
                        if (running) {
                                accumulated += Apollo.now() - startedAt;
                                running = false;
                        }
                });
                r.on("stopwatch.v1.ResetStopwatchMessage", m -> reset());
        }

        public long elapsedMs(long now) {
                return accumulated + (running ? Math.max(0L, now - startedAt) : 0L);
        }

        public boolean isShown() {
                return shown;
        }

        public boolean isRunning() {
                return running;
        }

        /** "m:ss.cc" */
        public static String format(long ms) {
                long cs = ms / 10;
                long s = cs / 100;
                StringBuilder sb = new StringBuilder();
                sb.append(s / 60).append(':');
                long sec = s % 60;
                if (sec < 10) {
                        sb.append('0');
                }
                sb.append(sec).append('.');
                long c = cs % 100;
                if (c < 10) {
                        sb.append('0');
                }
                return sb.append(c).toString();
        }

        @Override
        public void reset() {
                running = false;
                shown = false;
                accumulated = 0;
                startedAt = 0;
        }

        @Override
        public String describeState() {
                return !shown ? "hidden" : (running ? "running " : "stopped ") + format(elapsedMs(Apollo.now()));
        }
}
