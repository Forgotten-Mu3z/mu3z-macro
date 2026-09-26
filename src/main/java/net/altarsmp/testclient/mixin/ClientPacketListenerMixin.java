package net.altarsmp.testclient.mixin;

import net.altarsmp.testclient.util.CombatHooks;
import net.altarsmp.testclient.util.ServerClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Detects totem pops (entity event 35), knockback applied to our own player (for Jump Reset), and times the
 * server's time-sync packets for {@link ServerClock}.
 * Vanilla handlers first run on the network thread and immediately re-queue themselves for the client
 * thread, so HEAD sees each packet twice (network thread first) and TAIL only on the client thread.
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

	@Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
	private void altar$onSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && packet.getId() == mc.player.getId()) {
			CombatHooks.onOwnKnockback(packet.getMovement());
		}
	}

	@Inject(method = "handleSetTime", at = @At("HEAD"))
	private void altar$onSetTimeArrived(ClientboundSetTimePacket packet, CallbackInfo ci) {
		if (!Minecraft.getInstance().isSameThread()) {
			ServerClock.onTimeSync(packet.gameTime());
		}
	}

	@Inject(method = "handleSetTime", at = @At("TAIL"))
	private void altar$onSetTimeHandled(ClientboundSetTimePacket packet, CallbackInfo ci) {
		ServerClock.onTimeSyncHandled(packet.gameTime());
	}
}
