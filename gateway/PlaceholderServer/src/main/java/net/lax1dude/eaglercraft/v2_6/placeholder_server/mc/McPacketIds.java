package net.lax1dude.eaglercraft.v2_6.placeholder_server.mc;

/**
 * EaglerCraft 26.1.2 - Minecraft protocol packet identifiers used by the
 * Eagler gateway MC bridge.
 *
 * <p>The bridge speaks a minimal subset of the vanilla server protocol
 * (protocol 775, the 26.1.2 release) towards the backend server:
 * handshake, login, the configuration phase, keepalive/ping, plugin
 * messages (custom payloads), system chat and disconnect.</p>
 *
 * <p>Every default below is the packet's index in the decompiled 26.1.2
 * protocol templates ({@code docs/decompiled-26.1.2-full-src.zip}:
 * {@code LoginProtocols}, {@code ConfigurationProtocols},
 * {@code GameProtocols}) - ids are assigned in registration order, so the
 * index IS the wire id. Packet ids shift between releases, so each one can
 * still be overridden per deployment (config {@code mc_*} keys in the
 * Bungee/Velocity plugins). Every inbound packet the bridge does not
 * consume is skipped via length framing, which is stable across versions.</p>
 */
public final class McPacketIds {

    // ===== login state, server -> client =====

    /** Disconnect (login state), JSON text component. */
    public int loginDisconnect = 0x00;
    /** Encryption Request. The bridge is offline-mode only and refuses. */
    public int loginEncryptionRequest = 0x01;
    /** Login Finished (a.k.a. Login Success). */
    public int loginSuccess = 0x02;
    /** Set Compression. */
    public int loginSetCompression = 0x03;
    /** Login Plugin Request / custom query (answered with a "no"). */
    public int loginPluginRequest = 0x04;
    /** Cookie Request (login state, answered with "no cookie"). */
    public int loginCookieRequest = 0x05;

    // ===== login state, client -> server =====

    /** Login Plugin Response / custom query answer. */
    public int loginPluginResponse = 0x02;
    /** Login Acknowledged: moves both sides into CONFIGURATION. */
    public int loginAcknowledged = 0x03;
    /** Cookie Response (login + configuration share the id order). */
    public int loginCookieResponse = 0x04;

    // ===== configuration state, server -> client =====

    public int configCookieRequest = 0x00;
    public int configCustomPayload = 0x01;
    public int configDisconnect = 0x02;
    public int configFinish = 0x03;
    public int configKeepAlive = 0x04;
    public int configPing = 0x05;
    public int configSelectKnownPacks = 0x0E;
    public int configCodeOfConduct = 0x13;

    // ===== configuration state, client -> server =====

    public int configClientInformation = 0x00;
    public int configCookieResponse = 0x01;
    public int configCustomPayloadC2S = 0x02;
    public int configFinishAck = 0x03;
    public int configKeepAliveResponse = 0x04;
    public int configPong = 0x05;
    public int configSelectKnownPacksC2S = 0x07;
    public int configAcceptCodeOfConduct = 0x09;

    // ===== play state, server -> client =====

    /** Custom Payload (plugin message). */
    public int playCustomPayload = 0x18;
    /** Keep Alive. */
    public int playKeepAlive = 0x2C;
    /** Ping (answered with Pong). */
    public int playPing = 0x3D;
    /** System Chat Message (NBT component + overlay boolean). */
    public int playSystemChat = 0x79;
    /** Disconnect (play state), NBT text component. */
    public int playDisconnect = 0x20;
    /** Start Configuration: the server re-enters CONFIGURATION. */
    public int playStartConfiguration = 0x76;
    /** Cookie Request (play state). */
    public int playCookieRequest = 0x15;

    // ===== play state, client -> server =====

    /** Keep Alive response. */
    public int playKeepAliveResponse = 0x1C;
    /** Chat Message (string + timestamp + salt + signature + last-seen update). */
    public int playChat = 0x09;
    /** Chat Command (unsigned slash command, without the leading '/'). */
    public int playChatCommand = 0x07;
    /** Custom Payload (plugin message). */
    public int playCustomPayloadC2S = 0x16;
    /** Pong (answer to Ping). */
    public int playPong = 0x2D;
    /** Configuration Acknowledged (answer to Start Configuration). */
    public int playConfigurationAck = 0x10;
    /** Cookie Response (play state). */
    public int playCookieResponse = 0x15;

    /** Protocol version advertised in the handshake (26.1.2 release = 775). */
    public int protocolVersion = 775;

    /** Copies this configuration so callers can tweak ids per session. */
    public McPacketIds copy() {
        McPacketIds c = new McPacketIds();
        c.loginDisconnect = loginDisconnect;
        c.loginEncryptionRequest = loginEncryptionRequest;
        c.loginSuccess = loginSuccess;
        c.loginSetCompression = loginSetCompression;
        c.loginPluginRequest = loginPluginRequest;
        c.loginCookieRequest = loginCookieRequest;
        c.loginPluginResponse = loginPluginResponse;
        c.loginAcknowledged = loginAcknowledged;
        c.loginCookieResponse = loginCookieResponse;
        c.configCookieRequest = configCookieRequest;
        c.configCustomPayload = configCustomPayload;
        c.configDisconnect = configDisconnect;
        c.configFinish = configFinish;
        c.configKeepAlive = configKeepAlive;
        c.configPing = configPing;
        c.configSelectKnownPacks = configSelectKnownPacks;
        c.configCodeOfConduct = configCodeOfConduct;
        c.configClientInformation = configClientInformation;
        c.configCookieResponse = configCookieResponse;
        c.configCustomPayloadC2S = configCustomPayloadC2S;
        c.configFinishAck = configFinishAck;
        c.configKeepAliveResponse = configKeepAliveResponse;
        c.configPong = configPong;
        c.configSelectKnownPacksC2S = configSelectKnownPacksC2S;
        c.configAcceptCodeOfConduct = configAcceptCodeOfConduct;
        c.playCustomPayload = playCustomPayload;
        c.playKeepAlive = playKeepAlive;
        c.playPing = playPing;
        c.playSystemChat = playSystemChat;
        c.playDisconnect = playDisconnect;
        c.playStartConfiguration = playStartConfiguration;
        c.playCookieRequest = playCookieRequest;
        c.playKeepAliveResponse = playKeepAliveResponse;
        c.playChat = playChat;
        c.playChatCommand = playChatCommand;
        c.playCustomPayloadC2S = playCustomPayloadC2S;
        c.playPong = playPong;
        c.playConfigurationAck = playConfigurationAck;
        c.playCookieResponse = playCookieResponse;
        c.protocolVersion = protocolVersion;
        return c;
    }
}
