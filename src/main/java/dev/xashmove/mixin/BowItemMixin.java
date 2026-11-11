package dev.xashmove.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.item.BowItem;

import dev.xashmove.combat.CombatVars;
import dev.xashmove.combat.XashCombat;

/** sv_bow_draw_time. Static, so it uses the server's values when a server is running in this JVM. */
@Mixin(BowItem.class)
public abstract class BowItemMixin {
	@Inject(method = "getPowerForTime(I)F", at = @At("HEAD"), cancellable = true, require = 0)
	private static void xash$bowPower(int charge, CallbackInfoReturnable<Float> cir) {
		CombatVars c = XashCombat.current(false);
		if (c.bowDrawTime() != 1.0) cir.setReturnValue(c.bowPower(charge));
	}
}
