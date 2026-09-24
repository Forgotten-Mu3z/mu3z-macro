package net.altarsmp.testclient.module.utility;

import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.Chat;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.ServerGuard;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pearl Macro (action): one key press selects an ender pearl from the hotbar, throws it with a normal
 * use-item packet, and selects the previous slot again. If the offhand already holds pearls it throws from
 * the offhand without swapping. Humanlike: random delay between select, throw and swap back. Blatant:
 * select, throw and swap back in the same tick. The module must be enabled (armed) for the key to work.
 */
public final class PearlMacro extends Module {
	private enum Stage { IDLE, THROW, RESTORE }

	private final IntSetting delayMin = add(new IntSetting("delayMin", "Step delay min",
			"Humanlike: minimum delay between select, throw and swap back", 40, 0, 500, "ms"));
	private final IntSetting delayMax = add(new IntSetting("delayMax", "Step delay max",
			"Humanlike: maximum delay between select, throw and swap back", 100, 0, 500, "ms"));
	private final BooleanSetting swapBack = add(new BooleanSetting("swapBack", "Swap back",
			"Return to the previously held slot after throwing", true));

	private Stage stage = Stage.IDLE;
	private final Deadline next = new Deadline();
	private int originalSlot = -1;

	public PearlMacro() {
		super("pearl_macro", "Pearl Macro", "Key press: swap to pearl, throw, swap back", Kind.ACTION);
	}

	@Override
	public void onKeyPressed() {
		LocalPlayer player = mc().player;
		if (player == null || mc().screen != null) {
			return;
		}
		if (!isEnabled()) {
			Chat.actionBar("Pearl Macro is not armed - enable it in the AltarTestClient screen");
			return;
		}
		if (!ServerGuard.isAllowed()) {
			Chat.actionBar("AltarTestClient is inactive on this server");
			return;
		}
		if (stage != Stage.IDLE) {
			return;
		}
		if (player.getOffhandItem().is(Items.ENDER_PEARL)) {
			if (!player.getCooldowns().isOnCooldown(player.getOffhandItem())) {
				throwPearl(InteractionHand.OFF_HAND);
			}
			return;
		}
		int slot = InventoryUtil.findHotbar(stack -> stack.is(Items.ENDER_PEARL));
		if (slot < 0) {
			Chat.actionBar("No ender pearl in hotbar");
			return;
		}
		ItemStack pearls = player.getInventory().getItem(slot);
		if (player.getCooldowns().isOnCooldown(pearls)) {
			Chat.actionBar("Ender pearl is on cooldown");
			return;
		}
		if (!ActionLock.tryAcquire(this)) {
			return;
		}
		originalSlot = InventoryUtil.selectedSlot();
		InventoryUtil.select(slot);
		log("SELECT_PEARL", "", "slot " + originalSlot + "->" + slot);
		stage = Stage.THROW;
		next.in(stepDelay(delayMin, delayMax));
		if (blatant()) {
			onTick();
		}
	}

	@Override
	public void onTick() {
		if (stage == Stage.THROW && next.passed()) {
			throwPearl(InteractionHand.MAIN_HAND);
			if (swapBack.get() && originalSlot >= 0) {
				stage = Stage.RESTORE;
				next.in(stepDelay(delayMin, delayMax));
			} else {
				reset();
				return;
			}
		}
		if (stage == Stage.RESTORE && next.passed()) {
			InventoryUtil.select(originalSlot);
			log("SWAP_BACK", "", "slot " + originalSlot);
			reset();
		}
	}

	private void throwPearl(InteractionHand hand) {
		LocalPlayer player = mc().player;
		InteractionResult result = mc().gameMode.useItem(player, hand);
		if (result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.CLIENT) {
			player.swing(hand);
		}
		log("THROW_PEARL", "", "hand " + hand + " result " + result.getClass().getSimpleName()
				+ String.format(java.util.Locale.ROOT, " yaw %.1f pitch %.1f", player.getYRot(), player.getXRot()));
	}

	private void reset() {
		ActionLock.release(this);
		stage = Stage.IDLE;
		next.clear();
		originalSlot = -1;
	}

	@Override
	public void resetState() {
		if (stage != Stage.IDLE && swapBack.get() && originalSlot >= 0 && mc().player != null) {
			InventoryUtil.select(originalSlot);
		}
		reset();
	}
}
