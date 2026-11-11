package dev.xashmove.combat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import dev.xashmove.XashMove;
import dev.xashmove.XashRuntime;
import dev.xashmove.server.XashServer;

/**
 * Server-side combat tuning. Hooks are deliberately "outside" the item code where possible (arrow spawn,
 * attribute modifiers, a reload timer) so they survive Minecraft refactors.
 */
public final class XashCombat {
	private static final Identifier NO_COOLDOWN = XashMove.id("no_attack_cooldown");
	/** Player -> server tick at which their crossbow auto-reloads. */
	private static final Map<UUID, Long> RELOADS = new ConcurrentHashMap<>();
	private static long ticks;

	private XashCombat() {
	}

	/** Values for whichever side is asking: the running server's config, else what the server synced to us. */
	public static CombatVars current(boolean clientSide) {
		if (!clientSide && XashServer.isRunning()) return XashServer.combat();
		if (clientSide && XashRuntime.clientCombat() != null) return XashRuntime.clientCombat();
		return XashServer.isRunning() ? XashServer.combat() : CombatVars.VANILLA;
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> onEntityLoad(entity));
		ServerTickEvents.END_SERVER_TICK.register(XashCombat::tick);
	}

	private static void onEntityLoad(Entity entity) {
		if (entity.getType() != EntityTypes.ARROW && entity.getType() != EntityTypes.SPECTRAL_ARROW) return;
		if (!(entity instanceof Projectile arrow) || !(arrow.getOwner() instanceof ServerPlayer player)) return;
		CombatVars c = XashServer.combat();
		if (c == CombatVars.VANILLA) return;
		// Only arrows that were just fired: still next to a shooter holding a bow/crossbow. This skips arrows
		// re-loaded with their chunk, so their speed isn't scaled twice.
		if (arrow.tickCount > 1 || arrow.distanceToSqr(player) > 9.0) return;
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		if (!isBow(main) && !isCrossbow(main) && !isBow(off) && !isCrossbow(off)) return;

		boolean crossbow = isCrossbow(main) || (!isBow(main) && isCrossbow(off));
		double vel = crossbow ? c.crossbowVelocity() : c.bowVelocity();
		// Arrow damage in Minecraft scales with impact speed, so faster arrows also hit harder.
		if (vel != 1.0) arrow.setDeltaMovement(arrow.getDeltaMovement().scale(vel));

		if (crossbow && c.crossbowAutoload()) {
			int delay = c.crossbowChargeTicks(25);
			RELOADS.put(player.getUUID(), ticks + delay);
		}
	}

	private static boolean isBow(ItemStack s) {
		return s.getItem() instanceof BowItem;
	}

	private static boolean isCrossbow(ItemStack s) {
		return s.getItem() instanceof CrossbowItem;
	}

	private static void tick(MinecraftServer server) {
		ticks++;
		CombatVars c = XashServer.combat();
		boolean everySecond = ticks % 20 == 0;
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (everySecond) applyCooldownModifier(p, c.noAttackCooldown());
			Long due = RELOADS.get(p.getUUID());
			if (due != null && ticks >= due) {
				RELOADS.remove(p.getUUID());
				autoload(p);
			}
		}
	}

	private static void applyCooldownModifier(ServerPlayer p, boolean noCooldown) {
		AttributeInstance speed = p.getAttribute(Attributes.ATTACK_SPEED);
		if (speed == null) return;
		boolean has = speed.hasModifier(NO_COOLDOWN);
		if (noCooldown && !has) {
			speed.addTransientModifier(new AttributeModifier(NO_COOLDOWN, 1000.0, AttributeModifier.Operation.ADD_VALUE));
		} else if (!noCooldown && has) {
			speed.removeModifier(NO_COOLDOWN);
		}
	}

	/** HL crossbow: reload by itself after firing, if you have ammo. */
	private static void autoload(ServerPlayer p) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack bow = p.getItemInHand(hand);
			if (!isCrossbow(bow) || CrossbowItem.isCharged(bow)) continue;
			ItemStack ammo = p.getProjectile(bow);
			if (ammo.isEmpty()) return;
			ItemStack one = ammo.copyWithCount(1);
			if (!p.getAbilities().instabuild) ammo.shrink(1);
			bow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(ItemStackTemplate.fromNonEmptyStack(one)));
			return;
		}
	}

	/** Vanilla clients aren't told about our attribute until it changes; nothing else to clean up on leave. */
	public static void onLeave(Player p) {
		RELOADS.remove(p.getUUID());
	}
}
