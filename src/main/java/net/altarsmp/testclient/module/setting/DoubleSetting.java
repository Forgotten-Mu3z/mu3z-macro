package net.altarsmp.testclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public final class DoubleSetting extends Setting<Double> implements NumberSetting {
	private final double min;
	private final double max;
	private final double step;
	private final String unit;

	public DoubleSetting(String key, String label, String description, double defaultValue, double min, double max, double step, String unit) {
		super(key, label, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = step;
		this.unit = unit;
	}

	@Override
	protected Double sanitize(Double newValue) {
		if (newValue == null || newValue.isNaN()) {
			return defaultValue();
		}
		double snapped = step > 0 ? Math.round(newValue / step) * step : newValue;
		return Math.max(min, Math.min(max, snapped));
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
			set(element.getAsDouble());
		}
	}

	@Override
	public String displayValue() {
		int decimals = step >= 1 ? 0 : step >= 0.1 ? 1 : 2;
		return String.format(Locale.ROOT, "%." + decimals + "f", value) + unit;
	}

	@Override
	public double normalized() {
		return max == min ? 0 : (value - min) / (max - min);
	}

	@Override
	public void setNormalized(double position) {
		set(min + position * (max - min));
	}
}
