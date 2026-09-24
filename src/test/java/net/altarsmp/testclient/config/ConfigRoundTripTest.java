package net.altarsmp.testclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.ModuleManager;
import net.altarsmp.testclient.module.setting.Setting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConfigRoundTripTest {
	@BeforeEach
	void setUp() {
		ModuleManager.init();
		GlobalConfig global = ConfigManager.global();
		global.setAllowedServers(GlobalConfig.DEFAULT_ALLOWED_SERVERS);
		global.settings().forEach(Setting::reset);
	}

	private static JsonElement bundledDefault() throws Exception {
		try (Reader reader = new InputStreamReader(
				ConfigRoundTripTest.class.getResourceAsStream("/altartestclient-default.json"), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader);
		}
	}

	@Test
	void bundledDefaultMatchesCodeDefaults() throws Exception {
		assertEquals(bundledDefault(), ConfigManager.serialize());
	}

	@Test
	void everyModuleHasUniqueIdAndSettingKeys() {
		Set<String> ids = new HashSet<>();
		for (Module module : ModuleManager.all()) {
			assertTrue(ids.add(module.id()), "duplicate module id " + module.id());
			Set<String> keys = new HashSet<>();
			keys.add("enabled");
			for (Setting<?> setting : module.settings()) {
				assertTrue(keys.add(setting.key()), "duplicate key " + setting.key() + " in " + module.id());
			}
		}
		assertEquals(11, ModuleManager.all().size());
	}

	@Test
	void editedValuesSurviveRoundTrip() {
		JsonObject edited = ConfigManager.serialize();
		JsonObject trigger = edited.getAsJsonObject("modules").getAsJsonObject("trigger_bot");
		trigger.addProperty("speed", "BLATANT");
		trigger.addProperty("delayMin", 75);
		edited.getAsJsonObject("global").getAsJsonArray("allowedServers").add("play.altarsmp.net");

		ConfigManager.apply(edited);
		JsonObject reserialized = ConfigManager.serialize();
		assertEquals(edited, reserialized);
		assertTrue(ConfigManager.global().allowedServers().contains("play.altarsmp.net"));
	}
}
