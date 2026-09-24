package net.altarsmp.testclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.altarsmp.testclient.AltarTestClient;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.module.ModuleManager;
import net.altarsmp.testclient.module.setting.Setting;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Reads and writes {@code config/altartestclient.json}. On first launch the bundled
 * {@code altartestclient-default.json} is copied into place. Keys missing from the file keep their
 * code defaults, unknown keys are ignored. Changes are saved at most once a second.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final String FILE_NAME = "altartestclient.json";
	private static final String BUNDLED_DEFAULT = "/altartestclient-default.json";
	private static final long SAVE_DEBOUNCE_MS = 1000;
	private static final int CONFIG_VERSION = 1;

	private static final GlobalConfig GLOBAL = new GlobalConfig();
	private static boolean dirty;
	private static long dirtySince;

	private ConfigManager() {
	}

	public static GlobalConfig global() {
		return GLOBAL;
	}

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
	}

	public static void load() {
		Path path = path();
		try {
			if (Files.notExists(path)) {
				copyBundledDefault(path);
			}
			if (Files.exists(path)) {
				try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
					apply(JsonParser.parseReader(reader));
				}
			}
			AltarTestClient.LOGGER.info("Loaded config from {}", path);
		} catch (Exception e) {
			AltarTestClient.LOGGER.error("Could not read {}, using defaults", path, e);
		}
		dirty = false;
	}

	private static void copyBundledDefault(Path path) throws IOException {
		try (InputStream in = ConfigManager.class.getResourceAsStream(BUNDLED_DEFAULT)) {
			Files.createDirectories(path.getParent());
			if (in != null) {
				Files.copy(in, path);
			} else {
				writeTo(path);
			}
		}
	}

	static void apply(JsonElement root) {
		if (root == null || !root.isJsonObject()) {
			return;
		}
		JsonObject json = root.getAsJsonObject();
		if (json.has("global") && json.get("global").isJsonObject()) {
			JsonObject global = json.getAsJsonObject("global");
			readSettings(global, GLOBAL.settings());
			if (global.has("allowedServers") && global.get("allowedServers").isJsonArray()) {
				List<String> servers = new ArrayList<>();
				for (JsonElement element : global.getAsJsonArray("allowedServers")) {
					if (element.isJsonPrimitive()) {
						servers.add(element.getAsString());
					}
				}
				GLOBAL.setAllowedServers(servers);
			}
		}
		if (json.has("modules") && json.get("modules").isJsonObject()) {
			JsonObject modules = json.getAsJsonObject("modules");
			for (Module module : ModuleManager.all()) {
				if (!modules.has(module.id()) || !modules.get(module.id()).isJsonObject()) {
					continue;
				}
				JsonObject obj = modules.getAsJsonObject(module.id());
				readSettings(obj, module.settings());
				if (obj.has("enabled") && obj.get("enabled").isJsonPrimitive()) {
					module.setEnabled(obj.get("enabled").getAsBoolean(), false);
				}
			}
		}
	}

	private static void readSettings(JsonObject obj, List<Setting<?>> settings) {
		for (Setting<?> setting : settings) {
			if (obj.has(setting.key())) {
				setting.fromJson(obj.get(setting.key()));
			}
		}
	}

	static JsonObject serialize() {
		JsonObject root = new JsonObject();
		root.addProperty("configVersion", CONFIG_VERSION);

		JsonObject global = new JsonObject();
		JsonArray servers = new JsonArray();
		GLOBAL.allowedServers().forEach(servers::add);
		global.add("allowedServers", servers);
		for (Setting<?> setting : GLOBAL.settings()) {
			global.add(setting.key(), setting.toJson());
		}
		root.add("global", global);

		JsonObject modules = new JsonObject();
		for (Module module : ModuleManager.all()) {
			JsonObject obj = new JsonObject();
			obj.addProperty("enabled", module.isEnabled());
			for (Setting<?> setting : module.settings()) {
				obj.add(setting.key(), setting.toJson());
			}
			modules.add(module.id(), obj);
		}
		root.add("modules", modules);
		return root;
	}

	public static void markDirty() {
		if (!dirty) {
			dirty = true;
			dirtySince = System.currentTimeMillis();
		}
	}

	/** Called every client tick; saves once changes have settled. */
	public static void tick() {
		if (dirty && System.currentTimeMillis() - dirtySince >= SAVE_DEBOUNCE_MS) {
			save();
		}
	}

	public static void save() {
		try {
			writeTo(path());
			dirty = false;
		} catch (IOException e) {
			AltarTestClient.LOGGER.error("Could not save config", e);
		}
	}

	private static void writeTo(Path path) throws IOException {
		Files.createDirectories(path.getParent());
		Path temp = path.resolveSibling(FILE_NAME + ".tmp");
		try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
			GSON.toJson(serialize(), writer);
		}
		Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
	}
}
