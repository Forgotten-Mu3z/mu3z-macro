package net.altarsmp.testclient;

import net.altarsmp.testclient.config.ConfigManager;
import net.altarsmp.testclient.hud.ModuleListHud;
import net.altarsmp.testclient.input.Keybinds;
import net.altarsmp.testclient.log.ActionLogger;
import net.altarsmp.testclient.module.ModuleManager;
import net.altarsmp.testclient.util.Chat;
import net.altarsmp.testclient.util.ClientTicks;
import net.altarsmp.testclient.util.ServerClock;
import net.altarsmp.testclient.util.ServerGuard;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point. Wires modules, config, key mappings, HUD, logging and the tick loop together.
 *
 * <p>Order of work each client tick (END_CLIENT_TICK, i.e. after the vanilla tick):
 * key presses, allow-list check, module ticks, log flush, debounced config save.
 */
public final class AltarTestClient implements ClientModInitializer {
	public static final String MOD_ID = "altartestclient";
	public static final Logger LOGGER = LoggerFactory.getLogger("AltarTestClient");

	private static boolean announcePending;

	@Override
	public void onInitializeClient() {
		ModuleManager.init();
		ConfigManager.load();
		Keybinds.register();
		HudElementRegistry.addLast(ModuleListHud.ID, new ModuleListHud());

		ClientTickEvents.END_CLIENT_TICK.register(AltarTestClient::onEndTick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onJoin(client));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ModuleManager.resetAll();
			ConfigManager.save();
			ActionLogger.endSession();
		});
		LOGGER.info("AltarTestClient loaded with {} modules", ModuleManager.all().size());
	}

	private static void onEndTick(Minecraft mc) {
		ClientTicks.increment();
		ServerGuard.update(mc);
		Keybinds.poll(mc);
		if (announcePending && mc.player != null) {
			announcePending = false;
			announce();
		}
		ModuleManager.tick(mc);
		ActionLogger.flush();
		ConfigManager.tick();
	}

	private static void onJoin(Minecraft mc) {
		String host = ServerGuard.describeServer(mc);
		ServerClock.reset();
		ActionLogger.startSession(host);
		ActionLogger.logEvent("SESSION_START", host, "");
		announcePending = true;
	}

	private static void onDisconnect() {
		ModuleManager.resetAll();
		ServerClock.reset();
		ActionLogger.logEvent("SESSION_END", ServerGuard.currentHost(), "");
		ActionLogger.endSession();
		ConfigManager.save();
	}

	private static void announce() {
		ActionLogger.logEvent("GUARD", ServerGuard.currentHost(), ServerGuard.isAllowed() ? "allowed" : "blocked");
		if (ServerGuard.isAllowed()) {
			Chat.info("Active on " + ServerGuard.currentHost() + ". "
					+ Keybinds.openGuiKey().getTranslatedKeyMessage().getString() + " = settings, "
					+ Keybinds.panicKey().getTranslatedKeyMessage().getString() + " = panic.");
		} else {
			Chat.warn("Inactive: '" + ServerGuard.currentHost() + "' is not in allowedServers (config/altartestclient.json).");
		}
	}
}
