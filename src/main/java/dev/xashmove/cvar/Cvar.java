package dev.xashmove.cvar;

import java.util.Locale;

/**
 * A console variable in the Xash/GoldSrc sense: every value is a number (booleans are 0/1, enums are indices),
 * which keeps syncing, presets and JSON storage trivial.
 */
public final class Cvar {
	public enum Type { FLOAT, INT, BOOL, ENUM }

	public enum Scope {
		/** Movement variable owned by the server (FCVAR_MOVEVARS). Clients keep a local copy for vanilla servers. */
		SERVER,
		/** Server-only rule, never sent to clients (fall damage, hunger, kicks...). */
		SERVER_ONLY,
		/** Client preference (input speeds, HUD, view effects). */
		CLIENT
	}

	public final String name;
	public final Type type;
	public final Scope scope;
	public final double def;
	public final double min;
	public final double max;
	public final String description;
	public final String[] labels;

	Cvar(String name, Type type, Scope scope, double def, double min, double max, String description, String... labels) {
		this.name = name;
		this.type = type;
		this.scope = scope;
		this.def = def;
		this.min = min;
		this.max = max;
		this.description = description;
		this.labels = labels;
	}

	public double clamp(double value) {
		if (Double.isNaN(value)) return def;
		double v = Math.max(min, Math.min(max, value));
		if (type != Type.FLOAT) v = Math.round(v);
		return v;
	}

	/** Parses "1", "0.5", "on", "true", "pure", ... into a numeric value. Throws IllegalArgumentException. */
	public double parse(String raw) {
		String s = raw.trim().toLowerCase(Locale.ROOT);
		if (type == Type.ENUM) {
			for (int i = 0; i < labels.length; i++) {
				if (labels[i].equals(s)) return i;
			}
		}
		if (type == Type.BOOL) {
			switch (s) {
				case "on", "true", "yes", "enable", "enabled" -> { return 1; }
				case "off", "false", "no", "disable", "disabled" -> { return 0; }
				default -> { }
			}
		}
		try {
			if (s.startsWith("0x")) return Long.parseLong(s.substring(2), 16);
			if (s.startsWith("#")) return Long.parseLong(s.substring(1), 16);
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Bad value '" + raw + "' for " + name + " (" + describeRange() + ")");
		}
	}

	public String format(double value) {
		return switch (type) {
			case BOOL -> value != 0 ? "1" : "0";
			case INT -> Long.toString(Math.round(value));
			case ENUM -> {
				int i = (int) Math.round(value);
				yield i >= 0 && i < labels.length ? labels[i] : Integer.toString(i);
			}
			case FLOAT -> {
				if (value == Math.rint(value) && Math.abs(value) < 1e9) yield Long.toString((long) value);
				yield new java.math.BigDecimal(value).round(new java.math.MathContext(5)).stripTrailingZeros().toPlainString();
			}
		};
	}

	public String describeRange() {
		return switch (type) {
			case BOOL -> "0/1";
			case ENUM -> String.join("|", labels);
			default -> format(min) + " .. " + format(max);
		};
	}
}
