package net.altarsmp.testclient.module.combat;

import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.EnumSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.ItemUtil;
import net.altarsmp.testclient.util.TargetUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Breach Swap: hooks every attack the client sends (manual clicks, Trigger Bot, Autoclicker) via
 * {@code MultiPlayerGameModeMixin}. Just before the attack packet it selects the best hotbar mace with
 * Breach/Density, so the held-item packet and the attack packet reach the server in the same tick and the
 * hit uses that weapon; afterwards it selects the previous slot again. Blatant: swap back in the same
 * tick, immediately after the attack packet. Humanlike: swap back after a random delay.
 */
public final class BreachSwap extends Module {
	public enum Weapon {
		BREACH("Breach mace"), DENSITY("Density mace"), EITHER("Breach or Density"), ANY_MACE("Any mace");

		private final String displayName;

		Weapon(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	private final EnumSetting<Weapon> weapon = add(new EnumSetting<>("weapon", "Weapon",
			"Which mace to swap to (highest enchantment level wins)", Weapon.BREACH));
	private final BooleanSetting playersOnly = add(new BooleanSetting("playersOnly", "Players only",
			"Only swap when attacking players", true));
	private final IntSetting restoreMin = add(new IntSetting("restoreDelayMin", "Swap-back delay min",
			"Humanlike: minimum delay before swapping back", 30, 0, 500, "ms"));
	private final IntSetting restoreMax = add(new IntSetting("restoreDelayMax", "Swap-back delay max",
			"Humanlike: maximum delay before swapping back", 90, 0, 500, "ms"));

	private final Deadline restore = new Deadline();
	private int restoreSlot = -1;
	private boolean swappedThisAttack;

	public BreachSwap() {
		super("breach_swap", "Breach Swap", "Swaps to a Breach/Density mace for the tick of each attack", Kind.TOGGLE);
	}

	/** Called right before the attack packet is sent. */
	public void beforeAttack(Entity target) {
		if (!isEnabled() || mc().player == null || (playersOnly.get() && !(target instanceof Player))
				|| !ActionLock.isFreeFor(this)) {
			return;
		}
		int slot = InventoryUtil.findBestHotbar(this::score);
		int current = InventoryUtil.selectedSlot();
		if (slot < 0 || slot == current) {
			return;
		}
		if (restoreSlot < 0) {
			restoreSlot = current;
		}
		InventoryUtil.select(slot);
		swappedThisAttack = true;
		ItemStack mace = mc().player.getInventory().getItem(slot);
		log("SWAP_TO_MACE", TargetUtil.describe(target), "slot " + current + "->" + slot
				+ " breach " + ItemUtil.breachLevel(mace) + " density " + ItemUtil.densityLevel(mace)
				+ " fall " + String.format(java.util.Locale.ROOT, "%.2f", mc().player.fallDistance));
	}

	/** Called right after the attack packet is sent. */
	public void afterAttack(Entity target) {
		if (!swappedThisAttack) {
			return;
		}
		swappedThisAttack = false;
		if (blatant()) {
			restoreNow();
		} else {
			restore.in(stepDelay(restoreMin, restoreMax));
		}
	}

	@Override
	public boolean runsWithScreenOpen() {
		return true;
	}

	@Override
	public void onTick() {
		if (restore.passed()) {
			restoreNow();
		}
	}

	private void restoreNow() {
		restore.clear();
		if (restoreSlot >= 0 && mc().player != null) {
			InventoryUtil.select(restoreSlot);
			log("SWAP_BACK", "", "slot " + restoreSlot);
		}
		restoreSlot = -1;
	}

	private int score(ItemStack stack) {
		if (!ItemUtil.isMace(stack)) {
			return 0;
		}
		int breach = ItemUtil.breachLevel(stack);
		int density = ItemUtil.densityLevel(stack);
		return switch (weapon.get()) {
			case BREACH -> breach > 0 ? 10 + breach : 0;
			case DENSITY -> density > 0 ? 10 + density : 0;
			case EITHER -> breach + density > 0 ? 10 + Math.max(breach, density) : 0;
			case ANY_MACE -> 1 + breach + density;
		};
	}

	@Override
	public void resetState() {
		swappedThisAttack = false;
		if (restoreSlot >= 0) {
			restoreNow();
		}
	}
}
