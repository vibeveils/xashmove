package dev.xashmove.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import dev.xashmove.server.XashServer;

/**
 * Pure mode: modded players move faster and accelerate harder than vanilla expects, so skip the
 * "moved too quickly" / "moved wrongly" checks for them (the same switch vanilla uses for the
 * disablePlayerMovementCheck game rule and for singleplayer owners).
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "shouldCheckPlayerMovement(Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
	private void xash$relaxMoveCheck(boolean elytra, CallbackInfoReturnable<Boolean> cir) {
		if (XashServer.relaxMovementCheck(this.player)) cir.setReturnValue(false);
	}
}
