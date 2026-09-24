package net.altarsmp.testclient.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client-side-only feedback messages (never sent to the server). */
public final class Chat {
	private Chat() {
	}

	public static void info(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.GRAY));
	}

	public static void warn(String message) {
		send(Component.literal(message).withStyle(ChatFormatting.GOLD));
	}

	public static void actionBar(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.GOLD), true);
		}
	}

	private static void send(Component body) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.displayClientMessage(Component.literal("[AltarTest] ").withStyle(ChatFormatting.RED).append(body), false);
		}
	}
}
