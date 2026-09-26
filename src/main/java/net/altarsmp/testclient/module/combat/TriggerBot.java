package net.altarsmp.testclient.module.combat;

import java.util.Locale;
import net.altarsmp.testclient.mixin.LivingEntityAccessor;
import net.altarsmp.testclient.mixin.MinecraftInvoker;
import net.altarsmp.testclient.module.ActionLock;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.DoubleSetting;
import net.altarsmp.testclient.module.setting.EnumSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.util.CombatHooks;
import net.altarsmp.testclient.util.Deadline;
import net.altarsmp.testclient.util.Humanizer;
import net.altarsmp.testclient.util.ServerClock;
import net.altarsmp.testclient.util.TargetUtil;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Trigger Bot: when the crosshair is on a valid player and the held weapon is ready, performs a normal
 * vanilla left click (same code path as the mouse, so reach and miss rules apply).
 *
 * <p>Timing decides when the weapon counts as ready:
 * <ul>
 * <li>PERFECT: the exact tick the server will score the hit as fully charged for the weapon in hand. The
 * weapon's cooldown comes from its attack-speed attribute (sword 12.5 ticks, axe 20, mace 33.3, ...) and the
 * server counts a hit as full once {@code ticks + 0.5 >= cooldown}. With ping compensation on, the tick
 * count is scaled by the measured server TPS and padded by one or two ticks when packet jitter is high, so
 * the hit never reaches the server a tick early. A steady ping needs no correction: the server's cooldown
 * clock starts (at our previous hit) and ends (at this hit) with the same one-way delay as ours.</li>
 * <li>COOLDOWN: when the client-side cooldown indicator reaches the Min cooldown fraction.</li>
 * </ul>
 * Speed: humanlike adds a reaction delay (min/max ms, +/- jitter) once the weapon is ready and the target is
 * under the crosshair; blatant clicks on that same tick.
 *
 * <p>Unshield: if you are holding a shield up (right mouse) when it is time to hit, it lowers the shield,
 * attacks and raises it again: all in one tick when blatant, with short random gaps when humanlike. The
 * shield only goes back up if you are still holding the use key.
 */
public final class TriggerBot extends Module {
	public enum Timing {
		PERFECT("Perfect (weapon + ping)"), COOLDOWN("Cooldown %");

		private final String displayName;

		Timing(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	private enum Stage { IDLE, ATTACK, RESHIELD }

	/** Cooldown fraction treated as "full": absorbs float rounding (a sword's cooldown is 12.500001 ticks). */
	private static final float FULL_EPSILON = 1.0E-3F;
	/** Right-click delay held while unshielded, so vanilla does not raise the shield before we do. */
	private static final int HOLD_SHIELD_TICKS = 40;
	/** Vanilla's right-click delay after using an item. */
	private static final int VANILLA_USE_DELAY = 4;

	private final EnumSetting<Timing> timing = add(new EnumSetting<>("timing", "Timing",
			"Perfect: exact server-side full-charge tick for the held weapon. Cooldown %: the Min cooldown threshold",
			Timing.PERFECT));
	private final BooleanSetting pingCompensation = add(new BooleanSetting("pingCompensation", "Ping compensation",
			"Perfect timing: adjust for measured server TPS and ping jitter", true));
	private final DoubleSetting cooldown = add(new DoubleSetting("cooldown", "Min cooldown",
			"Cooldown % timing: attack when the cooldown is at least this full (1.0 = fully charged)", 1.0, 0.5, 1.0, 0.05, ""));
	private final IntSetting delayMin = add(new IntSetting("delayMin", "Delay min",
			"Humanlike: minimum reaction delay", 60, 0, 1000, "ms"));
	private final IntSetting delayMax = add(new IntSetting("delayMax", "Delay max",
			"Humanlike: maximum reaction delay", 140, 0, 1000, "ms"));
	private final IntSetting jitter = add(new IntSetting("jitter", "Jitter",
			"Humanlike: extra uniform random +/- added to each delay", 25, 0, 200, "ms"));
	private final BooleanSetting unshield = add(new BooleanSetting("unshield", "Unshield to hit",
			"While blocking with a shield: lower it, attack, raise it again", true));
	private final IntSetting shieldDelayMin = add(new IntSetting("shieldDelayMin", "Unshield gap min",
			"Humanlike: minimum gap between lowering, hitting and raising", 30, 0, 300, "ms"));
	private final IntSetting shieldDelayMax = add(new IntSetting("shieldDelayMax", "Unshield gap max",
			"Humanlike: maximum gap between lowering, hitting and raising", 80, 0, 300, "ms"));

	private final Deadline pending = new Deadline();
	private final Deadline next = new Deadline();
	private @Nullable Player pendingTarget;
	private long pendingDelay;
	private Stage stage = Stage.IDLE;
	private InteractionHand shieldHand = InteractionHand.OFF_HAND;

	public TriggerBot() {
		super("trigger_bot", "Trigger Bot", "Attacks the player under the crosshair when the weapon is ready", Kind.TOGGLE);
	}

	/** Snapshot of the weapon-readiness calculation, kept for the log line. */
	private record Readiness(boolean ready, float cooldownTicks, int requiredTicks, int ticker) {
	}

	@Override
	public void onTick() {
		switch (stage) {
			case ATTACK -> {
				if (next.passed()) {
					attack();
				}
			}
			case RESHIELD -> {
				if (next.passed()) {
					reshield();
				}
			}
			case IDLE -> evaluate();
		}
	}

	private void evaluate() {
		LocalPlayer player = mc().player;
		Player target = TargetUtil.crosshairPlayer();
		boolean shielding = isShielding(player);
		boolean handsBusy = player.isUsingItem() && !(shielding && unshield.get());
		Readiness readiness = readiness(player);
		if (target == null || handsBusy || !ActionLock.isFreeFor(this) || !readiness.ready()) {
			clearPending();
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
		clearPending();
		if (shielding) {
			if (!ActionLock.tryAcquire(this)) {
				return;
			}
			shieldHand = player.getUsedItemHand();
			((MinecraftInvoker) mc()).altar$setRightClickDelay(HOLD_SHIELD_TICKS);
			mc().gameMode.releaseUsingItem(player);
			log("UNSHIELD", TargetUtil.describe(target), "hand " + shieldHand);
			stage = Stage.ATTACK;
			next.in(stepDelay(shieldDelayMin, shieldDelayMax));
			if (!next.passed()) {
				return;
			}
		}
		attack();
	}

	private void attack() {
		LocalPlayer player = mc().player;
		Player target = TargetUtil.crosshairPlayer();
		if (target != null) {
			Readiness readiness = readiness(player);
			float strength = player.getAttackStrengthScale(0.5F);
			CombatHooks.vanillaClick();
			log("ATTACK", TargetUtil.describe(target), String.format(Locale.ROOT,
					"timing %s weapon %s cooldown %.2ft ticks %d/%d strength %.3f tps %.1f jitter %.0fms ping %dms reaction %dms reach %.2f",
					timing.get().name(), player.getMainHandItem().getHoverName().getString(), readiness.cooldownTicks(),
					readiness.ticker(), readiness.requiredTicks(), strength, ServerClock.tps(), ServerClock.jitterMs(),
					ping(player), pendingDelay, TargetUtil.reachDistance(target)));
		} else {
			log("ATTACK_SKIPPED", "", "target left the crosshair while unshielding");
		}
		if (stage == Stage.ATTACK) {
			stage = Stage.RESHIELD;
			next.in(stepDelay(shieldDelayMin, shieldDelayMax));
			if (next.passed()) {
				reshield();
			}
		}
	}

	private void reshield() {
		LocalPlayer player = mc().player;
		if (mc().options.keyUse.isDown() && !player.isUsingItem()
				&& player.getItemInHand(shieldHand).has(DataComponents.BLOCKS_ATTACKS)) {
			InteractionResult result = mc().gameMode.useItem(player, shieldHand);
			if (result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.CLIENT) {
				player.swing(shieldHand);
			}
			log("RESHIELD", "", "hand " + shieldHand + " result " + result.getClass().getSimpleName());
		} else {
			log("RESHIELD_SKIPPED", "", mc().options.keyUse.isDown() ? "shield no longer in hand" : "use key released");
		}
		endShieldSequence(VANILLA_USE_DELAY);
	}

	/**
	 * PERFECT: ready once the raw cooldown tick counter reaches the tick count the server needs for a full-charge
	 * hit with the held weapon (scaled for server TPS, padded for jitter). COOLDOWN: client indicator threshold.
	 */
	private Readiness readiness(LocalPlayer player) {
		float cooldownTicks = player.getCurrentItemAttackStrengthDelay();
		int ticker = ((LivingEntityAccessor) player).altar$getAttackStrengthTicker();
		if (timing.get() == Timing.COOLDOWN) {
			boolean ready = player.getAttackStrengthScale(0.5F) >= cooldown.get().floatValue() - FULL_EPSILON;
			return new Readiness(ready, cooldownTicks, -1, ticker);
		}
		int required = pingCompensation.get()
				? perfectTicks(cooldownTicks, ServerClock.tps(), ServerClock.safetyTicks())
				: perfectTicks(cooldownTicks, 20.0, 0);
		return new Readiness(ticker >= required, cooldownTicks, required, ticker);
	}

	/**
	 * Client ticks since the last hit after which the server scores the next hit as fully charged.
	 * The server computes strength = (ticks + 0.5) / cooldown and needs &ge; 1. If the server runs slower than
	 * 20 TPS it counts fewer ticks than we do, so the count is scaled up; {@code safetyTicks} pads for jitter.
	 */
	static int perfectTicks(float cooldownTicks, double serverTps, int safetyTicks) {
		int serverTicks = (int) Math.ceil(cooldownTicks * (1.0F - FULL_EPSILON) - 0.5F);
		// Within half a tick per second of 20 TPS, measurement noise must not add a tick.
		double serverTicksPerClientTick = Math.abs(serverTps - 20.0) < 0.5 ? 1.0 : serverTps / 20.0;
		return (int) Math.ceil(serverTicks / serverTicksPerClientTick) + Math.max(0, safetyTicks);
	}

	private static boolean isShielding(LocalPlayer player) {
		return TargetUtil.isRaisingShield(player) && player.getUsedItemHand() == InteractionHand.OFF_HAND;
	}

	private static int ping(LocalPlayer player) {
		PlayerInfo info = mc().getConnection() == null ? null : mc().getConnection().getPlayerInfo(player.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	private void clearPending() {
		pending.clear();
		pendingTarget = null;
	}

	private void endShieldSequence(int rightClickDelay) {
		((MinecraftInvoker) mc()).altar$setRightClickDelay(rightClickDelay);
		ActionLock.release(this);
		stage = Stage.IDLE;
		next.clear();
	}

	@Override
	public void resetState() {
		clearPending();
		if (stage != Stage.IDLE) {
			// Let vanilla raise the shield again right away if the use key is still held.
			endShieldSequence(0);
		}
	}
}
