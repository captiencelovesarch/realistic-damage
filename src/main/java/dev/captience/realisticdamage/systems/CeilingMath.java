package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.config.Balance;

/**
 * The long-term injury ladder (1.6). Pure, so the rule is testable without a
 * runtime and the server (which lowers ceilings), the body model (which clamps
 * to them) and the client (which draws the band) can never disagree about what a
 * band means. Ceilings are bands, never figures: these fractions are the state
 * boundaries themselves, so a part pinned at band 1 reads Hurt, band 2 Injured,
 * band 3 Critical — the colour the player already knows is the ceiling.
 */
public final class CeilingMath {
	private CeilingMath() {}

	/** What happened to a part in one damage event. All three are worth exactly one rung. */
	public enum Event { DISABLED, BROKEN, DESTROYED }

	/** The most a part at this band can recover to, as a fraction of its max. */
	public static double fraction(int band) {
		return switch (band) {
			case 1 -> Balance.HEALTHY_ABOVE;   // 0.75, reads HURT (fromFraction is exclusive-above)
			case 2 -> Balance.HURT_ABOVE;      // 0.50, reads INJURED
			case 3 -> Balance.INJURED_ABOVE;   // 0.25, reads CRITICAL
			default -> 1.0;                    // no ceiling
		};
	}

	/** The state word a part at this band is pinned at. Band 0 is HEALTHY: no ceiling. */
	public static BodyState pinnedState(int band) {
		return BodyState.fromFraction(fraction(band));
	}

	/** Deepest band this part may ever be pinned at: limbs bottom out at Critical, head and chest at Hurt. */
	public static int floorBand(BodyPart part) {
		return part.isLimb() ? Balance.CEILING_FLOOR_BAND_LIMB : Balance.CEILING_FLOOR_BAND_CORE;
	}

	/**
	 * Sanitises a band from a save, a command or another body. Anything this mod
	 * never wrote — negative, or past {@link Balance#CEILING_BANDS} — becomes "no
	 * ceiling": a corrupt or foreign field must never invent a permanent injury.
	 * Real bands are clamped to the part's floor, so neither an admin nor a
	 * hand-edited save can put a head under the symptom ladder.
	 */
	public static int legalBand(BodyPart part, int band) {
		if (band <= 0 || band > Balance.CEILING_BANDS) {
			return 0;
		}
		return Math.min(band, floorBand(part));
	}

	/** Heads and chests cannot break, so for them only zeroing and destruction count. */
	public static boolean qualifies(Event event, BodyPart part) {
		return event != Event.BROKEN || part.isLimb();
	}

	/** One rung down, never past the floor, never up, and identity for an event this part cannot have. */
	public static int lowered(int band, Event event, BodyPart part) {
		int current = legalBand(part, band);
		return qualifies(event, part) ? Math.min(current + 1, floorBand(part)) : current;
	}
}
