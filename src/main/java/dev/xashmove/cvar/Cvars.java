package dev.xashmove.cvar;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import dev.xashmove.cvar.Cvar.Scope;
import dev.xashmove.cvar.Cvar.Type;

/**
 * Every console variable the mod knows about. Names and defaults follow Xash3D FWGS / HLSDK where a real
 * equivalent exists (sv_accelerate, sv_airaccelerate, edgefriction, cl_forwardspeed, cl_rollangle, ...).
 */
public final class Cvars {
	private static final Map<String, Cvar> ALL = new LinkedHashMap<>();
	private static final Map<String, Map<String, Double>> PRESETS = new LinkedHashMap<>();

	private static Cvar f(String n, Scope s, double d, double min, double max, String desc) {
		return reg(new Cvar(n, Type.FLOAT, s, d, min, max, desc));
	}

	private static Cvar i(String n, Scope s, double d, double min, double max, String desc) {
		return reg(new Cvar(n, Type.INT, s, d, min, max, desc));
	}

	private static Cvar b(String n, Scope s, boolean d, String desc) {
		return reg(new Cvar(n, Type.BOOL, s, d ? 1 : 0, 0, 1, desc));
	}

	private static Cvar e(String n, Scope s, int d, String desc, String... labels) {
		return reg(new Cvar(n, Type.ENUM, s, d, 0, labels.length - 1, desc, labels));
	}

	private static Cvar reg(Cvar c) {
		ALL.put(c.name, c);
		return c;
	}

	// ---------------------------------------------------------------- server: mode & rules (never synced)
	public static final Cvar SV_XASH_MODE = e("sv_xash_mode", Scope.SERVER_ONLY, 2,
			"Server movement mode: off = vanilla, vanilla = modded clients get Xash movement within vanilla-safe limits, pure = full Xash rules", "off", "vanilla", "pure");
	public static final Cvar SV_REQUIRE_CLIENT = b("sv_require_client", Scope.SERVER_ONLY, false,
			"pure mode: kick players who join without the mod");
	public static final Cvar SV_RELAX_MOVECHECK = b("sv_relax_movecheck", Scope.SERVER_ONLY, true,
			"pure mode: skip vanilla 'moved too quickly/wrongly' checks for modded players");
	public static final Cvar SV_HL_FALLDAMAGE = b("sv_hl_falldamage", Scope.SERVER_ONLY, true,
			"pure mode: GoldSrc fall damage (safe below 580 u/s impact, lethal at 1024 u/s)");
	public static final Cvar SV_FALLDAMAGE_SCALE = f("sv_falldamage_scale", Scope.SERVER_ONLY, 1, 0, 10,
			"pure mode: multiplier on HL fall damage");
	public static final Cvar SV_NO_HUNGER = b("sv_no_hunger", Scope.SERVER_ONLY, false,
			"pure mode: no food exhaustion (Half-Life has no hunger)");

	// ---------------------------------------------------------------- movevars (synced to clients)
	public static final Cvar SV_UNITS_PER_BLOCK = f("sv_units_per_block", Scope.SERVER, 40, 8, 128,
			"GoldSrc units per Minecraft block (HL player hull 72u = 1.8 blocks -> 40)");
	public static final Cvar SV_SUBSTEPS = i("sv_substeps", Scope.SERVER, 4, 1, 10,
			"Physics frames per game tick (4 = 80 Hz, close to a 100 fps HL client)");
	public static final Cvar SV_GRAVITY = f("sv_gravity", Scope.SERVER, 800, 0, 4000, "World gravity (u/s^2)");
	public static final Cvar SV_MAXSPEED = f("sv_maxspeed", Scope.SERVER, 320, 1, 4000, "Max speed a player can accelerate to on the ground (u/s)");
	public static final Cvar SV_ACCELERATE = f("sv_accelerate", Scope.SERVER, 10, 0, 100, "Ground acceleration");
	public static final Cvar SV_AIRACCELERATE = f("sv_airaccelerate", Scope.SERVER, 10, 0, 1000, "Air acceleration (air strafing strength)");
	public static final Cvar SV_WATERACCELERATE = f("sv_wateraccelerate", Scope.SERVER, 10, 0, 100, "Water acceleration");
	public static final Cvar SV_FRICTION = f("sv_friction", Scope.SERVER, 4, 0, 50, "Ground friction");
	public static final Cvar EDGEFRICTION = f("edgefriction", Scope.SERVER, 2, 0, 10, "Extra friction multiplier when about to walk off a ledge");
	public static final Cvar SV_WATERFRICTION = f("sv_waterfriction", Scope.SERVER, 1, 0, 10, "Water friction multiplier");
	public static final Cvar SV_STOPSPEED = f("sv_stopspeed", Scope.SERVER, 100, 0, 1000, "Below this speed friction acts as if moving at this speed");
	public static final Cvar SV_MAXVELOCITY = f("sv_maxvelocity", Scope.SERVER, 2000, 100, 10000, "Per-axis velocity clamp (u/s)");
	public static final Cvar SV_STEPSIZE = f("sv_stepsize", Scope.SERVER, 0, 0, 64,
			"Step-up height in units; 0 = vanilla 0.6 blocks (HL's 18u would block slabs). pure mode only");
	public static final Cvar SV_AIRSPEEDCAP = f("sv_airspeedcap", Scope.SERVER, 30, 0, 4000, "Wish-speed cap in air (HL: 30). Raise for 'easy' air control");
	public static final Cvar SV_JUMPHEIGHT = f("sv_jumpheight", Scope.SERVER, 45, 0, 400, "Jump height in units (HL: 45 -> 268 u/s takeoff)");
	public static final Cvar SV_BUNNYHOP_CAP = e("sv_bunnyhop_cap", Scope.SERVER, 1,
			"Bunny-hop limiter: off = unlimited (HL 'bj 1', Quake), hl = HL1 Steam PM_PreventMegaBunnyJumping, clamp = hard clamp", "off", "hl", "clamp");
	public static final Cvar SV_BUNNYHOP_FACTOR = f("sv_bunnyhop_factor", Scope.SERVER, 1.7, 1, 10, "Speed above factor*maxspeed triggers the limiter (HL: 1.7)");
	public static final Cvar SV_AUTOBUNNYHOP = b("sv_autobunnyhop", Scope.SERVER, true, "Allow holding jump to re-jump on landing (HL requires a fresh press / scroll wheel)");
	public static final Cvar SV_JUMP_BUFFER = i("sv_jump_buffer", Scope.SERVER, 0, 0, 500, "ms: a jump press this long before landing still counts (0 = HL)");
	public static final Cvar SV_LONGJUMP = b("sv_longjump", Scope.SERVER, false, "Long Jump Module: crouch + jump while moving");
	public static final Cvar SV_LONGJUMP_SPEED = f("sv_longjump_speed", Scope.SERVER, 350, 0, 2000, "Long jump base speed (HL: 350, x1.6 applied)");
	public static final Cvar SV_DUCKTUCK = f("sv_ducktuck", Scope.SERVER, 0.15, 0, 0.3, "Blocks the legs tuck up when ducking in air (duck-jump). 0 = off");
	public static final Cvar SV_HL_LADDERS = b("sv_hl_ladders", Scope.SERVER, true, "HL ladder physics: look-relative climbing, jump pushes off");
	public static final Cvar SV_LADDER_SPEED = f("sv_ladder_speed", Scope.SERVER, 200, 0, 2000, "Ladder climb speed (HL MAX_CLIMB_SPEED: 200)");
	public static final Cvar SV_HL_WATER = b("sv_hl_water", Scope.SERVER, true, "HL swimming (PM_WaterMove)");
	public static final Cvar SV_HL_LAVA = b("sv_hl_lava", Scope.SERVER, false, "Use HL swimming in lava too");
	public static final Cvar SV_WATERJUMP = b("sv_waterjump", Scope.SERVER, true, "Pop out of water onto ledges (PM_CheckWaterJump)");
	public static final Cvar SV_SNEAK_EDGE = b("sv_sneak_edge_protection", Scope.SERVER, false, "Keep vanilla 'sneaking stops you at edges' (always on in vanilla mode)");
	public static final Cvar SV_SPRINT = e("sv_sprint", Scope.SERVER, 0, "disabled = HL (no sprint), multiplier = sprint scales maxspeed", "disabled", "multiplier");
	public static final Cvar SV_SPRINT_MULTIPLIER = f("sv_sprint_multiplier", Scope.SERVER, 1.3, 1, 5, "Maxspeed multiplier while sprinting (sv_sprint multiplier)");
	public static final Cvar SV_RESPECT_ATTRIBUTES = b("sv_respect_attributes", Scope.SERVER, true, "Speed/Slowness/Jump Boost/gravity attributes scale HL values");
	public static final Cvar SV_BLOCK_FRICTION = b("sv_block_friction", Scope.SERVER, true, "Ice/slime/soul sand change friction and speed");
	public static final Cvar SV_ICE_ACCEL_MIN = f("sv_ice_accel_min", Scope.SERVER, 0.25, 0, 1, "Minimum ground acceleration fraction on slippery blocks");
	public static final Cvar SV_VF_MAX_HSPEED = f("sv_vf_max_hspeed", Scope.SERVER, 10, 1, 100,
			"vanilla mode: hard horizontal speed cap in blocks/s so anti-cheat never trips");

	// ---------------------------------------------------------------- client
	public static final Cvar CL_XASH = b("cl_xash", Scope.CLIENT, true, "Master switch for Xash movement on this client");
	public static final Cvar CL_XASH_VANILLA_SERVERS = b("cl_xash_vanilla_servers", Scope.CLIENT, true,
			"Use vanilla-friendly Xash movement on servers without the mod (uses your local sv_* profile)");
	public static final Cvar CL_FORWARDSPEED = f("cl_forwardspeed", Scope.CLIENT, 400, 0, 2000, "Forward key speed (clamped to sv_maxspeed)");
	public static final Cvar CL_BACKSPEED = f("cl_backspeed", Scope.CLIENT, 400, 0, 2000, "Back key speed");
	public static final Cvar CL_SIDESPEED = f("cl_sidespeed", Scope.CLIENT, 400, 0, 2000, "Strafe key speed");
	public static final Cvar CL_UPSPEED = f("cl_upspeed", Scope.CLIENT, 320, 0, 2000, "Swim down speed (sneak in water)");
	public static final Cvar CL_AUTOBUNNYHOP = b("cl_autobunnyhop", Scope.CLIENT, true, "Hold jump to bhop when the server allows it");
	public static final Cvar CL_ROLLANGLE = f("cl_rollangle", Scope.CLIENT, 2, -30, 30, "View roll when strafing (negative flips direction)");
	public static final Cvar CL_ROLLSPEED = f("cl_rollspeed", Scope.CLIENT, 200, 1, 2000, "Strafe speed for full roll");
	public static final Cvar CL_VIEWPUNCH = b("cl_viewpunch", Scope.CLIENT, true, "Landing / long jump view punch");
	public static final Cvar HUD_SPEEDOMETER = b("hud_speedometer", Scope.CLIENT, true, "Show speedometer");
	public static final Cvar HUD_SPEEDOMETER_UNITS = e("hud_speedometer_units", Scope.CLIENT, 0, "Speedometer units", "ups", "bps", "kmh");
	public static final Cvar HUD_SPEEDOMETER_Y = i("hud_speedometer_y", Scope.CLIENT, 24, -400, 400, "Speedometer offset below crosshair (gui px)");
	public static final Cvar HUD_SPEEDOMETER_SCALE = f("hud_speedometer_scale", Scope.CLIENT, 1, 0.5, 4, "Speedometer text scale");
	public static final Cvar HUD_SPEEDOMETER_COLOR = b("hud_speedometer_color", Scope.CLIENT, true, "Green when gaining speed, red when losing");
	public static final Cvar HUD_JUMPSPEED = b("hud_jumpspeed", Scope.CLIENT, true, "Show takeoff speed of the last jump");
	public static final Cvar HUD_MODE = b("hud_mode", Scope.CLIENT, true, "Show the active movement mode in the corner");
	public static final Cvar HUD_HL = b("hud_hl", Scope.CLIENT, false, "Half-Life style health/armor readout");
	public static final Cvar HUD_HL_HIDE_VANILLA = b("hud_hl_hide_vanilla", Scope.CLIENT, false, "Hide vanilla hearts/armor/food while hud_hl is on");
	public static final Cvar HUD_COLOR = i("hud_color", Scope.CLIENT, 0xFFA000, 0, 0xFFFFFF, "HUD colour as RGB, e.g. 0xFFA000");

	static {
		// HL1 as shipped on Steam (HLSDK pm_shared defaults, scroll-wheel bhop, PreventMegaBunnyJumping).
		preset("hl1", Map.of());
		PRESETS.get("hl1").put("sv_autobunnyhop", 0.0);
		// Out-of-the-box default: HL values, but hold-to-hop because Minecraft can't bind the scroll wheel to jump.
		preset("xash", Map.of());
		// Quake / HL 'bj 1': no bunny-hop limiter.
		preset("quake", Map.of("sv_bunnyhop_cap", 0.0, "sv_autobunnyhop", 0.0));
		// Counter-Strike 1.6 feel.
		preset("cs16", Map.of("sv_maxspeed", 250.0, "sv_accelerate", 5.0, "sv_airaccelerate", 10.0,
				"sv_stopspeed", 75.0, "sv_bunnyhop_cap", 1.0, "sv_bunnyhop_factor", 1.2, "sv_autobunnyhop", 0.0));
		// KZ / climb servers.
		preset("kz", Map.of("sv_maxspeed", 250.0, "sv_airaccelerate", 100.0, "sv_bunnyhop_cap", 0.0, "sv_autobunnyhop", 0.0));
		// Bhop servers: unlimited, hold to hop, strong air control.
		preset("bhop", Map.of("sv_airaccelerate", 100.0, "sv_bunnyhop_cap", 0.0, "sv_autobunnyhop", 1.0, "sv_maxvelocity", 3500.0));
		// Fun: big air accel and higher air wish cap.
		preset("surf", Map.of("sv_airaccelerate", 150.0, "sv_airspeedcap", 60.0, "sv_bunnyhop_cap", 0.0, "sv_autobunnyhop", 1.0,
				"sv_maxvelocity", 3500.0, "sv_longjump", 1.0));
		// Close to vanilla speeds, safest on strict anti-cheat servers.
		preset("vanilla_safe", Map.of("sv_maxspeed", 230.0, "sv_bunnyhop_cap", 2.0, "sv_bunnyhop_factor", 1.3,
				"sv_airaccelerate", 10.0, "sv_longjump", 0.0));
	}

	private static void preset(String name, Map<String, Double> overrides) {
		Map<String, Double> m = new LinkedHashMap<>();
		for (Cvar c : ALL.values()) {
			if (c.scope == Scope.SERVER) m.put(c.name, c.def);
		}
		m.putAll(overrides);
		PRESETS.put(name, m);
	}

	private Cvars() {
	}

	public static Cvar get(String name) {
		return ALL.get(name.toLowerCase(Locale.ROOT));
	}

	public static Collection<Cvar> all() {
		return Collections.unmodifiableCollection(ALL.values());
	}

	public static Map<String, Map<String, Double>> presets() {
		return Collections.unmodifiableMap(PRESETS);
	}
}
