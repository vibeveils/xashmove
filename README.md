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
| O | **Options screen**: every cvar by category, plus styles and presets (also `/xashc menu`) |
| J | Toggle Xash movement (`cl_xash`) |
| K | Toggle speedometer |
| H | Toggle HL-style HUD |
| U | Cycle preset. In singleplayer it changes the world's server config; on vanilla servers it changes your local profile |
| Z | Zoom (HL crossbow secondary fire), hold or toggle (`cl_zoom_mode`) |
| G | `lastinv`: switch back to the previous weapon |
| 1-5 | With `cl_weapon_switch hl`: HL weapon buckets (press again to cycle; `hud_fastswitch 0` confirms with attack) |

Movement keys work like in Half-Life: W/A/S/D map to `+forward/+moveleft/...`, sneak is `+duck` and space is `+jump`.
Sprint is disabled by default (`sv_sprint disabled`, as in HL). Set `sv_sprint multiplier` to make sprint scale your maxspeed.
Minecraft can't bind the scroll wheel to jump, so the default is hold-to-hop (`sv_autobunnyhop 1`). For HL-authentic
re-press timing use preset `hl1`, or set `sv_autobunnyhop 0` and optionally `sv_jump_buffer 50`.

## Beyond movement

**Weapon switching (`cl_weapon_switch hl`).** Number keys 1-5 open HL's bucket menu instead of picking hotbar slots:
1 melee, 2 ranged, 3 tools, 4 consumables and other items, 5 blocks. Pressing the same number again cycles inside the
bucket. `hud_fastswitch 0` gives the classic "highlight, then click to confirm" behaviour. `G` is `lastinv`. The scroll
wheel still works and pops the menu up. Keys 6-9 keep their vanilla meaning. Only hotbar items count, because that's all
a server lets a client select.

**HUD.** HL crosshair (`hud_crosshair hl|dot|none`), ammo counter (loaded bolt plus arrow count for crossbows, arrows for
bows, durability or stack size for everything else), pickup history, damage flash, HL health/armor numbers, `cl_showfps`,
a small `net_graph` (fps, ping, speed, mode), the speedometer, and `hud_color` for the HL amber or any other colour.

**View.** HL `V_CalcBob` view bob (`cl_bob_style hl`, `cl_bob`, `cl_bobcycle`, `cl_bobup`), strafe roll, landing and
long-jump punch, and zoom.

**Bows and crossbows.** These are server rules, synced to modded clients so charge animations match:
`sv_bow_draw_time` (0 = instant full power), `sv_bow_velocity`, `sv_crossbow_charge_time`, `sv_crossbow_velocity`,
`sv_crossbow_autoload` (HL-style auto-reload from your arrows), `sv_item_use_slowdown 0` (full speed while drawing,
eating or blocking), and `sv_attack_cooldown none` (HL crowbar spam). Arrow damage in Minecraft scales with arrow speed,
so faster arrows also hit harder. `combat_hl` is a ready-made set. They work in `vanilla` and `pure` server modes;
`sv_combat 0` turns them all off. On a server without the mod, combat is always vanilla.

**GoldSrc-style player animations (`cl_anim goldsrc`).** These run on the normal Minecraft player model, for you and,
with `cl_anim_others`, for everyone you see:
- weapons held up in an aim pose that follows look pitch (two-handed for bows, crossbows and tridents; one-handed for others with `cl_anim_aim all`);
- HL's tucked-legs jump/fall pose;
- hand-over-hand ladder climbing driven by height;
- a longer running stride with little arm swing.

`cl_anim_strength` blends between vanilla (0) and GoldSrc (1). It's purely visual and local.

I didn't include an actual Half-Life player model. Valve's models (Gordon, the scientists and so on) are copyrighted
assets, and rendering `.mdl` files would mean writing a whole GoldSrc model renderer. The animations above give the
GoldSrc feel on the skin you already use.

**Other Xash/HL details.** `sv_falldamage_mode fixed` works like `mp_falldamage 0` (a flat 10 HP), HL fall damage
scaling, `sv_no_hunger`, and the long jump module.

## Options screen

Press **O**. There are tabs for Style, General, Movement, Combat, Weapons, HUD, View, Animation and Server. Toggles and
choices cycle when clicked; numbers have -/+ buttons; hover any option to see its description, default and range. Where
server cvars are saved depends on where you are:
- **singleplayer**: they edit the world's server config;
- **server without the mod**: they edit your local profile;
- **modded server**: they're read-only, and the admin changes them with `/xash`.

## Commands

Server console (`/xash`; read-only parts work for everyone, changes need permission level 2 or the
`xashmove:command.admin` permission node):

```
/xash                          status
/xash list [filter]            list cvars, e.g. /xash list bunny
/xash get sv_airaccelerate     value, default, range, help
/xash set sv_airaccelerate 100 change; synced to all clients instantly
/xash reset <cvar|all>
/xash preset <hl1|xash|quake|cs16|kz|bhop|surf|vanilla_safe|combat_vanilla|combat_hl|full_xash|mostly_minecraft>
/xash mode <off|vanilla|pure>
/xash reload                   re-read config/xashmove-server.json
```

Client console (`/xashc`) edits `cl_*`/`hud_*` and your **local profile** of `sv_*` (used only on servers without the mod):

```
/xashc                         status (mode + whether the server has the mod)
/xashc list [filter] | get | set | toggle | reset | preset | reload
/xashc style <vanilla|movement|classic|full>
/xashc menu                    open the options screen
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
| `sv_item_use_slowdown` | 1 | 0 .. 1 | Slowdown while drawing a bow / eating / blocking: 1 = vanilla (20% speed), 0 = HL (full speed while aiming) |
| `sv_bow_draw_time` | 1 | 0 .. 5 | Seconds to fully draw a bow (vanilla 1, 0 = instant full power) |
| `sv_bow_velocity` | 1 | 0.1 .. 5 | Arrow speed multiplier for bows (arrow damage scales with speed too) |
| `sv_crossbow_charge_time` | 1.25 | 0.05 .. 5 | Seconds to load a crossbow (vanilla 1.25; Quick Charge still applies on top) |
| `sv_crossbow_velocity` | 1 | 0.1 .. 5 | Bolt speed multiplier for crossbows; damage scales with speed (HL bolts fly ~2.5x faster) |
| `sv_crossbow_autoload` | 0 | 0/1 | HL-style auto reload: a fired crossbow reloads itself after sv_crossbow_charge_time if you carry arrows |
| `sv_attack_cooldown` | vanilla | vanilla|none | vanilla = 1.9 attack cooldown, none = HL crowbar-spam (full damage every swing) |

#### Server rules (pure mode, never sent to clients)

| cvar | default | range | what it does |
|---|---|---|---|
| `sv_xash_mode` | pure | off|vanilla|pure | Server movement mode: off = vanilla, vanilla = modded clients get Xash movement within vanilla-safe limits, pure = full Xash rules |
| `sv_require_client` | 0 | 0/1 | pure mode: kick players who join without the mod |
| `sv_relax_movecheck` | 1 | 0/1 | pure mode: skip vanilla 'moved too quickly/wrongly' checks for modded players |
| `sv_hl_falldamage` | 1 | 0/1 | pure mode: GoldSrc fall damage (safe below 580 u/s impact, lethal at 1024 u/s) |
| `sv_falldamage_scale` | 1 | 0 .. 10 | pure mode: multiplier on HL fall damage |
| `sv_no_hunger` | 0 | 0/1 | pure mode: no food exhaustion (Half-Life has no hunger) |
| `sv_falldamage_mode` | realistic | realistic|fixed | pure mode, like mp_falldamage: realistic = scales with impact speed, fixed = flat 10 HP whenever HL would hurt |
| `sv_combat` | 1 | 0/1 | Apply the sv_bow_* / sv_crossbow_* / sv_attack_cooldown combat cvars (any mode except off) |

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
| `hud_crosshair` | vanilla | vanilla|hl|dot|none | Crosshair style |
| `hud_crosshair_size` | 5 | 2 .. 20 | HL crosshair arm length (gui px) |
| `hud_ammo` | 0 | 0/1 | HL ammo readout: arrows for bows/crossbows, stack count or durability otherwise |
| `hud_history` | 0 | 0/1 | HL pickup history on the right side of the screen |
| `hud_history_time` | 4 | 1 .. 20 | Seconds a pickup stays in the history |
| `hud_damage_flash` | 0 | 0/1 | Red screen flash when hurt |
| `cl_showfps` | 0 | 0/1 | Show FPS in the top-right corner (Xash cl_showfps) |
| `net_graph` | 0 | 0/1 | Show fps / ping / speed / mode block (simplified net_graph) |
| `cl_weapon_switch` | vanilla | vanilla|hl | Number keys: vanilla = hotbar slots, hl = HL weapon buckets (1 melee, 2 ranged, 3 tools, 4 consumables/misc, 5 blocks) |
| `hud_fastswitch` | 1 | 0/1 | hl switching: select immediately (1) or open the menu and confirm with attack (0) |
| `hud_weapon_popup` | 1 | 0/1 | hl switching: show the weapon menu when the slot changes (scroll wheel too) |
| `cl_weapon_menu_time` | 2.5 | 0.5 .. 10 | Seconds the weapon menu stays open |
| `cl_bob_style` | vanilla | vanilla|hl|none | View bobbing: vanilla, hl (V_CalcBob up/down bob) or none |
| `cl_bob` | 0.01 | 0 .. 0.1 | HL bob amount |
| `cl_bobcycle` | 0.8 | 0.1 .. 2 | HL bob cycle length (s) |
| `cl_bobup` | 0.5 | 0.05 .. 0.95 | HL bob up-phase fraction |
| `cl_zoom_fov` | 0.3 | 0.05 .. 1 | Zoom key FOV multiplier (HL crossbow zoom ~0.22) |
| `cl_zoom_mode` | hold | hold|toggle | Zoom key behaviour |
| `cl_zoom_items` | ranged | any|ranged | Zoom works with: any item, or only ranged weapons (HL crossbow secondary fire) |
| `cl_anim` | vanilla | vanilla|goldsrc | Player model animation set |
| `cl_anim_aim` | ranged | off|ranged|all | goldsrc: hold weapons up in an aim pose that follows look pitch |
| `cl_anim_jump` | 1 | 0/1 | goldsrc: tucked-legs jump/fall pose |
| `cl_anim_ladder` | 1 | 0/1 | goldsrc: hand-over-hand ladder climbing |
| `cl_anim_armswing` | 0.35 | 0 .. 1 | goldsrc: arm swing while running (HL characters barely swing) |
| `cl_anim_stride` | 1.2 | 0.5 .. 2 | goldsrc: leg stride multiplier |
| `cl_anim_strength` | 1 | 0 .. 1 | Blend between vanilla (0) and goldsrc (1) poses |
| `cl_anim_others` | 1 | 0/1 | Apply goldsrc animations to other players too (purely visual, local) |

#### Presets

- **hl1**: `sv_autobunnyhop 0`
- **xash**: all defaults
- **quake**: `sv_bunnyhop_cap off`, `sv_autobunnyhop 0`
- **cs16**: `sv_maxspeed 250`, `sv_accelerate 5`, `sv_stopspeed 75`, `sv_bunnyhop_factor 1.2`, `sv_autobunnyhop 0`
- **kz**: `sv_maxspeed 250`, `sv_airaccelerate 100`, `sv_bunnyhop_cap off`, `sv_autobunnyhop 0`
- **bhop**: `sv_airaccelerate 100`, `sv_maxvelocity 3500`, `sv_bunnyhop_cap off`
- **surf**: `sv_airaccelerate 150`, `sv_maxvelocity 3500`, `sv_airspeedcap 60`, `sv_bunnyhop_cap off`, `sv_longjump 1`
- **vanilla_safe**: `sv_maxspeed 230`, `sv_bunnyhop_cap clamp`, `sv_bunnyhop_factor 1.3`
- **combat_vanilla**: all defaults
- **combat_hl**: `sv_item_use_slowdown 0`, `sv_bow_draw_time 0.35`, `sv_bow_velocity 1.5`, `sv_crossbow_charge_time 0.6`, `sv_crossbow_velocity 2.5`, `sv_crossbow_autoload 1`, `sv_attack_cooldown none`
- **full_xash**: `sv_longjump 1`, `sv_item_use_slowdown 0`, `sv_bow_draw_time 0.35`, `sv_bow_velocity 1.5`, `sv_crossbow_charge_time 0.6`, `sv_crossbow_velocity 2.5`, `sv_crossbow_autoload 1`, `sv_attack_cooldown none`
- **mostly_minecraft**: `sv_maxspeed 230`, `sv_bunnyhop_cap clamp`, `sv_bunnyhop_factor 1.3`


#### Client styles (`/xashc style <name>`, or the Style tab of the options screen)

How much Minecraft vs. how much Xash, for everything on your side. A style resets all look-and-feel cvars, but not your input speeds or `hud_color`.

- **vanilla**: pure Minecraft. Xash movement off and no HL effects.
- **movement**: Xash movement only, with Minecraft's look and feel.
- **classic**: movement plus HL view bob, HL crosshair, ammo counter and GoldSrc animations.
- **full**: everything above plus HL weapon buckets, the HL health/armor HUD (vanilla bars hidden), pickup history, damage flash and `net_graph`.

The server side has a matching scale: `/xash preset mostly_minecraft` up to `/xash preset full_xash`. You can also mix one movement preset with `combat_vanilla` or `combat_hl`.

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
- New in this version (compile-time): `Player.getProjectile`, `ChargedProjectiles.of(ItemStack)` and
  `DataComponents.CHARGED_PROJECTILES` (crossbow auto-reload), `net.minecraft.world.entity.projectile.Projectile`
  (arrow tuning), `Inventory.getContainerSize`, `Player.containerMenu.getCarried()`, `Minecraft.getFps()`,
  `ClientPacketListener.getPlayerInfo`, `HumanoidModel.head/rightArm/...` fields, `Screen.clearWidgets`.
- Mixin targets marked `require = 0` switch off quietly if they're renamed (check the log for Mixin warnings):
  `maxUpStep`, `calculateFallDamage`, `jumpInLiquid`, `goDownInWater`, `Player.maybeBackOffFromEdge`,
  `Player.causeFoodExhaustion`, `ServerGamePacketListenerImpl.shouldCheckPlayerMovement`, `GameRenderer.bobHurt`
  (view roll/punch), `GameRenderer.bobView` (HL bob), `GameRenderer.getFov` (zoom), `BowItem.getPowerForTime`,
  `CrossbowItem.getChargeDuration`, `LivingEntityRenderer.extractRenderState` and `HumanoidModel.setupAnim`
  (animations; all client-side ones are `require = 0`). `travel` and `jumpFromGround` are required.

## Licence

See `LICENSE`. The movement maths is ported from the Half-Life SDK's `pm_shared.c` (Valve licence: free,
non-commercial). Read it before publishing the mod.
