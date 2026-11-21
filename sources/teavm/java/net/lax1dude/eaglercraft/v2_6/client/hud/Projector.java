package net.lax1dude.eaglercraft.v2_6.client.hud;

import net.lax1dude.eaglercraft.v2_6.adapter.WorldGlRenderer;

/**
 * World to GUI-pixel projection with exactly the world renderer's camera
 * (WorldGlRenderer.perspective / makeView), so HUD-drawn markers line up
 * with the rendered world.
 */
final class Projector {

        private final float[] proj = new float[16];
        private final float[] view = new float[16];
        private final float[] mvp = new float[16];
        private int guiW;
        private int guiH;
        double eyeX;
        double eyeY;
        double eyeZ;
        float sx;
        float sy;
        /** Clip-space w of the last projection (distance along the view axis). */
        float depth;

        void setup(double eyeX, double eyeY, double eyeZ, float yaw, float pitch, float fov, int pxW, int pxH, int guiW,
                        int guiH) {
                this.eyeX = eyeX;
                this.eyeY = eyeY;
                this.eyeZ = eyeZ;
                this.guiW = guiW;
                this.guiH = guiH;
                float aspect = pxH > 0 ? (float) pxW / (float) pxH : 1f;
                WorldGlRenderer.perspective(proj, fov, aspect, 0.05f, 1000.0f);
                // camera-relative view: keeps float precision far from the origin
                WorldGlRenderer.makeView(view, 0, 0, 0, yaw, pitch);
                WorldGlRenderer.multiply(mvp, proj, view);
        }

        /** Projects a world point; false when it is behind the camera. Result in {@link #sx}, {@link #sy}. */
        boolean project(double x, double y, double z) {
                float px = (float) (x - eyeX);
                float py = (float) (y - eyeY);
                float pz = (float) (z - eyeZ);
                float cx = mvp[0] * px + mvp[4] * py + mvp[8] * pz + mvp[12];
                float cy = mvp[1] * px + mvp[5] * py + mvp[9] * pz + mvp[13];
                float cw = mvp[3] * px + mvp[7] * py + mvp[11] * pz + mvp[15];
                depth = cw;
                if (cw < 0.05f) {
                        return false;
                }
                sx = (cx / cw * 0.5f + 0.5f) * guiW;
                sy = (1f - (cy / cw * 0.5f + 0.5f)) * guiH;
                return true;
        }

        double distance(double x, double y, double z) {
                double dx = x - eyeX;
                double dy = y - eyeY;
                double dz = z - eyeZ;
                return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
}
