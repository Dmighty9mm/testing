package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.proto.ProtoMessage;

/**
 * title: server titles/subtitles with their own scale, fade timings and
 * an optional scale interpolation (scale moves toward interpolation_scale
 * by interpolation_rate of the distance per tick).
 */
public final class TitleModule extends ApolloModule {

        public static final int TYPE_TITLE = 1;
        public static final int TYPE_SUBTITLE = 2;

        public static final class Title {
                public final int type;
                public final List<ApolloText.Run> text;
                public final float scale;
                public final long fadeIn;
                public final long display;
                public final long fadeOut;
                public final float interpolationScale;
                public final float interpolationRate;
                public final long start;

                Title(int type, List<ApolloText.Run> text, float scale, long fadeIn, long display, long fadeOut,
                                float interpolationScale, float interpolationRate, long start) {
                        this.type = type;
                        this.text = text;
                        this.scale = scale;
                        this.fadeIn = fadeIn;
                        this.display = display;
                        this.fadeOut = fadeOut;
                        this.interpolationScale = interpolationScale;
                        this.interpolationRate = interpolationRate;
                        this.start = start;
                }

                public long end() {
                        return start + fadeIn + display + fadeOut;
                }

                public float alpha(long now) {
                        long t = now - start;
                        if (t < 0) {
                                return 0f;
                        }
                        if (t < fadeIn) {
                                return t / (float) fadeIn;
                        }
                        if (t < fadeIn + display) {
                                return 1f;
                        }
                        long out = t - fadeIn - display;
                        return out >= fadeOut ? 0f : 1f - out / (float) Math.max(1L, fadeOut);
                }

                public float scaleAt(long now) {
                        if (interpolationRate <= 0f || interpolationScale <= 0f) {
                                return scale;
                        }
                        double ticks = Math.max(0, now - start) / 50.0;
                        // exponential approach: each tick covers `rate` of the remaining distance
                        double k = 1.0 - Math.pow(1.0 - Math.min(1f, interpolationRate), ticks);
                        return (float) (scale + (interpolationScale - scale) * k);
                }
        }

        private final List<Title> active = new ArrayList<>();

        public TitleModule() {
                super("title", "Titles");
        }

        @Override
        protected void register(Apollo.Router r) {
                r.on("title.v1.DisplayTitleMessage", this::display);
                r.on("title.v1.ResetTitlesMessage", m -> reset());
        }

        private void display(ProtoMessage m) {
                int type = m.getInt("titleType") == TYPE_SUBTITLE ? TYPE_SUBTITLE : TYPE_TITLE;
                float scale = m.getFloat("scale");
                long fi = m.has("fadeInTime") ? m.getDurationMillis("fadeInTime") : 500;
                long dt = m.has("displayTime") ? m.getDurationMillis("displayTime") : 3500;
                long fo = m.has("fadeOutTime") ? m.getDurationMillis("fadeOutTime") : 1000;
                // one title and one subtitle at a time, like vanilla
                for (Iterator<Title> it = active.iterator(); it.hasNext();) {
                        if (it.next().type == type) {
                                it.remove();
                        }
                }
                active.add(new Title(type, ApolloText.pick(m.getString("adventureJsonMessage"), m.getMessage("message")),
                                scale > 0f ? Math.min(scale, 10f) : 1f, clamp(fi), clamp(dt), clamp(fo),
                                m.getFloat("interpolationScale"), m.getFloat("interpolationRate"), Apollo.now()));
        }

        private static long clamp(long ms) {
                return Math.max(0L, Math.min(ms, 600000L));
        }

        @Override
        public void tick(long now) {
                for (Iterator<Title> it = active.iterator(); it.hasNext();) {
                        if (now >= it.next().end()) {
                                it.remove();
                        }
                }
        }

        public List<Title> active() {
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
