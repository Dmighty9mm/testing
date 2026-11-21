package net.lax1dude.eaglercraft.v2_6.client.hud;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.lax1dude.eaglercraft.v2_6.adapter.EaglerGuiImpl;
import net.lax1dude.eaglercraft.v2_6.client.apollo.Apollo;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloDebugLog;
import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;
import net.lax1dude.eaglercraft.v2_6.client.network.PluginChannels;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

/**
 * Apollo debug screen: connection status, every module's enable state and
 * current state, and the most recent messages with how each was handled.
 * "Verbose log" also prints every plugin message to the browser console.
 */
public final class ApolloDebugScreen extends Screen {

        private final Screen parent;
        private int scroll;

        public ApolloDebugScreen(Screen parent) {
                this.parent = parent;
        }

        @Override
        protected void init() {
                this.addRenderableWidget(new Button(this.width - 154, this.height - 24, 150, Button.HEIGHT,
                                "Verbose log: " + (Apollo.isDebug() ? "ON" : "OFF"), btn -> {
                                        boolean on = !Apollo.isDebug();
                                        Apollo.setDebug(on);
                                        PluginChannels.setDebug(on);
                                        this.clearWidgets();
                                        this.init();
                                }));
                this.addRenderableWidget(new Button(4, this.height - 24, 100, Button.HEIGHT, "Back", btn -> this.onClose()));
        }

        @Override
        public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
                EaglerGuiImpl.fillRect(0, 0, this.width, this.height, 0xC0000000);
                int y = 4;
                String head = "Apollo: " + (Apollo.isServerApollo() ? "server speaks Apollo" : "no Apollo server")
                                + " | channel " + (Apollo.isJsonMode() ? Apollo.CHANNEL_JSON : Apollo.CHANNEL_PROTO)
                                + " | handshake " + (Apollo.isHandshakeSent() ? "sent" : "not sent")
                                + " | received " + Apollo.receivedCount() + ", dropped " + Apollo.droppedCount();
                EaglerGuiImpl.drawText(head, 4, y, 0xFFFFFF55, true);
                y += 12;
                int half = this.width / 2 - 6;
                int my = y;
                for (ApolloModule m : Apollo.modules()) {
                        String line = (m.isEnabled() ? "+ " : "- ") + m.displayName() + " (" + m.receivedCount() + "): "
                                        + m.describeState();
                        while (line.length() > 4 && EaglerGuiImpl.widthOf(line) > half) {
                                line = line.substring(0, line.length() - 2);
                        }
                        EaglerGuiImpl.drawText(line, 4, my, m.isEnabled() ? 0xFFDDDDDD : 0xFF888888, false);
                        my += 9;
                        if (my > this.height - 32) {
                                break;
                        }
                }
                List<ApolloDebugLog.Entry> log = Apollo.debugLog().entries();
                int ly = y;
                int lines = (this.height - 32 - y) / 9;
                int start = Math.max(0, log.size() - lines - scroll);
                for (int i = start; i < log.size() && ly < this.height - 32; i++) {
                        ApolloDebugLog.Entry e = log.get(i);
                        int c;
                        switch (e.status) {
                        case HANDLED:
                                c = 0xFF55FF55;
                                break;
                        case SENT:
                                c = 0xFF55FFFF;
                                break;
                        case ERROR:
                                c = 0xFFFF5555;
                                break;
                        default:
                                c = 0xFFFFAA00;
                                break;
                        }
                        String s = e.toString();
                        while (s.length() > 4 && EaglerGuiImpl.widthOf(s) > half) {
                                s = s.substring(0, s.length() - 2);
                        }
                        EaglerGuiImpl.drawText(s, this.width / 2f + 2, ly, c, false);
                        ly += 9;
                }
                super.render(poseStack, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
                scroll = Math.max(0, Math.min(Apollo.debugLog().entries().size(), scroll + (delta > 0 ? 3 : -3)));
                return true;
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
                if (this.minecraft != null) {
                        this.minecraft.setScreen(parent);
                }
        }
}
