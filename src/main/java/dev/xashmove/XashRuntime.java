package dev.xashmove;

import dev.xashmove.combat.CombatVars;
import dev.xashmove.physics.MoveVars;

/**
 * Client-side resolved state, kept in common code so the common mixins can read it without touching client
 * classes. Written only by {@code dev.xashmove.client.ClientState} on the render thread.
 */
public final class XashRuntime {
	private static volatile MoveVars clientVars;
	private static volatile CombatVars clientCombat;

	private XashRuntime() {
	}

	public static MoveVars clientVars() {
		return clientVars;
	}

	public static Mode clientMode() {
		MoveVars v = clientVars;
		return v == null ? Mode.OFF : v.mode;
	}

	public static void setClientVars(MoveVars vars) {
		clientVars = vars;
	}

	/** Combat values synced from a modded server, or null when connected to a vanilla server. */
	public static CombatVars clientCombat() {
		return clientCombat;
	}

	public static void setClientCombat(CombatVars combat) {
		clientCombat = combat;
	}
}
