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
	/**
	 * Exact condition figures on the inventory body panel. Off by default: the
	 * no-numbers rule is what keeps the HUD readable, and it is only on the
	 * dedicated detail screen that numbers earn their place. Blood and
	 * consciousness stay hidden even here — they are named in bands, never shown
	 * as figures, because knowing them exactly would remove the fear they exist
	 * to create.
	 */
	public boolean showNumericStats = false;

	public double vignetteIntensity = 1.0;
	public double sensitivityReductionIntensity = 1.0;
	/**
	 * How much the view keeps gliding after your hand stops (see MouseInertia).
	 * Separate from the sensitivity reduction above because they are different
	 * complaints: one is slow aim, the other is sloppy aim, and wanting one
	 * without the other is reasonable.
	 *
	 * <p>Boxed so that "absent from the file" is distinguishable from "set to
	 * zero". Before 1.1 this effect was governed by the sensitivity slider, so a
	 * config written by 1.0 has no key here — and defaulting it to 1.0 would
	 * switch the melty camera back on for someone who had deliberately turned it
	 * off. {@link #migrate()} seeds it from the old control instead.
	 */
	public Double lookInertiaIntensity;

	/** Never null after load; use this rather than the field. */
	public double lookInertia() {
		return lookInertiaIntensity == null ? sensitivityReductionIntensity : lookInertiaIntensity;
	}
	public double afterimageIntensity = 1.0;
	public double exposureIntensity = 1.0;
	public double ringingVolume = 1.0;
	/** true = synthesised tinnitus tone, false = the resonant bell ring. */
	public boolean tinnitusTone = true;
	/** True low-pass muffling of world audio (falls back to ducking if EFX is absent). */
	public double mufflingIntensity = 1.0;
	/** Flash, camera kick and thud when a heavy blow lands. */
	public double impactIntensity = 1.0;
	/** Fracture, destruction and collapse audio. */
	public double injurySoundVolume = 1.0;
	/** Real colour drain through vanilla's post-processing. */
	public double desaturationIntensity = 1.0;
	/** Real world-space warping, riding vanilla's nausea distortion. */
	public double distortionIntensity = 1.0;
	public double heartbeatVolume = 1.0;
	/** Grounded preset: the screen goes completely black while unconscious. */
	public boolean fullBlackUnconscious = false;
	/** Grounded preset: the camera sways and sags as consciousness fades. */
	public double cameraDriftIntensity = 0.0;
	/**
	 * Which set of injury sounds to play: the muted set that sits inside
	 * vanilla's palette, or the visceral set that does not. Presentation only,
	 * so each player picks their own — the server never decides this.
	 */
	public boolean visceralInjurySounds = false;

	/** Punchy and readable — effects present but never obscuring. */
	public void applyArcadePreset() {
		vignetteIntensity = 0.85;
		sensitivityReductionIntensity = 0.5;
		lookInertiaIntensity = 0.4;
		afterimageIntensity = 0.6;
		exposureIntensity = 0.6;
		ringingVolume = 0.8;
		heartbeatVolume = 1.0;
		fullBlackUnconscious = false;
		cameraDriftIntensity = 0.0;
		desaturationIntensity = 0.7;
		distortionIntensity = 0.5;
		visceralInjurySounds = false;
	}

	/** Heavier immersion: full blackout, camera drift, everything at full strength. */
	public void applyGroundedPreset() {
		vignetteIntensity = 1.0;
		sensitivityReductionIntensity = 1.0;
		lookInertiaIntensity = 1.0;
		afterimageIntensity = 1.0;
		exposureIntensity = 1.0;
		ringingVolume = 1.0;
		heartbeatVolume = 1.0;
		fullBlackUnconscious = true;
		cameraDriftIntensity = 1.0;
		desaturationIntensity = 1.0;
		distortionIntensity = 1.0;
		visceralInjurySounds = true;
	}

	/**
	 * The panic button: every sensory effect off, gameplay untouched. Lives here
	 * rather than in the screen so that adding an effect cannot quietly leave it
	 * out of the one control that promises to silence everything.
	 */
	public void disableAllEffects() {
		vignetteIntensity = 0.0;
		sensitivityReductionIntensity = 0.0;
		lookInertiaIntensity = 0.0;
		afterimageIntensity = 0.0;
		exposureIntensity = 0.0;
		ringingVolume = 0.0;
		heartbeatVolume = 0.0;
		cameraDriftIntensity = 0.0;
		desaturationIntensity = 0.0;
		distortionIntensity = 0.0;
		mufflingIntensity = 0.0;
		impactIntensity = 0.0;
		injurySoundVolume = 0.0;
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
		instance.migrate();
		save();
	}

	/**
	 * Carries settings forward when a control is split or renamed. An effect the
	 * player switched off must stay off across an update — that is the whole
	 * promise of the accessibility screen, and silently restoring one because a
	 * new JSON key defaulted to full strength would break it.
	 */
	public void migrate() {
		if (lookInertiaIntensity == null) {
			lookInertiaIntensity = sensitivityReductionIntensity;
		}
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
