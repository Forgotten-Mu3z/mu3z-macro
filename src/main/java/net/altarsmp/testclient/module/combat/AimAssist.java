package net.altarsmp.testclient.module.combat;

import java.util.Locale;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.DoubleSetting;
import net.altarsmp.testclient.module.setting.EnumSetting;
import net.altarsmp.testclient.util.Humanizer;
import net.altarsmp.testclient.util.RotationUtil;
import net.altarsmp.testclient.util.TargetUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Aim Assist: every tick picks the valid player inside the FOV cone and range that needs the smallest
 * turn; every rendered frame turns the camera toward the chosen aim point through {@code Entity.turn},
 * the same call mouse movement uses.
 *
 * <p>Humanlike: turn rate is capped at "Speed" degrees per second, slows down near the target, adds a
 * little Gaussian noise, stops inside a small dead zone and corrects pitch at reduced strength.
 * Blatant: snaps exactly onto the aim point every frame.
 */
public final class AimAssist extends Module {
	public enum AimPoint {
		HEAD("Head"), CHEST("Chest"), NEAREST("Nearest");

		private final String displayName;

		AimPoint(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}
	}

	private static final double DEAD_ZONE_DEGREES = 1.5;
	private static final double LOCK_LOG_DEGREES = 3.0;

	private final DoubleSetting fov = add(new DoubleSetting("fov", "FOV",
			"Only players inside this view cone are assisted", 90.0, 10.0, 360.0, 5.0, "°"));
	private final DoubleSetting range = add(new DoubleSetting("range", "Range",
			"Maximum distance to the target's hitbox", 4.5, 1.0, 8.0, 0.1, "m"));
	private final DoubleSetting turnSpeed = add(new DoubleSetting("turnSpeed", "Turn speed",
			"Humanlike: maximum turn rate", 120.0, 5.0, 720.0, 5.0, "°/s"));
	private final EnumSetting<AimPoint> aimPoint = add(new EnumSetting<>("aimPoint", "Aim point",
			"Where on the target to aim", AimPoint.CHEST));
	private final BooleanSetting vertical = add(new BooleanSetting("vertical", "Vertical",
			"Also correct pitch (up/down)", true));
	private final BooleanSetting whileAttacking = add(new BooleanSetting("whileAttacking", "Only while attacking",
			"Only assist while the attack button is held", false));

	private @Nullable Player target;
	private boolean lockLogged;

	public AimAssist() {
		super("aim_assist", "Aim Assist", "Smoothly turns toward the nearest player in the FOV", Kind.TOGGLE);
	}

	@Override
	public void onTick() {
		Player best = TargetUtil.bestInFov(range.get(), fov.get());
		if (best != target) {
			if (best != null) {
				log("TARGET", TargetUtil.describe(best), String.format(Locale.ROOT, "angle %.1f reach %.2f",
						RotationUtil.angleToPoint(mc().player, aimPointOf(best)), TargetUtil.reachDistance(best)));
			} else {
				log("TARGET_LOST", TargetUtil.describe(target), "");
			}
			target = best;
			lockLogged = false;
		}
	}

	@Override
	public void onFrame(double frameSeconds) {
		if (target == null || !TargetUtil.isValidTarget(target)) {
			return;
		}
		if (whileAttacking.get() && !mc().options.keyAttack.isDown()) {
			return;
		}
		LocalPlayer player = mc().player;
		float[] wanted = RotationUtil.anglesTo(player.getEyePosition(), aimPointOf(target));
		double yawDiff = Mth.wrapDegrees(wanted[0] - player.getYRot());
		double pitchDiff = vertical.get() ? wanted[1] - player.getXRot() : 0.0;
		double distance = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

		if (distance < LOCK_LOG_DEGREES && !lockLogged) {
			lockLogged = true;
			log("LOCK", TargetUtil.describe(target), String.format(Locale.ROOT, "error %.2f°", distance));
		}

		if (blatant()) {
			RotationUtil.turnBy(player, yawDiff, pitchDiff);
			return;
		}
		if (distance < DEAD_ZONE_DEGREES) {
			return;
		}
		double ease = Mth.clamp(distance / 25.0, 0.3, 1.0);
		double maxStep = turnSpeed.get() * frameSeconds * ease;
		double factor = Math.min(1.0, maxStep / distance);
		double noise = Humanizer.gaussian() * 0.05 * maxStep;
		RotationUtil.turnBy(player, yawDiff * factor + noise, pitchDiff * factor * 0.6);
	}

	private Vec3 aimPointOf(Player player) {
		return switch (aimPoint.get()) {
			case HEAD -> player.getEyePosition();
			case CHEST -> player.getBoundingBox().getCenter().add(0, player.getBbHeight() * 0.15, 0);
			case NEAREST -> RotationUtil.closestPoint(player.getBoundingBox(), mc().player.getEyePosition());
		};
	}

	@Override
	public void resetState() {
		target = null;
		lockLogged = false;
	}
}
