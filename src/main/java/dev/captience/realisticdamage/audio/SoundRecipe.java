package dev.captience.realisticdamage.audio;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.captience.realisticdamage.net.SoundMaterial;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

/**
 * How one injury event sounds in one sound set (2.0): a list of layers, each a
 * sound file at a pitch, a volume and a delay. Recipes are assets, in
 * {@code assets/<namespace>/rd_sounds/<set>/<event>.json}, so a sound can be
 * retuned without touching code and a resource pack can replace one.
 *
 * <p>Only a client ever plays one, but nothing here needs a client: it lives
 * in the common code so the unit tests can read every recipe the mod ships.
 *
 * <pre>{@code
 * {
 *   "subtitle": "realisticdamage.subtitle.fracture",
 *   "variants": [
 *     { "layers": [
 *       { "sound": "minecraft:block/bone_block/break2", "pitch": 1.3, "volume": 0.7 },
 *       { "sound": "minecraft:mob/slime/small2", "pitch": [0.65, 0.75], "delay": 1,
 *         "chance": 0.8, "min_strength": 0.5 }
 *     ] }
 *   ],
 *   "dry": [ ... the same shape, for a body with no blood in it ... ],
 *   "gain": 1.0,
 *   "quiet": 0.6,
 *   "small": false
 * }
 * }</pre>
 *
 * <p>{@code sound} names a file, not a sound event: {@code minecraft:block/
 * bone_block/break2} is {@code sounds/block/bone_block/break2.ogg} in the
 * player's own game. A sound event would pick any one of its files at random,
 * and a recipe tuned by ear is tuned to one of them.
 *
 * @param subtitle translation key, or null for none
 * @param variants at least one; a different one is picked each time
 * @param byMaterial variants for bodies that are not flesh; a material with
 *                   none uses {@code variants}
 * @param gain     0 to 1, over every layer: five vanilla sounds starting together
 *                 add up to more than full scale, and this brings the stack
 *                 back down without touching the balance between its layers
 * @param quiet    volume at strength 0, as a share of the volume at strength 1
 * @param small    one of many (a blood drop): counted against the budget
 */
public record SoundRecipe(@Nullable String subtitle, List<Variant> variants,
		Map<SoundMaterial, List<Variant>> byMaterial, float gain, float quiet, boolean small) {

	/** Volume at strength 0 unless the recipe says otherwise. */
	public static final float DEFAULT_QUIET = 0.6F;

	/**
	 * @param sound       the file, as {@code namespace:path} under {@code sounds/}, without {@code .ogg}
	 * @param far         the file to use for somebody else's body, or null for
	 *                    the same one. A stereo file cannot be placed in the
	 *                    world, so a stereo layer names its mono twin here.
	 * @param delay       in ticks; the game cannot start a sound more finely
	 * @param chance      0 to 1, rolled each time the recipe plays
	 * @param minStrength the layer only joins in at or above this strength
	 */
	public record Layer(Identifier sound, @Nullable Identifier far, float pitchLow, float pitchHigh,
			float volumeLow, float volumeHigh, int delay, float chance, float minStrength) {

		public Identifier file(boolean self) {
			return self || far == null ? sound : far;
		}

		public float pitch(RandomSource random) {
			return pitchLow + (pitchHigh - pitchLow) * random.nextFloat();
		}

		public float volume(RandomSource random) {
			return volumeLow + (volumeHigh - volumeLow) * random.nextFloat();
		}
	}

	public record Variant(List<Layer> layers) {}

	/** The variants for a body of this material. */
	public List<Variant> variantsFor(SoundMaterial material) {
		List<Variant> own = byMaterial.get(material);
		return own == null || own.isEmpty() ? variants : own;
	}

	/** The layers of one variant that play at this strength, before any dice. */
	public static List<Layer> layersAt(Variant variant, float strength) {
		List<Layer> out = new ArrayList<>(variant.layers().size());
		for (Layer layer : variant.layers()) {
			if (strength >= layer.minStrength()) {
				out.add(layer);
			}
		}
		return out;
	}

	/**
	 * A different variant from last time, when there is more than one, so the
	 * same take never comes twice running.
	 *
	 * @param previous the index played last, or null if none yet
	 */
	public static int pickVariant(int count, @Nullable Integer previous, RandomSource random) {
		if (count <= 1) {
			return 0;
		}
		if (previous == null || previous < 0 || previous >= count) {
			return random.nextInt(count);
		}
		int pick = random.nextInt(count - 1);
		return pick >= previous ? pick + 1 : pick;
	}

	/** How loud the whole recipe is at this strength, against full strength. */
	public float volumeAt(float strength) {
		float s = Math.clamp(strength, 0.0F, 1.0F);
		return gain * (quiet + (1.0F - quiet) * s);
	}

	/**
	 * Reads a recipe.
	 *
	 * @param exists whether a sound file is there to be played. A layer whose
	 *               file is missing is left out rather than failing the recipe:
	 *               a resource pack that removes a vanilla sound should cost one
	 *               layer, not the event.
	 * @param problems one line per thing left out, for the log
	 * @return null if nothing playable is left
	 */
	public static @Nullable SoundRecipe parse(JsonObject json, Predicate<Identifier> exists, List<String> problems) {
		List<Variant> variants = variants(json.get("variants"), "variants", exists, problems);
		if (variants.isEmpty()) {
			problems.add("no playable variant");
			return null;
		}
		Map<SoundMaterial, List<Variant>> byMaterial = new EnumMap<>(SoundMaterial.class);
		for (SoundMaterial material : SoundMaterial.values()) {
			if (material != SoundMaterial.FLESH && json.has(material.id())) {
				List<Variant> own = variants(json.get(material.id()), material.id(), exists, problems);
				if (!own.isEmpty()) {
					byMaterial.put(material, own);
				}
			}
		}
		String subtitle = json.has("subtitle") ? json.get("subtitle").getAsString() : null;
		float gain = json.has("gain") ? Math.clamp(json.get("gain").getAsFloat(), 0.0F, 1.0F) : 1.0F;
		float quiet = json.has("quiet") ? Math.clamp(json.get("quiet").getAsFloat(), 0.0F, 1.0F) : DEFAULT_QUIET;
		boolean small = json.has("small") && json.get("small").getAsBoolean();
		return new SoundRecipe(subtitle, List.copyOf(variants), Map.copyOf(byMaterial), gain, quiet, small);
	}

	private static List<Variant> variants(@Nullable JsonElement element, String name, Predicate<Identifier> exists,
			List<String> problems) {
		List<Variant> out = new ArrayList<>();
		if (element == null || !element.isJsonArray()) {
			problems.add("\"" + name + "\" is not a list");
			return out;
		}
		int index = 0;
		for (JsonElement entry : element.getAsJsonArray()) {
			index++;
			try {
				List<Layer> layers = new ArrayList<>();
				for (JsonElement layer : entry.getAsJsonObject().getAsJsonArray("layers")) {
					Layer parsed = layer(layer.getAsJsonObject());
					if (!exists.test(parsed.sound())) {
						problems.add(name + " " + index + ": no sound file " + parsed.sound());
					} else if (parsed.far() != null && !exists.test(parsed.far())) {
						problems.add(name + " " + index + ": no sound file " + parsed.far());
					} else {
						layers.add(parsed);
					}
				}
				if (layers.isEmpty()) {
					problems.add(name + " " + index + ": no layers left");
				} else {
					out.add(new Variant(List.copyOf(layers)));
				}
			} catch (RuntimeException e) {
				// Gson throws four different unchecked types for one bad file.
				problems.add(name + " " + index + ": " + e.getMessage());
			}
		}
		return out;
	}

	private static Layer layer(JsonObject json) {
		if (!json.has("sound")) {
			throw new JsonParseException("a layer has no \"sound\"");
		}
		Identifier sound = Identifier.parse(json.get("sound").getAsString());
		Identifier far = json.has("far") ? Identifier.parse(json.get("far").getAsString()) : null;
		float[] pitch = range(json.get("pitch"), 1.0F);
		float[] volume = range(json.get("volume"), 1.0F);
		int delay = json.has("delay") ? Math.max(0, json.get("delay").getAsInt()) : 0;
		float chance = json.has("chance") ? Math.clamp(json.get("chance").getAsFloat(), 0.0F, 1.0F) : 1.0F;
		float minStrength = json.has("min_strength") ? json.get("min_strength").getAsFloat() : 0.0F;
		return new Layer(sound, far, pitch[0], pitch[1], volume[0], volume[1], delay, chance, minStrength);
	}

	/** A number, or a [low, high] pair to pick between each time. */
	private static float[] range(@Nullable JsonElement element, float fallback) {
		if (element == null) {
			return new float[] {fallback, fallback};
		}
		if (element.isJsonArray()) {
			JsonArray pair = element.getAsJsonArray();
			if (pair.size() != 2) {
				throw new JsonParseException("a range needs two numbers, got " + pair.size());
			}
			float a = pair.get(0).getAsFloat(), b = pair.get(1).getAsFloat();
			return new float[] {Math.min(a, b), Math.max(a, b)};
		}
		float value = element.getAsFloat();
		return new float[] {value, value};
	}
}
