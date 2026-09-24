package net.altarsmp.testclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's private left-click handler so Trigger Bot / Autoclicker click exactly like the mouse. */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {
	@Invoker("startAttack")
	boolean altar$startAttack();
}
