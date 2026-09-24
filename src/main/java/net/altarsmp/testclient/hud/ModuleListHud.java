package net.altarsmp.testclient.hud;

import java.util.ArrayList;
import java.util.List;
import net.altarsmp.testclient.AltarTestClient;
import net.altarsmp.testclient.config.ConfigManager;
import net.altarsmp.testclient.config.GlobalConfig;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.ModuleManager;
import net.altarsmp.testclient.module.SpeedMode;
import net.altarsmp.testclient.util.ServerGuard;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/**
 * Top-left overlay listing enabled modules (with [H]/[B] speed tags), a warning when the current server
 * is not allow-listed, and a short banner after the panic key. Hidden with F1 and while F3 is open.
 * Colours are full ARGB: text with alpha 0 is invisible on 1.21.6+.
 */
public final class ModuleListHud implements HudElement {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(AltarTestClient.MOD_ID, "module_list");

	private static final int TITLE_COLOR = 0xFFFF5555;
	private static final int HUMANLIKE_COLOR = 0xFF55FF55;
	private static final int BLATANT_COLOR = 0xFFFFAA00;
	private static final int WARNING_COLOR = 0xFFFFFF55;
	private static final int PANIC_COLOR = 0xFFFF5555;
	private static final int BACKGROUND = 0x80000000;
	private static final long PANIC_BANNER_MS = 3000;

	private record Line(String text, int color) {
	}

	@Override
	public void render(GuiGraphics graphics, DeltaTracker tickCounter) {
		Minecraft mc = Minecraft.getInstance();
		GlobalConfig global = ConfigManager.global();
		if (!global.hud.get() || mc.options.hideGui || mc.player == null || mc.getDebugOverlay().showDebugScreen()) {
			return;
		}
		List<Line> lines = new ArrayList<>();
		lines.add(new Line("AltarTestClient", TITLE_COLOR));
		if (System.currentTimeMillis() - ModuleManager.lastPanicMillis() < PANIC_BANNER_MS) {
			lines.add(new Line("PANIC - all modules off", PANIC_COLOR));
		}
		if (!ServerGuard.isAllowed()) {
			lines.add(new Line("Inactive: " + ServerGuard.currentHost() + " not allow-listed", WARNING_COLOR));
		}
		for (Module module : ModuleManager.all()) {
			if (!module.isEnabled()) {
				continue;
			}
			String text = module.name();
			if (module.kind() == Module.Kind.ACTION) {
				text += " (armed)";
			}
			if (global.hudShowSpeed.get()) {
				text += " [" + module.speedMode().tag() + "]";
			}
			lines.add(new Line(text, module.speedMode() == SpeedMode.BLATANT ? BLATANT_COLOR : HUMANLIKE_COLOR));
		}

		Font font = mc.font;
		int x = 4;
		int y = 4;
		int width = 0;
		for (Line line : lines) {
			width = Math.max(width, font.width(line.text()));
		}
		int lineHeight = font.lineHeight + 1;
		graphics.fill(x - 2, y - 2, x + width + 2, y + lines.size() * lineHeight, BACKGROUND);
		for (Line line : lines) {
			graphics.drawString(font, line.text(), x, y, line.color(), true);
			y += lineHeight;
		}
	}
}
