package net.altarsmp.testclient.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ServerGuardTest {
	@Test
	void normalizesHosts() {
		assertEquals("play.example.net", ServerGuard.normalizeHost(" Play.Example.NET:25565 "));
		assertEquals("play.example.net", ServerGuard.normalizeHost("play.example.net."));
		assertEquals("127.0.0.1", ServerGuard.normalizeHost("127.0.0.1:25566"));
		assertEquals("::1", ServerGuard.normalizeHost("[::1]:25565"));
		assertEquals("", ServerGuard.normalizeHost(null));
	}

	@Test
	void matchesExactAndWildcardEntries() {
		List<String> allowed = List.of("localhost", "*.altarsmp.net", "  ");
		assertTrue(ServerGuard.matchesAny("localhost", allowed));
		assertTrue(ServerGuard.matchesAny("play.altarsmp.net", allowed));
		assertTrue(ServerGuard.matchesAny("altarsmp.net", allowed));
		assertFalse(ServerGuard.matchesAny("notaltarsmp.net", allowed));
		assertFalse(ServerGuard.matchesAny("hypixel.net", allowed));
		assertFalse(ServerGuard.matchesAny("", allowed));
	}
}
