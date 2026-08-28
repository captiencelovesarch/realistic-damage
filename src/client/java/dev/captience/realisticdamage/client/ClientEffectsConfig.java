package dev.captience.realisticdamage.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.captience.realisticdamage.RealisticDamage;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client-side presentation and accessibility settings (spec §44-45). All
 * sensory effects can be individually disabled or attenuated without touching
 * gameplay: the server never reads any of this.
 */
public final class ClientEffectsConfig {
	public boolean showBodyHud = true;
	/** 0 = only show the body HUD when something is wrong; 1 = always visible. */
	public boolean bodyHudAlwaysVisible = false;

	public double vignetteIntensity = 1.0;
	public double sensitivityReductionIntensity = 1.0;
	public double afterimageIntensity = 1.0;
	public double exposureIntensity = 1.0;
	public double ringingVolume = 1.0;
	public double heartbeatVolume = 1.0;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientEffectsConfig instance = new ClientEffectsConfig();

	public static ClientEffectsConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("realisticdamage-client.json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				instance = GSON.fromJson(Files.readString(path), ClientEffectsConfig.class);
				if (instance == null) {
					instance = new ClientEffectsConfig();
				}
			} catch (IOException | JsonSyntaxException e) {
				RealisticDamage.LOGGER.error("Could not read {}, using defaults", path, e);
				instance = new ClientEffectsConfig();
			}
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(instance));
		} catch (IOException e) {
			RealisticDamage.LOGGER.error("Could not write {}", path(), e);
		}
	}
}
