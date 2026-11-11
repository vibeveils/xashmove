package dev.xashmove.physics;

import java.util.Map;

import dev.xashmove.Mode;
import dev.xashmove.cvar.Cvar;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;

/**
 * Immutable snapshot of everything the movement code reads, already resolved for the active {@link Mode}.
 * Equivalent of GoldSrc's movevars_t plus the few client cvars pm_shared reads through usercmd_t.
 */
public final class MoveVars {
	public static final int BHOP_OFF = 0, BHOP_HL = 1, BHOP_CLAMP = 2;

	public final Mode mode;
	public final double unitsPerBlock;
	public final int substeps;
	public final double gravity, maxspeed, accelerate, airaccelerate, wateraccelerate;
	public final double friction, edgefriction, waterfriction, stopspeed, maxvelocity;
	public final double stepsize, airSpeedCap, jumpHeight;
	public final int bhopCap;
	public final double bhopFactor;
	public final boolean autoBhop;
	public final int jumpBufferTicks;
	public final boolean longJump;
	public final double longJumpSpeed;
	public final double duckTuck;
	public final boolean hlLadders, hlWater, hlLava, waterJump;
	public final double ladderSpeed;
	public final boolean sneakEdgeProtection;
	public final boolean sprintDisabled;
	public final double sprintMultiplier;
	public final boolean respectAttributes, blockFriction;
	public final double iceAccelMin;
	/** Horizontal speed cap in u/s, or 0 for none. */
	public final double hardSpeedCap;
	// client side input
	public final double forwardSpeed, backSpeed, sideSpeed, upSpeed;

	private MoveVars(Mode mode, Getter sv, CvarStore cl) {
		this.mode = mode;
		boolean vf = mode == Mode.VANILLA_FRIENDLY;
		unitsPerBlock = sv.get(Cvars.SV_UNITS_PER_BLOCK);
		substeps = (int) Math.round(sv.get(Cvars.SV_SUBSTEPS));
		maxspeed = sv.get(Cvars.SV_MAXSPEED);
		accelerate = sv.get(Cvars.SV_ACCELERATE);
		airaccelerate = sv.get(Cvars.SV_AIRACCELERATE);
		wateraccelerate = sv.get(Cvars.SV_WATERACCELERATE);
		friction = sv.get(Cvars.SV_FRICTION);
		edgefriction = sv.get(Cvars.EDGEFRICTION);
		waterfriction = sv.get(Cvars.SV_WATERFRICTION);
		stopspeed = sv.get(Cvars.SV_STOPSPEED);
		airSpeedCap = sv.get(Cvars.SV_AIRSPEEDCAP);
		bhopFactor = sv.get(Cvars.SV_BUNNYHOP_FACTOR);
		jumpBufferTicks = (int) Math.ceil(sv.get(Cvars.SV_JUMP_BUFFER) / 50.0);
		longJump = sv.bool(Cvars.SV_LONGJUMP);
		longJumpSpeed = sv.get(Cvars.SV_LONGJUMP_SPEED);
		duckTuck = sv.get(Cvars.SV_DUCKTUCK);
		hlLadders = sv.bool(Cvars.SV_HL_LADDERS);
		ladderSpeed = sv.get(Cvars.SV_LADDER_SPEED);
		hlWater = sv.bool(Cvars.SV_HL_WATER);
		hlLava = sv.bool(Cvars.SV_HL_LAVA);
		waterJump = sv.bool(Cvars.SV_WATERJUMP);
		sprintDisabled = sv.get(Cvars.SV_SPRINT) == 0;
		sprintMultiplier = sv.get(Cvars.SV_SPRINT_MULTIPLIER);
		respectAttributes = sv.bool(Cvars.SV_RESPECT_ATTRIBUTES);
		blockFriction = sv.bool(Cvars.SV_BLOCK_FRICTION);
		iceAccelMin = sv.get(Cvars.SV_ICE_ACCEL_MIN);

		boolean svAutoHop = sv.bool(Cvars.SV_AUTOBUNNYHOP);
		autoBhop = svAutoHop && (cl == null || cl.bool(Cvars.CL_AUTOBUNNYHOP));

		forwardSpeed = cl != null ? cl.get(Cvars.CL_FORWARDSPEED) : Cvars.CL_FORWARDSPEED.def;
		backSpeed = cl != null ? cl.get(Cvars.CL_BACKSPEED) : Cvars.CL_BACKSPEED.def;
		sideSpeed = cl != null ? cl.get(Cvars.CL_SIDESPEED) : Cvars.CL_SIDESPEED.def;
		upSpeed = cl != null ? cl.get(Cvars.CL_UPSPEED) : Cvars.CL_UPSPEED.def;

		if (vf) {
			// Everything a vanilla server re-simulates or polices must stay within what it would accept:
			// - step height is re-run server side by move(): keep vanilla
			// - sneaking edge guard is re-run server side: keep it
			// - long hang times trip "Flying is not enabled": keep gravity / jump sane
			// - speed: hard cap well under any 'moved too quickly' threshold
			stepsize = 0;
			sneakEdgeProtection = true;
			gravity = clamp(sv.get(Cvars.SV_GRAVITY), 600, 1600);
			jumpHeight = Math.min(sv.get(Cvars.SV_JUMPHEIGHT), 50);
			maxvelocity = Math.min(sv.get(Cvars.SV_MAXVELOCITY), 2000);
			hardSpeedCap = sv.get(Cvars.SV_VF_MAX_HSPEED) * unitsPerBlock;
			int cap = (int) Math.round(sv.get(Cvars.SV_BUNNYHOP_CAP));
			bhopCap = cap == BHOP_OFF ? BHOP_HL : cap;
		} else {
			stepsize = sv.get(Cvars.SV_STEPSIZE);
			sneakEdgeProtection = sv.bool(Cvars.SV_SNEAK_EDGE);
			gravity = sv.get(Cvars.SV_GRAVITY);
			jumpHeight = sv.get(Cvars.SV_JUMPHEIGHT);
			maxvelocity = sv.get(Cvars.SV_MAXVELOCITY);
			hardSpeedCap = 0;
			bhopCap = (int) Math.round(sv.get(Cvars.SV_BUNNYHOP_CAP));
		}
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	/** Server side / integrated server: values straight from the server store. */
	public static MoveVars fromStore(Mode mode, CvarStore sv, CvarStore client) {
		return new MoveVars(mode, sv::get, client);
	}

	/** Client side: values synced from a modded server (missing keys fall back to defaults). */
	public static MoveVars fromSynced(Mode mode, Map<String, Double> sv, CvarStore client) {
		return new MoveVars(mode, c -> {
			Double v = sv.get(c.name);
			return v != null ? c.clamp(v) : c.def;
		}, client);
	}

	public double toBlocksPerTick(double ups) {
		return ups / (20.0 * unitsPerBlock);
	}

	public double toUnitsPerSecond(double blocksPerTick) {
		return blocksPerTick * 20.0 * unitsPerBlock;
	}

	@FunctionalInterface
	private interface Getter {
		double get(Cvar c);

		default boolean bool(Cvar c) {
			return get(c) != 0;
		}
	}
}
