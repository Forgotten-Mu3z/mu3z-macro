package net.altarsmp.testclient.module.utility;

import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.Chat;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.ItemUtil;
import net.altarsmp.testclient.util.ServerGuard;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Elytra Swap (action): one key press swaps the chest slot between an elytra and the best chestplate in
 * the inventory using player-inventory click packets (no screen is opened). An item in the hotbar is
 * swapped with a single number-key style SWAP click on the chest armour slot; an item in the main
 * inventory takes three clicks (pick up, place on the chest slot, put the old item back).
 * Humanlike: random delay between the clicks. Blatant: all clicks in the same tick.
 */
public final class ElytraSwap extends Module {
	private enum Stage { IDLE, PLACE_ON_CHEST, PUT_BACK }

	private final IntSetting delayMin = add(new IntSetting("delayMin", "Click delay min",
			"Humanlike: minimum delay between inventory clicks", 50, 0, 500, "ms"));
	private final IntSetting delayMax = add(new IntSetting("delayMax", "Click delay max",
			"Humanlike: maximum delay between inventory clicks", 120, 0, 500, "ms"));

	private Stage stage = Stage.IDLE;
	private final Deadline next = new Deadline();
	private int sourceMenuSlot = -1;

	public ElytraSwap() {
		super("elytra_swap", "Elytra Swap", "Key press: swap chestplate <-> elytra", Kind.ACTION);
	}

	@Override
	public void onKeyPressed() {
		LocalPlayer player = mc().player;
		if (player == null || mc().screen != null) {
			return;
		}
		if (!isEnabled()) {
			Chat.actionBar("Elytra Swap is not armed - enable it in the AltarTestClient screen");
			return;
		}
		if (!ServerGuard.isAllowed()) {
			Chat.actionBar("AltarTestClient is inactive on this server");
			return;
		}
		if (stage != Stage.IDLE || !InventoryUtil.canClickInventory()) {
			return;
		}
		boolean wantElytra = !ItemUtil.isElytra(player.getItemBySlot(EquipmentSlot.CHEST));
		int source = wantElytra ? InventoryUtil.findInventory(ItemUtil::isElytra, false) : findBestChestplate();
		if (source < 0) {
			Chat.actionBar(wantElytra ? "No elytra in inventory" : "No chestplate in inventory");
			return;
		}
		String item = wantElytra ? "elytra" : "chestplate";
		if (source < InventoryUtil.HOTBAR_SIZE) {
			InventoryUtil.swapWithHotbar(InventoryUtil.CHEST_ARMOR_MENU_SLOT, source);
			log("EQUIP_" + item.toUpperCase(java.util.Locale.ROOT), "", "swap click with hotbar slot " + source);
			return;
		}
		if (!ActionLock.tryAcquire(this)) {
			return;
		}
		sourceMenuSlot = InventoryUtil.toMenuSlot(source);
		InventoryUtil.pickup(sourceMenuSlot);
		log("CLICK_PICKUP", "", item + " from menu slot " + sourceMenuSlot);
		stage = Stage.PLACE_ON_CHEST;
		next.in(stepDelay(delayMin, delayMax));
		if (blatant()) {
			onTick();
		}
	}

	@Override
	public void onTick() {
		if (stage == Stage.IDLE) {
			return;
		}
		LocalPlayer player = mc().player;
		if (player.containerMenu != player.inventoryMenu) {
			log("ABORT", "", "another container was opened");
			reset();
			return;
		}
		if (stage == Stage.PLACE_ON_CHEST && next.passed()) {
			InventoryUtil.pickup(InventoryUtil.CHEST_ARMOR_MENU_SLOT);
			log("CLICK_CHEST_SLOT", "", "menu slot " + InventoryUtil.CHEST_ARMOR_MENU_SLOT);
			stage = Stage.PUT_BACK;
			next.in(stepDelay(delayMin, delayMax));
		}
		if (stage == Stage.PUT_BACK && next.passed()) {
			if (!player.inventoryMenu.getCarried().isEmpty()) {
				InventoryUtil.pickup(sourceMenuSlot);
				log("CLICK_PUT_BACK", "", "menu slot " + sourceMenuSlot);
			}
			reset();
		}
	}

	/** Inventory index of the best chestplate: netherite > diamond > anything else; -1 if none. */
	private int findBestChestplate() {
		LocalPlayer player = mc().player;
		int best = -1;
		int bestScore = 0;
		for (int i = 0; i < InventoryUtil.MAIN_SIZE; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (!ItemUtil.isChestplate(stack)) {
				continue;
			}
			int score = stack.is(Items.NETHERITE_CHESTPLATE) ? 3 : stack.is(Items.DIAMOND_CHESTPLATE) ? 2 : 1;
			if (score > bestScore) {
				best = i;
				bestScore = score;
			}
		}
		return best;
	}

	private void reset() {
		ActionLock.release(this);
		stage = Stage.IDLE;
		next.clear();
		sourceMenuSlot = -1;
	}

	@Override
	public void resetState() {
		LocalPlayer player = mc().player;
		if (stage != Stage.IDLE && player != null && player.containerMenu == player.inventoryMenu
				&& !player.inventoryMenu.getCarried().isEmpty() && sourceMenuSlot >= 0) {
			InventoryUtil.pickup(sourceMenuSlot);
		}
		reset();
	}
}
