package net.altarsmp.testclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServerClockTest {
	private static final long MS = 1_000_000L;

	@BeforeEach
	void setUp() {
		ServerClock.reset();
	}

	@Test
	void steadyServerIsTwentyTpsWithNoJitter() {
		for (int i = 0; i < 10; i++) {
			ServerClock.record(i * 1000 * MS, 1000 + i * 20L);
		}
		assertEquals(20.0, ServerClock.tps(), 1e-6);
		assertEquals(0.0, ServerClock.jitterMs(), 1e-6);
		assertEquals(0, ServerClock.safetyTicks());
	}

	@Test
	void laggingServerMeasuresLowerTps() {
		for (int i = 0; i < 10; i++) {
			ServerClock.record(i * 1333 * MS, i * 20L);
		}
		assertEquals(15.0, ServerClock.tps(), 0.05);
	}

	@Test
	void jitteryArrivalsAddSafetyTicks() {
		long[] offsetsMs = {0, 40, -35, 30, -40, 45, -30, 35, -45, 40};
		for (int i = 0; i < offsetsMs.length; i++) {
			ServerClock.record((i * 1000 + offsetsMs[i]) * MS, i * 20L);
		}
		assertTrue(ServerClock.jitterMs() > 30, "jitter " + ServerClock.jitterMs());
		assertTrue(ServerClock.safetyTicks() >= 1);
	}

	@Test
	void timeJumpStartsFreshEstimate() {
		for (int i = 0; i < 5; i++) {
			ServerClock.record(i * 1333 * MS, i * 20L);
		}
		ServerClock.record(6000 * MS, 10L); // game time went backwards (world change)
		assertEquals(20.0, ServerClock.tps(), 1e-9);
	}
}
