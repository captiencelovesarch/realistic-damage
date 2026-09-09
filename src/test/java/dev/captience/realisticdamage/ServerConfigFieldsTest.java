package dev.captience.realisticdamage;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.List;

import dev.captience.realisticdamage.command.RealisticDamageCommands;
import dev.captience.realisticdamage.config.ServerConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract between {@link ServerConfig} and the {@code /rdmg config} command
 * that is generated from it (1.6). The command is reflective on purpose — a
 * setting added in a later release turns up there without anybody remembering to
 * wire it up — and these are the four things that reflection cannot check for
 * itself: that every field is a shape the command can expose, that no enum
 * default is null, that the count still matches what the README documents, and
 * that the printed order is the sorted one rather than whatever the JVM felt
 * like handing back.
 */
class ServerConfigFieldsTest {
	/** Bumped in the same commit as the README row, every time a setting is added. */
	private static final int DOCUMENTED_SETTINGS = 21;

	@Test
	void everySettingIsABooleanDoubleOrEnum() {
		for (Field field : RealisticDamageCommands.settingFields()) {
			Class<?> type = field.getType();
			assertTrue(type == boolean.class || type == double.class || type == int.class || type.isEnum(),
					"/rdmg config cannot expose " + field.getName() + " of type " + type.getSimpleName());
		}
	}

	/**
	 * Gson answers an unrecognised enum name with null rather than an error, so a
	 * null default would be indistinguishable from a typo in the file — and
	 * whichever branch fell through first would silently become the mode.
	 */
	@Test
	void enumSettingsHaveNonNullDefaults() throws IllegalAccessException {
		ServerConfig fresh = new ServerConfig();
		for (Field field : RealisticDamageCommands.settingFields()) {
			if (field.getType().isEnum()) {
				assertNotNull(field.get(fresh), field.getName() + " must ship with a default");
			}
		}
	}

	@Test
	void theSettingCountIsWhatTheReadmeDocuments() {
		assertEquals(DOCUMENTED_SETTINGS, RealisticDamageCommands.settingFields().size(),
				"a setting was added or removed without its README row");
	}

	/**
	 * {@code Class.getFields()} is documented to return fields in no particular
	 * order. The listing and the literal nodes both leaned on declaration order,
	 * which is nothing but HotSpot's current behaviour; sorting by name makes the
	 * README's table a mirror rather than a coincidence.
	 */
	@Test
	void theSettingsAreListedAlphabetically() {
		List<String> names = RealisticDamageCommands.settingFields().stream().map(Field::getName).toList();
		assertEquals(names.stream().sorted(Comparator.naturalOrder()).toList(), names,
				"/rdmg config must list its settings in name order");
	}
}
