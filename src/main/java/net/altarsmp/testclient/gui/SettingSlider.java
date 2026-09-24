package net.altarsmp.testclient.gui;

import net.altarsmp.testclient.config.ConfigManager;
import net.altarsmp.testclient.module.setting.NumberSetting;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** Vanilla slider bound to an int/double setting. */
final class SettingSlider extends AbstractSliderButton {
	private final NumberSetting setting;

	SettingSlider(int x, int y, int width, int height, NumberSetting setting) {
		super(x, y, width, height, Component.empty(), setting.normalized());
		this.setting = setting;
		updateMessage();
	}

	@Override
	protected void updateMessage() {
		setMessage(Component.literal(setting.label() + ": " + setting.displayValue()));
	}

	@Override
	protected void applyValue() {
		setting.setNormalized(value);
		// Snap the handle to the stepped value actually stored.
		value = setting.normalized();
		ConfigManager.markDirty();
	}
}
