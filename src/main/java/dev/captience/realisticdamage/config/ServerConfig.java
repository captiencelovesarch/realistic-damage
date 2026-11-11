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
 *
 * <p>Editable in game with {@code /rdmg config <setting> [value]}; the JSON is
 * storage, not the interface. Nobody should have to alt-tab to a text editor to
 * try a toggle, and the command is generated from these fields by reflection, so
 * a setting added here appears there without anyone remembering to wire it up.
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
	/**
	 * A blast or a heavy blow that does not kill knocks the body down as well
	 * (active ragdolls phase 4), not only a hard landing. Under
	 * {@link #enableKnockdown}.
	 */
	public boolean knockdownFromBlows = true;
	/** Consciousness cuts out in episodes on the way down (1.4). */
	public boolean enableMicrosleeps = true;
	/**
	 * An arm driven all the way to Disabled — grey on the body panel — loses its
	 * grip until it heals (1.7): its hand cannot use anything (no raised shield,
	 * no eating with it), two-handed items (bows, crossbows, spears) need both
	 * arms, and a limp main arm fights like a missing one. Off, a Disabled arm
	 * is only the strongest of the ordinary penalties, as it was before 1.7.
	 */
	public boolean disabledArmsLoseGrip = true;
	/**
	 * Dropping to the last two hearts floods you with adrenaline (1.7): a few
	 * seconds quicker and with the pain pushed back, then a crash of heavy legs
	 * and a breath that runs out after a short sprint.
	 */
	public boolean enableAdrenaline = true;
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
	 * A blast can take a limb outright (1.6): TNT and creepers are judged on the
	 * blast itself rather than on what the health bar let through. Sits under
	 * {@link #enableLimbDestruction}, because that flag is what makes a Destroyed
	 * limb exist at all — the hand lock and the offhand block key on it — so a
	 * blast must never create one while the Grounded preset says there is no
	 * destruction.
	 *
	 * <p>Off, no blast takes a limb. Explosion trauma is still routed from the
	 * blast's real centre, which is a 1.6 fix — an ownerless explosion had no
	 * source position at all and fell back to the head-and-chest split — and is
	 * deliberately not part of this toggle: it is geometry, not an injury rule.
	 * "Explosions exactly as they were in 1.5" would be a promise this flag does
	 * not keep.
	 */
	public boolean enableExplosiveDismemberment = true;

	/**
	 * Heads come off (2.0). An edge that lands on an already-Disabled head
	 * takes it off rather than crushing it, a lethal blast at its most violent
	 * throws it, and the head lands, rolls and can be picked up. Under
	 * {@link #enableLimbDestruction} like everything else that removes a part:
	 * without destruction a head is never destroyed in the first place.
	 */
	public boolean enableDecapitation = true;

	/**
	 * Necks snap (2.0): a limp or gliding body landing head first, a dive into
	 * water too shallow to stop it, an elytra into a wall at speed, a huge blunt
	 * blow to the head. Instant death; no totem saves it. Bosses are immune.
	 * Off, the same causes only ever wrench the neck.
	 */
	public boolean enableBrokenNecks = true;

	/**
	 * Whiplash (2.0): the same causes under the bar, and hard knockdowns, leave
	 * the neck stiff for 30 to 90 seconds. Players turn their view slower and
	 * quick turns hurt; mobs turn and aim worse.
	 */
	public boolean enableWhiplash = true;

	/**
	 * Mobs have bodies too (1.7): every entity type in the
	 * {@code realisticdamage:has_anatomy} tag takes regional trauma, bleeds,
	 * breaks, loses limbs, is concussed and collapses through the same pipeline
	 * a player does — humanoids out of the box, anything a datapack adds. Off,
	 * mobs are vanilla and no mob ever carries an attachment.
	 */
	public boolean enableMobAnatomy = true;

	/**
	 * A lost limb is a thing in the world (1.6.2): thrown from the loss, it
	 * lands, bleeds for most of a minute and is gone. Nested under
	 * {@link #enableLimbDestruction} by construction — no destruction, no limb —
	 * and its own switch because a server may want the injury without the prop:
	 * it is the only entity the mod spawns, and a server that counts entities
	 * is entitled to say no. Never saved, so switching it off leaves nothing
	 * behind.
	 */
	public boolean spawnSeveredLimbs = true;

	/**
	 * Hardcore mode: injuries leave a ceiling (1.6). A part driven to zero, a
	 * limb broken, a limb lost — each costs that region one rung of how well it
	 * can ever heal again, and food and time then take it that far and stop. The
	 * ceilings follow you through death, which is the whole point: a long-lived
	 * character accumulates a history you can read off their body.
	 *
	 * <p>Off by default, and deliberately so. Every other setting here scales
	 * something that already happens; this one changes what a life is worth, and
	 * that is a decision a server makes on purpose rather than one it discovers
	 * three weeks in.
	 */
	public boolean enableLongTermInjuries = false;

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

	/**
	 * Which injuries make a player the preferred target (1.6).
	 *
	 * <p>{@link PreyOnWounded#VISIBLE} is the default because it is the sentence
	 * on the roadmap: hostiles prefer targets that are visibly limping or
	 * bleeding. Both halves of that adjective are load-bearing. A leg the game
	 * itself calls Hurt or Injured costs at most a twelfth of your speed, which
	 * nobody can see, so the limp only counts from Critical down — the same rung
	 * the model starts posing at, because both read one definition. And a
	 * scratch seeps rather than drips, so blood only counts from a wound that is
	 * actually running. What is left fires on real injuries, and blood — the
	 * briefest of the signals — carries so little weight that it can tip a fresh
	 * acquisition but never pull a mob off the player it is already fighting.
	 * {@link PreyOnWounded#LIMPING} and {@link PreyOnWounded#DOWNED} scale it
	 * back for a server that wants less; {@code /rdmg config preyOnWounded off}
	 * turns it off entirely.
	 *
	 * <p>Only hostiles hunt this way, and only the ones that choose a target
	 * through {@code Mob.setTarget}. Brain-driven hostiles — piglins, hoglins,
	 * zoglins, wardens, breezes, creakings — read their target out of a memory
	 * instead, so this setting and {@link #unconsciousAggro} both leave them
	 * alone, as they always have.
	 */
	public PreyOnWounded preyOnWounded = PreyOnWounded.VISIBLE;

	public enum PreyOnWounded {
		/** 1.5 behaviour: the nearest player wins. */
		OFF,
		/** Only a body on the floor — unconscious, knocked down, or legless — is preferred. */
		DOWNED,
		/** Also a leg that is gone, broken, or worn down past the point where the limp shows. */
		LIMPING,
		/** Also a wound that is actually bleeding. The roadmap's "limping or bleeding". */
		VISIBLE;

		/** Legs count toward the score. Pure, so {@code PreyMath} stays unit-testable. */
		public boolean includesLimp() {
			return ordinal() >= LIMPING.ordinal();
		}

		/** Wounds count toward the score. */
		public boolean includesBleed() {
			return this == VISIBLE;
		}
	}

	/**
	 * The field kit's Weapons half (2.0): anything that hurts someone or
	 * protects you from being hurt — the brewed poisons, the trick arrows, the
	 * traps, the gas mask. Off by default, and asked about on first launch.
	 *
	 * <p>Off means off without a restart: the items stay registered, so a world
	 * never breaks when this flips, but nothing makes them (crafting, brewing,
	 * loot, trades), nothing shows them (the creative tab), an item already in a
	 * chest does nothing and says why, and its effects wear off at once. With
	 * both halves off, the core rule holds exactly: food and time are the only
	 * cure.
	 */
	public boolean enableWeapons = false;

	/**
	 * The field kit's Medicine half (2.0): anything that treats, supports or
	 * reads an injury that already happened — bandages, splints, the
	 * stethoscope, Numbing. Off by default for the same reason, and switched
	 * exactly the same way as {@link #enableWeapons}.
	 */
	public boolean enableMedicine = false;

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
			if (instance.preyOnWounded == null) {
				RealisticDamage.LOGGER.warn(
						"Unknown preyOnWounded in {}, using {}", path, PreyOnWounded.VISIBLE);
				instance.preyOnWounded = PreyOnWounded.VISIBLE;
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
