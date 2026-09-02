package dev.captience.realisticdamage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.captience.realisticdamage.util.InjuryMessages;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which action-bar lines the "Injury Text" switch is allowed to swallow (1.6),
 * checked against the shipped language file rather than against a list written
 * out here.
 *
 * <p>This is the test that matters in a year. The classification is a prefix
 * rule, so a message key added later is silently classified by whatever it
 * happens to be called — and a rescue acknowledgement named without the
 * {@code drag.} / {@code pressure.} / {@code feed.} shape would start being
 * suppressed, which breaks a feature rather than quieting an effect. Reading
 * {@code en_us.json} makes the next person to add a key say which side of the
 * line it falls on, in the same way {@code disableAllEffects()}'s doc comment
 * makes them say whether a new effect is atmosphere.
 */
class InjuryMessageKeysTest {
	/**
	 * The lines that answer a keypress rather than report a body, spelled out.
	 * Enumerated, not derived: deriving them from the same prefixes the
	 * production code uses would make this test agree with itself no matter what
	 * either of them said.
	 */
	private static final Set<String> INTERACTION_KEYS = Set.of(
			"realisticdamage.msg.drag.start",
			"realisticdamage.msg.drag.stop",
			"realisticdamage.msg.drag.lost",
			"realisticdamage.msg.pressure.self",
			"realisticdamage.msg.pressure.other",
			"realisticdamage.msg.feed.given",
			"realisticdamage.msg.feed.received",
			"realisticdamage.msg.feed.full");

	/**
	 * How many {@code realisticdamage.msg.*} lines ship. Bumped in the same
	 * commit as the key, which is the whole mechanism: the number moving is what
	 * forces somebody to look at this file and decide.
	 */
	private static final int SHIPPED_MESSAGE_KEYS = 62;

	@Test
	void everyShippedMessageKeyIsClassifiedDeliberately() throws IOException {
		Set<String> keys = messageKeys();
		assertEquals(SHIPPED_MESSAGE_KEYS, keys.size(),
				"a message key was added or removed without a decision about the Injury Text switch");
		for (String key : keys) {
			if (INTERACTION_KEYS.contains(key)) {
				assertFalse(InjuryMessages.isBodyReport(key),
						key + " answers a keypress; nothing else on screen says it, so it must survive");
			} else {
				assertTrue(InjuryMessages.isBodyReport(key),
						key + " reports the body, so the Injury Text switch must be able to silence it");
			}
		}
	}

	/** Every enumerated rescue line is really in the file — no stale entries here. */
	@Test
	void theRescueLinesAreAllStillShipped() throws IOException {
		Set<String> keys = messageKeys();
		for (String key : INTERACTION_KEYS) {
			assertTrue(keys.contains(key), key + " is exempted here but no longer ships");
		}
	}

	/**
	 * The one live risk in the whole fix: a filter that returns false too widely
	 * eats somebody else's action bar. The namespace test is what closes it, so
	 * it gets asserted against real vanilla keys rather than trusted.
	 */
	@Test
	void nothingOutsideOurNamespaceIsEverOurs() {
		for (String foreign : new String[] {
				"mount.onboard", "record.nowPlaying", "chat.type.text",
				"othermod.msg.break.right_leg", "realisticdamage.hud.concussion",
				"realisticdamage.triage.unconscious", ""}) {
			assertFalse(InjuryMessages.isBodyReport(foreign),
					foreign + " is not one of ours and must never be swallowed");
		}
	}

	/**
	 * Keys, scraped from the language file the mod actually ships. A regex
	 * rather than a JSON parser on purpose: the assertion is about what is in
	 * the resource jar, and pulling in a parser to read a flat string-to-string
	 * map would add a dependency to say the same thing.
	 */
	private static Set<String> messageKeys() throws IOException {
		String json;
		try (InputStream in = InjuryMessageKeysTest.class.getClassLoader()
				.getResourceAsStream("assets/realisticdamage/lang/en_us.json")) {
			assertTrue(in != null, "en_us.json is not on the test classpath");
			json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		Set<String> keys = new LinkedHashSet<>();
		Matcher matcher = Pattern.compile("\"(" + Pattern.quote(InjuryMessages.PREFIX) + "[^\"]*)\"\\s*:")
				.matcher(json);
		while (matcher.find()) {
			keys.add(matcher.group(1));
		}
		return keys;
	}
}
