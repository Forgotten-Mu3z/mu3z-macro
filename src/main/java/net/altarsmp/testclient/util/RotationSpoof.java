package net.altarsmp.testclient.util;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Silent aim: a pitch that is sent to the server instead of the camera's. {@code LocalPlayerMixin} swaps it in
 * for the duration of {@code LocalPlayer.sendPosition} only, so the regular movement packet (with your real
 * position change) carries it while the camera you see never moves.
 */
public final class RotationSpoof {
	private static float pitch = Float.NaN;
	private static float realPitch = Float.NaN;

	private RotationSpoof() {
	}

	public static void setPitch(float value) {
		pitch = Mth.clamp(value, -90.0F, 90.0F);
	}

	public static void clear() {
		pitch = Float.NaN;
	}

	public static boolean active() {
		return !Float.isNaN(pitch);
	}

	public static float pitch() {
		return pitch;
	}

	/** From LocalPlayerMixin at the start of sendPosition. */
	public static void beforeSend(LocalPlayer player) {
		if (active()) {
			realPitch = player.getXRot();
			player.setXRot(pitch);
		}
	}

	/** From LocalPlayerMixin when sendPosition returns. */
	public static void afterSend(LocalPlayer player) {
		if (!Float.isNaN(realPitch)) {
			player.setXRot(realPitch);
			realPitch = Float.NaN;
		}
	}
}
