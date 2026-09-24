package net.altarsmp.testclient.module.setting;

import com.google.gson.JsonElement;

/**
 * A single configurable value. Settings know how to serialise themselves to the JSON config and
 * how to describe themselves to the config screen; they have no Minecraft dependencies.
 */
public abstract class Setting<T> {
	private final String key;
	private final String label;
	private final String description;
	private final T defaultValue;
	protected T value;

	protected Setting(String key, String label, String description, T defaultValue) {
		this.key = key;
		this.label = label;
		this.description = description;
		this.defaultValue = defaultValue;
		this.value = defaultValue;
	}

	/** JSON key inside the owning module's config object. */
	public String key() {
		return key;
	}

	public String label() {
		return label;
	}

	public String description() {
		return description;
	}

	public T get() {
		return value;
	}

	public void set(T newValue) {
		this.value = sanitize(newValue);
	}

	public T defaultValue() {
		return defaultValue;
	}

	public void reset() {
		this.value = defaultValue;
	}

	protected T sanitize(T newValue) {
		return newValue == null ? defaultValue : newValue;
	}

	public abstract JsonElement toJson();

	/** Reads the value from JSON, keeping the current value if the element is missing or malformed. */
	public abstract void fromJson(JsonElement element);

	public abstract String displayValue();
}
