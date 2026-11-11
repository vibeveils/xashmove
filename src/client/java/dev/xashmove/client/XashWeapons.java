package dev.xashmove.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;

import dev.xashmove.cvar.CvarStore;
import dev.xashmove.cvar.Cvars;

/**
 * HL weapon selection: number keys 1-5 pick a bucket (pressing again cycles inside it), "lastinv" swaps back,
 * and with hud_fastswitch 0 the pick is only confirmed by attack, like the HL HUD menu.
 * Only hotbar slots are used, because that's all a vanilla server lets the client select.
 */
public final class XashWeapons {
	private static int menuBucket = -1;
	private static int pendingSlot = -1;
	private static int menuTicks;
	private static int lastSlot = -1;
	private static int previousSlot = -1;

	private XashWeapons() {
	}

	public static boolean menuOpen() {
		return menuTicks > 0;
	}

	public static int menuBucket() {
		return menuBucket;
	}

	public static int highlightedSlot() {
		Minecraft mc = Minecraft.getInstance();
		if (pendingSlot >= 0) return pendingSlot;
		return mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
	}

	/** Hotbar slots in a bucket, left to right. */
	public static List<Integer> slotsIn(Inventory inv, int bucket) {
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			if (ItemBuckets.of(inv.getItem(i)) == bucket) out.add(i);
		}
		return out;
	}

	/** START_CLIENT_TICK: runs before vanilla reads the hotbar keys, so consumed presses never reach it. */
	public static void startTick(Minecraft mc, KeyMapping lastInvKey) {
		LocalPlayer p = mc.player;
		if (p == null) return;
		CvarStore c = ClientState.config();
		Inventory inv = p.getInventory();
		boolean hl = c.integer(Cvars.CL_WEAPON_SWITCH) == 1;

		while (lastInvKey.consumeClick()) {
			if (previousSlot >= 0 && previousSlot < 9) select(inv, previousSlot);
		}

		if (hl) {
			KeyMapping[] keys = mc.options.keyHotbarSlots;
			for (int b = 0; b < ItemBuckets.COUNT && b < keys.length; b++) {
				while (keys[b].consumeClick()) bucketPressed(inv, b, c);
			}
			if (pendingSlot >= 0 && menuOpen()) {
				// HL: +attack picks the highlighted weapon instead of firing
				boolean confirmed = false;
				while (mc.options.keyAttack.consumeClick()) confirmed = true;
				if (confirmed) {
					select(inv, pendingSlot);
					closeMenu();
				}
			}
		}
	}

	/** END_CLIENT_TICK: remember slot history, pop the menu on scroll, time it out. */
	public static void endTick(Minecraft mc) {
		LocalPlayer p = mc.player;
		if (p == null) return;
		CvarStore c = ClientState.config();
		int slot = p.getInventory().getSelectedSlot();
		if (slot != lastSlot) {
			if (lastSlot >= 0) previousSlot = lastSlot;
			lastSlot = slot;
			if (c.integer(Cvars.CL_WEAPON_SWITCH) == 1 && c.bool(Cvars.HUD_WEAPON_POPUP) && pendingSlot < 0) {
				menuBucket = ItemBuckets.of(p.getInventory().getItem(slot));
				openMenu(c);
			}
		}
		if (menuTicks > 0 && --menuTicks == 0) closeMenu();
	}

	private static void bucketPressed(Inventory inv, int bucket, CvarStore c) {
		List<Integer> slots = slotsIn(inv, bucket);
		menuBucket = bucket;
		openMenu(c);
		if (slots.isEmpty()) {
			pendingSlot = -1;
			return;
		}
		int current = pendingSlot >= 0 ? pendingSlot : inv.getSelectedSlot();
		int idx = slots.indexOf(current);
		int next = slots.get((idx + 1) % slots.size()); // pressing the same number again cycles the bucket
		if (c.bool(Cvars.HUD_FASTSWITCH)) {
			select(inv, next);
			pendingSlot = -1;
		} else {
			pendingSlot = next;
		}
	}

	private static void select(Inventory inv, int slot) {
		if (slot != inv.getSelectedSlot()) inv.setSelectedSlot(slot);
	}

	private static void openMenu(CvarStore c) {
		menuTicks = (int) Math.round(c.get(Cvars.CL_WEAPON_MENU_TIME) * 20);
	}

	private static void closeMenu() {
		menuTicks = 0;
		pendingSlot = -1;
	}
}
