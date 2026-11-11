package dev.xashmove.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;

import dev.xashmove.combat.CombatVars;
import dev.xashmove.combat.XashCombat;

/** sv_crossbow_charge_time, on both sides so the client's charge animation matches the server. */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin {
	@Inject(method = "getChargeDuration(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)I",
			at = @At("RETURN"), cancellable = true, require = 0)
	private static void xash$chargeTime(ItemStack stack, LivingEntity shooter, CallbackInfoReturnable<Integer> cir) {
		if (shooter == null) return;
		CombatVars c = XashCombat.current(shooter.level().isClientSide());
		if (c.crossbowChargeTime() != 1.25) cir.setReturnValue(c.crossbowChargeTicks(cir.getReturnValue()));
	}
}
