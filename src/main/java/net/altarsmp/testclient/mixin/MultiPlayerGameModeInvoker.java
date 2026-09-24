package net.altarsmp.testclient.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the private "send held-item packet if the selected slot changed" method. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeInvoker {
	@Invoker("ensureHasSentCarriedItem")
	void altar$ensureHasSentCarriedItem();
}
