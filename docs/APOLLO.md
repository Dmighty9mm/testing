# Apollo server API support

This client (PlebMc) implements the client side of the **Apollo** server API, so
servers running the Apollo plugin (or speaking its JSON protocol) can drive
waypoints, notifications, titles, cooldowns, borders and the other modules below,
and can force-disable client mods. It also adds the client's own QoL mods, a mod
menu, a draggable HUD editor and an Apollo debug screen.

> **Identity.** `lunar:apollo` and `apollo:json` are protocol identifiers needed
> for compatibility. They are the only places those names appear. The client
> never presents itself as any other client: the handshake carries only the
> Minecraft version (no client brand, no client version, no installed-mod list),
> the `minecraft:brand` stays this client's own, and the UI says "PlebMc" /
> "Server API". No third-party names, logos or assets ship in the UI.

## Architecture

```
server (Paper + Apollo)            gateway (PlaceholderServer --backend)          browser client
  lunar:apollo / apollo:json  <-->   McBridge relays custom payloads    <-->   PluginChannels
                                     (config S01/C02, play S18/C16)            -> Apollo (router)
                                                                                -> modules (state)
                                                                                -> client/hud (drawing)
```

| Layer | Package | What it does |
|---|---|---|
| Transport | `client/network/PluginChannels` | `minecraft:register`, dispatch, payload caps, per-connection reset |
| Wire codec | `client/apollo/proto` | hand-written protobuf reader/writer, `google.protobuf.Any`, proto3 JSON (`"@type"`, snake/camel keys, `"1.5s"` durations, Value/Struct, RFC 3339). Schema tables generated from facts of the public `lunar-apollo-protobuf` 1.0.2 build (124 messages, 13 enums). Pure `java.util`, TeaVM-safe. |
| Core | `client/apollo/Apollo` | channels, handshake on join, router, debug log (last 100 messages with outcome), `OverrideConfigurableSettingsMessage` (module enable + options) |
| Modules | `client/apollo/module` | per-module state, expiry, flood caps, `reset()` on disconnect or when the server disables the module |
| Client mods | `client/mods` | FPS, CPS, keystrokes, coordinates, armor, potion effects, ping, clock, toggle sprint, zoom, fullbright; JSON-persisted toggles + HUD positions |
| Drawing + screens | `client/hud` | HUD mods, Apollo overlays (GUI quad batch only), mod menu, HUD editor, Apollo debug screen |

Vanilla hooks are one call each into `client/hud/PlebClient`: `Minecraft` (init),
`Gui.render`, `KeyboardHandler` (Right Shift menu, sprint toggle), `MouseHandler`
(CPS), `KeyboardInput` (toggle sprint), `GameRenderer` (zoom FOV, light floor),
`LocalPlayer` (border cancel entry/exit). `WorldGlRenderer` got one uniform
(`u_minLight`, 0 = unchanged output) for fullbright and the server brightness rule.

### Safety rules

- Every payload is decoded defensively. Malformed data, unknown message types and
  messages for disabled modules are logged in the debug screen and dropped; a bad
  payload never throws into the game loop or disconnects (fuzzed with 25k mutated
  payloads in `ApolloProtoTest`).
- Unknown fields from newer protocol versions are skipped and counted.
- Every module keeps bounded state (e.g. 256 waypoints, 1024 nametag overrides).
- Disconnect / server switch clears every module, all server rules and every mod
  override.

## Modules

"State" = decoded, stored, expired and reset correctly (JVM-tested).
"Drawn" = visible in the client today.

| Tier | Module | State | Drawn | Notes |
|---|---|---|---|---|
| 1 | waypoint | yes | yes | beam + label + distance, through walls; `prevent_removal` honored |
| 1 | notification | yes | yes | top-right toasts, slide/fade, Adventure JSON text |
| 1 | title | yes | yes | title/subtitle, server scale, fades, scale interpolation |
| 1 | cooldown | yes | yes | countdown tiles above the hotbar (icon shown as its item/texture name) |
| 1 | team | yes | yes | colored marker + name above teammates with a location |
| 1 | nametag | yes | no | the port renders no remote players yet |
| 1 | border | yes | yes | projected wall edges near the player, distance warning, cancel entry/exit blocks movement |
| 1 | server_rule | yes | n/a | brightness override drives the light floor; nametag distance / chat length exposed |
| 1 | mod_setting | yes | n/a | `"<mod>.enabled" = false` force-disables a client mod; `null` rolls back |
| 1 | rich_presence | yes | yes | shown in the mod menu status line and debug screen (no Discord in a browser) |
| 2 | beam | yes | yes | projected colored column |
| 2 | glow | yes | no | needs entity rendering |
| 2 | hologram | yes | yes | projected text lines (64-block range) |
| 2 | colored_fire | yes | no | needs entity rendering |
| 2 | glint | options | n/a | no enchanted items rendered yet |
| 2 | limb | yes | no | needs player models |
| 2 | vignette | yes | yes | edge darkening at the server opacity (the custom texture is not loaded) |
| 2 | stopwatch | yes | yes | top-center timer |
| 2 | tnt_countdown | yes | no | needs TNT entities; fuse math implemented |
| 2 | nick_hider | yes | partial | `apply()` ready; no own-name surfaces in the HUD yet |
| 3 | chat | yes | yes | live (updatable/removable) lines bottom-left |
| 3 | combat | options | n/a | options stored; the port has no miss penalty yet |
| 3 | entity | yes | no | needs entities |
| 3 | staff_mod | refused | - | the only staff mod Apollo defines is **X-ray**; refused by the no-cheats policy and logged as `IGNORED_POLICY` |
| 3 | transfer | yes | n/a | pings answer TIMED_OUT, transfers REJECTED (a browser cannot open raw host:port connections) |
| - | pay_now / tebex | declined | - | embedded store checkout not supported (handshake says so) |
| - | packet_enrichment, totem_counter, saturation, marker, server_link, inventory, cosmetic, auto_text_hotkey | options only | - | recognized so enable/disable does not log "unknown" |

### Known gaps

- **Schema age.** The newest public protos could not be fetched from this sandbox
  (buf.build and Lunar's Maven are blocked), so the tables come from the
  `lunar-apollo-protobuf` 1.0.2 npm build. Newer fields (waypoint/cooldown
  *styles*, markers, server links, cosmetics) are skipped as unknown fields:
  the base message still works. Regenerate with
  `scripts/extract_apollo_schema.py` + `scripts/gen_apollo_schema.py` from a newer
  bundle when available.
- **Ping mod** shows `n/a`: the port has no latency source yet.
- **Armor / potion HUD** show nothing in-game (the port has no armor slots or
  effects yet); the HUD editor previews them so they can be placed.
- **Full Paper play** is still pending: through `--backend`, plugin messages,
  commands and chat reach the real server, but the world you see is still the
  gateway's.

## Client mods

| Mod | Id (server option key) | Default | Notes |
|---|---|---|---|
| FPS | `fps` | on | |
| CPS | `cps` | on | left \| right |
| Keystrokes | `keystrokes` | on | WASD, LMB/RMB with CPS, jump bar |
| Coordinates | `coordinates` | on | XYZ + facing |
| Armor | `armorstatus` | off | |
| Potion effects | `potion-effects` | off | |
| Ping | `ping` | off | |
| Clock | `clock` | off | local time |
| Toggle Sprint | `toggle-sneak` | off | tap the sprint key; server option `toggle-sprint=false` also blocks it |
| Zoom | `zoom` | on | hold **C**; server option `zoom-divisor` honored |
| Fullbright | `lighting` | off | server option `full-bright=false` blocks it; a server `override-brightness` rule wins |

Open the mod menu with **Right Shift** in game. "Edit HUD layout" lets you drag
every enabled HUD element; positions are stored as screen fractions in
localStorage (`clientmods.mods`) so they survive resizes and GUI-scale changes.
"Apollo debug" shows each module's enable state and state, and the last 100
messages (green handled, orange ignored, red error, cyan sent). "Verbose log"
prints every plugin message to the browser console.

## Local test setup (Paper + Apollo + gateway)

Requirements: Java 21+ (Java 25 for 26.1.2 servers), Node 22, this repo.

1. **Paper.** Download a Paper build for 26.1.2 into `paper/`, run it once, accept
   the EULA, then set in `server.properties`:
   `online-mode=false`, `server-port=25565`, `enforce-secure-profile=false`.
   (The gateway bridge logs in as an offline player.)
2. **Apollo.** Put the Apollo Bukkit plugin jar (`apollo-bukkit-*.jar`, from the
   Apollo GitHub releases) in `paper/plugins/`. For the test commands below also
   add the matching example plugin (`apollo-example-bukkit-proto` for protobuf, or
   `-json` to exercise `apollo:json`). Restart Paper. If Apollo refuses to load on
   26.1.2, use the newest Apollo release; the example plugin is optional, any
   plugin calling the Apollo API works.
3. **Gateway.** Build and start it with the backend bridge:
   ```
   ./gradlew :PlaceholderServer:shadowJar
   java -jar gateway/PlaceholderServer/build/libs/PlaceholderServer-1.0.0.jar \
        --port 8081 --backend 127.0.0.1:25565 --verbose
   ```
   (BungeeCord/Velocity with the EaglercraftX plugins in front of Paper works the
   same way; their `config.yml` now carries the 26.1.2 packet ids.)
4. **Client.** Open the client, Multiplayer, add `ws://127.0.0.1:8081`, join.
   Paper should log the player joining; Apollo detects the client when the
   client's `minecraft:register` (with `lunar:apollo` / `apollo:json`) arrives.
5. Open **Right Shift > Apollo debug**: the header should say "server speaks
   Apollo" and `OverrideConfigurableSettingsMessage` should be listed as handled.
   Commands typed in chat with `/` go to Paper.

Without a Paper server, `node scripts/gw_relay_smoke.mjs` against
`McProtocolStub` + `PlaceholderServer --backend` checks the relay itself.

## Test command list (Apollo example plugin)

Run as an op in chat. Expected client result in brackets.

| Command | Module | Expected |
|---|---|---|
| `/waypoint display` / `remove` / `reset` | waypoint | KoTH/POI beams + labels; removed; all gone |
| `/notification display` / `reset` | notification | toast top-right for ~5 s |
| `/title display` / `displayInterpolated` / `reset` | title | big centered title; scale animates |
| `/cooldown displayItem` / `remove` / `reset` | cooldown | ENDER_PEARL tile counting down 15 s |
| `/border display` / `remove` / `reset` | border | colored edges nearby; cannot walk in/out when cancelled |
| `/team create` / `delete` | team | colored markers above teammates |
| `/nametag override` / `reset` | nametag | state only (debug screen count) |
| `/richpresence override` / `reset` | rich_presence | mod menu status line |
| `/serverrule nametagrenderdistance 32` | server_rule | debug screen shows the rule |
| `/modsettings disable` / `reset` | mod_setting | the example disables `lighting`: Fullbright shows "Blocked by server"; reset restores |
| `/beam display` / `remove` / `reset` | beam | colored column |
| `/glow override <player>` | glow | state only |
| `/hologram display` / `remove` / `reset` | hologram | floating lines |
| `/coloredfire override <player>` | colored_fire | state only |
| `/limb hideBody <player>` | limb | state only |
| `/vignette display` / `reset` | vignette | darkened edges |
| `/stopwatch stopwatch start` / `stop` / `reset` | stopwatch | top-center timer |
| `/tntcountdown set` | tnt_countdown | state only |
| `/nickhider override` / `reset` | nick_hider | debug screen shows the nick |
| `/chat display` | chat | live line counting down bottom-left |
| `/combat disablemisspenalty true` | combat | option stored |
| `/entity flipEntities` | entity | state only |
| `/staffmod enable` | staff_mod | **refused** (orange `IGNORED_POLICY` line) |
| `/transfer ping` / `transfer` | transfer | server receives TIMED_OUT / REJECTED |

To block zoom from your own plugin:

```java
Apollo.getModuleManager().getModule(ModSettingModule.class)
      .getOptions().set(player, ModZoom.ENABLED, false);
```

or over JSON on `apollo:json`:

```json
{"@type":"type.googleapis.com/lunarclient.apollo.configurable.v1.OverrideConfigurableSettingsMessage",
 "configurable_settings":[{"apollo_module":"mod_setting","enable":true,
   "properties":{"zoom.enabled":false}}]}
```

## Automated tests

| Test | Checks | What |
|---|---|---|
| `tests/apollo/ApolloProtoTest` | 116 | decoder vs bytes serialized by protobufjs (`tests/apollo/fixtures.txt`, regenerate with `scripts/gen_apollo_fixtures.cjs`), byte-identical re-encode, JSON paths, 25k fuzz |
| `tests/apollo/ApolloClientTest` | 44 | handshake, routing, enable/disable, overrides, policy refusals, safety, expiry, reset |
| `tests/apollo/ClientModsTest` | 28 | HUD layouts, saved layout, every server override of the client mods |
| `tests/geometry/PluginChannelsTest` / `BridgeRelayTest` | 33 / 28 | transport |

All run in the CI `geometry-unit` job. `scripts/teavm_api_check.py <classes>
<teavm-classlib.jar>` verifies compiled classes only use JDK members TeaVM 0.15
provides (no TeaVM build is possible in the sandbox).

## Licenses

- This repository: MIT (`LICENSE`, copyright lax1dude). Keep that notice.
- Apollo (github.com/LunarClient/Apollo, the server plugin and its examples): MIT.
  No Apollo code is included here; message and field names/numbers are protocol
  facts used for interoperability. The example commands above are from Apollo's
  own example plugin, which you install separately.
- `lunar-apollo-protobuf` (npm, GPLv3) is **not** vendored. It was only read to
  extract field numbers (`docs/apollo/apollo-schema-1.0.2.json`) and used as an
  independent encoder to produce the test fixtures.
- Nothing requested conflicts with these licenses. The one conflict inside the
  request is policy, not licensing: Apollo's only staff mod is X-ray, which the
  "no cheats" rule excludes, so the client refuses it.
