package dev.captience.realisticdamage.audio;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.net.InjurySoundPayload;
import dev.captience.realisticdamage.net.SoundMaterial;
import dev.captience.realisticdamage.net.SoundSet;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Injury sound recipes (2.0 audio): the format, and every recipe the mod ships.
 */
class SoundRecipeTest {
	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	private static SoundRecipe parse(String text) {
		List<String> problems = new ArrayList<>();
		SoundRecipe recipe = SoundRecipe.parse(json(text), id -> true, problems);
		assertTrue(problems.isEmpty(), problems.toString());
		return recipe;
	}

	@Test
	void aLayerReadsEveryFieldAndDefaultsTheRest() {
		SoundRecipe recipe = parse("""
				{ "subtitle": "some.key", "gain": 0.5, "quiet": 0.25, "small": true,
				  "variants": [ { "layers": [
				    { "sound": "minecraft:block/bone_block/break2" },
				    { "sound": "realisticdamage:grounded/fracture", "far": "realisticdamage:grounded/fracture_far",
				      "pitch": [1.2, 0.8], "volume": 0.7, "delay": 3, "chance": 0.4, "min_strength": 0.5 }
				  ] } ] }""");
		assertEquals("some.key", recipe.subtitle());
		assertTrue(recipe.small());
		SoundRecipe.Layer plain = recipe.variants().get(0).layers().get(0);
		assertEquals(Identifier.parse("minecraft:block/bone_block/break2"), plain.sound());
		assertNull(plain.far());
		assertEquals(1.0F, plain.pitchLow());
		assertEquals(1.0F, plain.pitchHigh());
		assertEquals(1.0F, plain.volumeLow());
		assertEquals(0, plain.delay());
		assertEquals(1.0F, plain.chance());
		assertEquals(0.0F, plain.minStrength());
		// The same file whoever it happened to, when there is no mono twin.
		assertEquals(plain.sound(), plain.file(false));

		SoundRecipe.Layer full = recipe.variants().get(0).layers().get(1);
		assertEquals(0.8F, full.pitchLow(), "a range is sorted");
		assertEquals(1.2F, full.pitchHigh());
		assertEquals(0.7F, full.volumeHigh());
		assertEquals(3, full.delay());
		assertEquals(0.4F, full.chance());
		assertEquals(0.5F, full.minStrength());
		assertEquals(Identifier.parse("realisticdamage:grounded/fracture"), full.file(true));
		assertEquals(Identifier.parse("realisticdamage:grounded/fracture_far"), full.file(false));

		// Strength scales between "quiet" and full, under the recipe's gain.
		assertEquals(0.5F * 0.25F, recipe.volumeAt(0.0F), 1.0e-6F);
		assertEquals(0.5F, recipe.volumeAt(1.0F), 1.0e-6F);
		assertEquals(0.5F, recipe.volumeAt(7.0F), 1.0e-6F, "strength is clamped");
	}

	@Test
	void aFixedPitchStaysFixed() {
		SoundRecipe.Layer layer = parse("""
				{ "variants": [ { "layers": [ { "sound": "a:b", "pitch": 0.85, "volume": 0.7 } ] } ] }""")
				.variants().get(0).layers().get(0);
		RandomSource random = RandomSource.create(1);
		for (int i = 0; i < 20; i++) {
			assertEquals(0.85F, layer.pitch(random));
			assertEquals(0.7F, layer.volume(random));
		}
	}

	@Test
	void heavyLayersOnlyJoinInAboveTheirStrength() {
		SoundRecipe recipe = parse("""
				{ "variants": [ { "layers": [
				    { "sound": "a:light" }, { "sound": "a:heavy", "min_strength": 0.6 } ] } ] }""");
		SoundRecipe.Variant variant = recipe.variants().get(0);
		assertEquals(1, SoundRecipe.layersAt(variant, 0.3F).size());
		assertEquals(2, SoundRecipe.layersAt(variant, 0.6F).size());
	}

	@Test
	void aDryBodyUsesItsOwnVariantsAndFleshNeverDoes() {
		SoundRecipe recipe = parse("""
				{ "variants": [ { "layers": [ { "sound": "a:bone" }, { "sound": "a:wet" } ] } ],
				  "dry": [ { "layers": [ { "sound": "a:bone" } ] } ] }""");
		assertEquals(2, recipe.variantsFor(SoundMaterial.FLESH).get(0).layers().size());
		assertEquals(1, recipe.variantsFor(SoundMaterial.DRY).get(0).layers().size());
		// No dry list: a dry body gets the ordinary one, not silence.
		SoundRecipe plain = parse("""
				{ "variants": [ { "layers": [ { "sound": "a:bone" } ] } ] }""");
		assertEquals(plain.variants(), plain.variantsFor(SoundMaterial.DRY));
	}

	@Test
	void aMissingFileCostsOneLayerNotTheRecipe() {
		List<String> problems = new ArrayList<>();
		SoundRecipe recipe = SoundRecipe.parse(json("""
				{ "variants": [ { "layers": [ { "sound": "a:there" }, { "sound": "a:gone" } ] } ] }"""),
				id -> !id.getPath().equals("gone"), problems);
		assertNotNull(recipe);
		assertEquals(1, recipe.variants().get(0).layers().size());
		assertEquals(1, problems.size());
		assertTrue(problems.get(0).contains("a:gone"), problems.get(0));
	}

	@Test
	void nothingPlayableIsNoRecipe() {
		List<String> problems = new ArrayList<>();
		assertNull(SoundRecipe.parse(json("""
				{ "variants": [ { "layers": [ { "sound": "a:gone" } ] } ] }"""), id -> false, problems));
		assertNull(SoundRecipe.parse(json("{ \"variants\": 3 }"), id -> true, problems));
		assertNull(SoundRecipe.parse(json("""
				{ "variants": [ { "layers": [ { "pitch": 2 } ] }, 7, { "layers": [ { "sound": "a:b", "pitch": [1] } ] } ] }"""),
				id -> true, problems));
		assertFalse(problems.isEmpty());
	}

	@Test
	void theSameVariantNeverComesTwiceRunning() {
		RandomSource random = RandomSource.create(7);
		assertEquals(0, SoundRecipe.pickVariant(1, 0, random));
		Integer previous = null;
		Set<Integer> seen = new HashSet<>();
		for (int i = 0; i < 300; i++) {
			int pick = SoundRecipe.pickVariant(4, previous, random);
			assertTrue(pick >= 0 && pick < 4);
			if (previous != null) {
				assertNotEquals(previous.intValue(), pick);
			}
			seen.add(pick);
			previous = pick;
		}
		assertEquals(4, seen.size(), "every variant gets its turn");
	}

	// ------------------------------------------------------------- what ships

	private static InputStream asset(String path) {
		return SoundRecipeTest.class.getResourceAsStream("/assets/realisticdamage/" + path);
	}

	private static SoundRecipe shipped(SoundSet set, InjurySound event, List<String> problems) throws IOException {
		try (InputStream in = asset("rd_sounds/" + set.id() + "/" + event.id() + ".json")) {
			assertNotNull(in, "no " + set.id() + " recipe for " + event.id());
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			// A vanilla file cannot be checked from here (the game's assets are
			// not on this classpath; the client gametest plays every one). One of
			// the mod's own must be in the jar.
			return SoundRecipe.parse(json, id -> !id.getNamespace().equals("realisticdamage")
					|| asset("sounds/" + id.getPath() + ".ogg") != null, problems);
		}
	}

	@Test
	void everyBodySoundHasACleanRecipeInBothSets() throws IOException {
		for (InjurySound event : InjurySound.values()) {
			if (event.isScream()) {
				continue;   // a voice is a sound event, not a recipe
			}
			for (SoundSet set : SoundSet.values()) {
				List<String> problems = new ArrayList<>();
				SoundRecipe recipe = shipped(set, event, problems);
				assertNotNull(recipe, set.id() + "/" + event.id());
				assertTrue(problems.isEmpty(), set.id() + "/" + event.id() + ": " + problems);
				assertNotNull(recipe.subtitle(), set.id() + "/" + event.id() + " has no subtitle");
			}
		}
	}

	@Test
	void groundedLayersAllHaveAMonoTwinForTheWorld() throws IOException {
		// A Grounded file is stereo, and the game cannot place a stereo sound:
		// without "far" somebody else's broken leg would play inside your head.
		for (InjurySound event : InjurySound.values()) {
			if (event.isScream() || event == InjurySound.NECK_CRACK) {
				continue;   // the neck is still the 1.x file, which is mono
			}
			SoundRecipe recipe = shipped(SoundSet.GROUNDED, event, new ArrayList<>());
			for (SoundMaterial material : SoundMaterial.values()) {
				for (SoundRecipe.Variant variant : recipe.variantsFor(material)) {
					for (SoundRecipe.Layer layer : variant.layers()) {
						assertNotNull(layer.far(), event.id() + ": " + layer.sound() + " has no \"far\"");
					}
				}
			}
		}
	}

	@Test
	void theApprovedArcadeBoneBreakIsExactlyTheOneThatWasHeard() throws IOException {
		// 13 Nov 2026: "the arcade version should sound as they are in the page".
		List<SoundRecipe.Layer> layers = shipped(SoundSet.ARCADE, InjurySound.FRACTURE, new ArrayList<>())
				.variants().get(0).layers();
		assertEquals(3, layers.size());
		assertEquals(Identifier.parse("minecraft:mob/turtle/egg/egg_crack4"), layers.get(0).sound());
		assertEquals(0.85F, layers.get(0).pitchLow());
		assertEquals(0.85F, layers.get(0).pitchHigh());
		assertEquals(1.0F, layers.get(0).volumeLow());
		assertEquals(Identifier.parse("minecraft:block/bone_block/break2"), layers.get(1).sound());
		assertEquals(1.3F, layers.get(1).pitchLow());
		assertEquals(0.7F, layers.get(1).volumeLow());
		assertEquals(Identifier.parse("minecraft:mob/goat/horn_break1"), layers.get(2).sound());
		assertEquals(1.15F, layers.get(2).pitchLow());
		assertEquals(0.35F, layers.get(2).volumeLow());
	}

	@Test
	void aSkeletonLosesALimbWithoutAnyWetLayers() throws IOException {
		SoundRecipe recipe = shipped(SoundSet.ARCADE, InjurySound.DESTRUCTION, new ArrayList<>());
		assertEquals(5, recipe.variantsFor(SoundMaterial.FLESH).get(0).layers().size());
		for (SoundRecipe.Layer layer : recipe.variantsFor(SoundMaterial.DRY).get(0).layers()) {
			String path = layer.sound().getPath();
			assertFalse(path.contains("slime") || path.contains("mud") || path.contains("honey"), path);
		}
	}

	// ------------------------------------------------------------- the wire

	@Test
	void thePayloadCarriesStrengthMaterialAndAForcedSet() {
		InjurySoundPayload original = new InjurySoundPayload(InjurySound.DECAPITATION, false, 1.5, -2.5, 3.5,
				0.375F, SoundMaterial.DRY, InjurySoundPayload.forcing(SoundSet.GROUNDED));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
		InjurySoundPayload.CODEC.encode(buf, original);
		InjurySoundPayload decoded = InjurySoundPayload.CODEC.decode(buf);
		assertEquals(original, decoded);
		assertEquals(SoundSet.GROUNDED, decoded.forcedSet());
		assertEquals(0, buf.readableBytes());
		// An ordinary event names no set: the listener's own.
		assertNull(new InjurySoundPayload(InjurySound.FRACTURE, true, 0, 0, 0, 1.0F, SoundMaterial.FLESH).forcedSet());
		assertEquals(SoundSet.ARCADE, new InjurySoundPayload(InjurySound.FRACTURE, true, 0, 0, 0, 1.0F,
				SoundMaterial.FLESH, InjurySoundPayload.forcing(SoundSet.ARCADE)).forcedSet());
	}

	@Test
	void newEventsRegisterNothingAndOldOrdinalsHaveNotMoved() {
		// The ordinal is the wire format, and an old server must still agree
		// with a new client about the six it knows.
		assertEquals(0, InjurySound.FRACTURE.ordinal());
		assertEquals(1, InjurySound.DESTRUCTION.ordinal());
		assertEquals(2, InjurySound.COLLAPSE.ordinal());
		assertEquals(3, InjurySound.SCREAM_SHARP.ordinal());
		assertEquals(4, InjurySound.SCREAM_SEVERE.ordinal());
		assertEquals(5, InjurySound.NECK_CRACK.ordinal());
		assertTrue(InjurySound.FRACTURE.hasSoundEvents());
		assertFalse(InjurySound.DECAPITATION.hasSoundEvents());
		assertFalse(InjurySound.BLOOD_SPLAT.hasSoundEvents());
		assertFalse(InjurySound.WOUND_OPEN.hasSoundEvents());
		assertEquals("fracture_visceral", InjurySound.FRACTURE.assetPath(true));
		assertEquals("scream_sharp", InjurySound.SCREAM_SHARP.assetPath(true));
		assertEquals(InjurySound.WOUND_OPEN, InjurySound.byId("wound_open"));
		assertNull(InjurySound.byId("nope"));
	}
}
