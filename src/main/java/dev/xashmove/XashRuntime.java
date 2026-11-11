package dev.xashmove;

import dev.xashmove.physics.MoveVars;

/**
 * Client-side resolved state, kept in common code so the common mixins can read it without touching client
 * classes. Written only by {@code dev.xashmove.client.ClientState} on the render thread.
 */
public final class XashRuntime {
	private static volatile MoveVars clientVars;

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
}
