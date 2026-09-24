package net.altarsmp.testclient.mixin;

import net.altarsmp.testclient.util.CombatHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects totem pops (entity event 35). Injected at TAIL so it only runs on the client thread, after
 * vanilla has re-dispatched the packet from the network thread.
 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	private static final byte TOTEM_USE_EVENT = 35;

	@Inject(method = "handleEntityEvent", at = @At("TAIL"))
	private void altar$onEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (packet.getEventId() != TOTEM_USE_EVENT || mc.level == null) {
			return;
		}
		Entity entity = packet.getEntity(mc.level);
		if (entity != null) {
			CombatHooks.onTotemPop(entity);
		}
	}
}
