package net.altarsmp.testclient.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.altarsmp.testclient.config.ConfigManager;
import net.altarsmp.testclient.log.ActionLogger;
import net.altarsmp.testclient.module.setting.EnumSetting;
import net.altarsmp.testclient.module.setting.IntSetting;
import net.altarsmp.testclient.module.setting.Setting;
import net.altarsmp.testclient.util.Humanizer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Base class for every module. A module owns its settings, its enabled flag and (after registration)
 * a key mapping. {@link ModuleManager} only calls {@link #onTick()} / {@link #onFrame(double)} while the
 * module is enabled, the player is in a world, and the current server is allow-listed.
 */
public abstract class Module {
	public enum Kind {
		/** Key press toggles the module on/off; it then acts on its own. */
		TOGGLE,
		/** Key press performs a one-shot action; "enabled" means the key is armed. */
		ACTION
	}

	private final String id;
	private final String name;
	private final String description;
	private final Kind kind;
	private final List<Setting<?>> settings = new ArrayList<>();
	protected final EnumSetting<SpeedMode> speed;
	private boolean enabled;
	private @Nullable KeyMapping keyMapping;

	protected Module(String id, String name, String description, Kind kind) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.kind = kind;
		this.speed = add(new EnumSetting<>("speed", "Speed",
				"Humanlike = randomised delays, Blatant = instant", SpeedMode.HUMANLIKE));
	}

	protected final <S extends Setting<?>> S add(S setting) {
		settings.add(setting);
		return setting;
	}

	public final String id() {
		return id;
	}

	public final String name() {
		return name;
	}

	public final String description() {
		return description;
	}

	public final Kind kind() {
		return kind;
	}

	public final List<Setting<?>> settings() {
		return Collections.unmodifiableList(settings);
	}

	public final SpeedMode speedMode() {
		return speed.get();
	}

	public final boolean isEnabled() {
		return enabled;
	}

	public final void setEnabled(boolean enabled) {
		setEnabled(enabled, true);
	}

	/** @param log whether to write a toggle line to the action log (false while loading config). */
	public final void setEnabled(boolean enabled, boolean log) {
		if (this.enabled == enabled) {
			return;
		}
		this.enabled = enabled;
		if (enabled) {
			onEnable();
		} else {
			resetState();
			ActionLock.release(this);
		}
		if (log) {
			ActionLogger.log(this, enabled ? "ENABLE" : "DISABLE", "", "");
			ConfigManager.markDirty();
		}
	}

	public final void toggle() {
		setEnabled(!enabled);
	}

	public @Nullable KeyMapping keyMapping() {
		return keyMapping;
	}

	public void setKeyMapping(KeyMapping keyMapping) {
		this.keyMapping = keyMapping;
	}

	/** Called when this module's key is pressed in-game. */
	public void onKeyPressed() {
		toggle();
	}

	protected void onEnable() {
	}

	/**
	 * Abort any in-progress sequence and undo temporary changes (e.g. restore the hotbar slot).
	 * Called on disable, panic and disconnect.
	 */
	public void resetState() {
	}

	/** Called once per client tick (end of tick) while enabled and allowed. */
	public void onTick() {
	}

	/** Called once per rendered frame while the mouse is grabbed; {@code frameSeconds} is the frame time. */
	public void onFrame(double frameSeconds) {
	}

	/** Whether {@link #onTick()} should still run while a GUI screen is open. */
	public boolean runsWithScreenOpen() {
		return false;
	}

	protected final boolean blatant() {
		return speed.get() == SpeedMode.BLATANT;
	}

	/** Delay for the next step: 0 in blatant mode, otherwise a random value between the two settings. */
	protected final long stepDelay(IntSetting min, IntSetting max) {
		return blatant() ? 0L : Humanizer.delayMs(min.get(), max.get());
	}

	protected final void log(String action, String target, String detail) {
		ActionLogger.log(this, action, target, detail);
	}

	protected static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
