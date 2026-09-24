package net.altarsmp.testclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class IntSetting extends Setting<Integer> implements NumberSetting {
	private final int min;
	private final int max;
	private final String unit;

	public IntSetting(String key, String label, String description, int defaultValue, int min, int max, String unit) {
		super(key, label, description, defaultValue);
		this.min = min;
		this.max = max;
		this.unit = unit;
	}

	public int min() {
		return min;
	}

	public int max() {
		return max;
	}

	@Override
	protected Integer sanitize(Integer newValue) {
		if (newValue == null) {
			return defaultValue();
		}
		return Math.max(min, Math.min(max, newValue));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			set((int) Math.round(element.getAsDouble()));
		}
	}

	@Override
	public String displayValue() {
		return value + unit;
	}

	@Override
	public double normalized() {
		return max == min ? 0 : (value - min) / (double) (max - min);
	}

	@Override
	public void setNormalized(double position) {
		set((int) Math.round(min + position * (max - min)));
	}
}
