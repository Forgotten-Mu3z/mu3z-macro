package net.altarsmp.testclient.mixin;

import net.altarsmp.testclient.util.RotationSpoof;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silent aim for Punch Bow's self boost: while {@link RotationSpoof} is active, the pitch is swapped in only
 * while vanilla builds the per-tick movement packet, then restored, so the camera never moves. Using the regular
 * packet (not an extra rotation-only one) keeps your real position change in it, which the server passes on to
 * the arrow.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void altar$swapInSpoofedPitch(CallbackInfo ci) {
		RotationSpoof.beforeSend((LocalPlayer) (Object) this);
	}

	@Inject(method = "sendPosition", at = @At("RETURN"))
	private void altar$restoreCameraPitch(CallbackInfo ci) {
		RotationSpoof.afterSend((LocalPlayer) (Object) this);
	}
}
