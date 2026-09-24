package net.altarsmp.testclient.module;

/**
 * HUMANLIKE: every automated step waits a randomised, roughly normal delay taken from the module's min/max
 * settings. BLATANT: the same steps run with no added delay (same tick, or the next tick where the game
 * mechanic requires it), so an anti-cheat can be tested against both.
 */
public enum SpeedMode {
	HUMANLIKE("Humanlike", "H"),
	BLATANT("Blatant", "B");

	private final String displayName;
	private final String tag;

	SpeedMode(String displayName, String tag) {
		this.displayName = displayName;
		this.tag = tag;
	}

	public String tag() {
		return tag;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
