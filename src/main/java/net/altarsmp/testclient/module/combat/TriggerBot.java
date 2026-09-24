package net.altarsmp.testclient.module.combat;

import java.util.Locale;
import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.DoubleSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.CombatHooks;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.Humanizer;
import net.altarsmp.testclient.util.TargetUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Trigger Bot: when the crosshair is on a valid player and the attack cooldown has reached the threshold,
 * performs a normal vanilla left click (same code path as the mouse, so reach and miss rules apply).
 *
 * <p>Humanlike: once both conditions are true it waits a reaction delay drawn from [min, max] ms plus a
 * uniform +/- jitter, then re-checks and clicks. Blatant: clicks on the first tick both are true.
 */
public final class TriggerBot extends Module {
	private final IntSetting delayMin = add(new IntSetting("delayMin", "Delay min",
			"Humanlike: minimum reaction delay", 60, 0, 1000, "ms"));
	private final IntSetting delayMax = add(new IntSetting("delayMax", "Delay max",
			"Humanlike: maximum reaction delay", 140, 0, 1000, "ms"));
	private final IntSetting jitter = add(new IntSetting("jitter", "Jitter",
			"Humanlike: extra uniform random +/- added to each delay", 25, 0, 200, "ms"));
	private final DoubleSetting cooldown = add(new DoubleSetting("cooldown", "Min cooldown",
			"Attack only when the attack cooldown is at least this full (1.0 = fully charged)", 1.0, 0.5, 1.0, 0.05, ""));

	private final Deadline pending = new Deadline();
	private @Nullable Player pendingTarget;
	private long pendingDelay;

	public TriggerBot() {
		super("trigger_bot", "Trigger Bot", "Attacks the player under the crosshair when the cooldown is ready", Kind.TOGGLE);
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc().player;
		Player target = TargetUtil.crosshairPlayer();
		if (target == null || player.isUsingItem() || !ActionLock.isFreeFor(this)
				|| player.getAttackStrengthScale(0.5F) < cooldown.get()) {
			resetState();
			return;
		}
		if (target != pendingTarget || !pending.armed()) {
			pendingTarget = target;
			pendingDelay = blatant() ? 0 : Math.max(0, Humanizer.delayMs(delayMin.get(), delayMax.get()) + Math.round(Humanizer.jitter(jitter.get())));
			pending.in(pendingDelay);
		}
		if (!pending.passed()) {
			return;
		}
		float strength = player.getAttackStrengthScale(0.5F);
		CombatHooks.vanillaClick();
		log("ATTACK", TargetUtil.describe(target), String.format(Locale.ROOT, "reaction %dms cooldown %.2f reach %.2f",
				pendingDelay, strength, TargetUtil.reachDistance(target)));
		resetState();
	}

	@Override
	public void resetState() {
		pending.clear();
		pendingTarget = null;
	}
}
