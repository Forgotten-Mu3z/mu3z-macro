package net.altarsmp.testclient.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes vanilla's private left-click handler (so Trigger Bot / Autoclicker click exactly like the mouse)
 * and the right-click repeat delay (so Trigger Bot's unshield step decides when the shield comes back up,
 * instead of vanilla re-raising it on the next tick while the use key is held).
 */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {
	@Invoker("startAttack")
	boolean altar$startAttack();

	@Accessor("rightClickDelay")
	void altar$setRightClickDelay(int ticks);
}
