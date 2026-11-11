package dev.xashmove.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;

import dev.xashmove.XashMove;
import dev.xashmove.physics.MoveContext;
import dev.xashmove.physics.MoveVars;
import dev.xashmove.physics.XashPhysics;
import dev.xashmove.server.XashServer;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
	@Shadow
	protected boolean jumping;

	public LivingEntityMixin(EntityType<?> type, Level level) {
		super(type, level);
	}

	/** The whole of PM_PlayerMove replaces vanilla travel() for the local player. */
	@Inject(method = "travel(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
	private void xash$travel(Vec3 input, CallbackInfo ci) {
		if (!((Object) this instanceof Player player) || !this.level().isClientSide()) return;
		MoveVars vars = XashMove.varsFor(player);
		if (vars == null) return;
		MoveContext ctx = new MoveContext(this.jumping, this.getBlockSpeedFactor(), this.getBlockJumpFactor(),
				this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7);
		if (XashPhysics.travel(player, input, ctx, vars)) ci.cancel();
	}

	/** Jumping happens inside PM_Jump; stop aiStep's vanilla jump from adding its own impulse. */
	@Inject(method = "jumpFromGround()V", at = @At("HEAD"), cancellable = true)
	private void xash$jumpFromGround(CallbackInfo ci) {
		if (!((Object) this instanceof Player player) || !this.level().isClientSide()) return;
		MoveVars vars = XashMove.varsFor(player);
		if (vars != null && XashPhysics.canHandle(player, vars)) ci.cancel();
	}

	@Inject(method = "jumpInLiquid(Lnet/minecraft/tags/TagKey;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void xash$jumpInLiquid(TagKey<Fluid> fluid, CallbackInfo ci) {
		if (!((Object) this instanceof Player player) || !this.level().isClientSide()) return;
		MoveVars vars = XashMove.varsFor(player);
		if (vars != null && XashPhysics.handlesLiquidJump(player, vars)) ci.cancel();
	}

	/** Sneak-to-sink is part of HL swimming (upmove), so vanilla's extra push down would double it. */
	@Inject(method = "goDownInWater()V", at = @At("HEAD"), cancellable = true, require = 0)
	private void xash$goDownInWater(CallbackInfo ci) {
		if (!((Object) this instanceof Player player) || !this.level().isClientSide()) return;
		MoveVars vars = XashMove.varsFor(player);
		if (vars != null && XashPhysics.handlesLiquidJump(player, vars)) ci.cancel();
	}

	/** sv_stepsize (pure mode only: a vanilla server re-runs step-up with its own height). Applied on both sides. */
	@Inject(method = "maxUpStep()F", at = @At("HEAD"), cancellable = true, require = 0)
	private void xash$maxUpStep(CallbackInfoReturnable<Float> cir) {
		if (!((Object) this instanceof Player player)) return;
		MoveVars vars = XashMove.pureVarsFor(player);
		if (vars != null && vars.stepsize > 0) {
			cir.setReturnValue((float) (vars.stepsize / vars.unitsPerBlock));
		}
	}

	// GoldSrc fall damage. Two descriptors because fall distance became a double in 1.21.5; whichever exists wins.
	@Inject(method = "calculateFallDamage(DF)I", at = @At("HEAD"), cancellable = true, require = 0)
	private void xash$fallDamageD(double fallDistance, float multiplier, CallbackInfoReturnable<Integer> cir) {
		if (this.level().isClientSide()) return;
		Integer dmg = XashServer.fallDamage((LivingEntity) (Object) this, fallDistance, multiplier);
		if (dmg != null) cir.setReturnValue(dmg);
	}

	@Inject(method = "calculateFallDamage(FF)I", at = @At("HEAD"), cancellable = true, require = 0)
	private void xash$fallDamageF(float fallDistance, float multiplier, CallbackInfoReturnable<Integer> cir) {
		if (this.level().isClientSide()) return;
		Integer dmg = XashServer.fallDamage((LivingEntity) (Object) this, fallDistance, multiplier);
		if (dmg != null) cir.setReturnValue(dmg);
	}
}
