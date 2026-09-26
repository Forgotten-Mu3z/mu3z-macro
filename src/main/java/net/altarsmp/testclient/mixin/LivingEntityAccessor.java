package net.altarsmp.testclient.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Raw attack-cooldown tick counter (ticks since the last attack or weapon switch). Vanilla only exposes it
 * clamped as a 0..1 fraction; Trigger Bot's perfect timing needs the exact tick count.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
	@Accessor("attackStrengthTicker")
	int altar$getAttackStrengthTicker();
}
