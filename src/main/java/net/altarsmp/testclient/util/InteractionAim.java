package net.altarsmp.testclient.util;

import net.altarsmp.testclient.module.RotateMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Implements {@link RotateMode} for modules that click blocks or crystals. */
public final class InteractionAim {
	private static final double FACING_TOLERANCE = 2.0;
	private static long lastTurnTick = Long.MIN_VALUE;

	private InteractionAim() {
	}

	/**
	 * Prepares to interact with {@code point}. Returns true if the interaction may happen now, false if the
	 * camera was just turned and the caller should retry next tick (CAMERA mode).
	 */
	public static boolean ready(RotateMode mode, Vec3 point) {
		LocalPlayer player = Minecraft.getInstance().player;
		switch (mode) {
			case OFF:
				return true;
			case PACKET:
				RotationUtil.faceInstantly(player, point);
				RotationUtil.sendRotationPacket(player);
				return true;
			case CAMERA:
			default:
				if (RotationUtil.isFacing(player, point, FACING_TOLERANCE) && lastTurnTick < ClientTicks.now()) {
					return true;
				}
				RotationUtil.faceInstantly(player, point);
				lastTurnTick = ClientTicks.now();
				return false;
		}
	}
}
