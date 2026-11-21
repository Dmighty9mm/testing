package net.lax1dude.eaglercraft.v2_6.client.hud;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.lax1dude.eaglercraft.v2_6.adapter.EaglerGuiImpl;
import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.module.RichPresenceModule;
import net.lax1dude.eaglercraft.v2_6.client.mods.ClientInfo;
import net.lax1dude.eaglercraft.v2_6.client.mods.Mod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/**
 * The mod menu (Right Shift in game): one toggle per mod (a mod the
 * server blocks shows "Blocked by server" and cannot be turned on), plus
 * the HUD editor and the Apollo debug screen.
 */
public final class ModMenuScreen extends Screen {

        private final Screen parent;

        public ModMenuScreen(Screen parent) {
                this.parent = parent;
        }

        @Override
        protected void init() {
                List<Mod> mods = PlebClient.mods().all();
                int colW = 150;
                int gap = 4;
                int cols = 2;
                int rows = (mods.size() + cols - 1) / cols;
                int x0 = this.width / 2 - colW - gap / 2;
                int y0 = Math.max(32, this.height / 2 - (rows * (Button.HEIGHT + gap) + 2 * (Button.HEIGHT + gap)) / 2);
                for (int i = 0; i < mods.size(); i++) {
                        final Mod m = mods.get(i);
                        int x = x0 + (i % cols) * (colW + gap);
                        int y = y0 + (i / cols) * (Button.HEIGHT + gap);
                        String state = m.isBlockedByServer() ? "Blocked by server" : m.isEnabled() ? "ON" : "OFF";
                        Button b = new Button(x, y, colW, Button.HEIGHT, m.name() + ": " + state, btn -> {
                                m.setEnabled(!m.isEnabled());
                                PlebClient.saveSettings();
                                this.clearWidgets();
                                this.init();
                        });
                        b.setActive(!m.isBlockedByServer());
                        this.addRenderableWidget(b);
                }
                int by = y0 + rows * (Button.HEIGHT + gap) + 8;
                this.addRenderableWidget(new Button(x0, by, colW, Button.HEIGHT, "Edit HUD layout", btn -> {
                        if (this.minecraft != null) {
                                this.minecraft.setScreen(new HudEditorScreen(this));
                        }
                }));
                this.addRenderableWidget(new Button(x0 + colW + gap, by, colW, Button.HEIGHT, "Apollo debug", btn -> {
                        if (this.minecraft != null) {
                                this.minecraft.setScreen(new ApolloDebugScreen(this));
                        }
                }));
                this.addRenderableWidget(new Button(this.width / 2 - 100, by + Button.HEIGHT + gap, 200, Button.HEIGHT, "Done",
                                btn -> this.onClose()));
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
                String title = ClientInfo.NAME + " Mods";
                EaglerGuiImpl.drawText(title, this.width / 2f - EaglerGuiImpl.widthOf(title) / 2f, 12, 0xFFFFFFFF, true);
                String status = Apollo.isServerApollo() ? "Server API: connected (" + Apollo.receivedCount() + " messages)"
                                : "Server API: not used by this server";
                RichPresenceModule rp = Apollo.module(RichPresenceModule.class);
                if (rp != null && !rp.summary().isEmpty()) {
                        status += " - " + rp.summary();
                }
                EaglerGuiImpl.drawText(status, this.width / 2f - EaglerGuiImpl.widthOf(status) / 2f, 22, 0xFFAAAAAA, false);
                super.render(poseStack, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == 1 || keyCode == PlebClient.KEY_RSHIFT) {
                        this.onClose();
                        return true;
                }
                return false;
        }

        @Override
        public void onClose() {
                PlebClient.saveSettings();
                if (this.minecraft != null) {
                        this.minecraft.setScreen(parent);
                        if (parent == null) {
                                this.minecraft.getMouseHandler().grabMouse();
                        }
                }
        }
}
