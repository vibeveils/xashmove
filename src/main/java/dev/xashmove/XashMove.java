package dev.xashmove;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import net.fabricmc.api.ModInitializer;

import dev.xashmove.net.XashNetworking;
import dev.xashmove.physics.MoveVars;
import dev.xashmove.server.XashServer;

public final class XashMove implements ModInitializer {
	public static final String MOD_ID = "xashmove";
	/** Bumped when the payload format changes. */
	public static final int PROTOCOL = 1;
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		XashNetworking.registerTypes();
		XashServer.init();
		LOGGER.info("Xash Movement loaded (GoldSrc pm_shared port)");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Movevars that apply to this player on the side we're running on, or null if Xash movement is off for them. */
	public static MoveVars varsFor(Player p) {
		if (p.level().isClientSide()) {
			if (!p.isLocalPlayer()) return null;
			MoveVars v = XashRuntime.clientVars();
			return v != null && v.mode != Mode.OFF ? v : null;
		}
		return XashServer.varsFor(p);
	}

	/** Pure-mode movevars for this player (both sides), or null. Used for rules the server must agree on. */
	public static MoveVars pureVarsFor(Player p) {
		MoveVars v = varsFor(p);
		return v != null && v.mode == Mode.PURE ? v : null;
	}
}
