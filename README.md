# Xash Movement (Fabric, Minecraft 26.3)

GoldSrc / Xash3D FWGS player movement for Minecraft. It's a port of the HLSDK `pm_shared.c` code that Xash3D FWGS runs:
ground and air acceleration, **air strafing**, **bunny hopping** (with or without HL's limiter), `edgefriction`,
`sv_stopspeed`, **duck-jumping**, the **Long Jump Module**, **HL ladders**, **HL swimming and water-jumps**, landing view
punch, strafe view roll, a speedometer and an HL-style HUD. Everything is set through Xash-style console variables
(`sv_airaccelerate 100`, `cl_rollangle 2`, ...) and presets.

## Modes

| Mode | Where the mod is installed | What you get |
|---|---|---|
| **Vanilla-friendly** (`vanilla`) | Client only. Works on vanilla, Paper and Spigot servers | Full GoldSrc physics on your client, held inside what a vanilla server will accept: speed capped at `sv_vf_max_hspeed` (10 b/s), vanilla step height, sneak edge-guard kept, sane gravity and jump height, bunny-hop limiter always on. Uses your local `sv_*` profile (`/xashc`). |
| **Pure Xash** (`pure`) | Server **and** clients (default when the server has the mod; singleplayer too) | The server owns every movevar and syncs it to clients live. Vanilla movement checks are skipped for modded players, `sv_stepsize` works, no sneak edge-guard, GoldSrc fall damage (safe below 580 u/s impact, lethal at 1024), optional no hunger, optional kick for players without the mod (`sv_require_client 1`). |
| Server `vanilla` mode | Server and some clients | Modded clients get vanilla-friendly movement using the server's movevars. Vanilla clients play normally and no gameplay rules change. Good for mixed servers. |
| `off` | | Vanilla movement. |

The client picks its mode on its own. If the server sends movevars it uses the server's mode. If the server has no mod it
falls back to vanilla-friendly (`cl_xash_vanilla_servers 1`). `cl_xash 0` (or the toggle key) turns it off everywhere.

Minecraft movement is client-authoritative, so all the physics run on the client. The server mod only syncs movevars,
relaxes checks and applies the gameplay rules. Server-side checks are only relaxed for players whose client reports
Xash movement switched on.

## Building

Requires JDK 25. The build uses Loom 1.19 with the non-remapping `net.fabricmc.fabric-loom` plugin (26.x is
unobfuscated), Fabric Loader 0.20.1 and Fabric API 0.165.2+26.3.

```
./gradlew build          # jar in build/libs/xashmove-1.0.0+26.3.jar
./gradlew runClient      # dev client
./gradlew runServer      # dev server
```

### GitHub Actions

`.github/workflows/build.yml` builds on every push, pull request or manual run (Actions tab > Build > Run workflow),
using Temurin JDK 25 with Gradle caching. The jars are uploaded as a workflow artifact named `xashmove-<commit>`.
If you push a tag starting with `v` (`git tag v1.0.0 && git push --tags`), it also creates a GitHub Release with the
jars attached.

Drop the jar plus Fabric API into `mods/` on the client, and also on the server for pure mode.

## Controls (rebindable under Options > Controls > Xash Movement)

| Key | Action |
|---|---|
| J | Toggle Xash movement (`cl_xash`) |
| K | Toggle speedometer |
| H | Toggle HL-style HUD |
| U | Cycle preset. In singleplayer it changes the world's server config; on vanilla servers it changes your local profile |

Movement keys work like in Half-Life: W/A/S/D map to `+forward/+moveleft/...`, sneak is `+duck` and space is `+jump`.
Sprint is disabled by default (`sv_sprint disabled`, as in HL). Set `sv_sprint multiplier` to make sprint scale your maxspeed.
Minecraft can't bind the scroll wheel to jump, so the default is hold-to-hop (`sv_autobunnyhop 1`). For HL-authentic
re-press timing use preset `hl1`, or set `sv_autobunnyhop 0` and optionally `sv_jump_buffer 50`.

## Commands

Server console (`/xash`; read-only parts work for everyone, changes need permission level 2 or the
`xashmove:command.admin` permission node):

```
/xash                          status
/xash list [filter]            list cvars, e.g. /xash list bunny
/xash get sv_airaccelerate     value, default, range, help
/xash set sv_airaccelerate 100 change; synced to all clients instantly
/xash reset <cvar|all>
/xash preset <hl1|xash|quake|cs16|kz|bhop|surf|vanilla_safe>
/xash mode <off|vanilla|pure>
/xash reload                   re-read config/xashmove-server.json
```

Client console (`/xashc`) edits `cl_*`/`hud_*` and your **local profile** of `sv_*` (used only on servers without the mod):

```
/xashc                         status (mode + whether the server has the mod)
/xashc list [filter] | get | set | toggle | reset | preset | reload
/xashc set hud_speedometer_units kmh
/xashc preset kz
```

Config files: `config/xashmove-server.json` and `config/xashmove-client.json`. Both are plain JSON and you can edit them by hand.

## Units

`sv_units_per_block 40`, so the HL player hull (72 u) is exactly a Minecraft player (1.8 blocks). At the defaults:
`sv_maxspeed 320` is 8 b/s, the 45 u jump is 1.125 blocks (clears a block, not a fence), and `sv_gravity 800` is 20 b/s².
The physics run `sv_substeps` (4) frames per tick (80 Hz). Your mouse turn is spread across those frames the way HL
updates view angles every frame, which is what makes air strafing feel right.

## Cvar reference

#### Movevars (server-owned, synced; local profile on vanilla servers)

| cvar | default | range | what it does |
|---|---|---|---|
| `sv_units_per_block` | 40 | 8 .. 128 | GoldSrc units per Minecraft block (HL player hull 72u = 1.8 blocks -> 40) |
| `sv_substeps` | 4 | 1 .. 10 | Physics frames per game tick (4 = 80 Hz, close to a 100 fps HL client) |
| `sv_gravity` | 800 | 0 .. 4000 | World gravity (u/s^2) |
| `sv_maxspeed` | 320 | 1 .. 4000 | Max speed a player can accelerate to on the ground (u/s) |
| `sv_accelerate` | 10 | 0 .. 100 | Ground acceleration |
| `sv_airaccelerate` | 10 | 0 .. 1000 | Air acceleration (air strafing strength) |
| `sv_wateraccelerate` | 10 | 0 .. 100 | Water acceleration |
| `sv_friction` | 4 | 0 .. 50 | Ground friction |
| `edgefriction` | 2 | 0 .. 10 | Extra friction multiplier when about to walk off a ledge |
| `sv_waterfriction` | 1 | 0 .. 10 | Water friction multiplier |
| `sv_stopspeed` | 100 | 0 .. 1000 | Below this speed friction acts as if moving at this speed |
| `sv_maxvelocity` | 2000 | 100 .. 10000 | Per-axis velocity clamp (u/s) |
| `sv_stepsize` | 0 | 0 .. 64 | Step-up height in units; 0 = vanilla 0.6 blocks (HL's 18u would block slabs). pure mode only |
| `sv_airspeedcap` | 30 | 0 .. 4000 | Wish-speed cap in air (HL: 30). Raise for 'easy' air control |
| `sv_jumpheight` | 45 | 0 .. 400 | Jump height in units (HL: 45 -> 268 u/s takeoff) |
| `sv_bunnyhop_cap` | hl | off|hl|clamp | Bunny-hop limiter: off = unlimited (HL 'bj 1', Quake), hl = HL1 Steam PM_PreventMegaBunnyJumping, clamp = hard clamp |
| `sv_bunnyhop_factor` | 1.7 | 1 .. 10 | Speed above factor*maxspeed triggers the limiter (HL: 1.7) |
| `sv_autobunnyhop` | 1 | 0/1 | Allow holding jump to re-jump on landing (HL requires a fresh press / scroll wheel) |
| `sv_jump_buffer` | 0 | 0 .. 500 | ms: a jump press this long before landing still counts (0 = HL) |
| `sv_longjump` | 0 | 0/1 | Long Jump Module: crouch + jump while moving |
| `sv_longjump_speed` | 350 | 0 .. 2000 | Long jump base speed (HL: 350, x1.6 applied) |
| `sv_ducktuck` | 0.15 | 0 .. 0.3 | Blocks the legs tuck up when ducking in air (duck-jump). 0 = off |
| `sv_hl_ladders` | 1 | 0/1 | HL ladder physics: look-relative climbing, jump pushes off |
| `sv_ladder_speed` | 200 | 0 .. 2000 | Ladder climb speed (HL MAX_CLIMB_SPEED: 200) |
| `sv_hl_water` | 1 | 0/1 | HL swimming (PM_WaterMove) |
| `sv_hl_lava` | 0 | 0/1 | Use HL swimming in lava too |
| `sv_waterjump` | 1 | 0/1 | Pop out of water onto ledges (PM_CheckWaterJump) |
| `sv_sneak_edge_protection` | 0 | 0/1 | Keep vanilla 'sneaking stops you at edges' (always on in vanilla mode) |
| `sv_sprint` | disabled | disabled|multiplier | disabled = HL (no sprint), multiplier = sprint scales maxspeed |
| `sv_sprint_multiplier` | 1.3 | 1 .. 5 | Maxspeed multiplier while sprinting (sv_sprint multiplier) |
| `sv_respect_attributes` | 1 | 0/1 | Speed/Slowness/Jump Boost/gravity attributes scale HL values |
| `sv_block_friction` | 1 | 0/1 | Ice/slime/soul sand change friction and speed |
| `sv_ice_accel_min` | 0.25 | 0 .. 1 | Minimum ground acceleration fraction on slippery blocks |
| `sv_vf_max_hspeed` | 10 | 1 .. 100 | vanilla mode: hard horizontal speed cap in blocks/s so anti-cheat never trips |

#### Server rules (pure mode, never sent to clients)

| cvar | default | range | what it does |
|---|---|---|---|
| `sv_xash_mode` | pure | off|vanilla|pure | Server movement mode: off = vanilla, vanilla = modded clients get Xash movement within vanilla-safe limits, pure = full Xash rules |
| `sv_require_client` | 0 | 0/1 | pure mode: kick players who join without the mod |
| `sv_relax_movecheck` | 1 | 0/1 | pure mode: skip vanilla 'moved too quickly/wrongly' checks for modded players |
| `sv_hl_falldamage` | 1 | 0/1 | pure mode: GoldSrc fall damage (safe below 580 u/s impact, lethal at 1024 u/s) |
| `sv_falldamage_scale` | 1 | 0 .. 10 | pure mode: multiplier on HL fall damage |
| `sv_no_hunger` | 0 | 0/1 | pure mode: no food exhaustion (Half-Life has no hunger) |

#### Client preferences

| cvar | default | range | what it does |
|---|---|---|---|
| `cl_xash` | 1 | 0/1 | Master switch for Xash movement on this client |
| `cl_xash_vanilla_servers` | 1 | 0/1 | Use vanilla-friendly Xash movement on servers without the mod (uses your local sv_* profile) |
| `cl_forwardspeed` | 400 | 0 .. 2000 | Forward key speed (clamped to sv_maxspeed) |
| `cl_backspeed` | 400 | 0 .. 2000 | Back key speed |
| `cl_sidespeed` | 400 | 0 .. 2000 | Strafe key speed |
| `cl_upspeed` | 320 | 0 .. 2000 | Swim down speed (sneak in water) |
| `cl_autobunnyhop` | 1 | 0/1 | Hold jump to bhop when the server allows it |
| `cl_rollangle` | 2 | -30 .. 30 | View roll when strafing (negative flips direction) |
| `cl_rollspeed` | 200 | 1 .. 2000 | Strafe speed for full roll |
| `cl_viewpunch` | 1 | 0/1 | Landing / long jump view punch |
| `hud_speedometer` | 1 | 0/1 | Show speedometer |
| `hud_speedometer_units` | ups | ups|bps|kmh | Speedometer units |
| `hud_speedometer_y` | 24 | -400 .. 400 | Speedometer offset below crosshair (gui px) |
| `hud_speedometer_scale` | 1 | 0.5 .. 4 | Speedometer text scale |
| `hud_speedometer_color` | 1 | 0/1 | Green when gaining speed, red when losing |
| `hud_jumpspeed` | 1 | 0/1 | Show takeoff speed of the last jump |
| `hud_mode` | 1 | 0/1 | Show the active movement mode in the corner |
| `hud_hl` | 0 | 0/1 | Half-Life style health/armor readout |
| `hud_hl_hide_vanilla` | 0 | 0/1 | Hide vanilla hearts/armor/food while hud_hl is on |
| `hud_color` | 16752640 | 0 .. 16777215 | HUD colour as RGB, e.g. 0xFFA000 |

#### Presets

- **hl1**: `sv_autobunnyhop 0`
- **xash**: all defaults
- **quake**: `sv_bunnyhop_cap off`, `sv_autobunnyhop 0`
- **cs16**: `sv_maxspeed 250`, `sv_accelerate 5`, `sv_stopspeed 75`, `sv_bunnyhop_factor 1.2`, `sv_autobunnyhop 0`
- **kz**: `sv_maxspeed 250`, `sv_airaccelerate 100`, `sv_bunnyhop_cap off`, `sv_autobunnyhop 0`
- **bhop**: `sv_airaccelerate 100`, `sv_maxvelocity 3500`, `sv_bunnyhop_cap off`
- **surf**: `sv_airaccelerate 150`, `sv_maxvelocity 3500`, `sv_airspeedcap 60`, `sv_bunnyhop_cap off`, `sv_longjump 1`
- **vanilla_safe**: `sv_maxspeed 230`, `sv_bunnyhop_cap clamp`, `sv_bunnyhop_factor 1.3`


## How it works

- `LivingEntityMixin.travel` (HEAD, cancellable) runs `XashPhysics` for the local player. Speed is kept in
  `deltaMovement` between ticks, so knockback, explosions, pistons, bubble columns, currents and other mods keep working.
  Each sub-frame runs PM_PlayerMove's order: gravity half-step, PM_Jump, PM_Friction (with the edge trace), PM_WalkMove
  or PM_AirMove, then the move itself through Minecraft's `Entity.move` (vanilla collision, stair stepping and block
  callbacks), then the second gravity half-step and PM_CheckFalling.
- Vanilla `jumpFromGround`, `jumpInLiquid` and `goDownInWater` are suppressed while Xash handles the player.
- Elytra, creative flight, spectator, riding, riptide, levitation, cobwebs and sprint-swimming fall back to vanilla.
- `PmCore` holds the maths with no Minecraft dependency, so it can be unit tested.

## Before you ship: names to verify against 26.3

I couldn't download Minecraft 26.3 to compile here. Fabric API usage was checked against Fabric API's 26.3 branch, and
the code was type-checked against stubs of those signatures. A few Minecraft method names come from the 1.21.x line and
should be confirmed on first `./gradlew build` / launch:

- Compile-time: `LivingEntity.getJumpBoostPower()`, `Entity.getBlockSpeedFactor()/getBlockJumpFactor()`,
  `Entity.stuckSpeedMultiplier`, `MobEffects.SLOW_FALLING/LEVITATION`,
  `Attributes.GRAVITY/JUMP_STRENGTH`, `GuiGraphicsExtractor.text/centeredText`.
- Mixin targets marked `require = 0` switch off quietly if they're renamed (check the log for Mixin warnings):
  `maxUpStep`, `calculateFallDamage`, `jumpInLiquid`, `goDownInWater`, `Player.maybeBackOffFromEdge`,
  `Player.causeFoodExhaustion`, `ServerGamePacketListenerImpl.shouldCheckPlayerMovement`, `GameRenderer.bobHurt`
  (view roll/punch). `travel` and `jumpFromGround` are required.

## Licence

See `LICENSE`. The movement maths is ported from the Half-Life SDK's `pm_shared.c` (Valve licence: free,
non-commercial). Read it before publishing the mod.
