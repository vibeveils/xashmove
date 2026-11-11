package dev.xashmove.server;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import dev.xashmove.Mode;
import dev.xashmove.XashMove;
import dev.xashmove.combat.CombatVars;
import dev.xashmove.combat.XashCombat;
import dev.xashmove.cvar.Cvar;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;
import dev.xashmove.net.ClientStatePayload;
import dev.xashmove.net.ServerConfigPayload;
import dev.xashmove.physics.MoveVars;
import dev.xashmove.physics.PmCore;

/** Server half: owns sv_* cvars, tracks which players run Xash movement, syncs movevars, applies pure-mode rules. */
public final class XashServer {
	private static final CvarStore CONFIG = new CvarStore(
			FabricLoader.getInstance().getConfigDir().resolve("xashmove-server.json"), Cvar.Scope.SERVER, Cvar.Scope.SERVER_ONLY);
	/** Players whose client runs the mod with cl_xash 1. */
	private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
	private static volatile MinecraftServer server;
	private static volatile MoveVars vars;
	private static volatile CombatVars combat = CombatVars.VANILLA;

	private XashServer() {
	}

	public static void init() {
		CONFIG.load();
		rebuild();

		ServerLifecycleEvents.SERVER_STARTING.register(s -> {
			server = s;
			CONFIG.load();
			rebuild();
			ACTIVE.clear();
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			server = null;
			ACTIVE.clear();
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			ServerPlayer player = handler.player;
			if (ServerPlayNetworking.canSend(player, ServerConfigPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload());
			} else if (mode() == Mode.PURE && CONFIG.bool(Cvars.SV_REQUIRE_CLIENT)) {
				handler.disconnect(Component.literal(
						"This server runs pure Xash movement. Install the Xash Movement mod (Fabric, Minecraft 26.3) to join."));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
			ACTIVE.remove(handler.player.getUUID());
			XashCombat.onLeave(handler.player);
		});

		ServerPlayNetworking.registerGlobalReceiver(ClientStatePayload.TYPE, (payload, context) -> {
			UUID id = context.player().getUUID();
			if (payload.enabled() && payload.protocol() == XashMove.PROTOCOL) ACTIVE.add(id);
			else ACTIVE.remove(id);
		});

		XashCombat.init();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> XashCommands.register(dispatcher));
	}

	// ------------------------------------------------------------------ state
	public static CvarStore config() {
		return CONFIG;
	}

	public static Mode mode() {
		return Mode.byId(CONFIG.integer(Cvars.SV_XASH_MODE));
	}

	public static int activeCount() {
		return ACTIVE.size();
	}

	public static boolean isActive(Player p) {
		return mode() != Mode.OFF && ACTIVE.contains(p.getUUID());
	}

	/** Server-side movevars for this player, or null when the player isn't running Xash movement. */
	public static MoveVars varsFor(Player p) {
		return isActive(p) ? vars : null;
	}

	public static MoveVars vars() {
		return vars;
	}

	private static void rebuild() {
		vars = MoveVars.fromStore(mode(), CONFIG, null);
		combat = mode() != Mode.OFF && CONFIG.bool(Cvars.SV_COMBAT) ? CombatVars.of(CONFIG) : CombatVars.VANILLA;
	}

	public static boolean isRunning() {
		return server != null;
	}

	/** Combat tuning currently in force on this server (vanilla values when off). */
	public static CombatVars combat() {
		return combat;
	}

	/** Call after any cvar change: persists, rebuilds and re-sends movevars to all modded clients. */
	public static void changed() {
		CONFIG.save();
		rebuild();
		MinecraftServer s = server;
		if (s == null) return;
		ServerConfigPayload payload = payload();
		for (ServerPlayer player : s.getPlayerList().getPlayers()) {
			if (ServerPlayNetworking.canSend(player, ServerConfigPayload.TYPE)) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	public static void reload() {
		CONFIG.load();
		changed();
	}

	/** Used by the client's preset key in singleplayer. Must run on the server thread. */
	public static boolean applyPreset(String name) {
		if (!CONFIG.applyPreset(name)) return false;
		changed();
		return true;
	}

	private static ServerConfigPayload payload() {
		java.util.Map<String, Double> vars = CONFIG.movevars();
		if (combat == CombatVars.VANILLA) {
			// combat disabled server-side: tell clients the vanilla values so their prediction matches
			for (dev.xashmove.cvar.Cvar c : dev.xashmove.cvar.Cvars.all()) {
				if ("combat".equals(c.category())) vars.put(c.name, c.def);
			}
		}
		return new ServerConfigPayload(XashMove.PROTOCOL, mode().ordinal(), vars);
	}

	// ------------------------------------------------------------------ pure-mode rules (server side only)
	private static boolean pure() {
		return mode() == Mode.PURE;
	}

	/** Skip vanilla's "moved too quickly / wrongly" checks for this player? */
	public static boolean relaxMovementCheck(ServerPlayer p) {
		return pure() && CONFIG.bool(Cvars.SV_RELAX_MOVECHECK) && ACTIVE.contains(p.getUUID());
	}

	public static boolean noHunger(Player p) {
		return pure() && CONFIG.bool(Cvars.SV_NO_HUNGER) && !p.level().isClientSide();
	}

	/**
	 * GoldSrc fall damage. Minecraft only tracks fall distance, so the impact speed is reconstructed with the
	 * server's sv_gravity: v = sqrt(2 g h). Below 580 u/s nothing, 1024 u/s kills a 100 HP player.
	 *
	 * @return damage in Minecraft half-hearts, or null to keep vanilla
	 */
	public static Integer fallDamage(LivingEntity e, double fallDistance, float multiplier) {
		if (!pure() || !(e instanceof ServerPlayer) || !CONFIG.bool(Cvars.SV_HL_FALLDAMAGE)) return null;
		MoveVars v = vars;
		double heightUnits = fallDistance * v.unitsPerBlock;
		double impact = Math.sqrt(2.0 * Math.max(v.gravity, 1.0) * Math.max(heightUnits, 0));
		double hp = PmCore.fallDamageHP(impact);
		if (CONFIG.integer(Cvars.SV_FALLDAMAGE_MODE) == 1 && hp > 0) hp = 10; // mp_falldamage 0: flat 10
		hp *= CONFIG.get(Cvars.SV_FALLDAMAGE_SCALE);
		double dmg = hp / 100.0 * e.getMaxHealth() * multiplier;
		return (int) Math.max(0, Math.round(dmg));
	}
}
