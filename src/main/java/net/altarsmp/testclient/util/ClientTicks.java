package net.altarsmp.testclient.util;

/** Monotonic client tick counter used for log timestamps and "next tick" scheduling. */
public final class ClientTicks {
	private static long ticks;

	private ClientTicks() {
	}

	public static void increment() {
		ticks++;
	}

	public static long now() {
		return ticks;
	}
}
