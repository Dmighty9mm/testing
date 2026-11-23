package net.lax1dude.eaglercraft.v2_6.client.mods;

/** Clicks in the last second, per mouse button (ring of timestamps). */
public final class ClickCounter {

        private static final int CAP = 64;
        private final long[][] times = new long[2][CAP];
        private final int[] head = new int[2];
        private final int[] size = new int[2];

        public void click(int button, long now) {
                if (button < 0 || button > 1) {
                        return;
                }
                times[button][head[button]] = now;
                head[button] = (head[button] + 1) % CAP;
                size[button] = Math.min(CAP, size[button] + 1);
        }

        public int cps(int button, long now) {
                if (button < 0 || button > 1) {
                        return 0;
                }
                int n = 0;
                for (int i = 0; i < size[button]; i++) {
                        if (now - times[button][i] < 1000L) {
                                n++;
                        }
                }
                return n;
        }

        public void clear() {
                size[0] = size[1] = 0;
                head[0] = head[1] = 0;
        }
}
