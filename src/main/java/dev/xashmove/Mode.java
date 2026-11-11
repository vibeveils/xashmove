package dev.xashmove;

/**
 * How Xash movement is running for a given connection.
 *
 * <ul>
 *   <li>{@link #OFF}: vanilla movement.</li>
 *   <li>{@link #VANILLA_FRIENDLY}: GoldSrc movement simulated on the client only, clamped so that a vanilla
 *       (or Paper/Spigot) server's movement checks never see anything it would reject. No server mod needed.</li>
 *   <li>{@link #PURE}: the server runs this mod, owns all movevars, relaxes its movement checks for modded
 *       players and applies Xash gameplay rules (HL fall damage, step size, no sneak edge-guard, ...).</li>
 * </ul>
 */
public enum Mode {
	OFF("off"),
	VANILLA_FRIENDLY("vanilla"),
	PURE("pure");

	public final String label;

	Mode(String label) {
		this.label = label;
	}

	public static Mode byId(int id) {
		Mode[] values = values();
		return id >= 0 && id < values.length ? values[id] : OFF;
	}
}
