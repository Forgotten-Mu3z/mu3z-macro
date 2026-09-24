package net.altarsmp.testclient.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.altarsmp.testclient.module.setting.BooleanSetting;
import net.altarsmp.testclient.module.setting.Setting;

/** Client-wide options (not tied to a module). */
public final class GlobalConfig {
	public static final List<String> DEFAULT_ALLOWED_SERVERS = List.of("localhost", "127.0.0.1");

	public final BooleanSetting allowSingleplayer = new BooleanSetting("allowSingleplayer", "Allow singleplayer",
			"Let modules run in singleplayer / integrated server worlds", true);
	public final BooleanSetting hud = new BooleanSetting("hud", "HUD overlay",
			"Show the enabled-module list in the top-left corner", true);
	public final BooleanSetting hudShowSpeed = new BooleanSetting("hudShowSpeed", "HUD speed tags",
			"Append [H] (humanlike) or [B] (blatant) to each module in the HUD", true);
	public final BooleanSetting logging = new BooleanSetting("logging", "Action log",
			"Write every automated action to .minecraft/altartestclient/logs", true);

	private final List<Setting<?>> settings = List.of(allowSingleplayer, hud, hudShowSpeed, logging);
	private List<String> allowedServers = new ArrayList<>(DEFAULT_ALLOWED_SERVERS);

	public List<Setting<?>> settings() {
		return settings;
	}

	public List<String> allowedServers() {
		return Collections.unmodifiableList(allowedServers);
	}

	public void setAllowedServers(List<String> servers) {
		List<String> cleaned = new ArrayList<>();
		for (String server : servers) {
			String trimmed = server.trim();
			if (!trimmed.isEmpty() && !cleaned.contains(trimmed)) {
				cleaned.add(trimmed);
			}
		}
		this.allowedServers = cleaned;
	}
}
