package net.altarsmp.testclient.module.combat;

import java.util.Locale;
import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.DoubleSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.CombatHooks;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.InventoryUtil;
import net.altarsmp.testclient.util.ItemUtil;
import net.altarsmp.testclient.util.TargetUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Stun Slam: when the target is actively blocking with a shield, select an axe and hit (the axe's
 * disable_blocking_for_seconds knocks the shield down server-side), then on the next tick select the
 * mace and hit again, then return to the original hotbar slot.
 *
 * <p>Blatant: axe hit on tick N, mace hit and swap back on tick N+1. Humanlike: a random step delay
 * is added before the mace hit (still at least one tick after the axe hit) and before the swap back.
 */
public final class StunSlam extends Module {
	private enum Stage { IDLE, MACE_HIT, RESTORE, COOLDOWN }

	private final DoubleSetting range = add(new DoubleSetting("range", "Range",
			"Max distance from your eyes to the target's hitbox", 3.0, 1.0, 6.0, 0.1, "m"));
	private final BooleanSetting crosshairOnly = add(new BooleanSetting("crosshairOnly", "Crosshair only",
			"Only trigger on the player under the crosshair (otherwise the nearest player in range)", true));
	private final IntSetting delayMin = add(new IntSetting("delayMin", "Step delay min",
			"Humanlike: minimum extra delay before each step", 30, 0, 500, "ms"));
	private final IntSetting delayMax = add(new IntSetting("delayMax", "Step delay max",
			"Humanlike: maximum extra delay before each step", 90, 0, 500, "ms"));
	private final IntSetting cooldownMs = add(new IntSetting("cooldownMs", "Re-trigger cooldown",
			"Wait this long after a combo before starting another", 500, 0, 3000, "ms"));
	private final BooleanSetting swapBack = add(new BooleanSetting("swapBack", "Swap back",
			"Return to the originally held slot afterwards", true));

	private Stage stage = Stage.IDLE;
	private final Deadline next = new Deadline();
	private @Nullable Player target;
	private int originalSlot = -1;

	public StunSlam() {
		super("stun_slam", "Stun Slam", "Axe hit to disable a raised shield, mace hit next tick, swap back", Kind.TOGGLE);
	}

	@Override
	public void onTick() {
		switch (stage) {
			case IDLE -> tryStart();
			case MACE_HIT -> {
				if (next.passed()) {
					maceHit();
				}
			}
			case RESTORE -> {
				if (next.passed()) {
					finish();
				}
			}
			case COOLDOWN -> {
				if (next.passed()) {
					stage = Stage.IDLE;
				}
			}
		}
	}

	private void tryStart() {
		if (!ActionLock.isFreeFor(this)) {
			return;
		}
		Player candidate = crosshairOnly.get() ? TargetUtil.crosshairPlayer() : TargetUtil.nearest(range.get());
		if (candidate == null || !TargetUtil.isBlockingWithShield(candidate) || TargetUtil.reachDistance(candidate) > range.get()) {
			return;
		}
		int axeSlot = InventoryUtil.findHotbar(ItemUtil::isShieldDisabler);
		int maceSlot = InventoryUtil.findHotbar(ItemUtil::isMace);
		if (axeSlot < 0 || maceSlot < 0 || !ActionLock.tryAcquire(this)) {
			return;
		}
		target = candidate;
		originalSlot = InventoryUtil.selectedSlot();
		InventoryUtil.select(axeSlot);
		CombatHooks.attack(candidate, false);
		log("AXE_HIT", TargetUtil.describe(candidate), String.format(Locale.ROOT, "slot %d->%d reach %.2f",
				originalSlot, axeSlot, TargetUtil.reachDistance(candidate)));
		stage = Stage.MACE_HIT;
		next.in(stepDelay(delayMin, delayMax), 1);
	}

	private void maceHit() {
		int maceSlot = InventoryUtil.findHotbar(ItemUtil::isMace);
		if (target == null || !TargetUtil.isValidTarget(target) || maceSlot < 0
				|| TargetUtil.reachDistance(target) > range.get() + 0.5) {
			log("ABORT", TargetUtil.describe(target), "target lost or mace missing");
			finish();
			return;
		}
		LocalPlayer player = mc().player;
		InventoryUtil.select(maceSlot);
		CombatHooks.attack(target, false);
		log("MACE_HIT", TargetUtil.describe(target), String.format(Locale.ROOT, "slot %d fall %.2f shieldUp %b",
				maceSlot, player.fallDistance, TargetUtil.isBlockingWithShield(target)));
		if (blatant()) {
			finish();
		} else {
			stage = Stage.RESTORE;
			next.in(stepDelay(delayMin, delayMax));
		}
	}

	private void finish() {
		if (swapBack.get() && originalSlot >= 0 && originalSlot != InventoryUtil.selectedSlot()) {
			InventoryUtil.select(originalSlot);
			log("SWAP_BACK", TargetUtil.describe(target), "slot " + originalSlot);
		}
		ActionLock.release(this);
		target = null;
		originalSlot = -1;
		stage = Stage.COOLDOWN;
		next.in(cooldownMs.get());
	}

	@Override
	public void resetState() {
		if ((stage == Stage.MACE_HIT || stage == Stage.RESTORE) && swapBack.get() && originalSlot >= 0 && mc().player != null) {
			InventoryUtil.select(originalSlot);
		}
		ActionLock.release(this);
		stage = Stage.IDLE;
		next.clear();
		target = null;
		originalSlot = -1;
	}
}
