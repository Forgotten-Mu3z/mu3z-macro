package net.altarsmp.testclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Player target selection shared by the combat modules. */
public final class TargetUtil {
	private TargetUtil() {
	}

	public static boolean isValidTarget(@Nullable Entity entity) {
		LocalPlayer self = Minecraft.getInstance().player;
		return entity instanceof Player player
				&& player != self
				&& player.isAlive()
				&& !player.isDeadOrDying()
				&& !player.isSpectator()
				&& !player.isRemoved();
	}

	/** The player under the crosshair, if any. */
	public static @Nullable Player crosshairPlayer() {
		HitResult hit = Minecraft.getInstance().hitResult;
		if (hit != null && hit.getType() == HitResult.Type.ENTITY
				&& ((EntityHitResult) hit).getEntity() instanceof Player player && isValidTarget(player)) {
			return player;
		}
		return null;
	}

	/** Closest valid player within {@code range} blocks (eye to nearest hitbox point). */
	public static @Nullable Player nearest(double range) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = mc.player.getEyePosition();
		Player best = null;
		double bestDist = range * range;
		for (AbstractClientPlayer player : mc.level.players()) {
			if (!isValidTarget(player)) {
				continue;
			}
			double dist = RotationUtil.closestPoint(player.getBoundingBox(), eye).distanceToSqr(eye);
			if (dist <= bestDist) {
				best = player;
				bestDist = dist;
			}
		}
		return best;
	}

	/** Valid player within range and inside the view cone that needs the smallest turn to face. */
	public static @Nullable Player bestInFov(double range, double fovDegrees) {
		Minecraft mc = Minecraft.getInstance();
		Vec3 eye = mc.player.getEyePosition();
		Player best = null;
		double bestAngle = fovDegrees / 2.0;
		for (AbstractClientPlayer player : mc.level.players()) {
			if (!isValidTarget(player)) {
				continue;
			}
			if (RotationUtil.closestPoint(player.getBoundingBox(), eye).distanceTo(eye) > range) {
				continue;
			}
			double angle = RotationUtil.angleToPoint(mc.player, player.getBoundingBox().getCenter());
			if (angle <= bestAngle) {
				best = player;
				bestAngle = angle;
			}
		}
		return best;
	}

	/** Shield is actively blocking (past the shield's raise delay). */
	public static boolean isBlockingWithShield(LivingEntity entity) {
		return entity.isBlocking();
	}

	/** Shield (any blocks_attacks item) is being raised, even before it starts blocking. */
	public static boolean isRaisingShield(LivingEntity entity) {
		return entity.isUsingItem() && entity.getUseItem().has(DataComponents.BLOCKS_ATTACKS);
	}

	public static double reachDistance(Entity target) {
		Vec3 eye = Minecraft.getInstance().player.getEyePosition();
		return RotationUtil.closestPoint(target.getBoundingBox(), eye).distanceTo(eye);
	}

	public static String describe(@Nullable Entity entity) {
		return entity == null ? "" : entity.getName().getString();
	}
}
