package net.altarsmp.testclient.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.altarsmp.testclient.AltarTestClient;
import net.altarsmp.testclient.gui.ConfigScreen;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.ModuleManager;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Registers one vanilla key mapping per module plus "open GUI" and "panic". They show up under
 * Options -> Controls -> Key Binds -> AltarTestClient and are saved in options.txt; module keys can also
 * be rebound from the AltarTestClient screen. Module keys start unbound.
 */
public final class Keybinds {
	public static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(AltarTestClient.MOD_ID, "main"));

	private static KeyMapping openGui;
	private static KeyMapping panic;

	private Keybinds() {
	}

	public static void register() {
		openGui = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.altartestclient.open_gui", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, CATEGORY));
		panic = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.altartestclient.panic", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, CATEGORY));
		for (Module module : ModuleManager.all()) {
			module.setKeyMapping(KeyBindingHelper.registerKeyBinding(new KeyMapping(
					"key.altartestclient." + module.id(), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY)));
		}
	}

	public static KeyMapping panicKey() {
		return panic;
	}

	public static KeyMapping openGuiKey() {
		return openGui;
	}

	/** Called every client tick. Vanilla only counts key presses while no screen is open. */
	public static void poll(Minecraft mc) {
		boolean panicked = false;
		while (panic.consumeClick()) {
			panicked = true;
		}
		if (panicked) {
			ModuleManager.panic();
		}
		while (openGui.consumeClick()) {
			mc.setScreen(new ConfigScreen(mc.screen));
		}
		for (Module module : ModuleManager.all()) {
			KeyMapping key = module.keyMapping();
			while (key != null && key.consumeClick()) {
				if (!panicked) {
					module.onKeyPressed();
				}
			}
		}
	}
}
