package net.lax1dude.eaglercraft.v2_6.client.mods;

import java.util.List;

/**
 * What the HUD mods read from the running game. The browser glue
 * implements it over Minecraft/LocalPlayer; tests use a fake.
 */
public interface ClientView {

        int fps();

        double x();

        double y();

        double z();

        /** Player yaw in degrees (vanilla: 0 = +Z south, 90 = -X west). */
        float yaw();

        /** Round-trip latency in ms, or -1 when unknown (singleplayer). */
        int pingMs();

        /** "HH:mm" local wall-clock time. */
        String localTime();

        /** Movement / interaction key state by vanilla key name: "forward", "left", "back", "right", "jump". */
        boolean keyDown(String key);

        /** 0 = attack (left), 1 = use (right). */
        boolean mouseDown(int button);

        /** Clicks per second over the last second for a button. */
        int cps(int button);

        /** Worn armor, head first: "name durability/max" strings ("" = empty slot). */
        List<String> armor();

        /** Active effects as "Name II 0:45". */
        List<String> effects();
}
