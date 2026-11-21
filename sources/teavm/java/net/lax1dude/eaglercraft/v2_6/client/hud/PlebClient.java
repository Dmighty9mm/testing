package net.lax1dude.eaglercraft.v2_6.client.hud;

import net.lax1dude.eaglercraft.v2_6.adapter.EaglerGuiImpl;
import net.lax1dude.eaglercraft.v2_6.adapter.WorldGlRenderer;
import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.BorderModule;
import net.lax1dude.eaglercraft.v2_6.client.mods.ClickCounter;
import net.lax1dude.eaglercraft.v2_6.client.mods.HudBox;
import net.lax1dude.eaglercraft.v2_6.client.mods.Mod;
import net.lax1dude.eaglercraft.v2_6.client.mods.ModRegistry;
import net.lax1dude.eaglercraft.v2_6.client.mods.SettingsStore;
import net.lax1dude.eaglercraft.v2_6.client.network.PluginChannels;
import net.lax1dude.eaglercraft.v2_6.internal.PlatformInput;
import net.lax1dude.eaglercraft.v2_6.internal.PlatformRuntime;
import net.lax1dude.eaglercraft.v2_6.internal.teavm.ClientMain;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Glue between the vanilla classes and this client's own features (mods,
 * HUD, Apollo). Every vanilla hook is a single call into this class:
 * <ul>
 * <li>Minecraft constructor: {@link #init}</li>
 * <li>Gui.render: {@link #renderHud}</li>
 * <li>KeyboardHandler.keyDown: {@link #onKeyDown}</li>
 * <li>MouseHandler.cursorPressed (in-world): {@link #onWorldClick}</li>
 * <li>GameRenderer: {@link #adjustFov}, {@link #minLight}</li>
 * <li>KeyboardInput sprint: {@link #sprintHeld}</li>
 * </ul>
 */
public final class PlebClient {

        /** LWJGL key codes (the port's key table). */
        public static final int KEY_RSHIFT = 54;
        public static final int KEY_C = 46;

        private static Minecraft mc;
        private static final ModRegistry mods = new ModRegistry();
        private static final ClickCounter clicks = new ClickCounter();
        private static BrowserView view;
        private static float lastFov = 70f;

        static final SettingsStore STORE = new SettingsStore() {
                @Override
                public String get(String key) {
                        return PlatformRuntime.localStorageGet("clientmods." + key);
                }

                @Override
                public void set(String key, String value) {
                        PlatformRuntime.localStorageSet("clientmods." + key, value);
                }
        };

        private PlebClient() {
        }

        public static void init(Minecraft minecraft) {
                if (mc != null) {
                        return;
                }
                mc = minecraft;
                view = new BrowserView(minecraft, clicks);
                PluginChannels.Log log = new PluginChannels.Log() {
                        @Override
                        public void info(String msg) {
                                ClientMain.log(msg);
                        }

                        @Override
                        public void warn(String msg) {
                                ClientMain.warn(msg);
                        }
                };
                Apollo.setLog(log);
                try {
                        Apollo.init();
                        mods.load(STORE);
                } catch (Throwable t) {
                        ClientMain.warn("[Client] mod/Apollo init failed: " + t);
                }
                PluginChannels.addSessionListener(new PluginChannels.SessionListener() {
                        @Override
                        public void onJoin() {
                        }

                        @Override
                        public void onReset() {
                                mods.toggleSprint.reset();
                                clicks.clear();
                        }
                });
        }

        public static ModRegistry mods() {
                return mods;
        }

        public static void saveSettings() {
                try {
                        mods.save(STORE);
                } catch (Throwable t) {
                        ClientMain.warn("[Client] could not save mod settings: " + t);
                }
        }

        static BrowserView view() {
                return view;
        }

        static Minecraft minecraft() {
                return mc;
        }

        // ==================== hooks ====================

        /** Key pressed with no screen consuming it; true = handled here. */
        public static boolean onKeyDown(int key) {
                if (mc == null || mc.screen != null || !mc.isLevelLoaded()) {
                        return false;
                }
                if (key == KEY_RSHIFT) {
                        mc.setScreen(new ModMenuScreen(null));
                        mc.getMouseHandler().releaseMouse();
                        return true;
                }
                if (key == KeyMapping.SPRINT.getKey()) {
                        mods.toggleSprint.onSprintKey();
                }
                return false;
        }

        /** In-world attack/use press (vanilla numbering: 0 attack, 1 use). */
        public static void onWorldClick(int button) {
                clicks.click(button, System.currentTimeMillis());
        }

        /** World field of view after zoom. */
        public static float adjustFov(float fov) {
                boolean held = mc != null && mc.screen == null && PlatformInput.isKeyDown(KEY_C);
                lastFov = mods.zoom.fov(fov, held);
                return lastFov;
        }

        /** Updates the world shader's light floor; call once per frame before the world draws. */
        public static void applyLighting() {
                WorldGlRenderer.setMinLight(mods.fullbright.minLight());
        }

        public static boolean sprintHeld() {
                return mods.toggleSprint.sprintHeld();
        }

        /** The movement hook: would this horizontal move cross an Apollo border that forbids it? */
        public static boolean borderBlocks(double fromX, double fromZ, double toX, double toZ) {
                BorderModule b = Apollo.module(BorderModule.class);
                return b != null && b.isEnabled() && b.blocksMove("", fromX, fromZ, toX, toZ);
        }

        /** Gui.render: HUD mods then Apollo overlays. */
        public static void renderHud(int guiW, int guiH, float partialTick) {
                if (mc == null) {
                        return;
                }
                try {
                        Apollo.tick();
                        long now = Apollo.now();
                        LocalPlayer p = mc.getPlayer();
                        com.mojang.blaze3d.platform.Window win = mc.getWindow();
                        ApolloOverlay.INSTANCE.render(guiW, guiH, now, p != null,
                                        p == null ? 0 : p.getEyeX(partialTick), p == null ? 0 : p.getEyeY(partialTick),
                                        p == null ? 0 : p.getEyeZ(partialTick), p == null ? 0 : p.yRot, p == null ? 0 : p.xRot,
                                        lastFov, win.getWidth(), win.getHeight());
                        if (!(mc.screen instanceof HudEditorScreen)) {
                                for (Mod m : mods.all()) {
                                        if (m.hasHud() && m.isActive()) {
                                                drawMod(m, m.layout(view, MEASURE), guiW, guiH, false);
                                        }
                                }
                        }
                } catch (Throwable t) {
                        ClientMain.warn("[Client] HUD render failed: " + t);
                }
        }

        static final HudBox.Measure MEASURE = new HudBox.Measure() {
                @Override
                public int width(String s) {
                        return EaglerGuiImpl.widthOf(s);
                }
        };

        /** Draws one HUD box at its saved position; returns {x, y, w, h}. */
        static int[] drawMod(Mod m, HudBox box, int guiW, int guiH, boolean outline) {
                if (box == null || box.width <= 0) {
                        return null;
                }
                int[] p = ModRegistry.place(m, box.width, box.height, guiW, guiH);
                for (HudBox.Rect r : box.rects) {
                        EaglerGuiImpl.fillRect(p[0] + r.x, p[1] + r.y, r.w, r.h, r.argb);
                }
                for (HudBox.Text t : box.texts) {
                        float tx = t.centered ? p[0] + t.x - EaglerGuiImpl.widthOf(t.text) / 2f : p[0] + t.x;
                        EaglerGuiImpl.drawText(t.text, tx, p[1] + t.y, t.argb, true);
                }
                if (outline) {
                        int c = 0xFFFFFF55;
                        EaglerGuiImpl.fillRect(p[0] - 1, p[1] - 1, box.width + 2, 1, c);
                        EaglerGuiImpl.fillRect(p[0] - 1, p[1] + box.height, box.width + 2, 1, c);
                        EaglerGuiImpl.fillRect(p[0] - 1, p[1], 1, box.height, c);
                        EaglerGuiImpl.fillRect(p[0] + box.width, p[1], 1, box.height, c);
                }
                return new int[] { p[0], p[1], box.width, box.height };
        }
}
