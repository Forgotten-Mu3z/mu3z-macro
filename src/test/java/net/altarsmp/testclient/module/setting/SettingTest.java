package net.altarsmp.testclient.module.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.google.gson.JsonPrimitive;
import net.altarsmp.testclient.module.SpeedMode;
import org.junit.jupiter.api.Test;

class SettingTest {
	@Test
	void intSettingClampsAndMapsSliderPosition() {
		IntSetting setting = new IntSetting("delay", "Delay", "", 100, 0, 500, "ms");
		setting.set(9000);
		assertEquals(500, setting.get());
		setting.setNormalized(0.5);
		assertEquals(250, setting.get());
		assertEquals(0.5, setting.normalized(), 1e-9);
		setting.fromJson(new JsonPrimitive("not a number"));
		assertEquals(250, setting.get());
	}

	@Test
	void doubleSettingSnapsToStep() {
		DoubleSetting setting = new DoubleSetting("range", "Range", "", 3.0, 1.0, 6.0, 0.1, "m");
		setting.set(3.14159);
		assertEquals(3.1, setting.get(), 1e-9);
		assertEquals("3.1m", setting.displayValue());
		setting.set(Double.NaN);
		assertEquals(3.0, setting.get(), 1e-9);
	}

	@Test
	void enumSettingParsesCaseInsensitivelyAndCycles() {
		EnumSetting<SpeedMode> setting = new EnumSetting<>("speed", "Speed", "", SpeedMode.HUMANLIKE);
		setting.fromJson(new JsonPrimitive("blatant"));
		assertEquals(SpeedMode.BLATANT, setting.get());
		setting.fromJson(new JsonPrimitive("nonsense"));
		assertEquals(SpeedMode.BLATANT, setting.get());
		setting.cycle();
		assertEquals(SpeedMode.HUMANLIKE, setting.get());
	}

	@Test
	void booleanSettingIgnoresWrongTypes() {
		BooleanSetting setting = new BooleanSetting("on", "On", "", true);
		setting.fromJson(new JsonPrimitive(0));
		setting.toggle();
		assertFalse(setting.get());
	}
}
