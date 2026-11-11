package dev.xashmove.physics;

/**
 * Minecraft-free port of the maths in HLSDK / Xash3D FWGS pm_shared.c. Vectors are double[3] in Minecraft
 * axes (x east, y up, z south); speeds are GoldSrc units per second. Kept separate so it can be unit tested
 * without a game instance.
 */
public final class PmCore {
	public static final double PUNCH_THRESHOLD = 350;      // PLAYER_FALL_PUNCH_THRESHHOLD
	public static final double MAX_SAFE_FALL_SPEED = 580;  // PLAYER_MAX_SAFE_FALL_SPEED
	public static final double FATAL_FALL_SPEED = 1024;    // PLAYER_FATAL_FALL_SPEED
	public static final double DUCKING_MULTIPLIER = 0.333; // PLAYER_DUCKING_MULTIPLIER
	public static final double LONGJUMP_HEIGHT = 56;
	public static final double LADDER_JUMP_SPEED = 270;
	public static final double ONGROUND_MAX_UPSPEED = 180; // PM_CatagorizePosition

	private PmCore() {
	}

	public static double dot(double[] a, double[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	public static double length(double[] a) {
		return Math.sqrt(dot(a, a));
	}

	public static double hlen(double[] a) {
		return Math.sqrt(a[0] * a[0] + a[2] * a[2]);
	}

	/** Normalises in place, returns the old length (VectorNormalize). */
	public static double normalize(double[] a) {
		double l = length(a);
		if (l > 0) {
			a[0] /= l;
			a[1] /= l;
			a[2] /= l;
		}
		return l;
	}

	public static double[] cross(double[] a, double[] b) {
		return new double[] {
				a[1] * b[2] - a[2] * b[1],
				a[2] * b[0] - a[0] * b[2],
				a[0] * b[1] - a[1] * b[0]
		};
	}

	/** Horizontal forward vector for a Minecraft yaw (degrees). */
	public static double[] flatForward(double yawDeg) {
		double y = Math.toRadians(yawDeg);
		return new double[] {-Math.sin(y), 0, Math.cos(y)};
	}

	/** Full look vector (pitch positive = looking down). */
	public static double[] lookVector(double yawDeg, double pitchDeg) {
		double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
		return new double[] {-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
	}

	/** Right-hand vector (HL v_right). Minecraft's xxa input is positive to the LEFT. */
	public static double[] rightVector(double yawDeg) {
		double y = Math.toRadians(yawDeg);
		return new double[] {-Math.cos(y), 0, -Math.sin(y)};
	}

	/**
	 * Builds the horizontal wish velocity like PM_WalkMove / PM_AirMove.
	 *
	 * @param forward  forward input (-1..1, Minecraft zza)
	 * @param left     strafe input (-1..1, Minecraft xxa, positive = left)
	 */
	public static double[] wishVelocity(double forward, double left, double yawDeg, double fwdSpeed, double backSpeed, double sideSpeed) {
		double fmove = forward * (forward >= 0 ? fwdSpeed : backSpeed);
		double smove = left * sideSpeed;
		double y = Math.toRadians(yawDeg);
		double sin = Math.sin(y), cos = Math.cos(y);
		return new double[] {smove * cos - fmove * sin, 0, fmove * cos + smove * sin};
	}

	/** PM_Accelerate. */
	public static void accelerate(double[] vel, double[] wishdir, double wishspeed, double accel, double dt, double playerFriction) {
		double currentspeed = dot(vel, wishdir);
		double addspeed = wishspeed - currentspeed;
		if (addspeed <= 0) return;
		double accelspeed = accel * dt * wishspeed * playerFriction;
		if (accelspeed > addspeed) accelspeed = addspeed;
		vel[0] += accelspeed * wishdir[0];
		vel[1] += accelspeed * wishdir[1];
		vel[2] += accelspeed * wishdir[2];
	}

	/** PM_AirAccelerate: the wish speed used for the 'how much to add' test is capped (30 u/s in HL). */
	public static void airAccelerate(double[] vel, double[] wishdir, double wishspeed, double accel, double dt, double cap, double playerFriction) {
		double wishspd = Math.min(wishspeed, cap);
		double currentspeed = dot(vel, wishdir);
		double addspeed = wishspd - currentspeed;
		if (addspeed <= 0) return;
		double accelspeed = accel * wishspeed * dt * playerFriction;
		if (accelspeed > addspeed) accelspeed = addspeed;
		vel[0] += accelspeed * wishdir[0];
		vel[1] += accelspeed * wishdir[1];
		vel[2] += accelspeed * wishdir[2];
	}

	/** Ground part of PM_Friction. */
	public static void groundFriction(double[] vel, double friction, double stopspeed, double dt) {
		double speed = length(vel);
		if (speed < 0.1) return;
		double control = speed < stopspeed ? stopspeed : speed;
		double drop = control * friction * dt;
		double newspeed = Math.max(speed - drop, 0) / speed;
		vel[0] *= newspeed;
		vel[1] *= newspeed;
		vel[2] *= newspeed;
	}

	/** PM_PreventMegaBunnyJumping. */
	public static void preventMegaBunnyJumping(double[] vel, double maxspeed, double factor) {
		double maxscaled = factor * maxspeed;
		if (maxscaled <= 0) return;
		double spd = length(vel);
		if (spd <= maxscaled) return;
		double fraction = (maxscaled / spd) * 0.65;
		vel[0] *= fraction;
		vel[1] *= fraction;
		vel[2] *= fraction;
	}

	/** Hard clamp of horizontal speed. */
	public static void clampHorizontal(double[] vel, double max) {
		if (max <= 0) return;
		double h = hlen(vel);
		if (h > max) {
			double s = max / h;
			vel[0] *= s;
			vel[2] *= s;
		}
	}

	/** PM_CheckVelocity. */
	public static void checkVelocity(double[] vel, double maxvelocity) {
		for (int i = 0; i < 3; i++) {
			if (Double.isNaN(vel[i]) || Double.isInfinite(vel[i])) vel[i] = 0;
			if (vel[i] > maxvelocity) vel[i] = maxvelocity;
			else if (vel[i] < -maxvelocity) vel[i] = -maxvelocity;
		}
	}

	/** Velocity part of PM_WaterMove (the move itself is done by Minecraft collision). */
	public static void waterMove(double[] vel, double[] wishvel, double maxspeed, double friction, double accel, double dt) {
		double[] wishdir = wishvel.clone();
		double wishspeed = normalize(wishdir);
		if (wishspeed > maxspeed) wishspeed = maxspeed;
		wishspeed *= 0.8;

		double speed = length(vel);
		double newspeed;
		if (speed > 0) {
			newspeed = speed - dt * speed * friction;
			if (newspeed < 0) newspeed = 0;
			double s = newspeed / speed;
			vel[0] *= s;
			vel[1] *= s;
			vel[2] *= s;
		} else {
			newspeed = 0;
		}

		if (wishspeed < 0.1) return;
		double addspeed = wishspeed - newspeed;
		if (addspeed > 0) {
			double accelspeed = accel * wishspeed * dt;
			if (accelspeed > addspeed) accelspeed = addspeed;
			vel[0] += accelspeed * wishdir[0];
			vel[1] += accelspeed * wishdir[1];
			vel[2] += accelspeed * wishdir[2];
		}
	}

	/**
	 * PM_LadderMove velocity: converts movement into the ladder face into vertical movement.
	 *
	 * @param normal  horizontal unit normal pointing out of the ladder, toward the player
	 * @return new velocity
	 */
	public static double[] ladderVelocity(double[] look, double[] right, double forward, double rightMove, double[] normal, boolean onFloor, double climbSpeed) {
		double[] velocity = {
				look[0] * forward + right[0] * rightMove,
				look[1] * forward + right[1] * rightMove,
				look[2] * forward + right[2] * rightMove
		};
		double[] up = {0, 1, 0};
		double[] perp = cross(up, normal);
		normalize(perp);
		double nrm = dot(velocity, normal);
		double[] lateral = {velocity[0] - normal[0] * nrm, velocity[1] - normal[1] * nrm, velocity[2] - normal[2] * nrm};
		double[] tmp = cross(normal, perp);
		double[] out = {lateral[0] - tmp[0] * nrm, lateral[1] - tmp[1] * nrm, lateral[2] - tmp[2] * nrm};
		if (onFloor && nrm > 0) {
			out[0] += normal[0] * climbSpeed;
			out[1] += normal[1] * climbSpeed;
			out[2] += normal[2] * climbSpeed;
		}
		return out;
	}

	/** PM_CalcRoll (degrees). */
	public static double calcRoll(double[] vel, double yawDeg, double rollAngle, double rollSpeed) {
		double[] right = rightVector(yawDeg);
		double side = dot(vel, right);
		double sign = side < 0 ? -1 : 1;
		side = Math.abs(side);
		side = side < rollSpeed ? side * rollAngle / rollSpeed : rollAngle;
		return side * sign;
	}

	/** PM_DropPunchAngle on a single axis. */
	public static float dropPunch(float angle, double dt) {
		float len = Math.abs(angle);
		len -= (float) ((10.0 + len * 0.5) * dt);
		if (len <= 0) return 0;
		return Math.copySign(len, angle);
	}

	/** HL fall damage in HL hit points (100 = dead) for an impact speed in u/s. */
	public static double fallDamageHP(double fallSpeed) {
		if (fallSpeed <= MAX_SAFE_FALL_SPEED) return 0;
		return (fallSpeed - MAX_SAFE_FALL_SPEED) * (100.0 / (FATAL_FALL_SPEED - MAX_SAFE_FALL_SPEED));
	}

	public static double jumpSpeed(double gravity, double height) {
		return Math.sqrt(2.0 * Math.max(gravity, 0) * Math.max(height, 0));
	}
}
