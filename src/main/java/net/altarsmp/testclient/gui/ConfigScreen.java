package net.altarsmp.testclient.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.altarsmp.testclient.config.ConfigManager;
import net.altarsmp.testclient.config.GlobalConfig;
import net.altarsmp.testclient.input.Keybinds;
import net.altarsmp.testclient.log.ActionLogger;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.ModuleManager;
import net.altarsmp.testclient.module.setting.Setting;
import net.altarsmp.testclient.util.ServerGuard;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Settings screen (Right Shift by default). Left column: "Global settings" plus one row per module with an
 * ON/OFF toggle. Right panel: the selected module's key bind and settings (buttons for booleans/enums,
 * sliders for numbers; hover for descriptions). Changes apply immediately and are saved to
 * config/altartestclient.json. The panic key works here too.
 */
public final class ConfigScreen extends Screen {
	private static final int TEXT_WHITE = 0xFFFFFFFF;
	private static final int TEXT_YELLOW = 0xFFFFFF55;
	private static final int TEXT_GRAY = 0xFFAAAAAA;
	private static final int TEXT_GREEN = 0xFF55FF55;
	private static final int TEXT_RED = 0xFFFF5555;
	private static final int HEADER_HEIGHT = 24;

	private static @Nullable Module lastSelected;

	private final @Nullable Screen parent;
	private @Nullable Module selected;
	private boolean capturingKey;
	private int panelX;
	private int panelY;
	private int panelWidth;
	private int allowListLabelY = -1;

	public ConfigScreen(@Nullable Screen parent) {
		super(Component.literal("AltarTestClient"));
		this.parent = parent;
		this.selected = lastSelected;
	}

	@Override
	protected void init() {
		List<Module> modules = ModuleManager.all();
		int top = 26;
		int bottomBarY = height - 26;
		int rowHeight = Mth.clamp((bottomBarY - 4 - top) / (modules.size() + 1), 12, 22);
		int buttonHeight = rowHeight - 2;
		int totalWidth = Math.min(width - 16, 470);
		int left = (width - totalWidth) / 2;
		int listWidth = Math.min(160, totalWidth / 3 + 10);
		int toggleWidth = 34;

		addRenderableWidget(Button.builder(entryLabel("Global settings", selected == null), b -> select(null))
				.bounds(left, top, listWidth, buttonHeight).build());
		for (int i = 0; i < modules.size(); i++) {
			Module module = modules.get(i);
			int y = top + (i + 1) * rowHeight;
			addRenderableWidget(Button.builder(entryLabel(module.name(), module == selected), b -> select(module))
					.bounds(left, y, listWidth - toggleWidth - 2, buttonHeight)
					.tooltip(Tooltip.create(Component.literal(module.description()))).build());
			addRenderableWidget(Button.builder(onOffText(module), b -> {
				module.toggle();
				b.setMessage(onOffText(module));
			}).bounds(left + listWidth - toggleWidth, y, toggleWidth, buttonHeight)
					.tooltip(Tooltip.create(Component.literal(module.kind() == Module.Kind.ACTION
							? "Arm / disarm the key for this one-shot action" : "Enable / disable"))).build());
		}

		panelX = left + listWidth + 8;
		panelY = top;
		panelWidth = totalWidth - listWidth - 8;
		int contentTop = top + HEADER_HEIGHT;
		int contentBottom = bottomBarY - 4;
		allowListLabelY = -1;

		List<AbstractWidget> widgets = new ArrayList<>();
		if (selected == null) {
			contentTop = buildGlobalPanel(widgets, contentTop, rowHeight, buttonHeight);
		} else {
			widgets.add(keyBindButton(selected));
			for (Setting<?> setting : selected.settings()) {
				widgets.add(SettingWidgets.create(setting));
			}
		}
		layoutColumns(widgets, contentTop, contentBottom, rowHeight, buttonHeight);

		int bottomWidth = Math.min(100, (totalWidth - 8) / 3);
		int bottomLeft = width / 2 - (bottomWidth * 3 + 8) / 2;
		addRenderableWidget(Button.builder(Component.literal("Panic").withStyle(ChatFormatting.RED), b -> {
			ModuleManager.panic();
			rebuildWidgets();
		}).bounds(bottomLeft, bottomBarY, bottomWidth, 20)
				.tooltip(Tooltip.create(Component.literal("Disable every module right now"))).build());
		addRenderableWidget(Button.builder(Component.literal("Open logs"), b -> openLogs())
				.bounds(bottomLeft + bottomWidth + 4, bottomBarY, bottomWidth, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(bottomLeft + (bottomWidth + 4) * 2, bottomBarY, bottomWidth, 20).build());
	}

	/** Adds the allow-list box and global toggles; returns the y where the column layout should start. */
	private int buildGlobalPanel(List<AbstractWidget> widgets, int contentTop, int rowHeight, int buttonHeight) {
		GlobalConfig global = ConfigManager.global();
		allowListLabelY = contentTop;
		EditBox allowList = new EditBox(font, panelX, contentTop + 11, panelWidth, Math.max(buttonHeight, 16),
				Component.literal("Allowed servers"));
		allowList.setMaxLength(1024);
		allowList.setValue(String.join(", ", global.allowedServers()));
		allowList.setHint(Component.literal("localhost, play.example.net, *.example.net").withStyle(ChatFormatting.DARK_GRAY));
		allowList.setResponder(text -> {
			global.setAllowedServers(Arrays.asList(text.split(",")));
			ConfigManager.markDirty();
		});
		allowList.setTooltip(Tooltip.create(Component.literal(
				"Modules only run on these hosts (port ignored, *.domain allowed). Singleplayer is controlled below.")));
		addRenderableWidget(allowList);

		for (Setting<?> setting : global.settings()) {
			widgets.add(SettingWidgets.create(setting));
		}
		widgets.add(Button.builder(Component.literal("Reload config file"), b -> {
			ConfigManager.load();
			rebuildWidgets();
		}).tooltip(Tooltip.create(Component.literal("Re-read config/altartestclient.json"))).build());
		return contentTop + 11 + Math.max(buttonHeight, 16) + 6;
	}

	/** One column, or two if the widgets do not fit vertically. */
	private void layoutColumns(List<AbstractWidget> widgets, int contentTop, int contentBottom, int rowHeight, int buttonHeight) {
		if (widgets.isEmpty()) {
			return;
		}
		int fitRows = Math.max(1, (contentBottom - contentTop) / rowHeight);
		int columns = widgets.size() > fitRows ? 2 : 1;
		int perColumn = (widgets.size() + columns - 1) / columns;
		int columnWidth = columns == 2 ? (panelWidth - 4) / 2 : panelWidth;
		for (int i = 0; i < widgets.size(); i++) {
			int column = i / perColumn;
			int row = i % perColumn;
			AbstractWidget widget = widgets.get(i);
			widget.setRectangle(columnWidth, buttonHeight, panelX + column * (columnWidth + 4), contentTop + row * rowHeight);
			addRenderableWidget(widget);
		}
	}

	private Button keyBindButton(Module module) {
		return Button.builder(keyText(module), b -> {
			capturingKey = true;
			b.setMessage(Component.literal("Key: ").append(Component.literal("> press a key <").withStyle(ChatFormatting.YELLOW)));
		}).tooltip(Tooltip.create(Component.literal(module.kind() == Module.Kind.ACTION
				? "Key that performs the action (module must be ON). Esc while binding = unbind."
				: "Key that toggles this module. Esc while binding = unbind."))).build();
	}

	private void select(@Nullable Module module) {
		selected = module;
		capturingKey = false;
		rebuildWidgets();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturingKey && selected != null && selected.keyMapping() != null) {
			KeyMapping mapping = selected.keyMapping();
			mapping.setKey(event.isEscape() ? InputConstants.UNKNOWN : InputConstants.getKey(event));
			KeyMapping.resetMapping();
			minecraft.options.save();
			capturingKey = false;
			rebuildWidgets();
			return true;
		}
		if (!(getFocused() instanceof EditBox) && Keybinds.panicKey().matches(event)) {
			ModuleManager.panic();
			rebuildWidgets();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		graphics.drawCenteredString(font, title, width / 2, 9, TEXT_RED);

		String header = selected == null ? "Global settings" : selected.name();
		graphics.drawString(font, header, panelX, panelY + 2, TEXT_YELLOW, true);
		String subtitle;
		int subtitleColor = TEXT_GRAY;
		if (selected == null) {
			boolean active = ServerGuard.isAllowed();
			subtitle = "Current: " + (ServerGuard.currentHost().isEmpty() ? "not connected" : ServerGuard.currentHost())
					+ (active ? " - ACTIVE" : " - inactive");
			subtitleColor = active ? TEXT_GREEN : TEXT_GRAY;
		} else {
			subtitle = selected.description();
		}
		graphics.drawString(font, font.plainSubstrByWidth(subtitle, panelWidth), panelX, panelY + 12, subtitleColor, false);
		if (allowListLabelY >= 0) {
			graphics.drawString(font, "Allowed servers (comma separated)", panelX, allowListLabelY, TEXT_WHITE, false);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void removed() {
		lastSelected = selected;
		ConfigManager.save();
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	private void openLogs() {
		Path current = ActionLogger.currentFile();
		Path dir = ActionLogger.logDir();
		try {
			Files.createDirectories(dir);
		} catch (Exception ignored) {
			// Opening will simply fail if the folder cannot be created.
		}
		Util.getPlatform().openPath(current != null ? current.getParent() : dir);
	}

	private static Component entryLabel(String name, boolean isSelected) {
		return isSelected ? Component.literal("> " + name).withStyle(ChatFormatting.YELLOW) : Component.literal(name);
	}

	private static Component onOffText(Module module) {
		return module.isEnabled()
				? Component.literal("ON").withStyle(ChatFormatting.GREEN)
				: Component.literal("OFF").withStyle(ChatFormatting.RED);
	}

	private static Component keyText(Module module) {
		KeyMapping mapping = module.keyMapping();
		Component key = mapping == null || mapping.isUnbound()
				? Component.literal("none").withStyle(ChatFormatting.GRAY)
				: mapping.getTranslatedKeyMessage().copy().withStyle(ChatFormatting.AQUA);
		return Component.literal("Key: ").append(key);
	}
}
