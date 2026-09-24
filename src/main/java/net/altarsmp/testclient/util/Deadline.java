package net.altarsmp.testclient.util;

/**
 * A one-shot "not before" time, optionally also "not before tick N". Modules arm it when a step is
 * scheduled and poll {@link #passed()} from their tick handler.
 */
public final class Deadline {
	private boolean armed;
	private long atNanos;
	private long notBeforeTick;

	/** Arms the deadline {@code delayMs} from now. */
	public void in(long delayMs) {
		armed = true;
		atNanos = System.nanoTime() + Math.max(0, delayMs) * 1_000_000L;
		notBeforeTick = 0;
	}

	/** Arms the deadline {@code delayMs} from now and also no earlier than {@code ticks} ticks from now. */
	public void in(long delayMs, int ticks) {
		in(delayMs);
		notBeforeTick = ClientTicks.now() + ticks;
	}

	public boolean armed() {
		return armed;
	}

	public boolean passed() {
		return armed && System.nanoTime() >= atNanos && ClientTicks.now() >= notBeforeTick;
	}

	public void clear() {
		armed = false;
	}
}
