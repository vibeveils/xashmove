package dev.xashmove.client.hud;

import java.util.Locale;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import dev.xashmove.Mode;
import dev.xashmove.XashMove;
import dev.xashmove.XashRuntime;
import dev.xashmove.client.ClientState;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;
import dev.xashmove.physics.MoveVars;
import dev.xashmove.physics.XashPlayerAccess;
import dev.xashmove.physics.XashPlayerState;

/** Speedometer (KZ/bhop style), mode tag and an optional Half-Life style health/armor readout. */
public final class XashHud {
	private static final int GAIN = 0xFF55FF55;
	private static final int LOSS = 0xFFFF5555;
	private static double smoothed;

	private XashHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, XashMove.id("speedometer"), XashHud::speedometer);
		HudElementRegistry.addLast(XashMove.id("hl_hud"), XashHud::hlHud);
		HudElementRegistry.addLast(XashMove.id("mode_tag"), XashHud::modeTag);

		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, XashHud::hideWhenHl);
		HudElementRegistry.replaceElement(VanillaHudElements.ARMOR_BAR, XashHud::hideWhenHl);
		HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, XashHud::hideWhenHl);
	}

	private static HudElement hideWhenHl(HudElement original) {
		return (g, dt) -> {
			CvarStore c = ClientState.config();
			if (c.bool(Cvars.HUD_HL) && c.bool(Cvars.HUD_HL_HIDE_VANILLA)) return;
			original.extractRenderState(g, dt);
		};
	}

	private static int hudColor() {
		return 0xFF000000 | (ClientState.config().integer(Cvars.HUD_COLOR) & 0xFFFFFF);
	}

	private static double unitsPerBlock() {
		MoveVars v = XashRuntime.clientVars();
		return v != null ? v.unitsPerBlock : Cvars.SV_UNITS_PER_BLOCK.def;
	}

	private static void speedometer(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		CvarStore c = ClientState.config();
		if (p == null || !c.bool(Cvars.HUD_SPEEDOMETER)) return;

		XashPlayerState s = ((XashPlayerAccess) p).xash$getState();
		double upb = unitsPerBlock();
		double ups = Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z) * 20.0 * upb;
		double previous = smoothed;
		smoothed += (ups - smoothed) * 0.5;

		int units = c.integer(Cvars.HUD_SPEEDOMETER_UNITS);
		String text = format(ups, upb, units);
		int color = hudColor();
		if (c.bool(Cvars.HUD_SPEEDOMETER_COLOR)) {
			if (smoothed > previous + 0.5) color = GAIN;
			else if (smoothed < previous - 0.5) color = LOSS;
		}

		Font font = mc.font;
		float scale = (float) c.get(Cvars.HUD_SPEEDOMETER_SCALE);
		int cx = g.guiWidth() / 2;
		int cy = g.guiHeight() / 2 + c.integer(Cvars.HUD_SPEEDOMETER_Y);

		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale);
		g.centeredText(font, text, 0, 0, color);
		if (c.bool(Cvars.HUD_JUMPSPEED) && p.tickCount - s.lastJumpTick < 60 && s.lastJumpTick > 0) {
			g.centeredText(font, "(" + format(s.lastJumpSpeed, upb, units) + ")", 0, 10, 0xFFAAAAAA);
		}
		g.pose().popMatrix();
	}

	private static String format(double ups, double upb, int units) {
		return switch (units) {
			case 1 -> String.format(Locale.ROOT, "%.2f b/s", ups / upb);
			case 2 -> String.format(Locale.ROOT, "%.1f km/h", ups / upb * 3.6);
			default -> Integer.toString((int) Math.round(ups));
		};
	}

	private static void modeTag(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !ClientState.config().bool(Cvars.HUD_MODE)) return;
		Mode m = ClientState.mode();
		if (m == Mode.OFF) return;
		String tag = m == Mode.PURE ? "XASH" : "XASH (vanilla-safe)";
		g.text(mc.font, tag, 4, g.guiHeight() - 12, (hudColor() & 0x00FFFFFF) | 0x99000000);
	}

	private static void hlHud(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || !ClientState.config().bool(Cvars.HUD_HL) || p.isSpectator()) return;

		int hp = (int) Math.ceil(p.getHealth() / Math.max(1f, p.getMaxHealth()) * 100f);
		int ap = Math.min(100, p.getArmorValue() * 5);
		int color = hp <= 25 ? 0xFFFF2020 : hudColor();

		float scale = 2.0f;
		int y = (int) ((g.guiHeight() - 26) / scale);
		g.pose().pushMatrix();
		g.pose().scale(scale);
		g.text(mc.font, "+ " + hp, 8, y, color);
		g.text(mc.font, "◆ " + ap, 52, y, hudColor());
		g.pose().popMatrix();
	}
}
