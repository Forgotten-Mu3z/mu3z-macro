package net.altarsmp.testclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HumanizerTest {
	@Test
	void delaysStayInsideRangeAndCentreOnMidpoint() {
		long sum = 0;
		int samples = 20_000;
		for (int i = 0; i < samples; i++) {
			long delay = Humanizer.delayMs(60, 140);
			assertTrue(delay >= 60 && delay <= 140, "delay out of range: " + delay);
			sum += delay;
		}
		assertEquals(100.0, sum / (double) samples, 2.0);
	}

	@Test
	void handlesSwappedAndDegenerateBounds() {
		assertEquals(50, Humanizer.delayMs(50, 50));
		long delay = Humanizer.delayMs(200, 100);
		assertTrue(delay >= 100 && delay <= 200);
		assertEquals(0, Humanizer.delayMs(-5, 0));
	}
}
