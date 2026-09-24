package net.altarsmp.testclient.util;

import java.util.List;
import java.util.Locale;
import net.altarsmp.testclient.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

/**
 * Keeps the client inert anywhere except the servers listed in {@code allowedServers} (plus
 * singleplayer if enabled). Evaluated once per tick; modules never run while this is false.
 */
public final class ServerGuard {
	private static boolean allowed;
	private static String currentHost = "";

	private ServerGuard() {
	}

	public static void update(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			allowed = false;
			currentHost = "";
			return;
		}
		currentHost = describeServer(mc);
		if (mc.hasSingleplayerServer()) {
			allowed = ConfigManager.global().allowSingleplayer.get();
		} else {
			ServerData server = mc.getCurrentServer();
			allowed = server != null && matchesAny(currentHost, ConfigManager.global().allowedServers());
		}
	}

	/** "singleplayer", the normalised host of the current server, or "unknown". */
	public static String describeServer(Minecraft mc) {
		if (mc.hasSingleplayerServer()) {
			return "singleplayer";
		}
		ServerData server = mc.getCurrentServer();
		return server == null ? "unknown" : normalizeHost(server.ip);
	}

	public static boolean isAllowed() {
		return allowed;
	}

	public static String currentHost() {
		return currentHost;
	}

	static boolean matchesAny(String host, List<String> patterns) {
		for (String pattern : patterns) {
			String p = normalizeHost(pattern);
			if (p.isEmpty()) {
				continue;
			}
			if (p.startsWith("*.")) {
				String suffix = p.substring(1);
				if (host.endsWith(suffix) || host.equals(p.substring(2))) {
					return true;
				}
			} else if (host.equals(p)) {
				return true;
			}
		}
		return false;
	}

	/** Lower-cases and strips the port / trailing dot: "Play.Example.net:25565" -> "play.example.net". */
	public static String normalizeHost(String address) {
		String host = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
		if (host.startsWith("[")) {
			int end = host.indexOf(']');
			host = end > 0 ? host.substring(1, end) : host;
		} else if (host.indexOf(':') == host.lastIndexOf(':')) {
			int colon = host.indexOf(':');
			host = colon >= 0 ? host.substring(0, colon) : host;
		}
		while (host.endsWith(".")) {
			host = host.substring(0, host.length() - 1);
		}
		return host;
	}
}
