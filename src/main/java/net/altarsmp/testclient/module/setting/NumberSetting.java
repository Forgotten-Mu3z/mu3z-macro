package net.altarsmp.testclient.module.setting;

/** Common view over numeric settings so the config screen can drive them with one slider type. */
public interface NumberSetting {
	String label();

	String description();

	String displayValue();

	/** Current value mapped into [0, 1]. */
	double normalized();

	/** Sets the value from a position in [0, 1], snapping to the setting's step. */
	void setNormalized(double position);
}
