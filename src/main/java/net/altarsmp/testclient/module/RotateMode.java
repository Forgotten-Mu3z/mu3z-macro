package net.altarsmp.testclient.module;

/** How modules that interact with blocks or crystals face their target first. */
public enum RotateMode {
	/** Interact without changing rotation. */
	OFF("Off"),
	/** Turn the real camera, then interact on a later tick once the new rotation has been sent normally. */
	CAMERA("Camera"),
	/** Turn the camera and immediately send an extra rotation packet before interacting. */
	PACKET("Packet");

	private final String displayName;

	RotateMode(String displayName) {
		this.displayName = displayName;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
