package net.lax1dude.eaglercraft.v2_6.client.hud;

import java.util.List;

import net.lax1dude.eaglercraft.v2_6.adapter.EaglerFont;
import net.lax1dude.eaglercraft.v2_6.adapter.EaglerGuiImpl;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;

/**
 * Text drawing for the HUD and Apollo overlays on top of the existing
 * GUI quad batch (EaglerGuiImpl.fillRect): colored runs, alpha, and a
 * scaled variant built from the same 8x8 font glyphs (no new GL path).
 */
public final class HudText {

        private HudText() {
        }

        /** Multiplies the alpha of an ARGB color by {@code a} (0..1). */
        public static int alpha(int argb, float a) {
                int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
                return (al << 24) | (argb & 0x00FFFFFF);
        }

        public static int width(List<ApolloText.Run> runs) {
                int w = 0;
                for (ApolloText.Run r : runs) {
                        w += EaglerGuiImpl.widthOf(r.text);
                }
                return w;
        }

        public static int width(List<ApolloText.Run> runs, float scale) {
                return Math.round(width(runs) * scale);
        }

        /** Draws runs at 1x; returns the width drawn. */
        public static int draw(List<ApolloText.Run> runs, float x, float y, float alpha, boolean shadow) {
                float cx = x;
                for (ApolloText.Run r : runs) {
                        cx += EaglerGuiImpl.drawText(r.text, cx, y, alpha(r.argb, alpha), shadow);
                }
                return Math.round(cx - x);
        }

        /** Draws runs at an arbitrary scale (titles); returns the width drawn. */
        public static int drawScaled(List<ApolloText.Run> runs, float x, float y, float scale, float alpha, boolean shadow) {
                if (Math.abs(scale - 1f) < 0.01f) {
                        return draw(runs, x, y, alpha, shadow);
                }
                float cx = x;
                for (ApolloText.Run r : runs) {
                        int argb = alpha(r.argb, alpha);
                        if (shadow) {
                                int sh = (argb & 0xFC000000) | ((argb >> 2) & 0x003F3F3F) | 0xFF000000;
                                drawGlyphs(r.text, cx + scale, y + scale, scale, alpha(sh, alpha));
                        }
                        cx += drawGlyphs(r.text, cx, y, scale, argb);
                }
                return Math.round(cx - x);
        }

        private static float drawGlyphs(String text, float x, float y, float scale, int argb) {
                float cx = x;
                for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        long g = EaglerFont.glyph(c);
                        for (int row = 0; row < EaglerFont.GLYPH_HEIGHT; row++) {
                                int run = -1;
                                for (int col = 0; col <= EaglerFont.CELL_WIDTH; col++) {
                                        boolean set = col < EaglerFont.CELL_WIDTH && EaglerFont.isPixelSet(g, col, row);
                                        if (set && run < 0) {
                                                run = col;
                                        } else if (!set && run >= 0) {
                                                EaglerGuiImpl.fillRect(cx + run * scale, y + row * scale, (col - run) * scale, scale, argb);
                                                run = -1;
                                        }
                                }
                        }
                        cx += EaglerFont.advance(c) * scale;
                }
                return cx - x;
        }

        /** Centered single line of runs. */
        public static void drawCentered(List<ApolloText.Run> runs, float centerX, float y, float scale, float alpha) {
                float w = width(runs) * scale;
                drawScaled(runs, centerX - w / 2f, y, scale, alpha, true);
        }
}
