package net.altarsmp.testclient.mixin;

import net.altarsmp.testclient.module.ModuleManager;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code turnPlayer} runs once per rendered frame while the mouse is grabbed, right after mouse movement is
 * applied to the camera; Aim Assist adds its own turn here so it is as smooth as mouse input.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("TAIL"))
	private void altar$afterTurnPlayer(double frameSeconds, CallbackInfo ci) {
		ModuleManager.frame(frameSeconds);
	}
}
