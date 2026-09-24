package net.altarsmp.testclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Yaw/pitch maths. Minecraft yaw: 0 = +Z (south), 90 = -X; pitch: -90 up, +90 down. */
public final class RotationUtil {
	/** {@link net.minecraft.world.entity.Entity#turn} multiplies its inputs by this. */
	private static final double TURN_SCALE = 0.15;

	private RotationUtil() {
	}

	/** {yaw, pitch} needed to look from {@code from} at {@code to}. */
	public static float[] anglesTo(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		float pitch = (float) -(Mth.atan2(dy, horizontal) * Mth.RAD_TO_DEG);
		return new float[] {Mth.wrapDegrees(yaw), Mth.clamp(pitch, -90.0F, 90.0F)};
	}

	/** Angle in degrees between the player's look direction and the direction to {@code point}. */
	public static double angleToPoint(LocalPlayer player, Vec3 point) {
		float[] target = anglesTo(player.getEyePosition(), point);
		float yawDiff = Mth.wrapDegrees(target[0] - player.getYRot());
		float pitchDiff = target[1] - player.getXRot();
		return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
	}

	public static Vec3 closestPoint(AABB box, Vec3 point) {
		return new Vec3(
				Mth.clamp(point.x, box.minX, box.maxX),
				Mth.clamp(point.y, box.minY, box.maxY),
				Mth.clamp(point.z, box.minZ, box.maxZ));
	}

	/**
	 * Turns the camera by the given deltas the same way mouse movement does ({@code Entity.turn} also
	 * shifts the previous-frame rotation, so rendering stays smooth).
	 */
	public static void turnBy(LocalPlayer player, double yawDelta, double pitchDelta) {
		player.turn(yawDelta / TURN_SCALE, pitchDelta / TURN_SCALE);
	}

	/** Turns the camera to face {@code point} exactly. */
	public static void faceInstantly(LocalPlayer player, Vec3 point) {
		float[] target = anglesTo(player.getEyePosition(), point);
		turnBy(player, Mth.wrapDegrees(target[0] - player.getYRot()), target[1] - player.getXRot());
	}

	public static boolean isFacing(LocalPlayer player, Vec3 point, double toleranceDegrees) {
		return angleToPoint(player, point) <= toleranceDegrees;
	}

	/** Sends the player's current rotation to the server right now (extra movement packet). */
	public static void sendRotationPacket(LocalPlayer player) {
		Minecraft.getInstance().getConnection().send(new ServerboundMovePlayerPacket.Rot(
				player.getYRot(), player.getXRot(), player.onGround(), player.horizontalCollision));
	}
}
