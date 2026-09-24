package net.altarsmp.testclient.module.combat;

import java.util.Locale;
import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.DoubleSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.CombatHooks;
import net.altarsmp.testclient.util.Humanizer;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Autoclicker: while the attack button is held (or always, if "Hold to click" is off) it performs vanilla
 * left clicks at the configured CPS through the normal click code path. Clicks are scheduled on a
 * nanosecond clock and executed on the client tick, so at high CPS several clicks can land in one tick,
 * just like fast physical clicking.
 *
 * <p>Humanlike: every interval is drawn from a normal distribution around 1000/CPS (spread set by
 * Randomization) with occasional longer hesitation gaps. Blatant: perfectly even intervals.
 */
public final class Autoclicker extends Module {
	private static final int MAX_CLICKS_PER_TICK = 4;

	private final DoubleSetting cps = add(new DoubleSetting("cps", "CPS",
			"Target clicks per second", 12.0, 1.0, 30.0, 0.5, ""));
	private final IntSetting randomization = add(new IntSetting("randomization", "Randomization",
			"Humanlike: interval spread as a percentage of the base interval", 20, 0, 60, "%"));
	private final BooleanSetting holdToClick = add(new BooleanSetting("holdToClick", "Hold to click",
			"Only click while the attack button is held", true));
	private final BooleanSetting ignoreBlocks = add(new BooleanSetting("ignoreBlocks", "Ignore blocks",
			"Pause while looking at a block so mining is not interrupted", true));

	private long nextClickNanos;
	private long lastClickNanos;

	public Autoclicker() {
		super("autoclicker", "Autoclicker", "Clicks at a configurable CPS with randomisation", Kind.TOGGLE);
	}

	@Override
	public void onTick() {
		HitResult hit = mc().hitResult;
		boolean active = (!holdToClick.get() || mc().options.keyAttack.isDown())
				&& !mc().player.isUsingItem()
				&& ActionLock.isFreeFor(this)
				&& !(ignoreBlocks.get() && hit != null && hit.getType() == HitResult.Type.BLOCK);
		if (!active) {
			nextClickNanos = 0;
			return;
		}
		long now = System.nanoTime();
		if (nextClickNanos == 0) {
			// The physical press already produced a vanilla click; start one interval later.
			nextClickNanos = now + intervalNanos();
			return;
		}
		int clicks = 0;
		while (now >= nextClickNanos && clicks < MAX_CLICKS_PER_TICK) {
			click(now);
			nextClickNanos += intervalNanos();
			clicks++;
		}
		if (now >= nextClickNanos) {
			nextClickNanos = now + intervalNanos();
		}
	}

	private void click(long now) {
		HitResult hit = mc().hitResult;
		String target = hit instanceof EntityHitResult entityHit ? entityHit.getEntity().getName().getString() : "";
		String type = hit == null ? "NONE" : hit.getType().name();
		double sinceLast = lastClickNanos == 0 ? -1 : (now - lastClickNanos) / 1_000_000.0;
		lastClickNanos = now;
		CombatHooks.vanillaClick();
		log("CLICK", target, String.format(Locale.ROOT, "hit %s interval %.1fms cps %.1f", type, sinceLast, cps.get()));
	}

	private long intervalNanos() {
		double baseMs = 1000.0 / cps.get();
		if (blatant()) {
			return Math.round(baseMs * 1_000_000.0);
		}
		double spread = baseMs * randomization.get() / 100.0;
		double ms = baseMs + Humanizer.gaussian() * spread / 2.0;
		if (Humanizer.jitter(1.0) > 0.94) {
			ms += baseMs * (1.0 + Math.abs(Humanizer.jitter(1.5)));
		}
		ms = Math.max(baseMs * 0.5, Math.min(baseMs * 3.0, ms));
		return Math.round(ms * 1_000_000.0);
	}

	@Override
	public void resetState() {
		nextClickNanos = 0;
		lastClickNanos = 0;
	}
}
