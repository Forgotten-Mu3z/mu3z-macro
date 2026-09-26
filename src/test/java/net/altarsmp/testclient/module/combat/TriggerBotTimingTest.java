package net.altarsmp.testclient.module.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TriggerBotTimingTest {
	/** Cooldown in ticks the way vanilla computes it: 1 / attackSpeed * 20, attackSpeed = 4 + modifier (float). */
	private static float cooldownTicks(float speedModifier) {
		double attackSpeed = 4.0 + (double) speedModifier;
		return (float) (1.0 / attackSpeed * 20.0);
	}

	@Test
	void swordIsReadyAtTwelveTicksDespiteFloatRounding() {
		float sword = cooldownTicks(-2.4F);
		assertEquals(12.5F, sword, 1.0E-4F);
		// 12 + 0.5 >= 12.500001 within rounding: the server already scores this as a full hit.
		assertEquals(12, TriggerBot.perfectTicks(sword, 20.0, 0));
	}

	@Test
	void perWeaponCooldowns() {
		assertEquals(20, TriggerBot.perfectTicks(cooldownTicks(-3.0F), 20.0, 0)); // most axes (1.0 speed)
		assertEquals(25, TriggerBot.perfectTicks(cooldownTicks(-3.2F), 20.0, 0)); // wooden/stone axe (0.8)
		assertEquals(33, TriggerBot.perfectTicks(cooldownTicks(-3.4F), 20.0, 0)); // mace (0.6)
		assertEquals(18, TriggerBot.perfectTicks(cooldownTicks(-2.9F), 20.0, 0)); // trident (1.1)
		assertEquals(5, TriggerBot.perfectTicks(cooldownTicks(0.0F), 20.0, 0));   // empty hand (4.0)
	}

	@Test
	void slowServerNeedsMoreClientTicks() {
		float sword = cooldownTicks(-2.4F);
		assertEquals(12, TriggerBot.perfectTicks(sword, 19.7, 0)); // noise around 20 TPS changes nothing
		assertEquals(16, TriggerBot.perfectTicks(sword, 15.0, 0)); // 16 client ticks = 12 server ticks
		assertEquals(24, TriggerBot.perfectTicks(sword, 10.0, 0));
	}

	@Test
	void jitterSafetyAddsTicks() {
		float sword = cooldownTicks(-2.4F);
		assertEquals(13, TriggerBot.perfectTicks(sword, 20.0, 1));
		assertEquals(14, TriggerBot.perfectTicks(sword, 20.0, 2));
	}
}
