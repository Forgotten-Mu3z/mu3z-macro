package net.altarsmp.testclient.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import net.altarsmp.testclient.AltarTestClient;
import net.altarsmp.testclient.config.ConfigManager;
import net.altarsmp.testclient.module.Module;
import net.altarsmp.testclient.util.ClientTicks;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Writes one CSV line per automated action to {@code .minecraft/altartestclient/logs/}, one file per
 * server session. Columns:
 * <pre>
 * epoch_ms, iso_time, client_tick, game_time, module, speed, action, target, detail
 * </pre>
 * {@code game_time} is the world's game time as synced from the server (the closest client-side value to
 * a server tick number); {@code client_tick} counts client ticks since launch. Lines are buffered and
 * flushed at the end of every client tick.
 */
public final class ActionLogger {
	private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	private static final DateTimeFormatter ISO_MILLIS =
			DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
	private static final String HEADER = "epoch_ms,iso_time,client_tick,game_time,module,speed,action,target,detail";

	private static @Nullable BufferedWriter writer;
	private static @Nullable Path currentFile;
	private static String sessionLabel = "no-server";
	private static boolean dirty;

	private ActionLogger() {
	}

	public static Path logDir() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("altartestclient").resolve("logs");
	}

	public static @Nullable Path currentFile() {
		return currentFile;
	}

	/** Starts a new log file for a server session (called on join). */
	public static void startSession(String serverLabel) {
		endSession();
		sessionLabel = serverLabel;
	}

	public static void endSession() {
		if (writer != null) {
			try {
				writer.close();
			} catch (IOException e) {
				AltarTestClient.LOGGER.warn("Failed to close action log", e);
			}
		}
		writer = null;
		currentFile = null;
		dirty = false;
	}

	/** Logs an automated action performed by {@code module}. */
	public static void log(Module module, String action, String target, String detail) {
		write(module.name(), module.speedMode().name(), action, target, detail);
	}

	/** Logs something observed rather than performed (totem pops, panic, session info). */
	public static void logEvent(String action, String target, String detail) {
		write("Client", "", action, target, detail);
	}

	private static void write(String module, String speed, String action, String target, String detail) {
		if (!ConfigManager.global().logging.get()) {
			return;
		}
		BufferedWriter out = open();
		if (out == null) {
			return;
		}
		long now = System.currentTimeMillis();
		Minecraft mc = Minecraft.getInstance();
		long gameTime = mc.level != null ? mc.level.getGameTime() : -1;
		String line = now + "," + ISO_MILLIS.format(Instant.ofEpochMilli(now)) + "," + ClientTicks.now() + "," + gameTime + ","
				+ csv(module) + "," + csv(speed) + "," + csv(action) + "," + csv(target) + "," + csv(detail);
		try {
			out.write(line);
			out.newLine();
			dirty = true;
		} catch (IOException e) {
			AltarTestClient.LOGGER.warn("Failed to write action log", e);
		}
	}

	private static @Nullable BufferedWriter open() {
		if (writer != null) {
			return writer;
		}
		try {
			Path dir = logDir();
			Files.createDirectories(dir);
			String safeLabel = sessionLabel.replaceAll("[^A-Za-z0-9._-]", "_");
			currentFile = dir.resolve("actions_" + LocalDateTime.now().format(FILE_STAMP) + "_" + safeLabel + ".csv");
			writer = Files.newBufferedWriter(currentFile, StandardCharsets.UTF_8);
			writer.write(HEADER);
			writer.newLine();
			dirty = true;
			AltarTestClient.LOGGER.info("Action log: {}", currentFile);
		} catch (IOException e) {
			AltarTestClient.LOGGER.error("Could not open action log", e);
			writer = null;
			currentFile = null;
		}
		return writer;
	}

	/** Called at the end of every client tick. */
	public static void flush() {
		if (dirty && writer != null) {
			try {
				writer.flush();
			} catch (IOException e) {
				AltarTestClient.LOGGER.warn("Failed to flush action log", e);
			}
			dirty = false;
		}
	}

	private static String csv(String value) {
		if (value == null) {
			return "";
		}
		if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0) {
			return '"' + value.replace("\"", "\"\"") + '"';
		}
		return value;
	}
}
