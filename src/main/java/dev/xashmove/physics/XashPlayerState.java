package dev.xashmove.physics;

/** Per-player transient movement state (the bits of playermove_t that persist between frames). */
public final class XashPlayerState {
	/** pmove->oldbuttons & IN_JUMP. */
	public boolean oldJump;
	public boolean prevJumpInput;
	public int jumpBufferTicks;

	public boolean prevDuck;
	public int duckPressTick = -1000;
	/** Legs are currently tucked up from an in-air duck. */
	public boolean tucked;

	/** pmove->flFallVelocity (u/s, positive = falling). */
	public double fallVelocity;
	/** pmove->waterjumptime (ms). */
	public double waterJumpTime;
	public double moveDirX, moveDirZ;
	public int ladderCooldown;
	/** Yaw used by the last physics frame, to spread mouse turning over sub-frames. */
	public float lastYaw;
	public boolean hasLastYaw;

	// view effects (degrees), decayed like PM_DropPunchAngle
	public float punchPitch, punchRoll;

	// HUD statistics
	public double horizontalSpeed;
	public double prevHorizontalSpeed;
	public double lastJumpSpeed;
	public int lastJumpTick = -1000;
	public boolean handledLastTick;
}
