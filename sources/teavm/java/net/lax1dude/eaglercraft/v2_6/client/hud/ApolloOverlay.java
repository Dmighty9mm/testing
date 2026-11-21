package net.lax1dude.eaglercraft.v2_6.client.hud;

import java.util.List;

import net.lax1dude.eaglercraft.v2_6.adapter.EaglerGuiImpl;
import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloText;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.BeamModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.BorderModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.ChatModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.CooldownModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.HologramModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.NotificationModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.StopwatchModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.TeamModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.TitleModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.VignetteModule;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.WaypointModule;

/**
 * Draws the Apollo modules' state on the HUD with the existing GUI quad
 * batch only (EaglerGuiImpl.fillRect / drawText): no new shaders, buffers
 * or GL state. World-anchored things (waypoints, beams, holograms, team
 * markers, borders) are projected with the world camera and drawn as HUD
 * markers, the way Apollo waypoints show through walls.
 */
final class ApolloOverlay {

        private final Projector cam = new Projector();

        private ApolloOverlay() {
        }

        static final ApolloOverlay INSTANCE = new ApolloOverlay();

        void render(int w, int h, long now, boolean haveCamera, double eyeX, double eyeY, double eyeZ, float yaw, float pitch,
                        float fov, int pxW, int pxH) {
                VignetteModule vignette = Apollo.module(VignetteModule.class);
                if (vignette != null && vignette.isEnabled() && vignette.isActive()) {
                        vignette(w, h, vignette.opacity());
                }
                if (haveCamera) {
                        cam.setup(eyeX, eyeY, eyeZ, yaw, pitch, fov, pxW, pxH, w, h);
                        beams();
                        waypoints();
                        holograms();
                        team();
                        border(w, h);
                }
                stopwatch(w, now);
                cooldowns(w, h, now);
                liveChat(h);
                titles(w, h, now);
                notifications(w, now);
        }

        // ==================== screen-space ====================

        private static void vignette(int w, int h, float opacity) {
                // darkened edges in 8 bands (vanilla vignette look, plain quads)
                int bands = 8;
                for (int i = 0; i < bands; i++) {
                        float a = opacity * 0.55f * (1f - i / (float) bands);
                        int c = HudText.alpha(0xFF000000, a / bands * 2f);
                        int bw = Math.max(1, w / 24);
                        int bh = Math.max(1, h / 24);
                        int x = i * bw;
                        int y = i * bh;
                        EaglerGuiImpl.fillRect(x, y, w - 2 * x, bh, c);
                        EaglerGuiImpl.fillRect(x, h - y - bh, w - 2 * x, bh, c);
                        EaglerGuiImpl.fillRect(x, y + bh, bw, h - 2 * y - 2 * bh, c);
                        EaglerGuiImpl.fillRect(w - x - bw, y + bh, bw, h - 2 * y - 2 * bh, c);
                }
        }

        private static void stopwatch(int w, long now) {
                StopwatchModule sw = Apollo.module(StopwatchModule.class);
                if (sw == null || !sw.isEnabled() || !sw.isShown()) {
                        return;
                }
                String s = StopwatchModule.format(sw.elapsedMs(now));
                int tw = EaglerGuiImpl.widthOf(s);
                EaglerGuiImpl.fillRect(w / 2f - tw / 2f - 3, 3, tw + 6, 12, 0x6F000000);
                EaglerGuiImpl.drawText(s, w / 2f - tw / 2f, 5, sw.isRunning() ? 0xFFFFFFFF : 0xFFFFFF55, true);
        }

        private static void cooldowns(int w, int h, long now) {
                CooldownModule cd = Apollo.module(CooldownModule.class);
                if (cd == null || !cd.isEnabled()) {
                        return;
                }
                List<CooldownModule.Cooldown> list = cd.active();
                int size = 24;
                int gap = 4;
                int total = list.size() * (size + gap) - gap;
                int x = w / 2 - total / 2;
                int y = h - 22 - 18 - size - 6;
                for (CooldownModule.Cooldown c : list) {
                        float f = c.fraction(now);
                        EaglerGuiImpl.fillRect(x, y, size, size, 0x80000000);
                        int fh = Math.round(size * f);
                        EaglerGuiImpl.fillRect(x, y + size - fh, size, fh, 0x80FF5555);
                        String label = c.icon.isEmpty() ? c.name : c.icon;
                        label = label.substring(0, Math.min(3, label.length()));
                        EaglerGuiImpl.drawText(label, x + 2, y + 2, 0xFFAAAAAA, true);
                        long ms = c.remaining(now);
                        String t = ms >= 10000 ? (ms / 1000) + "s" : (ms / 1000) + "." + (ms % 1000 / 100) + "s";
                        int tw = EaglerGuiImpl.widthOf(t);
                        EaglerGuiImpl.drawText(t, x + size / 2f - tw / 2f, y + size - 10, 0xFFFFFFFF, true);
                        x += size + gap;
                }
        }

        private static void liveChat(int h) {
                ChatModule chat = Apollo.module(ChatModule.class);
                if (chat == null || !chat.isEnabled()) {
                        return;
                }
                List<List<ApolloText.Run>> lines = chat.lines();
                int y = h - 48 - lines.size() * 10;
                for (List<ApolloText.Run> l : lines) {
                        int lw = HudText.width(l);
                        EaglerGuiImpl.fillRect(2, y - 1, lw + 4, 10, 0x80000000);
                        HudText.draw(l, 4, y, 1f, true);
                        y += 10;
                }
        }

        private static void titles(int w, int h, long now) {
                TitleModule tm = Apollo.module(TitleModule.class);
                if (tm == null || !tm.isEnabled()) {
                        return;
                }
                for (TitleModule.Title t : tm.active()) {
                        float a = t.alpha(now);
                        if (a <= 0.02f) {
                                continue;
                        }
                        float s = t.scaleAt(now) * (t.type == TitleModule.TYPE_TITLE ? 4f : 2f);
                        float y = t.type == TitleModule.TYPE_TITLE ? h / 2f - 10f - 8f * s / 2f - 8f : h / 2f + 5f;
                        HudText.drawCentered(t.text, w / 2f, y, s, a);
                }
        }

        private static void notifications(int w, long now) {
                NotificationModule nm = Apollo.module(NotificationModule.class);
                if (nm == null || !nm.isEnabled()) {
                        return;
                }
                int y = 4;
                int bw = 170;
                for (NotificationModule.Notification n : nm.active()) {
                        float v = n.visibility(now);
                        if (v <= 0f) {
                                continue;
                        }
                        List<String> desc = EaglerGuiImpl.wrapText(ApolloText.plain(n.description), bw - 8);
                        int bh = 16 + desc.size() * 10;
                        float x = w - 4 - bw * v;
                        EaglerGuiImpl.fillRect(x, y, bw, bh, HudText.alpha(0xD0101018, v));
                        EaglerGuiImpl.fillRect(x, y, 2, bh, HudText.alpha(0xFF55FFFF, v));
                        HudText.draw(n.title, x + 6, y + 4, v, true);
                        int ly = y + 15;
                        for (String line : desc) {
                                EaglerGuiImpl.drawText(line, x + 6, ly, HudText.alpha(0xFFCCCCCC, v), false);
                                ly += 10;
                        }
                        y += bh + 4;
                }
        }

        // ==================== world-anchored ====================

        private void beams() {
                BeamModule bm = Apollo.module(BeamModule.class);
                if (bm == null || !bm.isEnabled()) {
                        return;
                }
                for (BeamModule.Beam b : bm.all()) {
                        beam(b.pos.x, b.pos.y, b.pos.z, b.argb);
                }
        }

        /** A vertical beam from the block up to the build limit, as a projected column. */
        private void beam(double x, double y, double z, int argb) {
                if (!cam.project(x, y, z)) {
                        return;
                }
                float bx = cam.sx;
                float by = cam.sy;
                float depth = cam.depth;
                if (!cam.project(x, Math.max(y + 1, 320), z)) {
                        return;
                }
                float width = Math.max(1f, Math.min(6f, 30f / Math.max(1f, depth)));
                float top = Math.min(by, cam.sy);
                float bottom = Math.max(by, cam.sy);
                EaglerGuiImpl.fillRect(bx - width / 2f, top, width, bottom - top, HudText.alpha(argb, 0.45f));
        }

        private void waypoints() {
                WaypointModule wm = Apollo.module(WaypointModule.class);
                if (wm == null || !wm.isEnabled()) {
                        return;
                }
                for (WaypointModule.Waypoint wp : wm.visible("")) {
                        beam(wp.pos.x, wp.pos.y, wp.pos.z, wp.argb);
                        if (!cam.project(wp.pos.x, wp.pos.y + 1.5, wp.pos.z)) {
                                continue;
                        }
                        int dist = (int) Math.round(cam.distance(wp.pos.x, wp.pos.y, wp.pos.z));
                        String label = wp.name + " [" + dist + "m]";
                        int lw = EaglerGuiImpl.widthOf(label);
                        float lx = cam.sx - lw / 2f;
                        float ly = cam.sy - 14;
                        EaglerGuiImpl.fillRect(lx - 2, ly - 1, lw + 4, 10, 0x80000000);
                        EaglerGuiImpl.drawText(label, lx, ly, 0xFFFFFFFF, true);
                        EaglerGuiImpl.fillRect(cam.sx - 3, cam.sy - 3, 6, 6, wp.argb);
                }
        }

        private void holograms() {
                HologramModule hm = Apollo.module(HologramModule.class);
                if (hm == null || !hm.isEnabled()) {
                        return;
                }
                for (HologramModule.Hologram ho : hm.all()) {
                        if (cam.distance(ho.pos.x, ho.pos.y, ho.pos.z) > 64 || !cam.project(ho.pos.x, ho.pos.y, ho.pos.z)) {
                                continue;
                        }
                        float y = cam.sy - ho.lines.size() * 10;
                        for (List<ApolloText.Run> line : ho.lines) {
                                int lw = HudText.width(line);
                                if (ho.background) {
                                        EaglerGuiImpl.fillRect(cam.sx - lw / 2f - 2, y - 1, lw + 4, 10, 0x40000000);
                                }
                                HudText.draw(line, cam.sx - lw / 2f, y, 1f, ho.shadow);
                                y += 10;
                        }
                }
        }

        private void team() {
                TeamModule tm = Apollo.module(TeamModule.class);
                if (tm == null || !tm.isEnabled()) {
                        return;
                }
                for (TeamModule.Member m : tm.members()) {
                        if (!m.hasPos || !cam.project(m.pos.x, m.pos.y + 2.3, m.pos.z)) {
                                continue;
                        }
                        // marker: a small colored down-pointing chevron
                        for (int i = 0; i < 4; i++) {
                                EaglerGuiImpl.fillRect(cam.sx - 4 + i, cam.sy - 6 + i, 8 - 2 * i, 1, m.argb);
                        }
                        int lw = HudText.width(m.name);
                        HudText.draw(m.name, cam.sx - lw / 2f, cam.sy - 16, 1f, true);
                }
        }

        private void border(int w, int h) {
                BorderModule bm = Apollo.module(BorderModule.class);
                if (bm == null || !bm.isEnabled()) {
                        return;
                }
                for (BorderModule.Border b : bm.all()) {
                        double d = b.distanceToEdge(cam.eyeX, cam.eyeZ);
                        if (d > 24) {
                                continue;
                        }
                        // the wall's four bottom/top edges near the player, as projected segments
                        double y0 = Math.floor(cam.eyeY) - 2;
                        double y1 = y0 + 6;
                        double[][] corners = { { b.minX, b.minZ }, { b.maxX, b.minZ }, { b.maxX, b.maxZ }, { b.minX, b.maxZ } };
                        for (int i = 0; i < 4; i++) {
                                double[] p = corners[i];
                                double[] q = corners[(i + 1) % 4];
                                edge(p[0], y0, p[1], q[0], y0, q[1], b.argb);
                                edge(p[0], y1, p[1], q[0], y1, q[1], b.argb);
                        }
                        if (d < 6) {
                                String s = "Border " + ((int) (d * 10)) / 10.0 + "m";
                                int sw = EaglerGuiImpl.widthOf(s);
                                EaglerGuiImpl.drawText(s, w / 2f - sw / 2f, h / 2f + 20, b.argb, true);
                        }
                }
        }

        /** A world-space segment, sampled so only the visible part is drawn (dotted line). */
        private void edge(double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
                double len = Math.sqrt((x1 - x0) * (x1 - x0) + (z1 - z0) * (z1 - z0));
                int steps = (int) Math.min(400, Math.max(2, len * 2));
                int c = HudText.alpha(argb, 0.8f);
                for (int i = 0; i <= steps; i++) {
                        double t = i / (double) steps;
                        double x = x0 + (x1 - x0) * t;
                        double z = z0 + (z1 - z0) * t;
                        if (cam.distance(x, y0, z) < 32 && cam.project(x, y0 + (y1 - y0) * t, z)) {
                                EaglerGuiImpl.fillRect(cam.sx - 1, cam.sy - 1, 2, 2, c);
                        }
                }
        }
}
