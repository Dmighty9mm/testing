package net.lax1dude.eaglercraft.v2_6.client.mods;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout of one HUD element, in GUI pixels relative to the element's
 * top-left corner: rectangles (background / pressed key cells) and text.
 * Built by the mods (pure, testable), drawn by the HUD renderer.
 */
public final class HudBox {

        public static final int PAD = 2;
        public static final int LINE_H = 10;
        public static final int BG = 0x6F000000;
        public static final int BG_PRESSED = 0xB0FFFFFF;
        public static final int TEXT = 0xFFFFFFFF;
        public static final int TEXT_PRESSED = 0xFF000000;

        /** One rectangle. */
        public static final class Rect {
                public final int x;
                public final int y;
                public final int w;
                public final int h;
                public final int argb;

                Rect(int x, int y, int w, int h, int argb) {
                        this.x = x;
                        this.y = y;
                        this.w = w;
                        this.h = h;
                        this.argb = argb;
                }
        }

        /** One text item. */
        public static final class Text {
                public final String text;
                public final int x;
                public final int y;
                public final int argb;
                public final boolean centered;

                Text(String text, int x, int y, int argb, boolean centered) {
                        this.text = text;
                        this.x = x;
                        this.y = y;
                        this.argb = argb;
                        this.centered = centered;
                }
        }

        /** Text measurement (EaglerGuiImpl.widthOf in the browser). */
        public interface Measure {
                int width(String s);
        }

        public final List<Rect> rects = new ArrayList<>();
        public final List<Text> texts = new ArrayList<>();
        public int width;
        public int height;

        public HudBox rect(int x, int y, int w, int h, int argb) {
                rects.add(new Rect(x, y, w, h, argb));
                width = Math.max(width, x + w);
                height = Math.max(height, y + h);
                return this;
        }

        public HudBox text(String s, int x, int y, int argb, boolean centered) {
                texts.add(new Text(s, x, y, argb, centered));
                return this;
        }

        /** Standard text panel: a background with one line per entry. */
        public static HudBox lines(List<String> lines, Measure m) {
                HudBox b = new HudBox();
                int w = 0;
                for (String s : lines) {
                        w = Math.max(w, m.width(s));
                }
                if (lines.isEmpty()) {
                        return b;
                }
                b.rect(0, 0, w + PAD * 2, lines.size() * LINE_H + PAD, BG);
                for (int i = 0; i < lines.size(); i++) {
                        b.text(lines.get(i), PAD, PAD + i * LINE_H, TEXT, false);
                }
                return b;
        }
}
