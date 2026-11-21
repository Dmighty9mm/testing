package net.lax1dude.eaglercraft.v2_6.client.apollo.module;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v2_6.client.apollo.ApolloModule;

/** The module set, by tier (see docs/APOLLO.md). */
public final class ApolloModules {

        public static final String MOD_SETTING = "mod_setting";

        private ApolloModules() {
        }

        public static List<ApolloModule> create() {
                List<ApolloModule> l = new ArrayList<>();
                // Tier 1
                l.add(new WaypointModule());
                l.add(new NotificationModule());
                l.add(new TitleModule());
                l.add(new CooldownModule());
                l.add(new TeamModule());
                l.add(new NametagModule());
                l.add(new BorderModule());
                l.add(new ServerRuleModule());
                l.add(new ModSettingModule());
                l.add(new RichPresenceModule());
                // Tier 2
                l.add(new BeamModule());
                l.add(PlayerColorModule.glow());
                l.add(new HologramModule());
                l.add(PlayerColorModule.coloredFire());
                l.add(new OptionsOnlyModule("glint", "Glint"));
                l.add(new LimbModule());
                l.add(new VignetteModule());
                l.add(new StopwatchModule());
                l.add(new TntCountdownModule());
                l.add(new NickHiderModule());
                // Tier 3
                l.add(new ChatModule());
                l.add(new OptionsOnlyModule("combat", "Combat"));
                l.add(new EntityModule());
                l.add(new StaffModModule());
                l.add(new TransferModule());
                // recognized, options only or declined
                l.add(new OptionsOnlyModule("packet_enrichment", "Packet enrichment"));
                l.add(new OptionsOnlyModule("totem_counter", "Totem counter"));
                l.add(new OptionsOnlyModule("saturation", "Saturation"));
                l.add(new OptionsOnlyModule("auto_text_hotkey", "Auto text hotkey"));
                l.add(new OptionsOnlyModule("inventory", "Inventory"));
                l.add(new OptionsOnlyModule("marker", "Markers"));
                l.add(new OptionsOnlyModule("server_link", "Server links"));
                l.add(new OptionsOnlyModule("cosmetic", "Cosmetics"));
                l.add(new OptionsOnlyModule("pay_now", "PayNow", "no embedded store checkout",
                                "paynow.v1.OpenPayNowEmbeddedCheckoutMessage"));
                l.add(new OptionsOnlyModule("tebex", "Tebex", "no embedded store checkout",
                                "tebex.v1.OpenTebexEmbeddedCheckoutMessage"));
                return l;
        }
}
