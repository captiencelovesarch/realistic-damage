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
	/**
	 * A hard landing puts you on the ground for a moment (1.4). Separable from
	 * the rest of the fall rework because losing control of your character, even
	 * briefly, is the one part of this that some players will simply not want.
	 */
	public boolean enableKnockdown = true;
	/** Consciousness cuts out in episodes on the way down (1.4). */
	public boolean enableMicrosleeps = true;
	/**
	 * Compress vanilla's lethal fall tail so long drops maim rather than delete
	 * (1.4). Off, falls kill exactly as vanilla says they do and the body trauma
	 * simply rides along on top.
	 */
	public boolean softenFallLethality = true;
	/**
	 * Arcade preset: further trauma to an already-Disabled part destroys it
	 * permanently. A destroyed arm can no longer use its hand, a destroyed leg
	 * is a permanent limp, and a destroyed head or torso kills.
	 */
	public boolean enableLimbDestruction = true;

	/**
	 * How much attention a body on the floor attracts.
	 *
	 * <p>{@link UnconsciousAggro#NEARBY} is the default because the alternatives
	 * both answer the wrong question. Pulling in every hostile within line of
	 * sight does not make collapsing dangerous, it makes it fatal in advance —
	 * in singleplayer there is nobody to drag you out, so the fight was decided
	 * before you hit the ground. Ignoring you entirely makes going down free.
	 * Gating on distance puts the consequence where it was earned: collapse in a
	 * fight and the things fighting you finish it, collapse alone at the bottom
	 * of a ravine and you wake up wrecked instead of executed.
	 */
	public UnconsciousAggro unconsciousAggro = UnconsciousAggro.NEARBY;

	public enum UnconsciousAggro {
		/** Hostiles lose interest in you entirely while you are down. */
		NONE,
		/** Nothing new acquires you; whatever was already fighting you carries on. */
		ATTACKERS,
		/** Existing attackers, plus anything close enough to notice a body. */
		NEARBY,
		/** Anything hostile with line of sight, at full aggro radius. */
		ALL;

		/**
		 * May this mob take an unconscious player as a target?
		 *
		 * <p>Pure, so the policy is testable without a running game — and it has
		 * to be enforced on the way *in* to {@code Mob.setTarget}. Clearing
		 * targets on a timer does not work: vanilla re-acquires a player from up
		 * to the mob's follow range (35 blocks for a zombie), which is far outside
		 * either of our radii, so anything short of blocking the acquisition
		 * leaves every mode behaving like ALL.
		 *
		 * @param alreadyTargeting whether this mob was already onto this player
		 * @param distance         blocks between them
		 */
		public boolean allowsTargeting(boolean alreadyTargeting, double distance) {
			return switch (this) {
				case NONE -> false;
				case ATTACKERS -> alreadyTargeting;
				case NEARBY -> alreadyTargeting || distance <= Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS;
				case ALL -> true;
			};
		}
	}

	public void applyArcadePreset() {
		enableLimbDestruction = true;
	}

	public void applyGroundedPreset() {
		enableLimbDestruction = false;
	}

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
			// Gson answers an unrecognised enum name with null rather than an
			// error, so a typo in the file would otherwise leave the field null
			// and the mode silently decided by which branch happened to fall
			// through first.
			if (instance.unconsciousAggro == null) {
				RealisticDamage.LOGGER.warn(
						"Unknown unconsciousAggro in {}, using {}", path, UnconsciousAggro.NEARBY);
				instance.unconsciousAggro = UnconsciousAggro.NEARBY;
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
