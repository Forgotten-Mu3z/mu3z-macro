package net.altarsmp.testclient.util;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.altarsmp.testclient.mixin.MultiPlayerGameModeInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

/**
 * Hotbar selection and inventory clicks. Inventory indices are {@link Inventory} indices (0-8 hotbar,
 * 9-35 main); "menu slots" are {@link InventoryMenu} slot numbers used by click packets.
 */
public final class InventoryUtil {
	public static final int HOTBAR_SIZE = Inventory.SELECTION_SIZE;
	public static final int MAIN_SIZE = Inventory.INVENTORY_SIZE;
	public static final int CHEST_ARMOR_MENU_SLOT = InventoryMenu.ARMOR_SLOT_START + 1;

	private InventoryUtil() {
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static int selectedSlot() {
		return mc().player.getInventory().getSelectedSlot();
	}

	/** Hotbar slot matching the predicate, preferring the currently selected slot; -1 if none. */
	public static int findHotbar(Predicate<ItemStack> predicate) {
		Inventory inventory = mc().player.getInventory();
		int selected = inventory.getSelectedSlot();
		if (predicate.test(inventory.getItem(selected))) {
			return selected;
		}
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			if (predicate.test(inventory.getItem(i))) {
				return i;
			}
		}
		return -1;
	}

	/** Hotbar slot whose item scores highest (score must be > 0); -1 if none. */
	public static int findBestHotbar(ToIntFunction<ItemStack> score) {
		Inventory inventory = mc().player.getInventory();
		int best = -1;
		int bestScore = 0;
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			int s = score.applyAsInt(inventory.getItem(i));
			if (s > bestScore) {
				best = i;
				bestScore = s;
			}
		}
		return best;
	}

	/** Inventory index (0-35) matching the predicate; searches main inventory first if requested. */
	public static int findInventory(Predicate<ItemStack> predicate, boolean mainFirst) {
		Inventory inventory = mc().player.getInventory();
		if (mainFirst) {
			for (int i = HOTBAR_SIZE; i < MAIN_SIZE; i++) {
				if (predicate.test(inventory.getItem(i))) {
					return i;
				}
			}
			for (int i = 0; i < HOTBAR_SIZE; i++) {
				if (predicate.test(inventory.getItem(i))) {
					return i;
				}
			}
		} else {
			for (int i = 0; i < MAIN_SIZE; i++) {
				if (predicate.test(inventory.getItem(i))) {
					return i;
				}
			}
		}
		return -1;
	}

	/** Selects a hotbar slot and immediately sends the held-item packet (as vanilla does before an action). */
	public static void select(int hotbarSlot) {
		Minecraft mc = mc();
		if (hotbarSlot < 0 || hotbarSlot >= HOTBAR_SIZE || mc.player == null || mc.gameMode == null) {
			return;
		}
		mc.player.getInventory().setSelectedSlot(hotbarSlot);
		((MultiPlayerGameModeInvoker) mc.gameMode).altar$ensureHasSentCarriedItem();
	}

	/** True when inventory clicks can be sent: no other container open and nothing on the cursor. */
	public static boolean canClickInventory() {
		LocalPlayer player = mc().player;
		return player != null && player.containerMenu == player.inventoryMenu
				&& player.inventoryMenu.getCarried().isEmpty();
	}

	public static int toMenuSlot(int inventoryIndex) {
		return inventoryIndex < HOTBAR_SIZE ? InventoryMenu.USE_ROW_SLOT_START + inventoryIndex : inventoryIndex;
	}

	/** Swaps an inventory item with the offhand (same as pressing F over it in the inventory screen). */
	public static void swapWithOffhand(int inventoryIndex) {
		click(toMenuSlot(inventoryIndex), Inventory.SLOT_OFFHAND, ClickType.SWAP);
	}

	/** Swaps a menu slot with a hotbar slot (same as pressing a number key over it). */
	public static void swapWithHotbar(int menuSlot, int hotbarSlot) {
		click(menuSlot, hotbarSlot, ClickType.SWAP);
	}

	/** Left-click pickup/place on a menu slot. */
	public static void pickup(int menuSlot) {
		click(menuSlot, 0, ClickType.PICKUP);
	}

	private static void click(int menuSlot, int button, ClickType type) {
		Minecraft mc = mc();
		mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, menuSlot, button, type, mc.player);
	}
}
