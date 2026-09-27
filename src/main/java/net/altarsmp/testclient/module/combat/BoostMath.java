package net.altarsmp.testclient.module.combat;

/**
 * Aim calculation for Punch Bow's self boost, from the 1.21.11 arrow code:
 * <ul>
 * <li>The arrow spawns at eye height - 0.1 (1.52 above your feet) with speed 3 * bow power, plus the movement
 * the server last saw from you (horizontal only while on the ground).</li>
 * <li>Every tick it moves, then loses 1% of its speed and 0.05 of vertical speed to gravity.</li>
 * <li>It cannot hit you until it has left your hitbox plus a 1-block margin (so it must rise to 2.8 above your
 * feet), and hits when its path comes back within 0.3 of your hitbox (top at 1.8).</li>
 * </ul>
 * While you move at a steady speed the arrow keeps your speed but loses 1% of it per tick, so it drifts behind
 * you. The shot is aimed in your direction of movement, tilted just enough that its own sideways speed makes up
 * that drift and it comes down where you will be. Standing still it tilts by the configured angle toward where you
 * face, which sets the launch direction.
 */
public final class BoostMath {
	static final double SPAWN_HEIGHT = 1.52;
	static final double LEAVE_HEIGHT = 2.8;
	static final double HIT_HEIGHT = 1.8 + 0.3;
	static final double DRAG = 0.99;
	static final double GRAVITY = 0.05;
	/** Below this horizontal speed (blocks/tick) you count as standing still. */
	static final double STANDING_SPEED = 0.03;

	private BoostMath() {
	}

	/** Result: where to aim (degrees, Minecraft convention) and what the calculation assumed. */
	public record Aim(float yaw, float pitch, double speed, int flightTicks, double tiltDegrees) {
	}

	/** Bow power for a draw of {@code ticks}, as {@code BowItem.getPowerForTime}. */
	public static double power(int ticks) {
		double x = ticks / 20.0;
		return Math.min(1.0, (x * x + 2.0 * x) / 3.0);
	}

	/** Ticks until an arrow launched upward at {@code verticalSpeed} comes back down onto a standing hitbox; -1 if never. */
	public static int flightTicks(double verticalSpeed) {
		double y = SPAWN_HEIGHT;
		double vy = verticalSpeed;
		boolean left = false;
		for (int tick = 1; tick <= 400; tick++) {
			if (!left && y + Math.min(0.0, vy) - 1.0 >= 1.8) {
				left = true;
			}
			double next = y + vy;
			if (left && vy < 0 && next <= HIT_HEIGHT) {
				return tick;
			}
			if (next < 0 && !left) {
				return -1;
			}
			y = next;
			vy = vy * DRAG - GRAVITY;
		}
		return -1;
	}

	/** Sum of 0.99^i for i &lt; ticks: how far (in ticks of its starting speed) a coasting arrow travels. */
	static double carried(int ticks) {
		return (1.0 - Math.pow(DRAG, ticks)) / (1.0 - DRAG);
	}

	/**
	 * Aim for a self boost.
	 *
	 * @param vx          your movement along x per tick (what the server passes on to the arrow)
	 * @param vz          your movement along z per tick
	 * @param facingYaw   the yaw you are looking at, used when standing still
	 * @param drawTicks   bow draw time
	 * @param standingTilt tilt toward where you face when standing still, in degrees
	 */
	public static Aim aim(double vx, double vz, float facingYaw, int drawTicks, double standingTilt) {
		double launchSpeed = 3.0 * power(drawTicks);
		double speed = Math.sqrt(vx * vx + vz * vz);
		if (speed < STANDING_SPEED) {
			double tilt = standingTilt;
			int flight = flightTicks(launchSpeed * Math.cos(Math.toRadians(tilt)));
			return new Aim(facingYaw, (float) (-90.0 + tilt), speed, flight, tilt);
		}
		// Extra sideways speed u (along the movement) so that (v + u) * carried(t) = v * t at the landing tick t.
		double tilt = 0.0;
		int flight = flightTicks(launchSpeed);
		for (int i = 0; i < 4; i++) {
			if (flight <= 0) {
				break;
			}
			double extra = speed * (flight / carried(flight) - 1.0);
			tilt = Math.toDegrees(Math.asin(Math.min(1.0, extra / launchSpeed)));
			flight = flightTicks(launchSpeed * Math.cos(Math.toRadians(tilt)));
		}
		float yaw = (float) Math.toDegrees(Math.atan2(-vx, vz));
		return new Aim(yaw, (float) (-90.0 + tilt), speed, flight, tilt);
	}
}
