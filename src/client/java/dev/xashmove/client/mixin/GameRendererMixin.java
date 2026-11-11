package dev.xashmove.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;

import dev.xashmove.client.XashView;

/**
 * Camera effects. Purely cosmetic: injectors in this config default to require = 0, so if a future
 * version renames these methods the effect just switches off.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"))
	private void xash$viewRoll(PoseStack pose, float partialTick, CallbackInfo ci) {
		XashView.apply(pose);
	}

	@Inject(method = "bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"), cancellable = true)
	private void xash$bob(PoseStack pose, float partialTick, CallbackInfo ci) {
		if (XashView.bob(pose, partialTick)) ci.cancel();
	}

	/** Zoom. Works whether getFov returns float or double (the return value is handled as a Number). */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void xash$zoom(CallbackInfoReturnable<Object> cir) {
		double mul = XashView.fovMultiplier();
		if (mul == 1.0) return;
		Object original = cir.getReturnValue();
		if (original instanceof Float f) cir.setReturnValue((float) (f * mul));
		else if (original instanceof Double d) cir.setReturnValue(d * mul);
	}
}
