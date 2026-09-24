package net.altarsmp.testclient.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.altarsmp.testclient.AltarTestClient;
import net.altarsmp.testclient.log.ActionLogger;
import net.altarsmp.testclient.module.combat.AimAssist;
import net.altarsmp.testclient.module.combat.AnchorMacro;
import net.altarsmp.testclient.module.combat.AutoCrystal;
import net.altarsmp.testclient.module.combat.AutoTotem;
import net.altarsmp.testclient.module.combat.Autoclicker;
import net.altarsmp.testclient.module.combat.BreachSwap;
import net.altarsmp.testclient.module.combat.ShieldBreaker;
import net.altarsmp.testclient.module.combat.StunSlam;
import net.altarsmp.testclient.module.combat.TriggerBot;
import net.altarsmp.testclient.module.utility.ElytraSwap;
import net.altarsmp.testclient.module.utility.PearlMacro;
import net.altarsmp.testclient.util.ServerGuard;
import net.minecraft.client.Minecraft;

/** Owns every module and dispatches ticks, frames and panic. */
public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();
	private static BreachSwap breachSwap;
	private static AutoTotem autoTotem;
	private static long lastPanicMillis;

	private ModuleManager() {
	}

	public static void init() {
		MODULES.clear();
		register(new StunSlam());
		register(new TriggerBot());
		register(new AimAssist());
		autoTotem = register(new AutoTotem());
		register(new AutoCrystal());
		register(new AnchorMacro());
		breachSwap = register(new BreachSwap());
		register(new ShieldBreaker());
		register(new PearlMacro());
		register(new ElytraSwap());
		register(new Autoclicker());
	}

	private static <M extends Module> M register(M module) {
		MODULES.add(module);
		return module;
	}

	public static List<Module> all() {
		return Collections.unmodifiableList(MODULES);
	}

	public static BreachSwap breachSwap() {
		return breachSwap;
	}

	public static AutoTotem autoTotem() {
		return autoTotem;
	}

	private static boolean canRun(Minecraft mc) {
		return ServerGuard.isAllowed() && mc.player != null && mc.level != null && mc.gameMode != null;
	}

	/** End of every client tick. */
	public static void tick(Minecraft mc) {
		if (!canRun(mc)) {
			return;
		}
		for (Module module : MODULES) {
			if (!module.isEnabled() || (mc.screen != null && !module.runsWithScreenOpen())) {
				continue;
			}
			try {
				module.onTick();
			} catch (RuntimeException e) {
				AltarTestClient.LOGGER.error("Module {} crashed during tick, disabling it", module.name(), e);
				module.setEnabled(false);
			}
		}
	}

	/** Every rendered frame while the mouse is grabbed (from MouseHandlerMixin). */
	public static void frame(double frameSeconds) {
		Minecraft mc = Minecraft.getInstance();
		if (!canRun(mc) || mc.screen != null) {
			return;
		}
		double dt = Math.max(0.0, Math.min(frameSeconds, 0.1));
		for (Module module : MODULES) {
			if (module.isEnabled()) {
				try {
					module.onFrame(dt);
				} catch (RuntimeException e) {
					AltarTestClient.LOGGER.error("Module {} crashed during frame, disabling it", module.name(), e);
					module.setEnabled(false);
				}
			}
		}
	}

	/** Master kill switch: disables every module and aborts every in-flight sequence immediately. */
	public static void panic() {
		for (Module module : MODULES) {
			module.setEnabled(false);
		}
		ActionLock.clear();
		lastPanicMillis = System.currentTimeMillis();
		ActionLogger.logEvent("PANIC", "", "all modules disabled");
		AltarTestClient.LOGGER.warn("Panic key pressed: all modules disabled");
	}

	public static long lastPanicMillis() {
		return lastPanicMillis;
	}

	/** Aborts sequences without changing enabled flags (disconnect / world change). */
	public static void resetAll() {
		for (Module module : MODULES) {
			module.resetState();
		}
		ActionLock.clear();
	}
}
