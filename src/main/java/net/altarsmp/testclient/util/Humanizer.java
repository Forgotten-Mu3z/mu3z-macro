package net.altarsmp.testclient.util;

import java.util.concurrent.ThreadLocalRandom;

/** Randomness used by HUMANLIKE mode. */
public final class Humanizer {
	private Humanizer() {
	}

	/**
	 * A delay between {@code min} and {@code max} ms drawn from a normal distribution centred on the
	 * midpoint (sd = range / 4, clamped), which looks more like human reaction time than a flat
	 * uniform spread.
	 */
	public static long delayMs(int min, int max) {
		int lo = Math.max(0, Math.min(min, max));
		int hi = Math.max(0, Math.max(min, max));
		if (hi == lo) {
			return lo;
		}
		double mean = (lo + hi) / 2.0;
		double sd = (hi - lo) / 4.0;
		double sample = mean + ThreadLocalRandom.current().nextGaussian() * sd;
		return Math.round(Math.max(lo, Math.min(hi, sample)));
	}

	/** Uniform value in [-amount, amount]. */
	public static double jitter(double amount) {
		if (amount <= 0) {
			return 0;
		}
		return (ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0) * amount;
	}

	public static double gaussian() {
		return ThreadLocalRandom.current().nextGaussian();
	}
}
