package net.altarsmp.testclient.gui;

import net.altarsmp.testclient.config.ConfigManager;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.EnumSetting;
import net.altarsmp.testclient.module.setting.NumberSetting;
import net.altarsmp.testclient.module.setting.Setting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Builds the right vanilla widget for each setting type; position/size are set by the screen. */
final class SettingWidgets {
	private SettingWidgets() {
	}

	static AbstractWidget create(Setting<?> setting) {
		Tooltip tooltip = Tooltip.create(Component.literal(setting.description()));
		if (setting instanceof BooleanSetting bool) {
			return Button.builder(booleanText(bool), button -> {
				bool.toggle();
				button.setMessage(booleanText(bool));
				ConfigManager.markDirty();
			}).tooltip(tooltip).build();
		}
		if (setting instanceof EnumSetting<?> choice) {
			return Button.builder(enumText(choice), button -> {
				choice.cycle();
				button.setMessage(enumText(choice));
				ConfigManager.markDirty();
			}).tooltip(tooltip).build();
		}
		if (setting instanceof NumberSetting number) {
			SettingSlider slider = new SettingSlider(0, 0, 100, 20, number);
			slider.setTooltip(tooltip);
			return slider;
		}
		throw new IllegalArgumentException("Unsupported setting type: " + setting.getClass().getName());
	}

	private static Component booleanText(BooleanSetting setting) {
		return Component.literal(setting.label() + ": ")
				.append(Component.literal(setting.displayValue()).withStyle(setting.get() ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

	private static Component enumText(EnumSetting<?> setting) {
		return Component.literal(setting.label() + ": ")
				.append(Component.literal(setting.displayValue()).withStyle(ChatFormatting.AQUA));
	}
}
