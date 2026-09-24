package net.altarsmp.testclient.module;

import org.jspecify.annotations.Nullable;

/**
 * Only one module at a time may run a multi-step hotbar/inventory sequence, otherwise two modules would
 * fight over the selected slot. Single-tick actions just check {@link #isFreeFor(Module)}.
 */
public final class ActionLock {
	private static @Nullable Module owner;

	private ActionLock() {
	}

	public static boolean tryAcquire(Module module) {
		if (owner == null || owner == module) {
			owner = module;
			return true;
		}
		return false;
	}

	public static void release(Module module) {
		if (owner == module) {
			owner = null;
		}
	}

	public static boolean isFreeFor(Module module) {
		return owner == null || owner == module;
	}

	public static void clear() {
		owner = null;
	}
}
