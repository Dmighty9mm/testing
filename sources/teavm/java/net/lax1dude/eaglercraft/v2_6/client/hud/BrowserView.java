package net.lax1dude.eaglercraft.v2_6.client.hud;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.mods.ClickCounter;
import net.lax1dude.eaglercraft.v2_6.client.mods.ClientView;
import net.lax1dude.eaglercraft.v2_6.internal.PlatformInput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/** {@link ClientView} over the running browser client. */
final class BrowserView implements ClientView {

        private final Minecraft mc;
        private final ClickCounter clicks;

        BrowserView(Minecraft mc, ClickCounter clicks) {
                this.mc = mc;
                this.clicks = clicks;
        }

        private LocalPlayer player() {
                return mc.getPlayer();
        }

        @Override
        public int fps() {
                return mc.getFps();
        }

        @Override
        public double x() {
                LocalPlayer p = player();
                return p == null ? 0 : p.x;
        }

        @Override
        public double y() {
                LocalPlayer p = player();
                return p == null ? 0 : p.y;
        }

        @Override
        public double z() {
                LocalPlayer p = player();
                return p == null ? 0 : p.z;
        }

        @Override
        public float yaw() {
                LocalPlayer p = player();
                return p == null ? 0 : p.yRot;
        }

        @Override
        public int pingMs() {
                // no latency source in the port yet (no player-info latency, no
                // play ping/pong round trip); the mod shows "n/a"
                return -1;
        }

        @Override
        @SuppressWarnings("deprecation")
        public String localTime() {
                Date d = new Date();
                int h = d.getHours();
                int m = d.getMinutes();
                return (h < 10 ? "0" : "") + h + ":" + (m < 10 ? "0" : "") + m;
        }

        @Override
        public boolean keyDown(String key) {
                if (mc.screen != null) {
                        return false;
                }
                switch (key) {
                case "forward":
                        return KeyMapping.FORWARD.isDown();
                case "left":
                        return KeyMapping.LEFT.isDown();
                case "back":
                        return KeyMapping.BACK.isDown();
                case "right":
                        return KeyMapping.RIGHT.isDown();
                case "jump":
                        return KeyMapping.JUMP.isDown();
                default:
                        return false;
                }
        }

        @Override
        public boolean mouseDown(int button) {
                // DOM numbering: 0 = left, 2 = right
                return mc.screen == null && PlatformInput.isMouseDown(button == 0 ? 0 : 2);
        }

        @Override
        public int cps(int button) {
                return clicks.cps(button, System.currentTimeMillis());
        }

        @Override
        public List<String> armor() {
                return Collections.emptyList(); // the port has no armor slots yet
        }

        @Override
        public List<String> effects() {
                return Collections.emptyList(); // the port has no mob effects yet
        }
}
