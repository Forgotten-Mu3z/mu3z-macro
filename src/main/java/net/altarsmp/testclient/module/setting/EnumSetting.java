package net.altarsmp.testclient.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] constants;

	public EnumSetting(String key, String label, String description, E defaultValue) {
		super(key, label, description, defaultValue);
		this.constants = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public void cycle() {
		set(constants[(value.ordinal() + 1) % constants.length]);
	}

	@Override
	public JsonElement toJson() {
		return new JsonPrimitive(value.name());
	}

	@Override
	public void fromJson(JsonElement element) {
		if (element == null || !element.isJsonPrimitive()) {
			return;
		}
		String name = element.getAsString().trim().toUpperCase(Locale.ROOT);
		for (E constant : constants) {
			if (constant.name().equals(name)) {
				set(constant);
				return;
			}
		}
	}

	@Override
	public String displayValue() {
		return value.toString();
	}
}
