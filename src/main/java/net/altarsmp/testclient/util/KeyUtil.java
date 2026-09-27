package net.altarsmp.testclient.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Helpers for modules that press vanilla keys on the player's behalf. */
public final class KeyUtil {
	private KeyUtil() {
	}

	/** Whether the key or mouse button bound to {@code mapping} is physically held down right now. */
	public static boolean isPhysicallyDown(KeyMapping mapping) {
		InputConstants.Key key = KeyBindingHelper.getBoundKeyOf(mapping);
		Minecraft mc = Minecraft.getInstance();
		if (key.getType() == InputConstants.Type.KEYSYM) {
			return InputConstants.isKeyDown(mc.getWindow(), key.getValue());
		}
		if (key.getType() == InputConstants.Type.MOUSE) {
			return GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
		}
		return false;
	}
}
