package dev.xashmove.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLScancode;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import dev.xashmove.Mode;
import dev.xashmove.XashMove;
import dev.xashmove.client.hud.XashHud;
import dev.xashmove.client.hud.XashHudExtras;
import dev.xashmove.cvar.Cvars;
import dev.xashmove.net.ServerConfigPayload;
import dev.xashmove.server.XashServer;

public final class XashMoveClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(XashMove.id("main"));
	private static KeyMapping toggleKey;
	private static KeyMapping speedometerKey;
	private static KeyMapping presetKey;
	private static KeyMapping hlHudKey;
	private static KeyMapping zoomKey;
	private static KeyMapping lastInvKey;
	private static KeyMapping menuKey;
	private static boolean openMenuNextTick;
	private static int presetIndex;

	@Override
	public void onInitializeClient() {
		ClientState.init();

		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.xashmove.toggle", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_J, CATEGORY));
		speedometerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.xashmove.speedometer", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_K, CATEGORY));
		presetKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.xashmove.preset", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_U, CATEGORY));
		hlHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.xashmove.hlhud", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_H, CATEGORY));
		zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.xashmove.zoom", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_Z, CATEGORY));
		lastInvKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.xashmove.lastinv", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_G, CATEGORY));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.xashmove.menu", InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_O, CATEGORY));

		ClientPlayNetworking.registerGlobalReceiver(ServerConfigPayload.TYPE, (payload, context) -> ClientState.onServerConfig(payload));
		// INIT fires before any play packet is handled, so it can't wipe a config the server already sent.
		ClientPlayConnectionEvents.INIT.register((listener, client) -> ClientState.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> ClientState.onDisconnect());

		ClientTickEvents.START_CLIENT_TICK.register(mc -> XashWeapons.startTick(mc, lastInvKey));
		ClientTickEvents.END_CLIENT_TICK.register(XashMoveClient::tick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> XashClientCommands.register(dispatcher));
		XashHud.register();
	}

	/** Opens the options screen on the next tick (commands run while the chat screen is still closing). */
	public static void requestMenu() {
		openMenuNextTick = true;
	}

	private static void tick(Minecraft mc) {
		XashWeapons.endTick(mc);
		XashHudExtras.tick(mc);
		boolean zoomClicked = false;
		while (zoomKey.consumeClick()) zoomClicked = true;
		XashView.tickZoom(zoomKey.isDown(), zoomClicked);
		while (menuKey.consumeClick()) openMenuNextTick = true;
		if (openMenuNextTick) {
			openMenuNextTick = false;
			mc.gui.setScreen(new XashConfigScreen(null));
		}
		while (toggleKey.consumeClick()) {
			boolean on = !ClientState.config().bool(Cvars.CL_XASH);
			ClientState.config().set(Cvars.CL_XASH, on ? 1 : 0);
			ClientState.changed();
			ClientState.message("Xash movement: " + ClientState.modeDescription());
		}
		while (speedometerKey.consumeClick()) {
			boolean on = !ClientState.config().bool(Cvars.HUD_SPEEDOMETER);
			ClientState.config().set(Cvars.HUD_SPEEDOMETER, on ? 1 : 0);
			ClientState.changed();
		}
		while (hlHudKey.consumeClick()) {
			boolean on = !ClientState.config().bool(Cvars.HUD_HL);
			ClientState.config().set(Cvars.HUD_HL, on ? 1 : 0);
			ClientState.changed();
		}
		while (presetKey.consumeClick()) {
			cyclePreset(mc);
		}
	}

	private static void cyclePreset(Minecraft mc) {
		List<String> names = new ArrayList<>(Cvars.presets().keySet());
		presetIndex = (presetIndex + 1) % names.size();
		String name = names.get(presetIndex);

		IntegratedServer sp = mc.getSingleplayerServer();
		if (mc.hasSingleplayerServer() && sp != null) {
			sp.execute(() -> XashServer.applyPreset(name));
			ClientState.message("Movement preset: " + name);
		} else if (ClientState.serverInfo() != null && ClientState.mode() != Mode.OFF) {
			ClientState.message("This server controls movement. Ask an admin: /xash preset " + name);
		} else {
			ClientState.config().applyPreset(name);
			ClientState.changed();
			ClientState.message("Local movement preset (vanilla servers): " + name);
		}
	}
}
