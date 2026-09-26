package net.altarsmp.testclient.util;

import net.altarsmp.testclient.log.ActionLogger;
import net.altarsmp.testclient.mixin.MinecraftInvoker;
import net.altarsmp.testclient.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Attack helpers plus the callbacks invoked from the mixins. */
public final class CombatHooks {
	/** True while a module performs a scripted attack with a weapon it chose itself. */
	private static boolean suppressWeaponSwap;

	private CombatHooks() {
	}

	/**
	 * Attacks {@code target} with whatever is in the main hand (attack packet + swing), exactly like
	 * vanilla's attack path. When {@code allowWeaponSwap} is false, Breach Swap will not interfere.
	 */
	public static void attack(Entity target, boolean allowWeaponSwap) {
		Minecraft mc = Minecraft.getInstance();
		boolean previous = suppressWeaponSwap;
		suppressWeaponSwap = !allowWeaponSwap;
		try {
			mc.gameMode.attack(mc.player, target);
			mc.player.swing(InteractionHand.MAIN_HAND);
		} finally {
			suppressWeaponSwap = previous;
		}
	}

	/** Performs a vanilla left click on whatever the crosshair is on (respects miss cooldown etc). */
	public static void vanillaClick() {
		((MinecraftInvoker) Minecraft.getInstance()).altar$startAttack();
	}

	public static boolean isWeaponSwapSuppressed() {
		return suppressWeaponSwap;
	}

	/** From MultiPlayerGameModeMixin, before the attack packet is sent. */
	public static void beforeAttack(Player attacker, Entity target) {
		if (!suppressWeaponSwap && ServerGuard.isAllowed()) {
			ModuleManager.breachSwap().beforeAttack(target);
		}
	}

	/** From MultiPlayerGameModeMixin, after the attack packet has been sent. */
	public static void afterAttack(Player attacker, Entity target) {
		ModuleManager.breachSwap().afterAttack(target);
	}

	/** From ClientPacketListenerMixin when the server sets our own velocity (knockback), on the client thread. */
	public static void onOwnKnockback(Vec3 velocity) {
		ModuleManager.jumpReset().onKnockback(velocity);
	}

	/** From ClientPacketListenerMixin when an entity-event 35 (totem used) arrives. */
	public static void onTotemPop(Entity entity) {
		Minecraft mc = Minecraft.getInstance();
		if (entity == mc.player) {
			ActionLogger.logEvent("TOTEM_POP_SELF", "", "");
			ModuleManager.autoTotem().onOwnTotemPop();
		} else if (entity instanceof Player) {
			ActionLogger.logEvent("TOTEM_POP_OTHER", TargetUtil.describe(entity), "");
		}
	}
}
