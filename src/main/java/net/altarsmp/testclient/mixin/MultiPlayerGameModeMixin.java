package net.altarsmp.testclient.mixin;

import net.altarsmp.testclient.util.CombatHooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps every attack the client sends so Breach Swap can change the held item right before the attack
 * packet and restore it afterwards.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void altar$beforeAttack(Player player, Entity target, CallbackInfo ci) {
		CombatHooks.beforeAttack(player, target);
	}

	@Inject(method = "attack", at = @At("RETURN"))
	private void altar$afterAttack(Player player, Entity target, CallbackInfo ci) {
		CombatHooks.afterAttack(player, target);
	}
}
