package dev.xashmove.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.xashmove.Mode;

/**
 * PM_PlayerMove for a Minecraft player. Runs on the client for the local player only (Minecraft movement is
 * client authoritative); the server just validates the resulting positions.
 *
 * <p>Velocity lives in {@code deltaMovement} between ticks so knockback, explosions, pistons, bubble columns,
 * water currents and other mods keep working. Inside a tick it is converted to GoldSrc units per second and
 * integrated in {@code sv_substeps} frames, each one moved with Minecraft's own collision ({@code Entity.move}),
 * which also gives us vanilla stair stepping, block callbacks and fall-distance tracking.
 */
public final class XashPhysics {
	/** Keeps a grounded player pressed into the floor so Minecraft keeps reporting onGround. */
	private static final double GROUND_PROBE = 1.0E-3;
	private static final double STAND_HEIGHT = 1.8;
	private static final double CROUCH_HEIGHT = 1.5;

	private XashPhysics() {
	}

	/** Whether Xash physics should drive this player right now (otherwise vanilla handles the tick). */
	public static boolean canHandle(Player p, MoveVars v) {
		if (v == null || v.mode == Mode.OFF) return false;
		if (p.isSpectator() || p.getAbilities().flying || p.isFallFlying() || p.isPassenger()
				|| p.isSleeping() || p.isAutoSpinAttack() || p.isDeadOrDying()) {
			return false;
		}
		if (p.hasEffect(MobEffects.LEVITATION)) return false;
		if (p.isSwimming()) return false;
		if (p.isInLava() && !v.hlLava) return false;
		if (p.isInWater() && !v.hlWater) return false;
		return true;
	}

	/** True when Xash handles jumping in water (vanilla jumpInLiquid should be suppressed). */
	public static boolean handlesLiquidJump(Player p, MoveVars v) {
		return canHandle(p, v) && (p.isInWater() ? v.hlWater : v.hlLava);
	}

	/**
	 * Runs one game tick of movement.
	 *
	 * @return true if the tick was handled (vanilla travel must be cancelled)
	 */
	public static boolean travel(Player p, Vec3 input, MoveContext ctx, MoveVars v) {
		XashPlayerState s = ((XashPlayerAccess) p).xash$getState();
		if (v != null && v.mode != Mode.OFF && v.sprintDisabled && p.isSprinting()) {
			p.setSprinting(false);
		}
		if (ctx.stuck() || !canHandle(p, v)) {
			s.oldJump = ctx.jumping();
			s.prevJumpInput = ctx.jumping();
			s.prevDuck = p.isShiftKeyDown();
			s.tucked = false;
			s.waterJumpTime = 0;
			s.lastYaw = p.getYRot();
			s.hasLastYaw = true;
			s.handledLastTick = false;
			return false;
		}
		new Sim(p, input, ctx, v, s).run();
		s.handledLastTick = true;
		return true;
	}

	/** One tick of simulation; fields mirror playermove_t. */
	private static final class Sim {
		final Player p;
		final MoveContext ctx;
		final MoveVars v;
		final XashPlayerState s;
		final double upb;
		final double dt;
		final double msec;
		final double forwardIn, leftIn;
		final double[] vel = new double[3];
		final double maxspeed;
		final double gravity;
		final double jumpMult;
		final double jumpBoostUps;
		boolean onGround;
		boolean jumpHeld;
		/** View yaw for the current sub-frame (interpolated from last tick's yaw, like HL's per-frame angles). */
		double yaw;
		double yawStart, yawDelta;
		boolean usedLadder;
		int waterLevel;
		boolean inLava;

		Sim(Player p, Vec3 input, MoveContext ctx, MoveVars v, XashPlayerState s) {
			this.p = p;
			this.ctx = ctx;
			this.v = v;
			this.s = s;
			this.upb = v.unitsPerBlock;
			this.dt = 0.05 / Math.max(1, v.substeps);
			this.msec = dt * 1000.0;
			// LivingEntity.aiStep scales raw input by 0.98 before travel(); undo that so a full key press is 1.0.
			// Using an item (bow, food, shield) scales input by 0.2 in LocalPlayer; re-scale to sv_item_use_slowdown.
			double use = 1.0;
			if (p.isUsingItem() && !p.isPassenger() && v.itemUseSlowdown < 1.0) {
				use = (1.0 - v.itemUseSlowdown * 0.8) / 0.2;
			}
			this.forwardIn = clampUnit(input.z / 0.98 * use);
			this.leftIn = clampUnit(input.x / 0.98 * use);

			Vec3 dm = p.getDeltaMovement();
			vel[0] = v.toUnitsPerSecond(dm.x);
			vel[1] = v.toUnitsPerSecond(dm.y);
			vel[2] = v.toUnitsPerSecond(dm.z);

			double speedRatio = 1.0;
			double gravityRatio = 1.0;
			double jm = 1.0;
			double boost = 0.0;
			if (v.respectAttributes) {
				double base = p.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
				double cur = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
				if (p.isSprinting()) cur /= 1.3; // strip vanilla's sprint modifier, sprint is handled below
				if (base > 0) speedRatio = cur / base;
				gravityRatio = p.getAttributeValue(Attributes.GRAVITY) / 0.08;
				jm = p.getAttributeValue(Attributes.JUMP_STRENGTH) / 0.42;
				boost = v.toUnitsPerSecond(p.getJumpBoostPower());
			}
			if (p.hasEffect(MobEffects.SLOW_FALLING) && vel[1] <= 0) {
				gravityRatio = Math.min(gravityRatio, 0.125);
			}
			if (!v.sprintDisabled && p.isSprinting()) speedRatio *= v.sprintMultiplier;
			if (v.blockFriction) {
				speedRatio *= ctx.blockSpeedFactor();
				jm *= ctx.blockJumpFactor();
			}
			this.maxspeed = v.maxspeed * speedRatio;
			this.gravity = v.gravity * gravityRatio;
			this.jumpMult = jm;
			this.jumpBoostUps = boost;
			this.onGround = p.onGround() && vel[1] <= PmCore.ONGROUND_MAX_UPSPEED;

			double now = p.getYRot();
			double delta = s.hasLastYaw ? wrapDegrees(now - s.lastYaw) : 0;
			if (Math.abs(delta) > 90) delta = 0; // teleport / respawn: don't sweep
			this.yawStart = now - delta;
			this.yawDelta = delta;
			this.yaw = now;
		}

		static double wrapDegrees(double d) {
			d %= 360.0;
			if (d >= 180.0) d -= 360.0;
			if (d < -180.0) d += 360.0;
			return d;
		}

		static double clampUnit(double d) {
			return Math.max(-1.0, Math.min(1.0, d));
		}

		void run() {
			updateJumpInput();
			if (s.ladderCooldown > 0) s.ladderCooldown--;
			duckTuck();

			int n = Math.max(1, v.substeps);
			for (int i = 0; i < n; i++) {
				yaw = yawStart + yawDelta * (i + 1) / n;
				frame();
			}
			s.lastYaw = p.getYRot();
			s.hasLastYaw = true;

			if (v.hardSpeedCap > 0) PmCore.clampHorizontal(vel, v.hardSpeedCap);
			p.setDeltaMovement(v.toBlocksPerTick(vel[0]), v.toBlocksPerTick(vel[1]), v.toBlocksPerTick(vel[2]));
			if (usedLadder) p.resetFallDistance();

			s.prevHorizontalSpeed = s.horizontalSpeed;
			s.horizontalSpeed = PmCore.hlen(vel);
		}

		// ------------------------------------------------------------------ per tick input
		void updateJumpInput() {
			jumpHeld = ctx.jumping();
			if (jumpHeld && !s.prevJumpInput) {
				s.jumpBufferTicks = v.jumpBufferTicks;
			} else if (s.jumpBufferTicks > 0) {
				s.jumpBufferTicks--;
			}
			s.prevJumpInput = jumpHeld;
		}

		/** HL ducking in mid-air pulls the legs up (origin stays centred), which is what makes duck-jumps reach higher. */
		void duckTuck() {
			boolean duck = p.isShiftKeyDown();
			if (duck && !s.prevDuck) s.duckPressTick = p.tickCount;

			if (onGround) {
				s.tucked = false;
			} else if (v.duckTuck > 0 && !p.onClimbable() && waterLevel() < 2) {
				AABB bb = p.getBoundingBox();
				double t = v.duckTuck;
				if (duck && !s.prevDuck && !s.tucked) {
					AABB target = new AABB(bb.minX, bb.minY + t, bb.minZ, bb.maxX, bb.minY + t + CROUCH_HEIGHT, bb.maxZ);
					if (p.level().noCollision(p, target)) {
						p.setPos(p.getX(), p.getY() + t, p.getZ());
						s.tucked = true;
					}
				} else if (!duck && s.prevDuck && s.tucked) {
					AABB target = new AABB(bb.minX, bb.minY - t, bb.minZ, bb.maxX, bb.minY - t + STAND_HEIGHT, bb.maxZ);
					if (p.level().noCollision(p, target)) {
						p.setPos(p.getX(), p.getY() - t, p.getZ());
					}
					s.tucked = false;
				}
			}
			s.prevDuck = duck;
		}

		// ------------------------------------------------------------------ PM_PlayerMove (MOVETYPE_WALK)
		void frame() {
			waterLevel = waterLevel();
			s.punchPitch = PmCore.dropPunch(s.punchPitch, dt);
			s.punchRoll = PmCore.dropPunch(s.punchRoll, dt);

			if (!onGround) s.fallVelocity = -vel[1];

			if (v.hlLadders && s.ladderCooldown <= 0 && p.onClimbable()) {
				usedLadder = true;
				ladderMove();
				doMove();
				return;
			}

			boolean inWater = waterLevel >= 2;
			if (!inWater) addHalfGravity();

			if (s.waterJumpTime > 0) {
				waterJump();
				doMove();
				return;
			}

			if (inWater) {
				if (waterLevel == 2 && v.waterJump) checkWaterJump();
				if (vel[1] < 0 && s.waterJumpTime > 0) s.waterJumpTime = 0;
				if (jumpHeld) {
					// PM_Jump, swimming branch
					onGround = false;
					vel[1] = inLava ? 50 : 100;
				} else {
					s.oldJump = false;
				}
				waterMove();
				doMove();
				checkFalling();
				return;
			}

			if (jumpHeld) jump();
			else s.oldJump = false;

			if (onGround) {
				vel[1] = 0;
				friction();
			}
			PmCore.checkVelocity(vel, v.maxvelocity);

			if (onGround) walkMove();
			else airMove();

			doMove();
			PmCore.checkVelocity(vel, v.maxvelocity);
			if (waterLevel() < 2) addHalfGravity();
			if (onGround) vel[1] = 0;
			checkFalling();
		}

		/** PM_AddCorrectGravity / PM_FixupGravityVelocity: half the gravity before the move, half after. */
		void addHalfGravity() {
			vel[1] -= gravity * 0.5 * dt;
		}

		int waterLevel() {
			double water = p.getFluidHeight(FluidTags.WATER);
			double lava = v.hlLava ? p.getFluidHeight(FluidTags.LAVA) : 0;
			inLava = lava > water;
			double h = Math.max(water, lava);
			if (h <= 0) return 0;
			if (h >= p.getEyeHeight()) return 3;
			return h >= p.getBbHeight() * 0.5 ? 2 : 1;
		}

		// ------------------------------------------------------------------ jumping
		void jump() {
			if (s.waterJumpTime > 0) return;
			if (!onGround) {
				// HL: holding jump in the air means you have to let go before the next jump.
				if (!v.autoBhop) s.oldJump = true;
				return;
			}
			boolean allowed = v.autoBhop || !s.oldJump || s.jumpBufferTicks > 0;
			if (!allowed) return;

			onGround = false;
			s.jumpBufferTicks = 0;

			switch (v.bhopCap) {
				case MoveVars.BHOP_HL -> PmCore.preventMegaBunnyJumping(vel, maxspeed, v.bhopFactor);
				case MoveVars.BHOP_CLAMP -> PmCore.clampHorizontal(vel, maxspeed * v.bhopFactor);
				default -> { }
			}

			double hspeed = PmCore.hlen(vel);
			boolean ducking = p.isShiftKeyDown();
			if (v.longJump && ducking && p.tickCount - s.duckPressTick <= 20 && hspeed > 50) {
				double[] f = PmCore.flatForward(yaw);
				vel[0] = f[0] * v.longJumpSpeed * 1.6;
				vel[2] = f[2] * v.longJumpSpeed * 1.6;
				vel[1] = PmCore.jumpSpeed(gravity, PmCore.LONGJUMP_HEIGHT) * jumpMult;
				s.punchPitch = -5;
			} else {
				vel[1] = PmCore.jumpSpeed(gravity, v.jumpHeight) * jumpMult + jumpBoostUps;
			}
			addHalfGravity(); // "Decay it for simulation"
			s.oldJump = true;
			s.lastJumpSpeed = PmCore.hlen(vel);
			s.lastJumpTick = p.tickCount;
		}

		// ------------------------------------------------------------------ ground
		double surfaceFriction() {
			if (!v.blockFriction) return 1.0;
			BlockPos below = BlockPos.containing(p.getX(), p.getBoundingBox().minY - 0.5000001, p.getZ());
			float bf = p.level().getBlockState(below).getBlock().getFriction();
			// Vanilla default is 0.6; ice 0.98, slime 0.8. Map to HL "player friction".
			double ratio = (1.0 - bf) / 0.4;
			return Math.max(0.02, Math.min(2.0, ratio));
		}

		void friction() {
			double speed = PmCore.length(vel);
			if (speed < 0.1) return;
			double f = v.friction * surfaceFriction();
			if (v.edgefriction != 1.0 && atEdge(speed)) f *= v.edgefriction;
			PmCore.groundFriction(vel, f, v.stopspeed, dt);
		}

		/** PM_Friction's hull trace 16u ahead and 34u down: nothing below means we're about to walk off. */
		boolean atEdge(double speed) {
			double ox = vel[0] / speed * 16.0 / upb;
			double oz = vel[2] / speed * 16.0 / upb;
			AABB bb = p.getBoundingBox();
			AABB probe = new AABB(bb.minX + ox, bb.minY - 34.0 / upb, bb.minZ + oz, bb.maxX + ox, bb.minY, bb.maxZ + oz);
			return p.level().noCollision(p, probe);
		}

		void walkMove() {
			double[] wish = wish();
			double wishspeed = PmCore.normalize(wish);
			if (wishspeed > maxspeed) wishspeed = maxspeed;

			double sf = surfaceFriction();
			double accelMult = sf < 1.0 ? Math.max(sf, v.iceAccelMin) : 1.0;
			vel[1] = 0;
			PmCore.accelerate(vel, wish, wishspeed, v.accelerate, dt, accelMult);
			vel[1] = 0;

			if (PmCore.length(vel) < 1.0) {
				vel[0] = 0;
				vel[2] = 0;
			}
		}

		void airMove() {
			double[] wish = wish();
			double wishspeed = PmCore.normalize(wish);
			if (wishspeed > maxspeed) wishspeed = maxspeed;
			PmCore.airAccelerate(vel, wish, wishspeed, v.airaccelerate, dt, v.airSpeedCap, 1.0);
		}

		double[] wish() {
			return PmCore.wishVelocity(forwardIn, leftIn, yaw, v.forwardSpeed, v.backSpeed, v.sideSpeed);
		}

		// ------------------------------------------------------------------ water
		void waterMove() {
			double[] look = PmCore.lookVector(yaw, p.getXRot());
			double[] right = PmCore.rightVector(yaw);
			double fmove = forwardIn * (forwardIn >= 0 ? v.forwardSpeed : v.backSpeed);
			double smove = -leftIn * v.sideSpeed;
			double upmove = p.isShiftKeyDown() ? -v.upSpeed : 0;
			double[] wishvel = {
					look[0] * fmove + right[0] * smove,
					look[1] * fmove + right[1] * smove,
					look[2] * fmove + right[2] * smove
			};
			if (fmove == 0 && smove == 0 && upmove == 0) wishvel[1] -= 60; // drift towards bottom
			else wishvel[1] += upmove;
			PmCore.waterMove(vel, wishvel, maxspeed, v.friction * v.waterfriction, v.wateraccelerate, dt);
		}

		/** PM_CheckWaterJump: facing a wall at waist height with free space at head height pops you out. */
		void checkWaterJump() {
			if (s.waterJumpTime > 0) return;
			if (vel[1] < -180) return;
			double[] f = PmCore.flatForward(yaw);
			double h = PmCore.hlen(vel);
			if (h > 0 && (vel[0] * f[0] + vel[2] * f[2]) / h < 0) return;

			AABB bb = p.getBoundingBox();
			double reach = 24.0 / upb;
			double px = p.getX() + f[0] * reach, pz = p.getZ() + f[2] * reach;
			double waistY = bb.minY + p.getBbHeight() * 0.5 + 8.0 / upb;
			boolean wall = !p.level().noCollision(p, point(px, waistY, pz));
			boolean clear = p.level().noCollision(p, point(px, bb.maxY, pz));
			if (wall && clear) {
				s.waterJumpTime = 2000;
				vel[1] = 225;
				s.moveDirX = f[0] * 50;
				s.moveDirZ = f[2] * 50;
				s.oldJump = true;
			}
		}

		void waterJump() {
			s.waterJumpTime = Math.min(s.waterJumpTime, 10000) - msec;
			if (s.waterJumpTime <= 0 || waterLevel == 0) s.waterJumpTime = 0;
			vel[0] = s.moveDirX;
			vel[2] = s.moveDirZ;
		}

		static AABB point(double x, double y, double z) {
			double r = 0.01;
			return new AABB(x - r, y - r, z - r, x + r, y + r, z + r);
		}

		// ------------------------------------------------------------------ ladders
		void ladderMove() {
			p.resetFallDistance();
			double[] normal = ladderNormal();
			double climb = Math.min(v.ladderSpeed, maxspeed);
			if (p.isShiftKeyDown()) climb *= PmCore.DUCKING_MULTIPLIER;
			double forward = Math.signum(forwardIn) * climb;
			double rightMove = -Math.signum(leftIn) * climb;

			if (jumpHeld && !s.oldJump) {
				vel[0] = normal[0] * PmCore.LADDER_JUMP_SPEED;
				vel[1] = 0;
				vel[2] = normal[2] * PmCore.LADDER_JUMP_SPEED;
				s.ladderCooldown = 5;
				s.oldJump = true;
				onGround = false;
				return;
			}
			if (!jumpHeld) s.oldJump = false;

			if (forward != 0 || rightMove != 0) {
				double[] out = PmCore.ladderVelocity(PmCore.lookVector(yaw, p.getXRot()), PmCore.rightVector(yaw),
						forward, rightMove, normal, onGround, climb);
				vel[0] = out[0];
				vel[1] = out[1];
				vel[2] = out[2];
			} else {
				vel[0] = vel[1] = vel[2] = 0;
			}
		}

		/** Ladders face away from their wall; vines/scaffolding have no facing so assume we're looking at them. */
		double[] ladderNormal() {
			BlockState st = p.level().getBlockState(p.blockPosition());
			if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				Direction d = st.getValue(BlockStateProperties.HORIZONTAL_FACING);
				return new double[] {d.getStepX(), 0, d.getStepZ()};
			}
			double[] f = PmCore.flatForward(yaw);
			if (Math.abs(f[0]) > Math.abs(f[2])) return new double[] {-Math.signum(f[0]), 0, 0};
			return new double[] {0, 0, -Math.signum(f[2])};
		}

		// ------------------------------------------------------------------ landing
		void checkFalling() {
			if (onGround && s.fallVelocity >= PmCore.PUNCH_THRESHOLD && waterLevel == 0) {
				s.punchRoll = (float) Math.min(s.fallVelocity * 0.013, 15.0);
				if (s.punchPitch > 8) s.punchPitch = 8;
			}
			if (onGround) s.fallVelocity = 0;
		}

		// ------------------------------------------------------------------ the move itself
		void doMove() {
			double bx = vel[0] * dt / upb;
			double by = vel[1] * dt / upb;
			double bz = vel[2] * dt / upb;
			if (onGround && by <= 0 && by > -GROUND_PROBE) by = -GROUND_PROBE;

			Vec3 before = p.position();
			p.setDeltaMovement(v.toBlocksPerTick(vel[0]), v.toBlocksPerTick(vel[1]), v.toBlocksPerTick(vel[2]));
			p.move(MoverType.SELF, new Vec3(bx, by, bz));
			Vec3 after = p.position();

			// Minecraft clips against axis-aligned planes: equivalent to PM_ClipVelocity with overbounce 1.
			if (Math.abs((after.x - before.x) - bx) > 1.0E-7) vel[0] = 0;
			if (Math.abs((after.z - before.z) - bz) > 1.0E-7) vel[2] = 0;
			if (p.verticalCollision) {
				double bounced = p.getDeltaMovement().y; // slime blocks bounce, everything else zeroes
				if (vel[1] < 0) vel[1] = bounced > 1.0E-4 ? v.toUnitsPerSecond(bounced) : 0;
				else if (vel[1] > 0) vel[1] = 0;
			}
			onGround = p.onGround() && vel[1] <= PmCore.ONGROUND_MAX_UPSPEED;
		}
	}
}
