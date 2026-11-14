package dev.captience.realisticdamage.client.audio;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.audio.SoundRecipe;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.net.SoundSet;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

/**
 * Every recipe the resource packs hold, reloaded with them (F3+T), keyed by
 * sound set and event: {@code rd_sounds/arcade/fracture.json} is how a bone
 * breaks in the Arcade set.
 */
public final class SoundRecipes {
	private SoundRecipes() {}

	private static final String FOLDER = "rd_sounds";

	private static volatile Map<String, SoundRecipe> recipes = Map.of();

	public static void init() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				RealisticDamage.id("sound_recipes"), new SimpleReloadListener<Map<String, SoundRecipe>>() {
					@Override
					protected Map<String, SoundRecipe> prepare(PreparableReloadListener.SharedState state) {
						return load(state.resourceManager());
					}

					@Override
					protected void apply(Map<String, SoundRecipe> loaded, PreparableReloadListener.SharedState state) {
						recipes = loaded;
						RealisticDamage.LOGGER.info("Loaded {} injury sound recipes", loaded.size());
					}
				});
	}

	public static @Nullable SoundRecipe get(SoundSet set, InjurySound event) {
		return recipes.get(key(set.id(), event.id()));
	}

	/** Every "set/event" that loaded, sorted; for the tests and the log. */
	public static Set<String> loaded() {
		return new TreeSet<>(recipes.keySet());
	}

	private static String key(String set, String event) {
		return set + "/" + event;
	}

	private static Map<String, SoundRecipe> load(ResourceManager manager) {
		Map<String, SoundRecipe> out = new HashMap<>();
		Map<Identifier, Resource> files = manager.listResources(FOLDER, id -> id.getPath().endsWith(".json"));
		for (Map.Entry<Identifier, Resource> file : files.entrySet()) {
			// rd_sounds/<set>/<event>.json, in any namespace: a pack that adds or
			// replaces a recipe does not have to pretend to be this mod.
			String[] parts = file.getKey().getPath().split("/");
			if (parts.length != 3) {
				continue;
			}
			String event = parts[2].substring(0, parts[2].length() - ".json".length());
			List<String> problems = new ArrayList<>();
			SoundRecipe recipe = null;
			try (Reader reader = file.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				recipe = SoundRecipe.parse(json,
						sound -> manager.getResource(Sound.SOUND_LISTER.idToFile(sound)).isPresent(), problems);
			} catch (Exception e) {
				problems.add(String.valueOf(e.getMessage()));
			}
			for (String problem : problems) {
				RealisticDamage.LOGGER.warn("Injury sound recipe {}: {}", file.getKey(), problem);
			}
			if (recipe != null) {
				out.put(key(parts[1], event), recipe);
			}
		}
		return Map.copyOf(out);
	}
}
