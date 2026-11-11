package dev.xashmove.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import dev.xashmove.XashMove;
import dev.xashmove.physics.MoveVars;
import dev.xashmove.physics.XashPlayerAccess;
import dev.xashmove.physics.XashPlayerState;
import dev.xashmove.server.XashServer;

@Mixin(Player.class)
public abstract class PlayerMixin implements XashPlayerAccess {
	@Unique
	private final XashPlayerState xash$state = new XashPlayerState();

	@Override
	public XashPlayerState xash$getState() {
		return xash$state;
	}

	/**
	 * GoldSrc has no "sneak stops you at ledges". Pure mode only, and on both sides, because the server repeats
	 * this logic when validating movement - disabling it only on the client would rubber-band you.
	 */
	@Inject(method = "maybeBackOffFromEdge(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/MoverType;)Lnet/minecraft/world/phys/Vec3;",
			at = @At("HEAD"), cancellable = true, require = 0)
	private void xash$noEdgeGuard(Vec3 movement, MoverType type, CallbackInfoReturnable<Vec3> cir) {
		MoveVars vars = XashMove.pureVarsFor((Player) (Object) this);
		if (vars != null && !vars.sneakEdgeProtection) cir.setReturnValue(movement);
	}

	@Inject(method = "causeFoodExhaustion(F)V", at = @At("HEAD"), cancellable = true, require = 0)
	private void xash$noHunger(float amount, CallbackInfo ci) {
		if (XashServer.noHunger((Player) (Object) this)) ci.cancel();
	}
}
