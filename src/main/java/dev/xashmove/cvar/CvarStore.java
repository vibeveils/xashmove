package dev.xashmove.cvar;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.xashmove.XashMove;

/** A set of cvar values (for a given set of scopes) backed by a JSON file. */
public final class CvarStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final Set<Cvar.Scope> scopes;
	private final Map<String, Double> values = new LinkedHashMap<>();

	public CvarStore(Path file, Cvar.Scope first, Cvar.Scope... rest) {
		this.file = file;
		this.scopes = EnumSet.of(first, rest);
		resetAll();
	}

	public boolean owns(Cvar c) {
		return c != null && scopes.contains(c.scope);
	}

	public double get(Cvar c) {
		Double v = values.get(c.name);
		return v != null ? v : c.def;
	}

	public boolean bool(Cvar c) {
		return get(c) != 0;
	}

	public int integer(Cvar c) {
		return (int) Math.round(get(c));
	}

	public void set(Cvar c, double value) {
		if (!owns(c)) throw new IllegalArgumentException(c.name + " is not settable here");
		values.put(c.name, c.clamp(value));
	}

	public void reset(Cvar c) {
		values.put(c.name, c.def);
	}

	public void resetAll() {
		values.clear();
		for (Cvar c : Cvars.all()) {
			if (scopes.contains(c.scope)) values.put(c.name, c.def);
		}
	}

	/** Applies a preset's movevars (only cvars this store owns). */
	public boolean applyPreset(String name) {
		Map<String, Double> p = Cvars.presets().get(name);
		if (p == null) return false;
		p.forEach((k, v) -> {
			Cvar c = Cvars.get(k);
			if (owns(c)) values.put(c.name, c.clamp(v));
		});
		return true;
	}

	/** Snapshot of the SERVER-scope (synced) movevars. */
	public Map<String, Double> movevars() {
		Map<String, Double> out = new LinkedHashMap<>();
		for (Cvar c : Cvars.all()) {
			if (c.scope == Cvar.Scope.SERVER) out.put(c.name, get(c));
		}
		return out;
	}

	public void load() {
		resetAll();
		if (!Files.exists(file)) {
			save();
			return;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonElement root = JsonParser.parseReader(r);
			if (root.isJsonObject()) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject().entrySet()) {
					Cvar c = Cvars.get(e.getKey());
					if (!owns(c) || !e.getValue().isJsonPrimitive()) continue;
					try {
						var prim = e.getValue().getAsJsonPrimitive();
						double v = prim.isNumber() ? prim.getAsDouble() : prim.isBoolean() ? (prim.getAsBoolean() ? 1 : 0) : c.parse(prim.getAsString());
						values.put(c.name, c.clamp(v));
					} catch (RuntimeException ex) {
						XashMove.LOGGER.warn("Ignoring bad value for {} in {}: {}", e.getKey(), file, ex.getMessage());
					}
				}
			}
		} catch (IOException | RuntimeException ex) {
			XashMove.LOGGER.error("Could not read {}, using defaults", file, ex);
		}
		save(); // writes any newly added cvars back
	}

	public void save() {
		JsonObject o = new JsonObject();
		for (Cvar c : Cvars.all()) {
			if (!scopes.contains(c.scope)) continue;
			double v = get(c);
			switch (c.type) {
				case BOOL -> o.addProperty(c.name, v != 0);
				case ENUM -> o.addProperty(c.name, c.format(v));
				case INT -> o.addProperty(c.name, Math.round(v));
				case FLOAT -> o.addProperty(c.name, v);
			}
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(o, w);
			}
		} catch (IOException ex) {
			XashMove.LOGGER.error("Could not write {}", file, ex);
		}
	}
}
