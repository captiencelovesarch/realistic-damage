package dev.captience.realisticdamage.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.captience.realisticdamage.RealisticDamage;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Server-authoritative gameplay settings (spec §45). These scale or disable
 * systems; the deterministic base numbers stay in {@link Balance}. Loaded from
 * {@code config/realisticdamage-server.json}; missing file writes defaults.
 */
public final class ServerConfig {
	public double bodyDamageMultiplier = 1.0;
	public double bleedingRateMultiplier = 1.0;
	public double consciousnessLossMultiplier = 1.0;
	public double healingMultiplier = 1.0;
	public double fallTraumaMultiplier = 1.0;
	public double pvpTraumaMultiplier = 1.0;
	public boolean enableBleeding = true;
	public boolean enableBrokenLimbs = true;
	public boolean enableConsciousness = true;
	public boolean enableBurns = true;
	public boolean enableHitLocation = true;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ServerConfig instance = new ServerConfig();

	public static ServerConfig get() {
		return instance;
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("realisticdamage-server.json");
	}

	public static void load() {
		Path path = path();
		if (Files.exists(path)) {
			try {
				instance = GSON.fromJson(Files.readString(path), ServerConfig.class);
				if (instance == null) {
					instance = new ServerConfig();
				}
			} catch (IOException | JsonSyntaxException e) {
				RealisticDamage.LOGGER.error("Could not read {}, using defaults", path, e);
				instance = new ServerConfig();
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
