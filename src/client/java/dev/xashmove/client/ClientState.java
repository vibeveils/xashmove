package dev.xashmove.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

import dev.xashmove.Mode;
import dev.xashmove.XashMove;
import dev.xashmove.XashRuntime;
import dev.xashmove.cvar.Cvar;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;
import dev.xashmove.net.ClientStatePayload;
import dev.xashmove.net.ServerConfigPayload;
import dev.xashmove.physics.MoveVars;

/**
 * Decides which movement mode the local player is in:
 * <ul>
 *   <li>server sent movevars -> use them, in the server's mode (vanilla-friendly or pure)</li>
 *   <li>no mod on the server -> vanilla-friendly with the local sv_* profile (if cl_xash_vanilla_servers)</li>
 *   <li>cl_xash 0 -> off</li>
 * </ul>
 */
public final class ClientState {
	/** Client prefs (cl_*, hud_*) plus a local copy of the sv_* movevars used on vanilla servers. */
	private static final CvarStore CONFIG = new CvarStore(
			FabricLoader.getInstance().getConfigDir().resolve("xashmove-client.json"), Cvar.Scope.CLIENT, Cvar.Scope.SERVER);

	private static ServerConfigPayload serverInfo;
	private static boolean connected;

	private ClientState() {
	}

	public static void init() {
		CONFIG.load();
		recompute();
	}

	public static CvarStore config() {
		return CONFIG;
	}

	public static ServerConfigPayload serverInfo() {
		return serverInfo;
	}

	public static void onJoin() {
		connected = true;
		serverInfo = null;
		recompute();
	}

	public static void onDisconnect() {
		connected = false;
		serverInfo = null;
		recompute();
	}

	public static void onServerConfig(ServerConfigPayload payload) {
		if (payload.protocol() != XashMove.PROTOCOL) {
			XashMove.LOGGER.warn("Server uses Xash protocol {}, we have {}; treating it as a vanilla server", payload.protocol(), XashMove.PROTOCOL);
			serverInfo = null;
		} else {
			serverInfo = payload;
		}
		recompute();
		sendState();
	}

	/** Call after a client cvar changed. */
	public static void changed() {
		CONFIG.save();
		recompute();
		sendState();
	}

	public static void recompute() {
		XashRuntime.setClientVars(resolve());
	}

	private static MoveVars resolve() {
		if (!CONFIG.bool(Cvars.CL_XASH)) return null;
		ServerConfigPayload info = serverInfo;
		if (info != null) {
			Mode m = Mode.byId(info.mode());
			return m == Mode.OFF ? null : MoveVars.fromSynced(m, info.movevars(), CONFIG);
		}
		if (!CONFIG.bool(Cvars.CL_XASH_VANILLA_SERVERS)) return null;
		// No mod on the server (or main menu): vanilla-friendly with the local profile.
		return MoveVars.fromStore(Mode.VANILLA_FRIENDLY, CONFIG, CONFIG);
	}

	private static void sendState() {
		if (connected && ClientPlayNetworking.canSend(ClientStatePayload.TYPE)) {
			ClientPlayNetworking.send(new ClientStatePayload(XashMove.PROTOCOL, CONFIG.bool(Cvars.CL_XASH)));
		}
	}

	public static Mode mode() {
		return XashRuntime.clientMode();
	}

	public static String modeDescription() {
		Mode m = mode();
		if (m == Mode.OFF) return CONFIG.bool(Cvars.CL_XASH) ? "off (server)" : "off";
		boolean server = serverInfo != null;
		return switch (m) {
			case PURE -> "pure" + (Minecraft.getInstance().hasSingleplayerServer() ? " (singleplayer)" : " (server)");
			case VANILLA_FRIENDLY -> "vanilla-friendly" + (server ? " (server)" : " (client only)");
			default -> m.label;
		};
	}

	public static void message(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.displayClientMessage(Component.literal(text), true);
	}
}
