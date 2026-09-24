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
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Shield Breaker: when the target starts raising a shield, waits a reaction delay and selects an axe from
 * the hotbar. Optionally hits once the shield is actually blocking and the cooldown is ready (which
 * disables the shield). When the shield comes down or the target is gone it swaps back to the previous
 * slot. Humanlike: random reaction and swap-back delays. Blatant: swaps on the same tick the shield is seen.
 */
public final class ShieldBreaker extends Module {
	private enum Stage { IDLE, REACTING, HOLDING_AXE, RESTORING }

	private final DoubleSetting range = add(new DoubleSetting("range", "Range",
			"Max distance from your eyes to the target's hitbox", 4.0, 1.0, 6.0, 0.1, "m"));
	private final BooleanSetting crosshairOnly = add(new BooleanSetting("crosshairOnly", "Crosshair only",
			"Only react to the player under the crosshair (otherwise the nearest player in range)", true));
	private final IntSetting reactMin = add(new IntSetting("reactDelayMin", "Reaction min",
			"Humanlike: minimum delay before swapping to the axe", 90, 0, 1000, "ms"));
	private final IntSetting reactMax = add(new IntSetting("reactDelayMax", "Reaction max",
			"Humanlike: maximum delay before swapping to the axe", 200, 0, 1000, "ms"));
	private final BooleanSetting autoHit = add(new BooleanSetting("autoHit", "Auto hit",
			"Also hit with the axe once the shield is blocking and the cooldown is ready", false));
	private final BooleanSetting swapBack = add(new BooleanSetting("swapBack", "Swap back",
			"Return to the previous slot once the shield is down", true));

	private Stage stage = Stage.IDLE;
	private final Deadline next = new Deadline();
	private @Nullable Player target;
	private int originalSlot = -1;

	public ShieldBreaker() {
		super("shield_breaker", "Shield Breaker", "Swaps to an axe when the target raises a shield", Kind.TOGGLE);
	}

	@Override
	public void onTick() {
		if (stage == Stage.IDLE) {
			Player candidate = crosshairOnly.get() ? TargetUtil.crosshairPlayer() : TargetUtil.nearest(range.get());
			if (candidate == null || !TargetUtil.isRaisingShield(candidate) || TargetUtil.reachDistance(candidate) > range.get()
					|| !ActionLock.isFreeFor(this) || ItemUtil.isShieldDisabler(mc().player.getMainHandItem())
					|| InventoryUtil.findHotbar(ItemUtil::isShieldDisabler) < 0) {
				return;
			}
			target = candidate;
			stage = Stage.REACTING;
			next.in(stepDelay(reactMin, reactMax));
		}
		if (stage == Stage.REACTING) {
			if (target == null || !TargetUtil.isValidTarget(target) || !TargetUtil.isRaisingShield(target)) {
				reset();
				return;
			}
			if (!next.passed()) {
				return;
			}
			int axeSlot = InventoryUtil.findHotbar(ItemUtil::isShieldDisabler);
			if (axeSlot < 0 || !ActionLock.tryAcquire(this)) {
				reset();
				return;
			}
			originalSlot = InventoryUtil.selectedSlot();
			InventoryUtil.select(axeSlot);
			log("SWAP_TO_AXE", TargetUtil.describe(target), "slot " + originalSlot + "->" + axeSlot);
			stage = Stage.HOLDING_AXE;
		}
		if (stage == Stage.HOLDING_AXE) {
			boolean shieldGone = target == null || !TargetUtil.isValidTarget(target) || !TargetUtil.isRaisingShield(target)
					|| TargetUtil.reachDistance(target) > range.get() + 1.0;
			if (!shieldGone && autoHit.get() && TargetUtil.isBlockingWithShield(target)
					&& mc().player.getAttackStrengthScale(0.5F) >= 0.95F && TargetUtil.reachDistance(target) <= range.get()) {
				CombatHooks.attack(target, false);
				log("AXE_HIT", TargetUtil.describe(target), String.format(Locale.ROOT, "reach %.2f", TargetUtil.reachDistance(target)));
			}
			if (shieldGone) {
				if (swapBack.get()) {
					stage = Stage.RESTORING;
					next.in(stepDelay(reactMin, reactMax));
				} else {
					reset();
					return;
				}
			}
		}
		if (stage == Stage.RESTORING && next.passed()) {
			if (originalSlot >= 0) {
				InventoryUtil.select(originalSlot);
				log("SWAP_BACK", TargetUtil.describe(target), "slot " + originalSlot);
			}
			reset();
		}
	}

	private void reset() {
		ActionLock.release(this);
		stage = Stage.IDLE;
		next.clear();
		target = null;
		originalSlot = -1;
	}

	@Override
	public void resetState() {
		if ((stage == Stage.HOLDING_AXE || stage == Stage.RESTORING) && swapBack.get() && originalSlot >= 0 && mc().player != null) {
			InventoryUtil.select(originalSlot);
		}
		reset();
	}
}
