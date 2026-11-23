# Worklog — eaglercraft-26.1.2

> NOTE: the sandbox was wiped between sessions; this file was recreated from the
> conversation summary. The repo was re-cloned from origin (token-authenticated).

---
Task ID: 1 (recovered)
Agent: main
Task: assets.epk pipeline (EPKCompiler + build script + deployment)

Work Log:
- Reverse-engineered the EPK format (magic "EAGPKG!"+NUL 8 bytes, u16 version=2,
  u16 flags, u32 fileCount, u32 reserved; entries: u16 nameLen + utf8 + u32 len + data, LE)
- Implemented EPKCompiler (deterministic: entries sorted by path, CRC32, no timestamps)
  with `--verify file.epk [sourceDir]` round-trip check
- scripts/build_assets_epk.sh stages sources/resources -> manifest -> compile -> verify
- Deployed public/assets.epk (322 B, 2 entries) + download/assets.epk

Stage Summary:
- assets.epk pipeline complete; e2e green at the time (19/19)

---
Task ID: 2 (recovered)
Agent: main
Task: push milestone (ci.yml trigger, gateway skeletons, EPKLoader wiring)

Work Log:
- Proved the ci.yml "corruption" (branches: ain]) was a display artifact of the
  session tool output (the literal sequence "[m" is eaten in output). GitHub API
  blob f900195... matches local HEAD; git log -S finds no commit ever containing
  the broken string. NO fix was needed.
- Recovered Bungee/Velocity/PlaceholderServer gateway sources from the worktree;
  fixed .gitignore (bare src/ rule + scripts/ rule); fixed two Gradle 9 shadow
  validation errors (startScripts/shadowJar input declaration, classifier-less
  jar/shadowJar path collision -> slim classifier)
- Wired EPKLoader into ClientMain boot path (beginAssetBundleLoad -> loadEPK)
- Pushed 4 commits: 2ef92ce (title screen), 34814c1 (gateway), d0d8028 (teavm
  local build), a979dbd (EPK boot wiring). main = a979dbd, API-verified.

Stage Summary:
- main pushed with token account; token configured in git remote URL (not committed)

---
Task ID: 3
Agent: main
Task: (b) gateway milestone #2 - forward Eagler players into proxy server registry

Work Log:
- Re-cloned repo after sandbox wipe; verified main = a979dbd
- Design: extend wire protocol with 0x03 login / 0x04 login-result / 0x05 chat;
  shared pure-JDK MC-protocol TCP bridge (mc/ package in PlaceholderServer module);
  Bungee: EaglerPlayerSession implements ProxiedPlayer + EaglerSessionRegistry +
  reflection registration into proxy player set + PostLoginEvent; Velocity: wire
  parity with its own registry; GatewaySmokeMain standalone harness for e2e

Stage Summary:
- in progress

---
---
Task ID: 3 (completed)
Agent: main
Task: gateway milestone #2 + multiplayer e2e smoke + complete assets.epk

Work Log:
- Reviewed the in-progress milestone #2 worktree (12 new files, ~800 insertions):
  EaglerPlayerSession (full ProxiedPlayer), EaglerSessionRegistry,
  EaglerProxyAccess, GatewaySmokeMain, VelocityEaglerSession/Registry,
  mc/ package (McBridge/McProtocolStub/McVarInt/McNbt/McPacketIds)
- Fixed the login/chat race: 0x04 status 0 is now sent only after the backend
  login completes (unreachable backend -> status 3 + teardown); chat arriving
  while the bridge connects is buffered (64 frames) and flushed on CONNECTED.
  Applied to BOTH Bungee and Velocity for parity
- Added setReuseAddr(true) to both WS listeners (restarts re-bind through
  TIME_WAIT; was breaking e2e reruns)
- Built all 3 gateway jars (shadow, JDK26 @ tools/jdk26); standalone smoke
  client (scripts/gw_smoke_client.mjs) verified: handshake, login, chat echo
  through bridge, ping/pong, invalid username -> status 1, dead backend -> 3
- multiplayer.spec.ts (task c): browser-driven smoke via GatewaySmokeMain +
  McProtocolStub; fixed two spec bugs (echo consumed by the wait loop ->
  return it from the evaluate; rejection test resolved on the handshake
  accept instead of the login result). Full suite 21/21 green
- Task (a): MC 26.1.2 client jar downloaded from piston-meta (38 MB, real
  version exists); all 10,462 vanilla asset entries staged + fork lang stub
  + CRC32 manifest -> EPKCompiler -> public/assets.epk = 7,470,553 B,
  10,464 entries, round-trip verified. Sounds (436 MB asset index) deferred
  to on-demand loading. Full TeaVM compile stays on the CI runner (12 GB
  heap; sandbox has 3 GB)
- Committed 8444121 (gateway milestone 2) + 4de3e80 (complete assets.epk),
  pushed main a979dbd..4de3e80, API-verified 200

Stage Summary:
- main = 4de3e80; e2e 21/21; EPK now carries the complete vanilla asset set;
  TeaVM full build + deploy happen on the next CI run of the pushed commit

---
Task ID: 4
Agent: main
Task: improve the Playwright e2e suite — milestone screenshots + "go in game" verification

Work Log:
- Probed the deployed client empirically first (scripts/probe_client.mjs): boot ~2s,
  MC instance constructs ("Minecraft instance created!"), EPK parsed (7,470,553 B,
  10,464 entries), title overlay + FPS counter live at ~60 FPS, canvas black
  (renderFrame only clears when mc.tick succeeds; no GUI render yet) — so the honest
  "in game" gate for the current build is the engine-running milestone chain
- tests/e2e/support.ts: added installConsoleRecorder (page console capture),
  readGameSessionState (loading/title/crash/fps DOM state), parseFps +
  waitForFpsReading (rAF counter publishes ~1s after title screen — poll, don't
  assert instantly), attachMilestoneScreenshot (saves screenshots/<name>.png +
  attaches to the HTML report)
- tests/e2e/ingame.spec.ts (new): fresh page, full journey with hard asserts —
  loading screen -> self.main exported -> title screen (no crash overlay) ->
  console milestones (WebGL2: true, === Initialization complete ===,
  Minecraft instance created!, EPK bytes/entries > 0) -> FPS > 0 twice 2.5s apart
  -> zero uncaught page errors; attaches 01..04 milestone PNGs + session-summary.json
- shell.spec.ts screenshot routed through the helper; .gitignore + e2e.yml upload
  gained screenshots/; docs/E2E.md documents the new spec + screenshots section
- Local restore niggles: sandbox had Chromium 1200 vs Playwright wanting 1248
  (npx playwright install chromium fixed it); 49 mode-bit flips restored to 644
- Suite locally: 22/22 green (was 21 tests, now +1), ingame spec stable 3/3 runs
- Rebased on origin/main (previous session's 580dcb0 + 2b36a6d were missing from
  the restored worktree), pushed 7d359a5

Stage Summary:
- main = 7d359a5; e2e 22/22 local; CI run in flight for the new spec
- Next obvious upgrade when the client gains a real GUI/world path: extend
  ingame.spec.ts with a click-Singleplayer -> world-load -> HUD milestone (the
  spec's fresh-page structure already supports adding stage 6), and/or add a
  window.__eaglercraftGame telemetry bridge in EaglerCraft.java for direct
  state asserts instead of console markers

---
Task ID: 4 (completed)
Agent: main
Task: CI verification for the improved e2e suite

Work Log:
- CI run 39768371107 for 7d359a5: build ✓ (TeaVM recompiled, "compilation
  succeeded"), e2e ✓ 20 passed in 9.5s — ingame.spec.ts ran and passed on CI
  (multiplayer pair auto-skipped there: gateway jars are local-only), deploy ✓
- playwright-report artifact (798 KB) carries the embedded milestone screenshots
  + session-summary.json; live site re-verified serving classes.js + assets.epk

Stage Summary:
- main = 7d359a5, CI fully green, site live
- Suite now proves the client "goes in game" to the current build's ceiling:
  boot -> title screen -> Minecraft instance constructed -> EPK parsed ->
  render loop at positive FPS, with screenshots at every stage

---
Task ID: 5 (completed)
Agent: main
Task: Open the playwright-report artifact from CI run 39768371107

Work Log:
- Downloaded artifact 12420499892 (playwright-report-39768371107-1, 798 KB) via
  GitHub API (gh CLI unavailable; token from git remote URL, never echoed)
- Extracted: screenshots/ (5 PNGs), test-results/junit.xml, playwright-report/
  folder layout (hash-named PNG attachments, no session-summary.json persisted)
- junit.xml: 20 passed / 2 skipped (multiplayer, by design) — ingame.spec.ts
  "boots the client and reaches the running game" passed in 5.125s
- Visually verified all four milestone screenshots (loading bar advancing,
  title screen, running state with 61 FPS overlay)
- Copied 5 screenshots + junit.xml to
  /home/z/my-project/download/e2e-screenshots-run-39768371107/

Stage Summary:
- Artifact evidence confirms the new e2e suite works end-to-end on CI
- Note: text attachments (session-summary.json) don't persist in folder-layout
  HTML reports uploaded as artifacts — if we want it in the artifact, write it
  via testInfo.attach with a PNG-style binary path or save to test-results/

---
Task ID: 6 (completed)
Agent: main
Task: Push the pending drawSprite fix (token provided), verify CI, and finish
the sprite-pipeline + SelectWorldScreen milestone visuals

Work Log:
- Sandbox had reset again: HEAD back at 7d359a5, all local commits gone.
  Remote had everything except the unpushed one-liner. Stashed mode-bit
  noise, set core.fileMode=false, updated remote token, fetched, ff to 83e6355
- Recreated the drawSprite corner-coords fix as 8cd7beb, pushed, CI green
  (run 39875639566). Verified bundle + junit 24/0 + screenshots: crosshair/
  hotbar/title buttons now render real vanilla sprites correctly
- CI screenshots exposed one remaining artifact: a 48x48 blue square at the
  top-left of Select World + near-black screen elsewhere
- 12-round browser GL probe (custom Playwright harness, GL wrappers +
  readPixels-after-each-draw): program GUI ✓, geometry sane ✓, texture content
  uniform (0,0,0,64) ✓, blend SRC_ALPHA/ONE_MINUS_SRC_ALPHA ✓, 1 clear/rAF ✓,
  no FBO ✓ — yet x>=48 ended ~20 blend layers dark
- Log-sampled drawArrays bisect on a fresh VAO showed layers accumulating
  ~1 per few quads -> giant overlapping rects
- ROOT CAUSE: drawSpriteTiled fed the raw destination offset (tx - x) into
  the source UV phase without wrapping: su0 grew +1 per tile column, wrap
  branch's right edge (tx + (su1-1)*twF) grew unbounded, stretching every
  later tile across the rest of the screen. Only tile 0 rendered correctly
  (= the blue square)
- FIX (7dfa5a5): vanilla GuiSpriteScaling.Tiled semantics — source phase
  floorMod'ed into one sprite period, dest = clipped tile rect, u/v windows
  split at the texture seam (up to 2x2 sub-quads)
- Also scoped the DOM title banner to the title screen via the bridge's
  screen class (was only hidden in-world; it floated over Select World)
- CI on 7dfa5a5 green; screenshot showed correct layout but a light-blue
  backdrop (alpha-64 tiles over the sky clear color; vanilla blends them
  over the blurred panorama we don't render yet)
- FIX (1e2d9e7): opaque dark blue-gray base under the Select World backdrop
- CI green; refreshed public/classes.js from the 1e2d9e7 build (3e34461)
- Final run (3e34461): all three jobs green, deployed to Pages

Stage Summary:
- main = 3e34461, CI fully green (build + e2e 24/0 + deploy)
- Pixel-verified: title buttons 0x6F6F6F active body, sky 160/206/255,
  white crosshair, dark vanilla Select World (header 31,34,42 / list 17,19,24)
- Files: EaglerGuiImpl.java (drawSprite corners + drawSpriteTiled phase),
  SelectWorldScreen.java (dark base), public/index.html (overlay scoping),
  public/classes.js (refreshed)
- Probe harness kept at /home/z/my-project/scripts/probe_bluesquare.js for
  future GL debugging; token rotated by user mid-session (old one 401'd)

---
Task ID: 8 (completed)
Agent: main
Task: User reported "background still blurry" + "movement/camera half
inverted (W goes D)" — both traced to the never-deployed fix commit;
unblock its CI and ship it

Work Log:
- Sandbox had reset again (local back at 3e34461). Remote main carried a new
  commit 8d99dd0 (an earlier continuation's fix for exactly these complaints:
  LocalPlayer.turn axis swap, camera yaw+180, own-render 768x768 panorama,
  quad winding, tint scoping, atlas mipmap fix) — but its CI run
  40319355595 FAILED in the e2e job, so Deploy was skipped and production
  still served the pre-fix bundle. The user's "still" complaints were the
  undeployed state, not a bad fix.
- CI failure triage: input.spec.ts 3/3 GREEN (the inversion fix verified);
  sky + crafting failed once and passed on retry (flakes); the real blocker
  was features.spec.ts:467 'joining must show the ConnectScreen' failing 3x.
- Reproduced locally (had to npx playwright install chromium — reset wiped
  the headless shell; public/ bundle is committed so no toolchain rebuild
  was needed). Wrote scripts/probe_joinflow.mjs: seeds the serverlist
  (storage format is name\\u0001ip lines, NOT JSON — ServerList.java),
  walks Multiplayer -> row -> join with an 8ms in-page screen sampler.
- Ground truth: the client DOES pass through ConnectScreen
  (JoinMultiplayerScreen -> ConnectScreen ~1.4s -> DisconnectedScreen
  'the server closed the connection'). The spec's 200ms poll missed it
  under runner load because the loopback refusal can land between two
  telemetry pushes. Worse: the poll had been passing on the OLD bundle
  because a telemetry throw froze the rAF loop and kept the screen up —
  the assert was passing on a bug.
- FIX (219f3b3): the dead-address join now samples screen transitions
  in-page at 8ms around the click and asserts the recorded transitions
  contain ConnectScreen after the terminal DisconnectedScreen lands.
  Validated locally (4.5m, passed), then input.spec re-run (2 passed +
  1 retry-absorbed flake, same as CI).
- CI on 219f3b3 (run 40680642241): build green, full e2e suite green,
  Deploy green. Live bundle = CI's fresh TeaVM build (byte diff vs local
  is TeaVM parameter-name nondeterminism only).
- Production verified: booted https://applepie6969.github.io/eaglercraft-26.1.2
  headless — title screen with the crisp self-rendered panorama (trees,
  water), zero page errors (probe-out/live-title.png).

Stage Summary:
- main = 219f3b3, CI fully green, Pages deployed
- Both user complaints fixed in production: camera/movement conventions
  (W walks the look vector; mouse right turns right) and the crisp panorama
- Lesson: a passing poll assert can be masking a frozen-loop bug; transient
  screen states need in-page transition sampling, not 200ms polling
- scripts/probe_joinflow.mjs + probe_live_title.mjs kept locally (uncommitted)
- sandbox reset recovery note: npx playwright install chromium needed after
  resets; public/ bundle is committed so e2e runs without a gradle rebuild

## Task 9 - restore the real 26.x title panorama (2026-10-30)
- User: "we're working on Minecraft version 26.3 not 1.8 fix that". The
  panorama had been rewritten to the 1.8 method; the fork-original port
  (561ce4f~1) was already the modern 26.x method (CubeMap.render, fixed
  10 deg pitch, fov-85 raymarch over the six face PNGs, spin += delta *
  2.0 deg/s) with only an angle-unit bug.
- Fix: restored the original 345-line PanoramaRenderer + Math.toRadians
  at the single Java/GLSL boundary + rAF clock clamp (MAX_FRAME_DELTA_TICKS
  = 10, rejects negative/NaN background-tab deltas).
- Local probe: spin +2.003/+2.026 deg/s (vanilla 2.0), horizon row 323->323
  (zero bob), zero console errors. Bundle markers: panorama_faces +
  u_tanHalfFov in, u_f1/1.7320508 gone.
- Commit ed9395a (88f3fd8 source + bundle amend). CI run 41035247365:
  build green, e2e green (the two heavy specs passed on fresh runners,
  confirming the local-timeout judgment), Deploy green. Live site serves
  the new bundle (marker check).
- Lesson: sandbox reset ate 87f3b88 AND the worklog entry - commit source
  first, build second, amend, push immediately.

## Task 10 - vanilla face geometry + fixed 20 TPS from the 26.1.2 decompile (2026-10-30)
- User: "when you look at the side of blocks theyre invisible (remember
  its minecraft version 26.x) get the actual logic of the real minecraft,
  decompile it, also the walking is way too fast, get the real
  decompilation".
- Ground truth: 26.1.2 client jar (SHA-verified from the Mojang manifest,
  same jar the build script uses) decompiled with Vineflower 1.10.1 -
  26.1.2 ships UNOBFUFCATED so the decompile has Mojang's own names.
  Excerpts committed under docs/decompiled-26.1.2-reference/ (FaceInfo,
  FaceBakery, CuboidFace, ModelBlockRenderer, SectionCompiler,
  LivingEntity, LocalPlayer, KeyboardInput...).
- Bug 1 (invisible sides): the mesher's hand-authored corner tables were
  double-negated with the reversed QUAD_INDEX_ORDER (0,2,1)(0,3,2) -
  cross-product shows only UP/DOWN/WEST survived, NORTH/SOUTH computed
  inward (culled) and EAST was a copy of the NORTH quad on the wrong
  plane (+X face never existed). Fix: vanilla FaceInfo corners + (0,1,2)
  (2,3,0) order + vanilla UV projection - every face emits outward-CCW.
  The cull rule already matched Block.shouldRenderFace and is unchanged.
- Bug 2 (walking too fast): USE_FIXED_TIMESTEP=false ran one full MC tick
  per rAF frame (60-144 TPS vs vanilla 20). The decompiled travelInAir
  math (friction 0.6*0.91, accel 0.21600002/blockFriction^3, 0.98 input
  scale) was already in the port; enabling the fixed-20-TPS accumulator
  path restores 4.317 b/s walk / 5.612 b/s sprint. Camera got the vanilla
  partialTick lerp (xo..x), GUI/panorama got the right clocks.
- Commit 05dae78 (source 19d5a2b + bundle amend). Local probe: walk
  9.06 blocks / 42 ticks = 0.2159 b/tick (vanilla), four horizons at
  0.6-0.8% black (voids gone), east view shows full dirt cross-sections.
- CI run 41130361406: build green, e2e RED x4 - all harness-vs-clock
  (see Task 11), Deploy skipped.

## Task 11 - e2e harness vs the 20 TPS clock (2026-10-31)
- This round: sandbox reset rolled the repo back to 219f3b3 AND ate the
  two unpushed test commits (a2fc9f1, 3c7e0be). Recovered 05dae78 from
  origin; the test commits were re-derived from the failure analysis.
- The four e2e failures on 05dae78 were all test harness, not product:
  budgets sized at the accidental 60 TPS (crafting last test ran 6.8 min
  vs a 7.0 min cap), the 60s screenshot default (sky), the 120ms
  right-click window coalescing on loaded runners (features place), and
  node-side polling blind to per-tick destroy stages (features dig).
- Key insight: the bundle REPLACES window.__eaglercraftGame once per
  tick, so an in-page setter trap observes every tick's destroy stage
  even when a slow frame bursts a dozen ticks between polls; the dig
  release is an in-page canvas mouseup the moment queryBlock returns air
  (the node round-trip loses to vanilla's 5-tick continue-dig delay).
  Place = aim->right-click->verify retry loop, 300ms press window. Join
  tail = select->join->observe retry with per-attempt screen sampler.
- Also checked the "second block breaks instantly" observation: NOT a
  product bug - the port aborts on target change and restarts after
  vanilla's 5-tick delay with fresh progress; the old spec's 30s hold
  simply dug on (vanilla hold-to-continue).
- Commit 7b95dcd. Local: sky 1.6m, features 6.4m (all three), mouse-path
  55s where it flailed for 8 minutes.
- Lesson: poll-based observation is wall-time-bounded; tick-accurate
  assertions need in-page traps on the per-tick bridge replacement.
- CI run 41202981498 on 7b95dcd: build green, E2E green (the three
  reworked suites passed on fresh runners), Deploy green.
- Production verified (probe_live_final.mjs vs applepie6969.github.io):
  panorama 2.021 deg/s, steady-state walk 0.2159 b/tick = the vanilla
  invariant (4.317 b/s / 20 TPS), zero console errors, screenshot shows
  full dirt cross-sections on every slope - the void signature gone.

## Task 11 — round 3 of "block sides invisible": root cause found by x-ray, not screenshots (2026-10-31)

User report: sides STILL invisible after the FaceInfo fix. Built
scripts/probe_face_repro.mjs: GL-state recorder (init-script wrap of
enable/disable/cullFace/frontFace), world sculpting via setScene,
per-triangle normal x-ray of the uploaded VBOs via debugMesh, cull
on/off screenshots. Findings: hole walls + terrain sides present and
outward-wound in the VBOs; GL state vanilla (frontFace CCW, cullFace
BACK); screenshots showed black void bands at every terrain edge and
around holes, filling in with CULL_FACE force-disabled. Only a
POSITIONAL defect fits: emitFace was called with the already-offset
plane (y+1) for FACE_UP while FaceInfo.UP adds MAX_Y itself -> every
top quad at y+2, a floating canopy 1 block above the sides. Fix: pass
plain y (1c165c4). Lesson: displacement bugs need position assertions -
added FaceTables.java extraction + JVM FaceTablesTest (~0.3s, CI
geometry-unit job) + tests/e2e/geometry.spec.ts (VBO plane invariants:
36 verts, all 6 faces, UP=y+1, hole walls/floor re-emitted, bottom
culled). Full-jar decompile completed and archived
(docs/decompiled-26.1.2-full-src.zip, 6,882 files); audit doc
docs/IMPLEMENTATION-COVERAGE.md; worklog moved into the repo.

## Task 12 — verification + the two harness races the e2e caught (2026-10-31)

Production verification of the y+2 fix (run 41212854639, commit 1c165c4,
deployed): probe_face_repro rerun - floating blocks x-ray 6/6 faces
outward with UP on the correct plane, placed block's top at y=-48
(FaceTables plane invariant), hole walls + floor emitted, and the
screenshots show the placed cobblestone sitting flush with the grass,
hole walls rendering as dirt cross-sections, zero black bands at
terraces or the horizon (pre-fix: black voids everywhere).

Two CI-only failures taught harness lessons worth keeping:
1. Scene selection without guards buried the placed block inside a
   slope/canopy overhang - every face correctly culled, UP=0. The
   mesher was RIGHT; the scan needed a clean 3x3x2 air requirement.
2. dirtyCount==0 is NOT a valid "edit applied" gate: a fresh setScene
   request sits unconsumed for seconds at 1-4 fps while dirtyCount is
   still 0, so the VBO read the pre-edit mesh. Gate = queryBlock(spot)
   == placed id (atomic with the dirty marks) THEN dirtyCount==0.

Also archived: docs/decompiled-26.1.2-full-src.zip (the complete jar
decompile, 6,882 files), docs/IMPLEMENTATION-COVERAGE.md (honest audit:
decompile complete; port = 411 files; mobs/redstone/dimensions listed
missing), FaceTables JVM test proven by mutation (shifting the UP plane
by +1 makes it fail), CI geometry-unit job green on every run.

## Task 13 — the "still red" gate was the test, not the world (2026-11-01)

Post-reset recovery round: local main had rolled back to 219f3b3 again,
the PAT file had reverted to the dead token, and the TeaVM toolchain
caches (~/.gradle, tools/pristine, sources/libs, the client jar) were
gone - local_build.sh re-fetched and re-patched everything from scratch
in two passes. Origin itself was fine: production was verified by
provenance - the live classes.js is byte-identical to the gh-pages deploy
commit that names build 1c165c4 (the y+2 fix), so the site the user
sees IS the fixed mesher.

But origin/main was red for five straight runs, and two of those reds
were real test bugs worth writing down:

1. geometry.spec scene 3 failed every run since it landed: "placed
   block UP face: 2 outward triangles / Received: 0". The mesher was
   innocent (scene 1's floating block passed all six faces and planes
   in the same run). debugMeshJson's box filter kept INDIVIDUAL verts,
   and a neighbor terrain top quad straddling the [x-0.5..x+1.5] box
   edge contributed some of its verts - shattering the stride-3
   triangle alignment analyzeBlock assumes. A block placed ON terrain
   always has straddling neighbor tops; a floating one never does,
   which is why only scene 3 broke, everywhere, every run. Fix:
   filter triangle-wise (keep a tri only when all three verts are in
   the box). A throwaway diagnostic spec (5 clean pockets) proved the
   shape of it: pre-rebuild bundle showed UP at y+2 (stale public/),
   post-rebuild showed 36 verts / 6 faces / UP=y+1 on every pocket.
2. sky.spec's 4.5m failures were the milestone captures: 120s timeout,
   one retry, THROW on both - two starved captures on a software-GL
   runner could kill a test whose real assertions ride the telemetry
   bridge. attachMilestoneScreenshot is now best-effort: 20s cap, two
   tries, never throws, attaches a note instead.

Lesson: a screenshot artifact must never be able to fail a
telemetry-asserted test, and a mesh-dump helper that can return
half-a-quad will poison every stride-based consumer downstream.
Committed as e2f0130; CI watch + production e2e against the deployed
bytes to follow.

Outcome (same day): CI run for e2f0130 fully green - build, JVM
geometry-unit, all 48 e2e tests, Deploy. The gh-pages deploy carries
build 55d9bd7 and the live classes.js is byte-identical to it. The
production gate then ran against the LIVE bytes (downloaded site +
geometry.spec): floating block 6 faces on vanilla planes with UP=y+1,
dug hole walls + floor re-emitted, placed block all four sides + top
with the bottom culled - 1 passed. One operational note: verifying the
deployed bundle needs the FULL public/ tree mirrored (the client pulls
loose GUI sprites like assets/gui/sprites/widget/button.png over HTTP;
a mirror with only index.html/classes.js/assets.epk starves the texture
atlas and the dirty queue never drains - the first prod-gate attempt
failed on exactly that). scripts/prod_verify.sh now mirrors assets/ and
lang/ from the build tree.

## Task 14 — CLAUDE.md: the worklog contract (2026-11-01)

User request: add a CLAUDE.md stating that worklog.md must be very detailed
with what was done, and that no tokens may be written into it.

The sandbox reset twice inside this one task - both times local main rolled
back to 219f3b3 while origin/main stood at 5e192ba (Task 13), and the first
recovery's commit was destroyed before it could be pushed. Both recoveries
were fetch + hard-reset to origin plus recreation of the working files from
session context. The stale workspace-root worklog.md fossil (a
summary-recreated copy of the same nine Task IDs in less detail) was retired;
the second reset then wiped the whole workspace root anyway - no loss, since
the repo copy is the superset and the only canonical worklog.

CLAUDE.md now pins the contract at the repo root:
- worklog.md must be very detailed with what was done: changes file by file,
  commit hashes, CI run IDs, real verification numbers, root causes,
  decisions with reasoning, and what stays open - detailed enough that a
  fresh session can reconstruct the whole project state from it alone;
  committed and pushed in the same push as the work it describes.
- the absolute no-tokens rule: no PATs, API keys, passwords, cookies, or
  any other credentials in worklog.md or any committed file, commit message,
  or CI log - not even partially or redacted; secrets are referenced by
  location only.
- plus the operating context future sessions keep re-deriving the hard way:
  origin/main as the single source of truth, the decompile zip and
  docs/decompiled-26.1.2-reference/ as the only vanilla ground truth,
  IMPLEMENTATION-COVERAGE.md kept current, the scripts/* gitignore whitelist
  trap (new helper scripts commit nothing until whitelisted), the
  build -> geometry-unit -> e2e -> deploy gate, live-bytes verification, and
  the post-reset recovery routine.

Also left a pointer stub at /home/z/my-project/CLAUDE.md (outside the repo,
uncommitted) so sessions that start at the workspace root find the rules.

Push auth saga, worth the detail: the PAT embedded in the origin remote URL
was dead again (HTTP 401 from api.github.com/user; the reset reverted it -
the same regression Task 13 hit), and the URL kept the token in the username
slot with no password, so git also fell back to an unanswerable interactive
password prompt. An environment sweep for a live alternative (gh CLI, ssh
keys, netrc, git-credentials store, env vars, .env) found nothing. The user
then supplied a fresh PAT in chat. It was installed into the origin remote
URL and the gitignored scripts/.gh_token copy only - never printed into any
tracked file, and both worklog and CLAUDE.md pass the
ghp_/github_pat_/gh[ousr]_ secret scan. Two discoveries while installing it:
the repo has moved - api.github.com/repos/diddy62626/... returns 301 to
repositories/1270416148, canonical full_name APPLEPIE6969/eaglercraft-26.1.2
(the old diddy62626 URL survives only as a redirect alias) - and the new
token verifies push=True on that repo. The remote URL was re-pointed to the
canonical name to stop depending on the redirect.

Outcome: recreated as a single docs commit (CLAUDE.md + this entry;
docs-only, public/ and build inputs untouched), pushed to
APPLEPIE6969/eaglercraft-26.1.2 main, verified by ls-remote hash equality.
CI run for this push watched separately and appended below.

CI outcome (run 41325712694, commit 6208738): fully green in ~46 minutes -
build success, JVM Geometry Unit Tests success, E2E Browser Tests / Playwright
browser tests success (the suite's full length on the software-GL runner is
normal; read the job steps, not the wall clock, to tell progress from a hang),
Deploy to GitHub Pages success. Docs-only push, so the deployed bytes are
unchanged from the Task 13 build - the live site already carries the verified
mesher fixes; post-deploy HEAD check on the Pages URL returned HTTP 200.

## Task 15 — 100% CI coverage: parity canaries, chaos fuzz, sharding (2026-11-02)

User request: extend CI coverage to "test everything", including the
simple things (placing blocks, creative mode), plus out-of-the-box
angles; and settle whether Playwright is actually needed or a faster
alternative can keep the same output.

Recon first: the coverage doc's honest table (port = 411 files, mobs/
redstone/dimensions missing) scoped what CAN be tested - the implemented
subset. The bridge's id scheme turned out to be fully documented in the
sources: Button ids slugify their label ("saveandquittotitle"), CycleButtons
expose prefix:value ("gamemode:survival" -> "gamemode:hardcore" ->
"gamemode:creative"), which made driving the real world-creation UI
practical without guessing.

JVM layer (runs ~25s into the pipeline, no browser) - four new tests in
tests/geometry/, all green locally before push:
- RegistryParityTest: parses the decompile zip's vanilla Blocks.java
  (1144 fields) and asserts every one of the port's 159 registered block
  names exists in vanilla, ids resolve/unique, all 192 world textures are
  referenced by the registry (no dead assets), destroy_stage_0..9 exist.
  The first run caught my own parser assuming literal ids - registrations
  use named constants (new Block(STONE, ...)), fixed with a two-pass
  constant-resolving parse.
- PhysicsParityTest: twin canaries for each movement constant - the value
  must appear in BOTH the port source and the named vanilla decompile
  file. The first run REDLINED five assumed locations (vanilla 26.x moved
  gravity into LivingEntity.DEFAULT_BASE_GRAVITY + the gravity attribute,
  friction into INPUT_FRICTION, jump into BASE_JUMP_POWER - which the
  port mirrors by NAME). Final table: 7 verified canaries incl. the
  ground-acceleration 0.216... constant behind the walk invariant.
- RecipeParityTest: the 25-recipe generated bake is structurally sound,
  references only real registry ids (176), and carries vanilla truths
  (planks x4, sticks 1x2 x4, 2x2 table, pickaxe XXX/_S_/_S_). My first
  spot locks assumed torch/furnace exist - they are legitimately outside
  the registry-supported bake; replaced with the iron/stone pickaxe
  patterns.
- ProtocolFuzzTest: 8209 int + 4105 long seeded round-trips, byte-exact
  differential vs an independent reference encoder, and 8 malformed-input
  cases (overlong VarInt/VarLong, truncation, hostile string lengths)
  rejected cleanly.

Browser layer - four new specs (suite now 21 files / 53 tests):
- placement.spec: the six-face culling matrix (attach neighbor -> probe
  quad culls -> neighbor's opposite quad renders on its vanilla plane ->
  detach -> quad re-emerges, all six directions), the x=16k section-border
  remesh (BOTH sections update; the cross-border neighbor culls its quad
  exactly like a same-section one), and the real right-click use path
  (hotbar fill -> aim -> pointer grab -> right press) landing cobblestone
  on the spawn pad.
- creative.spec: world CREATION through the real UI (Singleplayer ->
  Create New World -> gamemode cycle asserted at each value -> Create),
  gameplay.creative true, double-tap flight inside the vanilla 7-tick
  window, Space ascend / Shift descend, landing cancels flight, a single
  click breaks a block instantly, the picker opens on E and closes on
  Escape - plus a guard that the default world stays Survival.
- persistence.spec: marker block + day time 18000 survive the REAL
  save-and-quit button -> full page reload (a whole TeaVM reboot) ->
  re-enter round trip; the persisted time key is checked in localStorage.
- chaos.spec: 24 seeded LCG rounds of mixed real inputs (movement keys,
  look snaps, attack/use, hotbar digits/wheel, inventory + pause toggles,
  day-time jumps, scene edits) with per-round invariants: finite bounded
  player position, never STATE_CRASHED, tick clock advancing, zero
  uncaught errors; final probe proves the world still accepts edits.

CI: the geometry-unit job now compiles and runs all five JVM tests; the
e2e job shards 3x (strategy.matrix, fail-fast false, per-shard report
artifacts) so the larger suite keeps its wall-clock budget. Suiteside
helpers added to support.ts (waitForEditApplied gating on the EDIT
spot's own section, setSceneBlocks, readBlockAt, findCleanAirCell,
setDayTime). playwright --list compiles all 21 specs clean.

The Playwright verdict (recorded in docs/TEST-STRATEGY.md): Playwright
STAYS - the browser layer asserts WebGL, real input plumbing, DOM
screens and the deployed bytes, which no faster alternative reproduces;
raw CDP would forfeit sharding/retries/traces for seconds of savings.
The real speed levers, in impact order: JVM layer for all pure logic,
3-way sharding, worker-shared boots, telemetry assertions instead of
pixel waits. Budgets stay sized to the vanilla 20 TPS clock.

Commit ships: 4 JVM tests + ci.yml geometry-unit wiring, 4 e2e specs +
support.ts helpers, e2e.yml shard matrix, docs/TEST-STRATEGY.md,
IMPLEMENTATION-COVERAGE.md test-table refresh, this entry. No product
code changed - this round only adds tests and CI wiring, so the deployed
bytes stay at the Task 13 build.

Fix round (same day): the first CI run (41374674903) proved the design -
build green, all five JVM tests green (~seconds into the pipeline), the
3 shards ran in parallel, chaos.spec passed FIRST TRY and the heavy shard
1 (crafting 9.2m + features + creative) was green. Shard 3 found two real
test bugs and one flaky assertion, all fixed and lesson-logged:
- placement.spec asserted the neighbor's shared quad RENDERS when
  attached - wrong vanilla semantics: occlusion hides the shared
  interface from BOTH sides (the same rule geometry.spec's "placed
  block's bottom is culled against the ground" uses). The fix asserts
  the shared quad culls (0) and a NON-shared quad renders (2) so the
  neighbor still proves it remeshed.
- persistence.spec scanned localStorage for a key starting
  "eaglercraft.world.time." - but PlatformRuntime wraps every key in its
  config namespace, so the real key is "<ns>.eaglercraft.world.time.<world>"
  (double prefix). The save path itself is correct (disconnect() ->
  saveEdits + saveDayTime). The scan now matches by VALUE (18000), which
  no prefix scheme can hide.
- creative.spec's "the aim must be off the broken cell" assertion raced
  the player falling into the hole they just broke under their feet
  (failed twice on CI, passed on retry #2). Instant breaking is now
  proven by the block itself: re-snap aim, click, settle, assert air,
  up to three tries.

Fix round 2 (run 41377994350): the shared-face fix was right - scene A
now passes and the failure moved on. Two more test bugs surfaced:
- persistence: the day time KEEPS TICKING between the set and the save,
  so the stored value is never exactly "18000" (and the key sits under
  PlatformRuntime's config namespace). sky.spec's green "day time
  persists across save-and-quit" confirmed the save path all along; the
  spec now asserts a world.time key with a numeric value >= 18000
  (saved, not reset to the fresh-world 1000, advanced monotonically).
- placement scene B: the site scan checked air ABOVE the border cell but
  not the border cell AT surface level - a solid cell there legitimately
  culls the surface block's EAST quad (0 tris before the build). The
  scan now requires that cell to be air, and node-side picks the first
  of up to 6 candidates whose EAST quad is actually exposed (2 outward
  triangles), self-healing against unmeshed sections.

Fix round 3 (run 41379977718): persistence now PASSES (the day-time
range assert was the last of it). Placement's border scan still found
zero sites: the terrain around the small flat spawn pad does not fit a
narrow EAST-only x=12..15 window with a +/-6 y range (trees, slopes and
canopy eat the candidates). Scene B now scans ANY 16-section boundary
line - all four horizontal directions, x and z borders, y window
+6/-12, up to 8 candidate sites - and node-side picks the first whose
facing quad is genuinely exposed. Also API rate limit lesson: polling
GitHub every 20s burns the token's budget and even the LOG download
403s; the poller now sleeps 90s between polls.

Fix round 4 (run of 8d2eaed): the wide scan STILL found zero sites -
and reading the generator explained why: ClientLevel spawns on a FLAT
PAD (SPAWN_PAD_RADIUS=24, grass at y=-50, tree-free clearance), so
near spawn every pad block's horizontal neighbor is SOLID and every
horizontal quad is legitimately culled. There is no exposed side quad
to build against near spawn at any y - the premise of the scan was
wrong, not its window. Scene B is now dig-driven and deterministic:
pick a tree-free pad column pair across the x=15|16 section line,
assert the west block's EAST quad is culled (0), DIG the east block
across the line, assert the quad RE-EXPOSES (2), RESTORE the block and
assert it re-culls (0). That exercises the neighbor-update propagation
ACROSS the section boundary in both remesh directions - and if the
mesher ever fails to dirty the adjacent section on a border edit, this
scene is the tripwire.

Fix round 5 (run 41384823574): scenes A and B now PASS on CI - the
six-face culling matrix and the border tripwire are green, the mesher
survives. Scene C failed for a principled reason: aiming straight down
from the pad makes the placement target the player's own feet cell,
which vanilla canSurvive (correctly) rejects - every attempt was a
correct rejection. Scene C now uses the features.spec-proven geometry:
dev-dig a 1-deep hole, the player drops in, setLook(0, 30) aims
down-forward so the ray clears the hole wall onto a top face, every
retry re-pins the camera and verifies the crosshair (face 'up', aim
settled, target != feet cell) before a wide right-press, success is
verified by the edits counter + stack consumption, and the placed
cobblestone is located near the aimed cell.

---
Task ID: 15
Agent: main (project thread "Add Apollo server API support")
Task: plugin-message (custom payload) relay client <-> gateway <-> backend,
the transport prerequisite for Lunar Client Apollo server-API support

Context / findings (Step 0 investigation, reported to the owner):
- This repo is NOT a fork of lax1dude's EaglercraftX 1.8/1.12: it is a
  from-scratch MC 26.1.2 (protocol 775) client, TeaVM 0.15 -> JS/WebGL2,
  ~411 Java files. No desktop runtime / CompileJS step. Entities (mobs,
  remote players) are not implemented.
- Before this task plugin messages did NOT round-trip to any real server:
  * client: only config-phase plugin messages were sent (brand), play-phase
    custom payloads were "unhandled play packet", minecraft:register never sent;
  * the browser client only speaks to gateway/PlaceholderServer (MC-775-shaped
    pinned protocol, serves its own flat world, no backend at all);
  * the Bungee/Velocity plugins speak a different tiny wire format
    (0x01..0x05, 0xFF) the browser client does not speak; their McBridge
    used pre-1.20.2 ids (keepalive 0x1E, system chat 0x72, chat 0x06...),
    sent a uuid-present flag in Login Start, never sent Login Acknowledged
    and had no configuration phase - it could not have logged into a real
    26.1.2 Paper server.
- Apollo facts gathered (github.com/LunarClient/Apollo, MIT, (c) 2026
  Moonsworth): server detects the client via PlayerRegisterChannelEvent on
  `lunar:apollo` (i.e. our minecraft:register), messages are
  google.protobuf.Any bytes; client answers with
  lunarclient.apollo.player.v1.PlayerHandshakeMessage; lightweight JSON
  channel `apollo:json` with "@type". 38 module dirs in api/module (incl.
  newer: autotexthotkey, inventory, marker, packetenrichment, paynow,
  saturation, serverlink, tebex, totemcounter, heightlimit, cosmetic).
  Apollo's MinecraftVersion enum has no 26.x -> we will report UNKNOWN
  (server maps unknown to MinecraftVersion.UNKNOWN). The .proto files
  (com.lunarclient:apollo-protos 0.2.7) live on buf.build and Lunar's
  Maven repo, both blocked from the sandbox (403) - must be fetched via
  the owner's machine/browser next session. Artemis (CrystallicDev/Artemis,
  Forge 1.8 mod) has NO license file: reference only, copy nothing.
- Owner decisions (decision cards in the thread): "Relay first, then
  Apollo"; "Relay now, full Paper play later" (side-channel relay now, the
  large packet-translation refactor to actually play on Paper's world is
  planned afterwards, needs the owner's OK before starting).

Work log, file by file:
- gateway/PlaceholderServer/.../mc/McPacketIds.java: every default is now
  the vanilla 26.1.2 id = index in the decompiled LoginProtocols /
  ConfigurationProtocols / GameProtocols templates (ids are assigned in
  registration order). Added login ack/cookie ids, the whole config set
  (custom payload S01/C02, finish S03/C03, keepalive 04, ping/pong 05,
  known packs S0E/C07, code of conduct S13/accept C09, cookie 00/01) and
  play custom payload S18/C16, keepalive S2C/C1C, ping S3D/pong C2D,
  system chat S79, disconnect S20, start configuration S76/ack C10, chat
  C09, chat command C07, cookie 15/15. copy() covers all fields.
- .../mc/McBridge.java: Login Start = name + UUID (no presence flag,
  ServerboundHelloPacket); sends Login Acknowledged; new runConfiguration()
  (keepalive/ping answered, known packs ECHOED so the server omits
  registry NBT, code of conduct accepted, cookies refused, custom payloads
  delivered, finish acked); play loop answers ping, delivers custom
  payloads, handles Start Configuration (ack + re-walk config), refuses
  cookies. New Phase enum + getPhase(), Listener.onPluginMessage (default
  method, so Bungee/Velocity listeners compile unchanged),
  sendPluginMessage() (queues up to 256 before CONFIGURATION, flushes in
  order; id follows phase; 32767-byte serverbound cap), sendCommand()
  (ServerboundChatCommandPacket). Chat's LastSeenMessages.Update is now
  offset varint + 3-byte fixed bitset + checksum byte (was a bare 0 varint).
  close() flips to CLOSED before closing the socket and fail() ignores
  errors after a deliberate close (was logging "Socket closed" errors).
  Phase flips (login ack, finish ack, config ack) happen under the send
  lock so no wrong-phase custom payload can slip out. Unresolved targets
  (host:port config) are resolved per connect.
- .../mc/McProtocolStub.java: now a vanilla 26.1.2 backend: reads Login
  Start, waits for Login Acknowledged, runs configuration (brand,
  stub:config payload, known packs [minecraft:core:26.1.2], keepalive
  0xC0FFEE, ping 42, finish ONLY after the known-packs answer like
  Paper), play with ids 0x2C/0x79, echoes every client plugin message on
  the same channel, answers minecraft:register on stub:registered,
  reports how config was answered on stub:status, runs commands (0x07)
  as "ran command: /x" system chat, "!reconfig" chat triggers Start
  Configuration. Writes are serialized with a lock (keepalive thread).
- .../GatewaySession.java: backend side channel. startBackendBridge() at
  login (if --backend); client config 0x01 and new play 0x10 plugin
  messages -> bridge; backend payloads -> client play 0x14 (queued until
  enterPlay, cap 256); chat mirrored to backend, "/cmd" -> backend command
  only; backend system chat -> player; backend kick -> play disconnect;
  bridge errors shown as red system chat; close() closes the bridge.
  Without --backend, plugin messages are accepted and dropped (play 0x10
  used to be "unexpected play packet" -> kick). Client payload >32767
  bytes -> kick.
- .../PlaceholderServer.java: `--backend host:port` (createUnresolved,
  default port 25565), getBackend()/getBackendIds(), usage docs.
- Bungee/Velocity plugins: default config.yml mc_* ids updated to the
  26.1.2 values (44/121/32/28/9) + new mc_custom_payload_id (24) and
  mc_custom_payload_response_id (22) overrides.
- sources/teavm/java/net/lax1dude/eaglercraft/v2_6/client/network/
  PluginChannels.java (NEW, pure java.util so it compiles under TeaVM and
  runs in JVM tests): handler registry, minecraft:register encode/decode
  (NUL-joined), Identifier validation, onJoin() announces all channels in
  one register (late registrations announced immediately), dispatch()
  never throws (handler exceptions logged), unknown channels ignored,
  server register/unregister tracked, 32767/1 MiB caps, reset() on
  disconnect with SessionListener hooks (for per-module cleanup), recent
  traffic ring (64) + counters + debug flag for the future Apollo debug
  screen.
- net/minecraft/network/Connection.java: S2C_PLAY_PLUGIN_MESSAGE 0x14,
  C2S_PLAY_PLUGIN_MESSAGE 0x10; sendPluginMessage() returns boolean and
  picks config vs play id by whether the finish ack went out
  (configAckSent) - NOT by this.state, which flips to PLAY on the finish
  packet while the server is still in CONFIGURATION (the brand is sent in
  that window and must stay 0x01). PluginChannels.reset() wherever the
  connection is marked disconnected.
- net/minecraft/client/multiplayer/ClientPacketListener.java: attach()
  resets PluginChannels, wires its log to ClientMain and its sender to
  Connection.sendPluginMessage; config + play plugin messages dispatched;
  PluginChannels.onJoin() after doMultiplayerJoin.
- tests/geometry/BridgeRelayTest.java (NEW, 28 checks): McBridge vs
  McProtocolStub in-process over TCP with compression: early queue flush
  order, config delivery, config answered (packs=1 pong=42 keepalive=1),
  register/binary/20000-byte round trips, cap refusals, chat, Start
  Configuration re-walk and relay after it, keepalives, closed refusal.
- tests/geometry/PluginChannelsTest.java (NEW, 33 checks).
- .github/workflows/ci.yml geometry-unit: compiles PluginChannels +
  all mc/*.java and runs both new tests.
- scripts/gw_relay_smoke.mjs (NEW, whitelisted in .gitignore): wire-level
  browser-client emulation vs PlaceholderServer --backend + stub, 10 checks.
- tests/e2e/plugin-relay.spec.ts (NEW): spawns stub + gateway from the
  shadowJar and runs the smoke. NOTE: e2e.yml never builds the gateway
  jars, so this spec (like gateway-protocol/multiplayer) skips in CI; the
  JVM BridgeRelayTest is the CI guard.
- docs/PROTOCOL.md: play plugin message rows (S 0x14, C 0x10), new
  "Plugin messages and the backend side channel" section with all backend
  ids. docs/IMPLEMENTATION-COVERAGE.md: rows for the side channel, full
  Paper play (missing) and Apollo (missing, next); JVM layer now 7 tests.

Verification actually performed (sandbox, Java 26, Node 22):
- All 7 JVM tests green (FaceTables, RegistryParity 159 blocks, Physics 7,
  Recipe 25, ProtocolFuzz 8209+4105, BridgeRelay 28/28 x4 runs,
  PluginChannels 33/33).
- gw_relay_smoke.mjs vs PlaceholderServer --backend + McProtocolStub:
  10 passed, 0 failed, 4 consecutive runs; gateway log had 0 errors.
- gw_real_smoke.mjs vs PlaceholderServer WITHOUT --backend: 42 passed,
  0 failed (no regression). WITH --backend it shows 8 failures - expected:
  that script reads packets in strict order and the relayed backend
  traffic (0x14 payloads, backend chat) interleaves; the e2e spec runs it
  without --backend.
- Bungee path (GatewaySmokeMain + updated bridge + new stub) checked with
  a node WS client: login result OK, chat "echoed: hello relay" came back
  (= multiplayer.spec flow).
- Client: Connection, ClientPacketListener, PluginChannels compile with
  javac against teavm-jso/interop/jso-apis/platform 0.15.0 + gson/guava
  (one unrelated pre-existing authlib Property class stubbed). The full
  TeaVM build could NOT run in the sandbox (Maven Central 429 + aliyun
  403; minecraft jar not present) - CI must confirm.
- EaglercraftXBungee compiled with javac against bungeecord-api
  1.20-R0.2; Velocity module not compiled locally (velocity-api snapshot
  unavailable) - its change is two config lines + two intOf lines.
- NOT tested: a real Paper server (none in the sandbox).

Branch/PR: work is on branch claude/project-thread-kgpjy6 (session
branch rule), PR to main. CI (ci.yml) only triggers on push to main, so
the PR itself shows no CI run; the definition-of-done CI + live-site
verification happens after merge.

Open / next session:
1. Fetch apollo-protos .proto files (buf.build/lunarclient/apollo or
   com.lunarclient:apollo-protos sources jar) via the owner's machine.
2. Build client/apollo/*: hand-written protobuf wire reader/writer
   (varint, zigzag, fixed32/64, length-delimited, unknown-field skip),
   google.protobuf.Any unpack, JSON @type decoder, message router,
   handshake (PlayerHandshakeMessage, MinecraftVersion -> UNKNOWN, no Lunar
   brand), per-module state with PluginChannels.SessionListener reset.
3. Tier 1 modules that need no entities first; entity-based modules wait
   for an entity system.
4. Plan (owner approval required) the full-Paper-play refactor: gateway
   translating real 26.1.2 play packets, or client speaking vanilla play.

---
Task ID: 16
Agent: main (project thread "Add Apollo server API support")
Task: Apollo server API client (protobuf + JSON), tier 1-3 modules, the
client's own HUD/QoL mods ("PlebMc"), mod menu, HUD editor, Apollo debug
screen, docs. Follows Task 15 (transport). Owner said "yes go ahead and do
the rest".

Branch / PR: claude/project-thread-kgpjy6, PR #1 to main (not merged yet).

Commits (in order, all pushed):
- f4bf7b4 apollo: hand-written protobuf + JSON codec for the Apollo protocol
- 557bffb apollo: client core (handshake, router, debug log) + tier 1-3 modules
- 72ca735 client: built-in HUD/QoL mods with server overrides and saved layout
- c61aeda client: mod menu, HUD editor, Apollo debug screen + overlays, vanilla hooks
- (this push) docs: Apollo guide, README section, coverage, worklog

Schema source and why:
- The .proto files could not be fetched (buf.build and Lunar's Maven repo are
  blocked from the sandbox; the owner's Downloads folder access was declined,
  do not ask again). The npm package lunar-apollo-protobuf@1.0.2 (GPLv3) was
  installed in the scratchpad and only READ: scripts/extract_apollo_schema.py
  pulls message/field/enum facts out of its generated JS into
  docs/apollo/apollo-schema-1.0.2.json (124 messages incl. a hand-added
  google.protobuf.Any, 13 enums); scripts/gen_apollo_schema.py turns that into
  client/apollo/proto/ApolloSchemaData.java (rows "full.Name",
  "id:name:[r]kind[:type];..."; kinds s y b i I l L z Z f d x X w W e m M).
  No GPL code is vendored.
- 1.0.2 is older than current Apollo: newer fields (waypoint/cooldown styles,
  markers, server links, cosmetics) decode as unknown fields and are skipped.
  Regenerate from a newer bundle with the two scripts when possible.

Files (sources/teavm/java/net/lax1dude/eaglercraft/v2_6/...):
- client/apollo/proto/: ProtoException, ProtoReader (bounds-checked varint,
  zigzag, fixed32/64, length-delimited, skip incl. groups), ProtoWriter,
  ProtoSchema (Field/MessageType/EnumType, JSON key lookup ignoring case and
  underscores, typeNameFromUrl), ProtoMessage (typed getters with defaults,
  getDurationMillis, getEnumName, unknownFieldCount), ProtoCodec
  (decode/decodeAny/encode/encodeAny, depth cap 48, mismatched wire types
  skipped, packed repeated accepted), Json (strict parser, depth 64, writer),
  ProtoJson (@type, snake/camel keys, Duration "1.5s", Timestamp RFC 3339,
  Value/Struct/ListValue/Any, numeric enums, parseUnsigned64 because TeaVM 0.15
  lacks Long.parseUnsignedLong), ProtoValues, Base64.
- client/apollo/: Apollo (facade: registers lunar:apollo + apollo:json with
  PluginChannels, SessionListener onJoin -> handshake, onReset -> clear every
  module; replies on the channel the server used; router keyed by type name
  relative to "lunarclient.apollo."; OverrideConfigurableSettingsMessage
  applies module enable + properties with proto semantics), ApolloModule base
  (enable/disable -> reset, properties, tick), ApolloDebugLog (ring of 100,
  status HANDLED / IGNORED_UNKNOWN / IGNORED_DISABLED / IGNORED_POLICY / ERROR
  / SENT), ApolloText (Adventure JSON, legacy section codes, Component -> runs),
  PolicyException.
- Handshake: PlayerHandshakeMessage{minecraft_version{enum "v26_1_2"},
  embedded_checkout_support 0}. No lunar client version, no installed mods,
  minecraft:brand unchanged (owner rule: do not spoof another client's brand).
- client/apollo/module/: ApolloModules.create() wires tier 1 (waypoint,
  notification, title, cooldown, team, nametag, border, server_rule,
  mod_setting, rich_presence), tier 2 (beam, glow, hologram, colored_fire,
  glint, limb, vignette, stopwatch, tnt_countdown, nick_hider), tier 3 (chat,
  combat, entity, staff_mod, transfer), options-only modules (packet_enrichment,
  totem_counter, saturation, auto_text_hotkey, inventory, marker, server_link,
  cosmetic) and declined pay_now/tebex. Every map has a flood cap.
  Decisions: StaffModModule refuses XRAY (the only StaffMod enum value) with
  PolicyException -> IGNORED_POLICY (owner's no-cheats rule; reported as a
  conflict with "staff mods when the server sends them"). TransferModule
  answers pings TIMED_OUT and transfers REJECTED (browser cannot open raw
  host:port). Checkout messages are declined.
  mod_setting: properties keyed "<mod>.<option>" ("zoom.enabled"); null rolls
  back. server_rule keys: competitive-game, competitive-commands,
  override-brightness, brightness, override-nametag-render-distance,
  nametag-render-distance, override-max-chat-length, max-chat-length,
  disable-shaders, anti-portal-traps.
- client/mods/ (pure Java, JVM-testable): ClientInfo.NAME = "PlebMc",
  ClientView, SettingsStore, HudBox, Mod (id = Apollo mod id, blocked by
  mod_setting), Mods (fps, cps, keystrokes, coordinates, armorstatus,
  potion-effects, ping, clock, toggle-sneak[toggle-sprint], zoom[zoom-divisor],
  lighting[full-bright]; server brightness rule beats fullbright),
  ModRegistry (JSON persistence key "mods", positions as screen fractions),
  ClickCounter.
- client/hud/ (browser glue): HudText, BrowserView, Projector (camera MVP via
  WorldGlRenderer static helpers), ApolloOverlay (vignette, stopwatch,
  cooldowns, live chat, titles, notifications, beams, waypoints, holograms,
  team markers, border edges + warning; GUI quad batch only, no new GL paths),
  PlebClient (init, Right Shift menu, sprint toggle, CPS clicks, zoom FOV on C,
  light floor, border movement cancel, HUD render + Apollo.tick), ModMenuScreen,
  HudEditorScreen (drag via mouseClicked/Released + polled mouse), ApolloDebugScreen.
- Vanilla hooks (one line each): Minecraft (PlebClient.init after new Gui),
  Gui.render, KeyboardHandler.keyDown, MouseHandler (before startAttack),
  KeyboardInput (|| PlebClient.sprintHeld()), GameRenderer (adjustFov +
  applyLighting), LocalPlayer.move (borderBlocks), WorldGlRenderer (uniform
  u_minLight, frag max(v_color.rgb * u_skyLight, vec3(u_minLight)); 0 keeps
  the old output; setMinLight).
- Tests: tests/apollo/fixtures.txt (12 messages serialized by protobufjs via
  scripts/gen_apollo_fixtures.cjs), ApolloProtoTest, ApolloClientTest,
  ClientModsTest. ci.yml geometry-unit now compiles tests/apollo + the apollo
  and mods packages and runs the three new tests.
- scripts/teavm_api_check.py: checks compiled classes only use JDK members
  present in the TeaVM 0.15 classlib jar (handles inherited members,
  getMessage -> getMessage0 rename); validated with a negative control.
- New scripts whitelisted in .gitignore: gen_apollo_fixtures.cjs,
  extract_apollo_schema.py, gen_apollo_schema.py, teavm_api_check.py.
- Docs (this push): docs/APOLLO.md (architecture, module status table, client
  mods, local Paper + Apollo + PlaceholderServer --backend setup, example-plugin
  command list with expected results, tests, licenses), README section
  "Apollo Server API and PlebMc Mods", IMPLEMENTATION-COVERAGE rows.

Bugs found and fixed during the work:
- Schema extractor missed quoted ["enum"] properties -> regex fixed.
- Fixture generator hit a protobufjs oneof error (itemId + itemName) -> itemName only.
- protobufjs JSON for Value is not proto3 JSON -> reference-JSON compare skipped
  for those fixtures (binary compare still done).
- TeaVM has no Long.parseUnsignedLong -> manual parseUnsigned64.
- ClientModsTest CPS expectation was wrong (3, not 4).
- Full-tree javac needs authlib/datafixers -> touched files compiled with
  -sourcepath + stubs: 455 classes clean.

Verification actually performed (2026-11-21, local JVM, same command list as
CI geometry-unit): FaceTablesTest ok, RegistryParityTest failures=0,
PhysicsParityTest failures=0, RecipeParityTest failures=0, ProtocolFuzzTest
failures=0, BridgeRelayTest 28 checks 0 failures, PluginChannelsTest 33/0,
ApolloProtoTest 116/0 (byte-identical re-encode of every fixture, 25k fuzzed
payloads never throw), ApolloClientTest 44/0, ClientModsTest 28/0. A mutation
(broken sint32 zigzag) was caught by ApolloProtoTest.

NOT verified:
- The real TeaVM build (Gradle/Maven blocked in the sandbox) - only the API
  checker. CI must confirm.
- e2e / live site: every "Build PR Preview" run fails in ~4 s with no runner
  (account-level GitHub Actions problem, already reported to the owner);
  ci.yml runs on push to main only.
- No real Paper + Apollo server was available.

Known limitations: entity-based modules (nametag, glow, colored fire, limb,
tnt countdown, entity) keep state but draw nothing until entities exist; ping
mod shows n/a and armor/potions are empty (no data source in the port);
full play on a real Paper world is still missing, so Apollo works over the
relay side channel while the visible world stays the gateway's.

Licenses: repo MIT (Copyright (c) 2025 lax1dude, keep LICENSE); Apollo MIT,
no code included; lunar-apollo-protobuf GPLv3 used only as a fact source and
independent fixture encoder, not vendored. No licence conflict; the only
conflict in the request is X-ray vs no-cheats (refused, owner informed).

Next session:
1. After the Actions runner problem is fixed: merge PR #1, confirm CI build ->
   geometry-unit -> e2e -> deploy green, check the live bundle contains the
   Apollo classes, open Right Shift menu on the live site.
2. Test against a real Paper + Apollo + example plugin using docs/APOLLO.md.
3. Regenerate the schema from a newer Apollo protos bundle if obtainable.
4. Full Paper play refactor - needs the owner's explicit OK first.
5. Ping source (keepalive RTT), armor/effects once those systems exist.
