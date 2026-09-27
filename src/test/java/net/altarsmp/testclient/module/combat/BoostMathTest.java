package net.altarsmp.testclient.module.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Checks {@link BoostMath} against an independent re-implementation of the 1.21.11 arrow physics: shoot with the
 * computed aim, move the player at a constant speed, and require the arrow to land on the player for draw counts
 * one tick below and above the configured one (the server's own count can differ by a tick).
 */
class BoostMathTest {
	private static final double WALK = 0.216;
	private static final double SPRINT = 0.28;
	private static final double SPRINT_JUMP = 0.35;

	/** Result of one simulated shot: tick of the hit (or -1) and the arrow's horizontal direction at the hit. */
	private record Shot(int hitTick, double pushYaw) {
	}

	/** Mirrors AbstractArrow/Projectile: spawn at eye - 0.1, inherit horizontal movement on the ground, leftOwner rule, 0.3 margin. */
	private static Shot shoot(BoostMath.Aim aim, int serverDraw, double vx, double vz) {
		double speed = 3.0 * BoostMath.power(serverDraw);
		double yaw = Math.toRadians(aim.yaw());
		double pitch = Math.toRadians(aim.pitch());
		double ax = -Math.sin(yaw) * Math.cos(pitch) * speed + vx;
		double ay = -Math.sin(pitch) * speed;
		double az = Math.cos(yaw) * Math.cos(pitch) * speed + vz;
		double x = 0;
		double y = 1.52;
		double z = 0;
		double px = 0;
		double pz = 0;
		boolean left = false;
		for (int tick = 1; tick < 200; tick++) {
			if (!left) {
				boolean overlap = x - 0.25 + Math.min(0, ax) - 1 < px + 0.3 && x + 0.25 + Math.max(0, ax) + 1 > px - 0.3
						&& z - 0.25 + Math.min(0, az) - 1 < pz + 0.3 && z + 0.25 + Math.max(0, az) + 1 > pz - 0.3
						&& y + Math.min(0, ay) - 1 < 1.8 && y + 0.5 + Math.max(0, ay) + 1 > 0;
				left = !overlap;
			}
			double nx = x + ax;
			double ny = y + ay;
			double nz = z + az;
			if (left) {
				double margin = Math.max(0, Math.min(0.3, (tick - 2) / 20.0));
				for (int s = 0; s <= 40; s++) {
					double qx = x + (nx - x) * s / 40;
					double qy = y + (ny - y) * s / 40;
					double qz = z + (nz - z) * s / 40;
					if (Math.abs(qx - px) <= 0.3 + margin && Math.abs(qz - pz) <= 0.3 + margin
							&& qy >= -margin && qy <= 1.8 + margin) {
						return new Shot(tick, Math.toDegrees(Math.atan2(-ax, az)));
					}
				}
			}
			x = nx;
			y = ny;
			z = nz;
			px += vx;
			pz += vz;
			ax *= 0.99;
			az *= 0.99;
			ay = ay * 0.99 - 0.05;
			if (y < 0) {
				return new Shot(-1, 0);
			}
		}
		return new Shot(-1, 0);
	}

	private static void assertLandsForDrawJitter(double speed, double directionDegrees, int draw) {
		double dir = Math.toRadians(directionDegrees);
		double vx = -Math.sin(dir) * speed;
		double vz = Math.cos(dir) * speed;
		BoostMath.Aim aim = BoostMath.aim(vx, vz, 37.0F, draw, 1.5);
		for (int serverDraw = draw - 1; serverDraw <= draw + 1; serverDraw++) {
			Shot shot = shoot(aim, serverDraw, vx, vz);
			String label = String.format("speed %.3f dir %.0f draw %d (server %d) aim %s", speed, directionDegrees, draw, serverDraw, aim);
			assertTrue(shot.hitTick() > 0, "arrow missed: " + label);
			if (speed >= BoostMath.STANDING_SPEED) {
				double off = Math.abs(((shot.pushYaw() - directionDegrees) % 360 + 540) % 360 - 180);
				assertTrue(off < 10, "push direction off by " + off + " degrees: " + label);
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
		BoostMath.Aim aim = BoostMath.aim(0, 0, 37.0F, 5, 1.5);
		assertEquals(37.0F, aim.yaw(), 1e-4);
		assertEquals(-88.5F, aim.pitch(), 1e-4);
		for (int serverDraw = 4; serverDraw <= 6; serverDraw++) {
			assertTrue(shoot(aim, serverDraw, 0, 0).hitTick() > 0, "standing, server draw " + serverDraw);
		}
	}

	@Test
	void walkingSprintingAndSprintJumpingLandInEveryDirection() {
		for (double speed : new double[] {0.1, WALK, SPRINT, SPRINT_JUMP}) {
			for (int direction = 0; direction < 360; direction += 45) {
				assertLandsForDrawJitter(speed, direction, 5);
			}
		}
	}

	@Test
	void fasterMovementNeedsMoreTilt() {
		double walk = BoostMath.aim(0, WALK, 0, 5, 1.5).tiltDegrees();
		double sprint = BoostMath.aim(0, SPRINT, 0, 5, 1.5).tiltDegrees();
		assertTrue(walk > 1.5 && sprint > walk, "walk " + walk + " sprint " + sprint);
	}

	@Test
	void aimsAlongMovementNotFacing() {
		// Moving toward -x (yaw 90 in Minecraft's convention) while facing yaw 37.
		BoostMath.Aim aim = BoostMath.aim(-WALK, 0, 37.0F, 5, 1.5);
		assertEquals(90.0F, aim.yaw(), 1e-3);
	}
}
