package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * The VR resources that must agree with each other or with the code (1.7
 * alpha.10). None of these can fail loudly at runtime: a drifted copy just
 * looks subtly different in one eye, and a short uniform block reads garbage.
 */
class VrResourcesTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/realisticdamage");
	private static final Path CLIENT = Path.of("src/client/java/dev/captience/realisticdamage/client");

	/** The right eye's afterimage chain is the left eye's, byte for byte. */
	@Test
	void rightEyeAfterimageIsTheSameChain() throws IOException {
		assertArrayEquals(Files.readAllBytes(ASSETS.resolve("post_effect/afterimage.json")),
				Files.readAllBytes(ASSETS.resolve("post_effect/afterimage_right.json")));
	}

	/** The lens's textures are copies of the HUD's (post chains only read textures/effect). */
	@Test
	void lensTexturesMatchTheHud() throws IOException {
		for (String name : new String[] {"injury_vignette", "blood_lens"}) {
			assertArrayEquals(Files.readAllBytes(ASSETS.resolve("textures/misc/" + name + ".png")),
					Files.readAllBytes(ASSETS.resolve("textures/effect/" + name + ".png")), name);
		}
	}

	/** Shader, chain file and VrLens agree on how many ops the uniform block holds. */
	@Test
	void lensOpCountAgreesEverywhere() throws IOException {
		int shader = intAfter(Files.readString(ASSETS.resolve("shaders/post/vr_lens.fsh")), "#define MAX_OPS (\\d+)");
		int code = intAfter(Files.readString(CLIENT.resolve("VrLens.java")), "static final int MAX_OPS = (\\d+);");
		assertEquals(shader, code, "vr_lens.fsh MAX_OPS vs VrLens.MAX_OPS");

		JsonObject chain = JsonParser.parseString(Files.readString(ASSETS.resolve("post_effect/vr_lens.json")))
				.getAsJsonObject();
		JsonArray block = chain.getAsJsonArray("passes").get(0).getAsJsonObject()
				.getAsJsonObject("uniforms").getAsJsonArray("LensConfig");
		assertEquals(1 + code * 4, block.size(), "vr_lens.json LensConfig entries (header + 4 per op)");
	}

	private static int intAfter(String text, String regex) {
		Matcher matcher = Pattern.compile(regex).matcher(text);
		assertTrue(matcher.find(), regex);
		return Integer.parseInt(matcher.group(1));
	}
}
