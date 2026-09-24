package net.altarsmp.testclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String key, String label, String description, boolean defaultValue) {
		super(key, label, description, defaultValue);
	}

	public void toggle() {
		set(!get());
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value);
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
			set(element.getAsBoolean());
		}
	}

	@Override
	public String displayValue() {
		return value ? "ON" : "OFF";
	}
}
