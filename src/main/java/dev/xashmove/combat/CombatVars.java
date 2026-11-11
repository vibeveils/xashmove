package dev.xashmove.combat;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import dev.xashmove.cvar.Cvar;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;

/** Snapshot of the combat cvars. Defaults equal vanilla, so {@link #VANILLA} means "no change". */
public record CombatVars(double bowDrawTime, double bowVelocity,
		double crossbowChargeTime, double crossbowVelocity,
		boolean crossbowAutoload, boolean noAttackCooldown) {

	public static final CombatVars VANILLA = of(c -> c.def);

	public static CombatVars of(ToDoubleFunction<Cvar> g) {
		return new CombatVars(
				g.applyAsDouble(Cvars.SV_BOW_DRAW_TIME), g.applyAsDouble(Cvars.SV_BOW_VELOCITY),
				g.applyAsDouble(Cvars.SV_CROSSBOW_CHARGE_TIME), g.applyAsDouble(Cvars.SV_CROSSBOW_VELOCITY),
				g.applyAsDouble(Cvars.SV_CROSSBOW_AUTOLOAD) != 0, g.applyAsDouble(Cvars.SV_ATTACK_COOLDOWN) == 1);
	}

	public static CombatVars of(CvarStore store) {
		return of(store::get);
	}

	public static CombatVars of(Map<String, Double> synced) {
		return of(c -> {
			Double v = synced.get(c.name);
			return v != null ? c.clamp(v) : c.def;
		});
	}

	/** Vanilla BowItem.getPowerForTime with the draw time stretched/compressed. */
	public float bowPower(int chargeTicks) {
		if (bowDrawTime <= 0.0001) return 1.0f;
		float f = (float) (chargeTicks / (20.0 * bowDrawTime));
		f = (f * f + f * 2.0f) / 3.0f;
		return Math.min(f, 1.0f);
	}

	/** Crossbow charge ticks, scaling vanilla's value (which already includes Quick Charge) by our charge time. */
	public int crossbowChargeTicks(int vanillaTicks) {
		return Math.max(1, (int) Math.round(vanillaTicks * (crossbowChargeTime / 1.25)));
	}
}
