package dev.xashmove.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.GameRenderer;

import dev.xashmove.client.XashView;

/**
 * Hooks the same spot vanilla uses for the hurt tilt. Purely cosmetic: injectors in this config default to
 * require = 0, so if a future snapshot renames the method the view effects just switch off.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"))
	private void xash$viewRoll(PoseStack pose, float partialTick, CallbackInfo ci) {
		XashView.apply(pose);
	}
}
