package net.lax1dude.eaglercraft.v2_6.client.hud;

import com.mojang.blaze3d.vertex.PoseStack;

import net.lax1dude.eaglercraft.v2_6.adapter.EaglerGuiImpl;
import net.lax1dude.eaglercraft.v2_6.client.mods.HudBox;
import net.lax1dude.eaglercraft.v2_6.client.mods.Mod;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/**
 * Drag HUD elements to place them. Positions are stored as fractions of
 * the screen, so a layout survives window and GUI-scale changes, and are
 * saved when the editor closes.
 */
public final class HudEditorScreen extends Screen {

        private final Screen parent;
        private Mod dragging;
        private int grabDX;
        private int grabDY;

        public HudEditorScreen(Screen parent) {
                this.parent = parent;
        }

        @Override
        protected void init() {
                this.addRenderableWidget(new Button(this.width / 2 - 50, this.height - 28, 100, Button.HEIGHT, "Done",
                                btn -> this.onClose()));
        }

        @Override
        public boolean shouldBlurBackground() {
                return false;
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
                EaglerGuiImpl.fillRect(0, 0, this.width, this.height, 0x40000000);
                if (dragging != null) {
                        dragging.setPosition((mouseX - grabDX) / (float) this.width, (mouseY - grabDY) / (float) this.height);
                }
                for (Mod m : PlebClient.mods().all()) {
                        if (!m.hasHud() || !m.isEnabled()) {
                                continue;
                        }
                        HudBox box = m.preview(PlebClient.view(), PlebClient.MEASURE);
                        int[] r = PlebClient.drawMod(m, box, this.width, this.height, true);
                        if (r != null && m.isBlockedByServer()) {
                                EaglerGuiImpl.fillRect(r[0], r[1], r[2], r[3], 0x80FF0000);
                        }
                }
                String hint = "Drag elements to move them - toggle mods in the mod menu";
                EaglerGuiImpl.drawText(hint, this.width / 2f - EaglerGuiImpl.widthOf(hint) / 2f, this.height - 42, 0xFFFFFFFF, true);
                super.render(poseStack, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (super.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                }
                if (button != 0) {
                        return false;
                }
                // topmost (last drawn) element under the cursor
                Mod hit = null;
                int[] hitRect = null;
                for (Mod m : PlebClient.mods().all()) {
                        if (!m.hasHud() || !m.isEnabled()) {
                                continue;
                        }
                        HudBox box = m.preview(PlebClient.view(), PlebClient.MEASURE);
                        if (box == null || box.width <= 0) {
                                continue;
                        }
                        int[] p = net.lax1dude.eaglercraft.v2_6.client.mods.ModRegistry.place(m, box.width, box.height, this.width,
                                        this.height);
                        if (mouseX >= p[0] && mouseX < p[0] + box.width && mouseY >= p[1] && mouseY < p[1] + box.height) {
                                hit = m;
                                hitRect = p;
                        }
                }
                if (hit != null) {
                        dragging = hit;
                        grabDX = (int) mouseX - hitRect[0];
                        grabDY = (int) mouseY - hitRect[1];
                        return true;
                }
                return false;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
                if (dragging != null) {
                        dragging = null;
                        PlebClient.saveSettings();
                        return true;
                }
                return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                if (keyCode == 1) {
                        this.onClose();
                        return true;
                }
                return false;
        }

        @Override
        public void onClose() {
                dragging = null;
                PlebClient.saveSettings();
                if (this.minecraft != null) {
                        this.minecraft.setScreen(parent);
                }
        }
}
