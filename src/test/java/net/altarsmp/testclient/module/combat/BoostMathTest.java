package net.altarsmp.testclient.module.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Checks {@link BoostMath} against an independent tick-by-tick simulation of 1.21.11 player movement and arrow
 * physics: you move at full speed, press the key, get slowed to 20% input while the bow is drawn, release, and
 * speed back up while the arrow flies. The arrow must land on you for the configured draw and for the server
 * counting the draw one tick shorter or longer.
 */
class BoostMathTest {
	private static final double RETENTION = 0.6 * 0.91;
	private static final double WALK_ACCEL = BoostMath.groundAccel(0.1, 0.6);
	private static final double SPRINT_ACCEL = BoostMath.groundAccel(0.13, 0.6);
	private static final int DRAW = 5;

	private record Shot(int hitTick, double pushYaw) {
	}

	/**
	 * @param left,forward raw keys held for the whole test (1/0/-1)
	 * @param accel        acceleration after the shot
	 * @param drawAccel    acceleration while the bow is drawn (a sprint cannot start while drawing)
	 * @param movingBefore whether you were already moving at full speed before pressing the key
	 */
	private static Shot run(double left, double forward, double accel, double drawAccel, boolean movingBefore, int serverDraw) {
		float yaw = 30.0F;
		double[] full = BoostMath.keysToWorld(left, forward, yaw, 1.0);
		double[] drawnKeys = BoostMath.keysToWorld(left, forward, yaw, BoostMath.DRAW_SLOWDOWN);
		// As PunchBow does: the draw-phase input is expressed relative to the after-shot acceleration.
		double[] drawn = {drawnKeys[0] * drawAccel / accel, drawnKeys[1] * drawAccel / accel};
		double dx = movingBefore ? accel * full[0] / (1 - RETENTION) : 0;
		double dz = movingBefore ? accel * full[1] / (1 - RETENTION) : 0;
		double px = 0;
		double pz = 0;
		BoostMath.Aim aim = null;
		// Ticks 1..DRAW: bow drawn (slowed input). The aim is computed at the end of tick DRAW - 1.
		for (int tick = 1; tick <= DRAW; tick++) {
			dx = RETENTION * dx + accel * drawn[0];
			dz = RETENTION * dz + accel * drawn[1];
			px += dx;
			pz += dz;
			if (tick == DRAW - 1) {
				aim = BoostMath.aim(new BoostMath.Motion(dx, dz, full[0], full[1], drawn[0], drawn[1], accel, RETENTION), yaw, DRAW, 1.5);
			}
		}
		// Release at the end of tick DRAW: the arrow inherits this tick's movement (on the ground: horizontal only).
		double speed = 3.0 * BoostMath.power(serverDraw);
		double ayaw = Math.toRadians(aim.yaw());
		double apitch = Math.toRadians(aim.pitch());
		double ax = -Math.sin(ayaw) * Math.cos(apitch) * speed + dx;
		double ay = -Math.sin(apitch) * speed;
		double az = Math.cos(ayaw) * Math.cos(apitch) * speed + dz;
		double x = px;
		double y = 1.52;
		double z = pz;
		boolean leftOwner = false;
		for (int tick = 1; tick < 200; tick++) {
			if (!leftOwner) {
				boolean overlap = x - 0.25 + Math.min(0, ax) - 1 < px + 0.3 && x + 0.25 + Math.max(0, ax) + 1 > px - 0.3
						&& z - 0.25 + Math.min(0, az) - 1 < pz + 0.3 && z + 0.25 + Math.max(0, az) + 1 > pz - 0.3
						&& y + Math.min(0, ay) - 1 < 1.8 && y + 0.5 + Math.max(0, ay) + 1 > 0;
				leftOwner = !overlap;
			}
			double nx = x + ax;
			double ny = y + ay;
			double nz = z + az;
			if (leftOwner) {
				double margin = Math.max(0, Math.min(0.3, (tick - 2) / 20.0));
				for (int s = 0; s <= 40; s++) {
					double qx = x + (nx - x) * s / 40;
					double qy = y + (ny - y) * s / 40;
					double qz = z + (nz - z) * s / 40;
					if (Math.abs(qx - px) <= 0.3 + margin && Math.abs(qz - pz) <= 0.3 + margin && qy >= -margin && qy <= 1.8 + margin) {
						return new Shot(tick, Math.toDegrees(Math.atan2(-ax, az)));
					}
				}
			}
			x = nx;
			y = ny;
			z = nz;
			// You, after the shot: full input again.
			dx = RETENTION * dx + accel * full[0];
			dz = RETENTION * dz + accel * full[1];
			px += dx;
			pz += dz;
			ax *= 0.99;
			az *= 0.99;
			ay = ay * 0.99 - 0.05;
			if (y < 0) {
				return new Shot(-1, 0);
			}
		}
		return new Shot(-1, 0);
	}

	private static void assertHits(double left, double forward, double accel, double drawAccel, boolean movingBefore,
			int fromServerDraw, int toServerDraw) {
		double[] dir = BoostMath.keysToWorld(left, forward, 30.0F, 1.0);
		double moveYaw = Math.toDegrees(Math.atan2(-dir[0], dir[1]));
		for (int serverDraw = fromServerDraw; serverDraw <= toServerDraw; serverDraw++) {
			Shot shot = run(left, forward, accel, drawAccel, movingBefore, serverDraw);
			String label = String.format("keys l=%.0f f=%.0f accel %.3f movingBefore %b server draw %d", left, forward, accel, movingBefore, serverDraw);
			assertTrue(shot.hitTick() > 0, "arrow missed: " + label);
			if (left != 0 || forward != 0) {
				double off = Math.abs(((shot.pushYaw() - moveYaw) % 360 + 540) % 360 - 180);
				assertTrue(off < 10, "launch direction off by " + off + " degrees: " + label);
			}
		}
	}

	@Test
	void flightTimesMatchTheGameForEachDraw() {
		assertEquals(-1, BoostMath.flightTicks(3.0 * BoostMath.power(3)), "3 ticks never clears your hitbox");
		assertEquals(17, BoostMath.flightTicks(3.0 * BoostMath.power(4)));
		assertEquals(22, BoostMath.flightTicks(3.0 * BoostMath.power(5)));
		assertEquals(27, BoostMath.flightTicks(3.0 * BoostMath.power(6)));
	}

	@Test
	void standingStillAimsTowardWhereYouFace() {
		BoostMath.Aim aim = BoostMath.aim(new BoostMath.Motion(0, 0, 0, 0, 0, 0, WALK_ACCEL, RETENTION), 37.0F, DRAW, 1.5);
		assertEquals(37.0F, aim.yaw(), 1e-4);
		assertEquals(-88.5F, aim.pitch(), 1e-4);
		for (int serverDraw = DRAW - 1; serverDraw <= DRAW + 1; serverDraw++) {
			assertTrue(run(0, 0, WALK_ACCEL, WALK_ACCEL, false, serverDraw).hitTick() > 0, "standing, server draw " + serverDraw);
		}
	}

	@Test
	void walkingAndSprintingHitInEveryKeyDirection() {
		double[][] keys = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {1, 1}, {-1, 1}, {1, -1}, {-1, -1}};
		for (double[] k : keys) {
			// Already moving at full speed; drawing does not stop a sprint, so the draw uses the same speed.
			assertHits(k[0], k[1], WALK_ACCEL, WALK_ACCEL, true, DRAW - 1, DRAW + 1);
			assertHits(k[0], k[1], SPRINT_ACCEL, SPRINT_ACCEL, true, DRAW - 1, DRAW + 1);
		}
	}

	@Test
	void startingToWalkAsYouPressTheKeyStillHits() {
		assertHits(0, 1, WALK_ACCEL, WALK_ACCEL, false, DRAW - 1, DRAW + 1);
		assertHits(1, 1, WALK_ACCEL, WALK_ACCEL, false, DRAW - 1, DRAW + 1);
	}

	/**
	 * Standing still, then sprinting as you press the key: the sprint only starts after the shot, so the arrow
	 * needs the biggest lead. It lands on you with the server's usual draw count; if the server counts a tick
	 * more or less, the best possible miss is ~0.65 blocks against a ~0.6-block hit window (physics limit).
	 */
	@Test
	void startingToSprintFromStandstillHitsWithTheUsualDrawCount() {
		assertHits(0, 1, SPRINT_ACCEL, WALK_ACCEL, false, DRAW, DRAW);
	}

	@Test
	void predictsFullSpeedNotTheSlowedDrawSpeed() {
		double[] full = BoostMath.keysToWorld(0, 1, 0.0F, 1.0);
		double[] drawn = BoostMath.keysToWorld(0, 1, 0.0F, BoostMath.DRAW_SLOWDOWN);
		BoostMath.Motion motion = new BoostMath.Motion(0, 0.06, full[0], full[1], drawn[0], drawn[1], WALK_ACCEL, RETENTION);
		BoostMath.Aim aim = BoostMath.aim(motion, 0.0F, DRAW, 1.5);
		assertEquals(0.2158, aim.targetSpeed(), 0.001, "full walking speed is 4.317 m/s");
		assertTrue(aim.releaseSpeed() < 0.07, "still slowed by the bow at the shot");
		assertTrue(aim.tiltDegrees() > 10, "must lead a lot: " + aim.tiltDegrees());
	}
}
