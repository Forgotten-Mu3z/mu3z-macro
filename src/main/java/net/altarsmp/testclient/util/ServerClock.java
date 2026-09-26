package net.altarsmp.testclient.util;

import java.util.ArrayDeque;

/**
 * Estimates the server's tick rate (TPS) and the timing jitter of packets arriving from it, using the
 * time-sync packet the server sends every 20 ticks ({@code ClientboundSetTimePacket}). Arrival times are
 * taken on the network thread (see {@code ClientPacketListenerMixin}), before the packet waits for the next
 * client frame.
 *
 * <p>A least-squares line through (game time, arrival time) gives milliseconds per server tick (so TPS);
 * the spread of arrival times around that line is the jitter: network jitter plus uneven server ticks.
 */
public final class ServerClock {
	private static final int WINDOW = 10;
	private static final long MAX_GAP_NANOS = 5_000_000_000L;
	private static final ArrayDeque<long[]> SAMPLES = new ArrayDeque<>();

	private static volatile double tps = 20.0;
	private static volatile double jitterMs = 0.0;
	private static long lastGameTime = Long.MIN_VALUE;

	private ServerClock() {
	}

	/** Records a time-sync packet that has just arrived from the network. */
	public static synchronized void onTimeSync(long gameTime) {
		record(System.nanoTime(), gameTime);
	}

	/** Called on the client thread; records the packet only if the network-thread hook missed it. */
	public static synchronized void onTimeSyncHandled(long gameTime) {
		if (gameTime != lastGameTime) {
			record(System.nanoTime(), gameTime);
		}
	}

	/** Package-private for tests: records a sample with an explicit arrival time. */
	static void record(long nanos, long gameTime) {
		long[] last = SAMPLES.peekLast();
		if (last != null && (gameTime <= last[1] || nanos - last[0] > MAX_GAP_NANOS)) {
			// World change, time jump or a long freeze: start a fresh estimate.
			SAMPLES.clear();
		}
		SAMPLES.addLast(new long[] {nanos, gameTime});
		while (SAMPLES.size() > WINDOW) {
			SAMPLES.removeFirst();
		}
		lastGameTime = gameTime;
		recompute();
	}

	private static void recompute() {
		int n = SAMPLES.size();
		if (n < 3) {
			tps = 20.0;
			jitterMs = 0.0;
			return;
		}
		long[] first = SAMPLES.peekFirst();
		double sx = 0;
		double sy = 0;
		double sxx = 0;
		double sxy = 0;
		for (long[] sample : SAMPLES) {
			double x = sample[1] - first[1];
			double y = (sample[0] - first[0]) / 1_000_000.0;
			sx += x;
			sy += y;
			sxx += x * x;
			sxy += x * y;
		}
		double denominator = n * sxx - sx * sx;
		if (denominator <= 0) {
			return;
		}
		double msPerTick = (n * sxy - sx * sy) / denominator;
		if (msPerTick <= 0) {
			return;
		}
		double intercept = (sy - msPerTick * sx) / n;
		double squares = 0;
		for (long[] sample : SAMPLES) {
			double x = sample[1] - first[1];
			double y = (sample[0] - first[0]) / 1_000_000.0;
			double residual = y - (intercept + msPerTick * x);
			squares += residual * residual;
		}
		tps = 1000.0 / msPerTick;
		jitterMs = Math.sqrt(squares / (n - 2));
	}

	public static synchronized void reset() {
		SAMPLES.clear();
		lastGameTime = Long.MIN_VALUE;
		tps = 20.0;
		jitterMs = 0.0;
	}

	/** Measured server ticks per second (20 until enough samples arrive). */
	public static double tps() {
		return tps;
	}

	/** Standard deviation of packet arrival times around the server's tick schedule, in ms. */
	public static double jitterMs() {
		return jitterMs;
	}

	/**
	 * Extra client ticks to wait so a hit timed for the server's full cooldown never reaches the server a tick
	 * early: 0 on a steady connection, 1 when jitter is around half a tick, 2 when it is worse.
	 */
	public static int safetyTicks() {
		return (int) Math.min(2, Math.round(jitterMs / 25.0));
	}
}
