package dev.xashmove.client;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Sorts items into Half-Life weapon slots (the HUD's numbered columns). */
public final class ItemBuckets {
	public static final int COUNT = 5;
	public static final String[] NAMES = {"MELEE", "RANGED", "TOOLS", "ITEMS", "BLOCKS"};

	private ItemBuckets() {
	}

	/** 0-based bucket, or -1 for an empty stack. */
	public static int of(ItemStack s) {
		if (s.isEmpty()) return -1;
		Item item = s.getItem();
		if (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE)) return 0;
		if (isRanged(s)) return 1;
		if (s.is(ItemTags.PICKAXES) || s.is(ItemTags.SHOVELS) || s.is(ItemTags.HOES) || s.is(Items.SHEARS)
				|| s.is(Items.FLINT_AND_STEEL) || s.is(Items.FISHING_ROD) || s.is(Items.BRUSH)) {
			return 2;
		}
		if (item instanceof BlockItem) return 4;
		return 3;
	}

	public static boolean isRanged(ItemStack s) {
		Item item = s.getItem();
		return item instanceof BowItem || item instanceof CrossbowItem || s.is(Items.TRIDENT);
	}
}
