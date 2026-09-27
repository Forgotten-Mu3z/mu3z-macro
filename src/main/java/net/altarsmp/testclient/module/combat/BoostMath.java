package net.altarsmp.testclient.module.combat;

/**
 * Aim calculation for Punch Bow's self boost, from the 1.21.11 arrow and movement code.
 *
 * <p>Arrow: spawns at eye height - 0.1 (1.52 above your feet) with speed 3 * bow power, plus the movement the
 * server last saw from you (horizontal only while on the ground). Every tick it moves, then keeps 99% of its
 * speed and loses 0.05 vertical speed to gravity. It cannot hit you until it has left your hitbox plus a 1-block
 * margin (it must rise to 2.8 above your feet) and hits when its path comes back within 0.3 of your hitbox.
 *
 * <p>You: on the ground your movement each tick is {@code retention * previous + accel * keys}, where retention
 * is block friction * 0.91 (0.546 on normal blocks) and accel comes from your movement speed (walk, sprint,
 * sneak, speed effects). While the bow is drawn your key input is multiplied by 0.2, so at the moment of the shot
 * you are slow, and the arrow inherits only that slow movement; after the shot you speed back up to your full
 * walking or sprinting speed. The aim therefore predicts your whole path (the release tick still slowed, then
 * accelerating to full speed) and points the arrow, tilted just enough, at where that path puts you when the
 * arrow comes down, so Punch launches you the way you are moving.
 */
public final class BoostMath {
	static final double SPAWN_HEIGHT = 1.52;
	static final double HIT_HEIGHT = 1.8 + 0.3;
	static final double DRAG = 0.99;
	static final double GRAVITY = 0.05;
	/** Key input multiplier while drawing a bow (the default use_effects speed multiplier). */
	static final double DRAW_SLOWDOWN = 0.2;
	/** Below this predicted speed (blocks/tick) you count as standing still. */
	static final double STANDING_SPEED = 0.03;

	private BoostMath() {
	}

	/**
	 * Your movement state, all horizontal and in blocks per tick.
	 *
	 * @param lastX     movement over the last tick (x), still slowed by the bow draw
	 * @param lastZ     movement over the last tick (z)
	 * @param keysX     world-space input of the movement keys you hold after the shot, at the game's input length
	 *                  (0.98 straight, 1.0 diagonal, times the sneak factor); 0 if no keys ({@link #keysToWorld})
	 * @param keysZ     same, z
	 * @param drawKeysX the same keys while the bow is still drawn (input multiplied by 0.2 before the game's
	 *                  diagonal correction)
	 * @param drawKeysZ same, z
	 * @param accel     ground acceleration for full input: movement speed * 0.216 / friction^3
	 * @param retention speed kept per tick on the ground: friction * 0.91
	 */
	public record Motion(double lastX, double lastZ, double keysX, double keysZ, double drawKeysX, double drawKeysZ,
			double accel, double retention) {
		/** Top speed you are heading for with the keys you hold. */
		public double targetSpeed() {
			return accel * Math.hypot(keysX, keysZ) / (1.0 - retention);
		}
	}

	/** Result: where to aim (degrees, Minecraft convention) and what the calculation assumed. */
	public record Aim(float yaw, float pitch, double targetSpeed, double releaseSpeed, int flightTicks, double tiltDegrees) {
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

	/** Your movement during the release tick (bow still drawn, input slowed): what the arrow inherits. */
	static double[] releaseMovement(Motion m) {
		return new double[] {
				m.retention() * m.lastX() + m.accel() * m.drawKeysX(),
				m.retention() * m.lastZ() + m.accel() * m.drawKeysZ()};
	}

	/** Where you will be {@code ticks} ticks after the shot, relative to the release point (full input again). */
	static double[] displacementAfter(Motion m, double[] release, int ticks) {
		double dx = release[0];
		double dz = release[1];
		double px = 0;
		double pz = 0;
		for (int i = 0; i < ticks; i++) {
			dx = m.retention() * dx + m.accel() * m.keysX();
			dz = m.retention() * dz + m.accel() * m.keysZ();
			px += dx;
			pz += dz;
		}
		return new double[] {px, pz};
	}

	/**
	 * Aim for a self boost released at the end of the next tick.
	 *
	 * @param facingYaw    the yaw you are looking at, used when standing still
	 * @param drawTicks    bow draw time
	 * @param standingTilt tilt toward where you face when standing still, in degrees
	 */
	public static Aim aim(Motion m, float facingYaw, int drawTicks, double standingTilt) {
		double launchSpeed = 3.0 * power(drawTicks);
		double[] release = releaseMovement(m);
		double releaseSpeed = Math.hypot(release[0], release[1]);
		double targetSpeed = m.targetSpeed();
		if (releaseSpeed < STANDING_SPEED && targetSpeed < STANDING_SPEED) {
			double tilt = standingTilt;
			int flight = flightTicks(launchSpeed * Math.cos(Math.toRadians(tilt)));
			return new Aim(facingYaw, (float) (-90.0 + tilt), targetSpeed, releaseSpeed, flight, tilt);
		}
		// Extra arrow speed u so that (release + u) * carried(t) lands on your predicted position at tick t.
		double tilt = 0.0;
		double ux = 0.0;
		double uz = 0.0;
		int flight = flightTicks(launchSpeed);
		for (int i = 0; i < 6 && flight > 0; i++) {
			double[] you = displacementAfter(m, release, flight);
			double carried = carried(flight);
			ux = you[0] / carried - release[0];
			uz = you[1] / carried - release[1];
			tilt = Math.toDegrees(Math.asin(Math.min(1.0, Math.hypot(ux, uz) / launchSpeed)));
			flight = flightTicks(launchSpeed * Math.cos(Math.toRadians(tilt)));
		}
		double lead = Math.hypot(ux, uz);
		if (lead <= 1.0E-6) {
			return new Aim(facingYaw, (float) (-90.0 + tilt), targetSpeed, releaseSpeed, flight, tilt);
		}
		double dirX = ux / lead;
		double dirZ = uz / lead;
		tilt = robustTilt(m, release, dirX, dirZ, tilt, drawTicks);
		flight = flightTicks(launchSpeed * Math.cos(Math.toRadians(tilt)));
		float yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
		return new Aim(yaw, (float) (-90.0 + tilt), targetSpeed, releaseSpeed, flight, tilt);
	}

	/**
	 * The server counts the draw with its own clock, which can be one tick shorter or longer than ours, and a
	 * big lead makes the landing point sensitive to the bow power. Picks the tilt (near the exact one) whose worst
	 * miss over those draw counts is smallest; draw counts too weak to clear your hitbox are ignored.
	 */
	static double robustTilt(Motion m, double[] release, double dirX, double dirZ, double exactTilt, int drawTicks) {
		double bestTilt = exactTilt;
		double bestWorst = Double.MAX_VALUE;
		for (double tilt = Math.max(0.0, exactTilt - 8.0); tilt <= Math.min(60.0, exactTilt + 8.0); tilt += 0.1) {
			double worst = 0.0;
			for (int serverDraw = drawTicks - 1; serverDraw <= drawTicks + 1; serverDraw++) {
				double miss = landingMiss(m, release, dirX, dirZ, tilt, serverDraw);
				if (!Double.isNaN(miss)) {
					worst = Math.max(worst, miss);
				}
			}
			if (worst < bestWorst) {
				bestWorst = worst;
				bestTilt = tilt;
			}
		}
		return bestTilt;
	}

	/** Horizontal distance between the arrow and you when it comes down; NaN if that draw never clears your hitbox. */
	static double landingMiss(Motion m, double[] release, double dirX, double dirZ, double tiltDegrees, int serverDraw) {
		double launchSpeed = 3.0 * power(serverDraw);
		double tilt = Math.toRadians(tiltDegrees);
		int flight = flightTicks(launchSpeed * Math.cos(tilt));
		if (flight <= 0) {
			return Double.NaN;
		}
		double sideways = launchSpeed * Math.sin(tilt);
		double carried = carried(flight);
		double arrowX = (release[0] + sideways * dirX) * carried;
		double arrowZ = (release[1] + sideways * dirZ) * carried;
		double[] you = displacementAfter(m, release, flight);
		return Math.hypot(arrowX - you[0], arrowZ - you[1]);
	}

	/**
	 * World-space key input with the game's input length, from the raw key vector (left, forward) and the camera
	 * yaw, exactly as LocalPlayer.modifyInput + Entity.getInputVector do. {@code factor} is the product of the
	 * sneak factor and, while the bow is drawn, {@link #DRAW_SLOWDOWN}.
	 */
	public static double[] keysToWorld(double left, double forward, float yawDegrees, double factor) {
		double length = Math.hypot(left, forward);
		if (length <= 0.0) {
			return new double[] {0.0, 0.0};
		}
		double nl = left / length;
		double nf = forward / length;
		double ratio = Math.abs(nf) > Math.abs(nl) ? Math.abs(nl) / Math.abs(nf) : Math.abs(nf) / Math.abs(nl);
		double scale = Math.min(length * 0.98 * factor * Math.sqrt(1.0 + ratio * ratio), 1.0);
		double l = nl * scale;
		double f = nf * scale;
		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);
		return new double[] {l * cos - f * sin, f * cos + l * sin};
	}

	/** Ground acceleration for full input: movement speed * 0.216 / friction^3. */
	public static double groundAccel(double movementSpeed, double friction) {
		return movementSpeed * (0.21600002 / (friction * friction * friction));
	}
}
