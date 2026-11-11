package dev.captience.realisticdamage.fieldkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.captience.realisticdamage.command.RealisticDamageCommands;
import dev.captience.realisticdamage.config.ServerConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The switches and the settings page, without a game (2.0).
 *
 * <p>The page is generated from {@code ServerConfig}'s fields the way
 * {@code /rdmg config} is, so it cannot miss a setting — but it can show one as
 * its raw field name with no explanation, which for a page that exists so that
 * nobody has to read a config file would be a quiet failure of the same rule.
 * The key test below is what turns that into a loud one.
 */
class FieldKitRulesTest {
	private static JsonObject lang() throws IOException {
		try (InputStream in = FieldKitRulesTest.class.getClassLoader()
				.getResourceAsStream("assets/realisticdamage/lang/en_us.json")) {
			if (in == null) {
				throw new AssertionError("the language file is not on the test classpath");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	/** Both switches exist, are booleans, and ship off: with both off, food and time are the only cure. */
	@Test
	void bothHalvesAreRealSettingsThatStartOff() throws NoSuchFieldException {
		ServerConfig fresh = new ServerConfig();
		for (Half half : Half.values()) {
			Field field = ServerConfig.class.getField(half.field());
			assertEquals(boolean.class, field.getType(), half + " must be a plain switch");
			assertFalse(half.on(fresh), half + " must start off");
		}
		assertTrue(RealisticDamageCommands.settingFields().stream()
						.anyMatch(field -> field.getName().equals("enableWeapons")),
				"the command (and so the page) must list the switch");
	}

	/** Each switch moves its own half and only its own. */
	@Test
	void eachSwitchMovesOnlyItsOwnHalf() {
		ServerConfig config = new ServerConfig();
		Half.MEDICINE.set(config, true);
		assertTrue(Half.MEDICINE.on(config));
		assertFalse(Half.WEAPONS.on(config), "turning Medicine on must leave Weapons off");
		assertTrue(config.enableMedicine);
		Half.WEAPONS.set(config, true);
		Half.MEDICINE.set(config, false);
		assertTrue(config.enableWeapons);
		assertFalse(config.enableMedicine);
	}

	/** What a datapack writes is what the codec reads: "weapons" and "medicine". */
	@Test
	void theHalvesAreNamedTheWayADatapackWritesThem() {
		assertEquals("weapons", Half.WEAPONS.getSerializedName());
		assertEquals("medicine", Half.MEDICINE.getSerializedName());
	}

	/**
	 * Every setting has a label and an explanation on the World rules page, and
	 * every enum value a name. A setting added without them still appears (the
	 * page falls back to the field name), so only this test notices.
	 */
	@Test
	void everySettingIsLabelledOnTheWorldRulesPage() throws IOException {
		JsonObject lang = lang();
		List<String> missing = new ArrayList<>();
		for (Field field : RealisticDamageCommands.settingFields()) {
			String key = "realisticdamage.rules." + field.getName();
			for (String needed : List.of(key, key + ".tooltip")) {
				if (!lang.has(needed)) {
					missing.add(needed);
				}
			}
			if (field.getType().isEnum()) {
				for (Object constant : field.getType().getEnumConstants()) {
					String value = key + "." + ((Enum<?>) constant).name().toLowerCase(Locale.ROOT);
					if (!lang.has(value)) {
						missing.add(value);
					}
				}
			}
		}
		assertTrue(missing.isEmpty(), "the World rules page has no words for " + missing);
	}

	/** The first-launch screen and the switched-off messages have their words too. */
	@Test
	void theFirstLaunchScreenAndTheRefusalsHaveTheirWords() throws IOException {
		JsonObject lang = lang();
		for (String key : List.of(
				"realisticdamage.fieldkit.welcome.title", "realisticdamage.fieldkit.welcome.intro",
				"realisticdamage.fieldkit.welcome.weapons", "realisticdamage.fieldkit.welcome.medicine",
				"realisticdamage.fieldkit.welcome.later", "realisticdamage.fieldkit.half.weapons",
				"realisticdamage.fieldkit.half.medicine", "realisticdamage.rules.open",
				"realisticdamage.rules.title", "realisticdamage.rules.note.local",
				"realisticdamage.rules.note.operator", "realisticdamage.rules.note.read_only",
				"itemGroup.realisticdamage.field_kit")) {
			assertTrue(lang.has(key), "missing " + key);
		}
		for (String key : List.of("realisticdamage.fieldkit.off", "realisticdamage.fieldkit.off.tooltip")) {
			assertTrue(lang.get(key).getAsString().contains("%s"), key + " must name the half that is off");
		}
	}

	/**
	 * Every setting survives the trip the page sends it on: printed as the
	 * command prints it, read back as the command reads it. Non-default values
	 * on purpose, so a parser that always answered the default would fail.
	 */
	@Test
	void everySettingRoundTripsThroughItsWord() throws IllegalAccessException {
		ServerConfig config = new ServerConfig();
		for (Field field : RealisticDamageCommands.settingFields()) {
			Class<?> type = field.getType();
			if (type == boolean.class) {
				field.setBoolean(config, !field.getBoolean(config));
			} else if (type == double.class) {
				field.setDouble(config, 1.25);
			} else if (type.isEnum()) {
				Object[] constants = type.getEnumConstants();
				field.set(config, constants[constants.length - 1]);
			}
		}
		for (Field field : RealisticDamageCommands.settingFields()) {
			String word = RulesSync.word(field, config);
			assertEquals(field.get(config), RulesSync.parse(field, word),
					field.getName() + " did not survive '" + word + "'");
		}
		Field weapons = RealisticDamageCommands.settingFields().stream()
				.filter(field -> field.getName().equals("enableWeapons")).findFirst().orElseThrow();
		assertThrows(IllegalArgumentException.class, () -> RulesSync.parse(weapons, "yes"),
				"a word that is not true or false must not quietly read as false");
	}

	/** The payload carries every setting, in order, and who may change them. */
	@Test
	void thePayloadRoundTrips() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("bodyDamageMultiplier", "1.25");
		values.put("enableMedicine", "true");
		values.put("enableWeapons", "false");
		values.put("preyOnWounded", "limping");
		RulesPayload sent = new RulesPayload(values, true);
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		RulesPayload.CODEC.encode(buf, sent);
		RulesPayload received = RulesPayload.CODEC.decode(buf);
		assertEquals(0, buf.readableBytes(), "the codec must read exactly what it wrote");
		assertEquals(sent.values(), received.values());
		assertEquals(List.copyOf(values.keySet()), List.copyOf(received.values().keySet()), "order is the page's order");
		assertTrue(received.canEdit());
	}

	/**
	 * The client's copy answers from the server once anything has arrived, and
	 * from this install's own file before then (the title screen).
	 */
	@Test
	void theClientsCopyFollowsTheServer() {
		try {
			RulesView.clear();
			assertFalse(RulesView.received());
			assertEquals(Half.MEDICINE.on(), RulesView.on(Half.MEDICINE), "before a server speaks, the local file answers");
			RulesView.accept(new RulesPayload(Map.of("enableMedicine", "true", "enableWeapons", "false"), false));
			assertTrue(RulesView.on(Half.MEDICINE));
			assertFalse(RulesView.on(Half.WEAPONS));
			assertFalse(RulesView.canEdit());
			int version = RulesView.version();
			RulesView.accept(new RulesPayload(Map.of("enableMedicine", "false", "enableWeapons", "true"), true));
			assertTrue(RulesView.version() != version, "a change must be visible to a screen waiting for one");
			assertTrue(RulesView.on(Half.WEAPONS));
			assertTrue(RulesView.canEdit());
		} finally {
			RulesView.clear();
		}
	}

	/** Every ServerConfig setting is in the payload the server builds, under its field name. */
	@Test
	void theServerSendsEverySetting() {
		Set<String> sent = RulesSync.values().keySet();
		for (Field field : RealisticDamageCommands.settingFields()) {
			assertTrue(sent.contains(field.getName()), field.getName() + " never reaches the client");
		}
		assertEquals("false", RulesSync.values().get("enableWeapons"));
	}
}
