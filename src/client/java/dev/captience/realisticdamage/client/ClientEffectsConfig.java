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
	/** Injury icons under the body diagram (bleeding, broken, destroyed, ...). */
	public boolean showStatusIcons = true;

	public double vignetteIntensity = 1.0;
	public double sensitivityReductionIntensity = 1.0;
	public double afterimageIntensity = 1.0;
	public double exposureIntensity = 1.0;
	public double ringingVolume = 1.0;
	/** true = synthesised tinnitus tone, false = the resonant bell ring. */
	public boolean tinnitusTone = true;
	/** True low-pass muffling of world audio (falls back to ducking if EFX is absent). */
	public double mufflingIntensity = 1.0;
	/** Flash, camera kick and thud when a heavy blow lands. */
	public double impactIntensity = 1.0;
	/** Real colour drain through vanilla's post-processing. */
	public double desaturationIntensity = 1.0;
	/** Real world-space warping, riding vanilla's nausea distortion. */
	public double distortionIntensity = 1.0;
	public double heartbeatVolume = 1.0;
	/** Grounded preset: the screen goes completely black while unconscious. */
	public boolean fullBlackUnconscious = false;
	/** Grounded preset: the camera sways and sags as consciousness fades. */
	public double cameraDriftIntensity = 0.0;

	/** Punchy and readable — effects present but never obscuring. */
	public void applyArcadePreset() {
		vignetteIntensity = 0.85;
		sensitivityReductionIntensity = 0.5;
		afterimageIntensity = 0.6;
		exposureIntensity = 0.6;
		ringingVolume = 0.8;
		heartbeatVolume = 1.0;
		fullBlackUnconscious = false;
		cameraDriftIntensity = 0.0;
		desaturationIntensity = 0.7;
		distortionIntensity = 0.5;
	}

	/** Heavier immersion: full blackout, camera drift, everything at full strength. */
	public void applyGroundedPreset() {
		vignetteIntensity = 1.0;
		sensitivityReductionIntensity = 1.0;
		afterimageIntensity = 1.0;
		exposureIntensity = 1.0;
		ringingVolume = 1.0;
		heartbeatVolume = 1.0;
		fullBlackUnconscious = true;
		cameraDriftIntensity = 1.0;
		desaturationIntensity = 1.0;
		distortionIntensity = 1.0;
	}

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
