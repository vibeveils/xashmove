package dev.xashmove.client.hud;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

import dev.xashmove.client.ClientState;
import dev.xashmove.client.ItemBuckets;
import dev.xashmove.client.XashWeapons;
import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;

/** Weapon menu, ammo, pickup history, damage flash, HL crosshair, cl_showfps and net_graph. */
public final class XashHudExtras {
	private record Pickup(String text, long tick) { }

	private static final Deque<Pickup> HISTORY = new ArrayDeque<>();
	private static Map<Item, Integer> lastCounts;
	private static long clientTicks;

	private XashHudExtras() {
	}

	// ------------------------------------------------------------------ ticking (pickup history)
	public static void tick(Minecraft mc) {
		clientTicks++;
		LocalPlayer p = mc.player;
		if (p == null) {
			lastCounts = null;
			HISTORY.clear();
			return;
		}
		if (!ClientState.config().bool(Cvars.HUD_HISTORY)) {
			lastCounts = null;
			return;
		}
		Inventory inv = p.getInventory();
		Map<Item, Integer> counts = new HashMap<>();
		Map<Item, ItemStack> sample = new HashMap<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			counts.merge(s.getItem(), s.getCount(), Integer::sum);
			sample.putIfAbsent(s.getItem(), s);
		}
		// Count the stack on the mouse cursor too, so shuffling items around the inventory isn't a "pickup".
		ItemStack carried = p.containerMenu.getCarried();
		if (!carried.isEmpty()) counts.merge(carried.getItem(), carried.getCount(), Integer::sum);
		if (lastCounts != null) {
			for (Map.Entry<Item, Integer> e : counts.entrySet()) {
				int before = lastCounts.getOrDefault(e.getKey(), 0);
				if (e.getValue() > before) {
					HISTORY.addFirst(new Pickup("+" + (e.getValue() - before) + " " + sample.get(e.getKey()).getHoverName().getString(), clientTicks));
				}
			}
			while (HISTORY.size() > 8) HISTORY.removeLast();
		}
		lastCounts = counts;
	}

	// ------------------------------------------------------------------ rendering
	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null) return;
		CvarStore c = ClientState.config();
		Font font = mc.font;
		int color = XashHud.hudColor();

		if (XashWeapons.menuOpen() && c.integer(Cvars.CL_WEAPON_SWITCH) == 1) weaponMenu(g, font, p, color);
		if (c.bool(Cvars.HUD_AMMO)) ammo(g, font, p, color);
		if (c.bool(Cvars.HUD_HISTORY)) history(g, font, color, c);
		if (c.bool(Cvars.HUD_SHOWFPS) && !c.bool(Cvars.HUD_NETGRAPH)) {
			String fps = mc.getFps() + " fps";
			g.text(font, fps, g.guiWidth() - font.width(fps) - 4, 4, color);
		}
		if (c.bool(Cvars.HUD_NETGRAPH)) netGraph(g, font, mc, p, color);
	}

	private static void weaponMenu(GuiGraphicsExtractor g, Font font, LocalPlayer p, int color) {
		Inventory inv = p.getInventory();
		int x = 8, y = 8, colW = 64;
		int open = XashWeapons.menuBucket();
		int highlighted = XashWeapons.highlightedSlot();
		for (int b = 0; b < ItemBuckets.COUNT; b++) {
			int bx = x + b * (colW + 4);
			boolean active = b == open;
			g.fill(bx, y, bx + 12, y + 12, active ? (color & 0x00FFFFFF) | 0xCC000000 : 0x66000000);
			g.text(font, Integer.toString(b + 1), bx + 3, y + 2, active ? 0xFF000000 : color);
			if (!active) continue;
			g.text(font, ItemBuckets.NAMES[b], bx + 15, y + 2, color);
			List<Integer> slots = XashWeapons.slotsIn(inv, b);
			int ry = y + 16;
			if (slots.isEmpty()) g.text(font, "(empty)", bx, ry, 0xFF888888);
			for (int slot : slots) {
				ItemStack s = inv.getItem(slot);
				boolean sel = slot == highlighted;
				g.fill(bx, ry - 2, bx + colW + 40, ry + 10, sel ? (color & 0x00FFFFFF) | 0x88000000 : 0x55000000);
				String name = s.getHoverName().getString();
				if (name.length() > 16) name = name.substring(0, 15) + ".";
				g.text(font, name, bx + 2, ry, sel ? 0xFFFFFFFF : color);
				ry += 13;
			}
		}
	}

	private static void ammo(GuiGraphicsExtractor g, Font font, LocalPlayer p, int color) {
		ItemStack held = p.getMainHandItem();
		if (held.isEmpty()) return;
		String text;
		if (ItemBuckets.isRanged(held) && !held.is(net.minecraft.world.item.Items.TRIDENT)) {
			int arrows = 0;
			Inventory inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack s = inv.getItem(i);
				if (s.is(ItemTags.ARROWS)) arrows += s.getCount();
			}
			String clip = held.getItem() instanceof CrossbowItem ? (CrossbowItem.isCharged(held) ? "1" : "0") + " | " : "";
			text = clip + arrows;
		} else if (held.isDamageableItem()) {
			text = (held.getMaxDamage() - held.getDamageValue()) + " / " + held.getMaxDamage();
		} else if (held.getCount() > 1 || held.getMaxStackSize() > 1) {
			text = Integer.toString(held.getCount());
		} else {
			return;
		}
		float scale = 2.0f;
		g.pose().pushMatrix();
		g.pose().scale(scale);
		int w = font.width(text);
		g.text(font, text, (int) ((g.guiWidth() - 10) / scale) - w, (int) ((g.guiHeight() - 26) / scale), color);
		g.pose().popMatrix();
	}

	private static void history(GuiGraphicsExtractor g, Font font, int color, CvarStore c) {
		long life = Math.round(c.get(Cvars.HUD_HISTORY_TIME) * 20);
		int y = g.guiHeight() / 2 + 40;
		Iterator<Pickup> it = HISTORY.iterator();
		while (it.hasNext()) {
			Pickup e = it.next();
			long age = clientTicks - e.tick();
			if (age > life) {
				it.remove();
				continue;
			}
			int alpha = (int) Math.max(40, 255 * Math.min(1.0, (life - age) / 20.0));
			g.text(font, e.text(), g.guiWidth() - font.width(e.text()) - 8, y, (color & 0x00FFFFFF) | (alpha << 24));
			y -= 11;
		}
	}

	private static void netGraph(GuiGraphicsExtractor g, Font font, Minecraft mc, LocalPlayer p, int color) {
		int ping = -1;
		if (mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
			if (info != null) ping = info.getLatency();
		}
		double upb = XashHud.unitsPerBlock();
		double ups = Math.hypot(p.getDeltaMovement().x, p.getDeltaMovement().z) * 20.0 * upb;
		String[] lines = {
				String.format(Locale.ROOT, "fps %d  ping %s", mc.getFps(), ping < 0 ? "-" : ping + "ms"),
				String.format(Locale.ROOT, "speed %d u/s  %.1f b/s", Math.round(ups), ups / upb),
				"move " + ClientState.modeDescription()
		};
		int w = 0;
		for (String l : lines) w = Math.max(w, font.width(l));
		int x = g.guiWidth() - w - 6, y = 4;
		g.fill(x - 3, y - 2, g.guiWidth() - 2, y + lines.length * 10 + 1, 0x66000000);
		for (String l : lines) {
			g.text(font, l, x, y, color);
			y += 10;
		}
	}

	public static void damageFlash(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		if (p == null || !ClientState.config().bool(Cvars.HUD_DAMAGE_FLASH) || p.hurtTime <= 0) return;
		int alpha = Math.min(120, p.hurtTime * 12);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), (alpha << 24) | 0xC00000);
	}

	/** Replaces the vanilla crosshair when hud_crosshair isn't "vanilla". */
	public static HudElement crosshair(HudElement vanilla) {
		return (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			int style = ClientState.config().integer(Cvars.HUD_CROSSHAIR);
			if (style == 0 || mc.player == null) {
				vanilla.extractRenderState(g, delta);
				return;
			}
			if (style == 3 || !mc.options.getCameraType().isFirstPerson()) return; // F1 already skips the whole HUD
			int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
			int color = XashHud.hudColor();
			if (style == 2) {
				g.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
				return;
			}
			int len = ClientState.config().integer(Cvars.HUD_CROSSHAIR_SIZE);
			int gap = 3;
			g.fill(cx - gap - len, cy, cx - gap, cy + 1, color);
			g.fill(cx + gap + 1, cy, cx + gap + 1 + len, cy + 1, color);
			g.fill(cx, cy - gap - len, cx + 1, cy - gap, color);
			g.fill(cx, cy + gap + 1, cx + 1, cy + gap + 1 + len, color);
		};
	}
}
