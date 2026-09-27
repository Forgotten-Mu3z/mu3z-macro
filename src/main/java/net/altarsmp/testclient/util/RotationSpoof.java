package net.altarsmp.testclient.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Silent aim: a rotation that is sent to the server instead of the camera's. {@code LocalPlayerMixin} swaps it in
 * for the duration of {@code LocalPlayer.sendPosition} only, so the regular movement packet (with your real
 * position change) carries it while the camera you see never moves. Your walking direction is computed on the
 * client from the camera, so a spoofed yaw does not change where you walk.
 */
public final class RotationSpoof {
	private static float yaw = Float.NaN;
	private static float pitch = Float.NaN;
	private static float realYaw = Float.NaN;
	private static float realPitch = Float.NaN;

	private RotationSpoof() {
	}

	/** Spoofs only the pitch; the real yaw is sent. */
	public static void setPitch(float value) {
		yaw = Float.NaN;
		pitch = Mth.clamp(value, -90.0F, 90.0F);
	}

	/** Spoofs yaw and pitch. */
	public static void set(float yawValue, float pitchValue) {
		yaw = yawValue;
		pitch = Mth.clamp(pitchValue, -90.0F, 90.0F);
	}

	public static void clear() {
		yaw = Float.NaN;
		pitch = Float.NaN;
	}

	public static boolean active() {
		return !Float.isNaN(pitch);
	}

	public static float pitch() {
		return pitch;
	}

	/** The spoofed yaw, or NaN if only the pitch is spoofed. */
	public static float yaw() {
		return yaw;
	}

	/** From LocalPlayerMixin at the start of sendPosition. */
	public static void beforeSend(LocalPlayer player) {
		if (!active()) {
			return;
		}
		realPitch = player.getXRot();
		player.setXRot(pitch);
		if (!Float.isNaN(yaw)) {
			realYaw = player.getYRot();
			// Keep the sent yaw next to the real one (same full turn), so it is not a 360-degree jump.
			player.setYRot(realYaw + Mth.wrapDegrees(yaw - realYaw));
		}
	}

	/** From LocalPlayerMixin when sendPosition returns. */
	public static void afterSend(LocalPlayer player) {
		if (!Float.isNaN(realPitch)) {
			player.setXRot(realPitch);
			realPitch = Float.NaN;
		}
		if (!Float.isNaN(realYaw)) {
			player.setYRot(realYaw);
			realYaw = Float.NaN;
		}
	}
}
