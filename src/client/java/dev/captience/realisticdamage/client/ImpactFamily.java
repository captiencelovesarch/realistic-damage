package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.damage.DamageCategory;

/**
 * The four ways a blow can look, collapsed from the seventeen ways it can be
 * classified (1.5).
 *
 * <p>Seventeen categories is the right resolution for deciding what a hit does
 * to a body; it is far too fine for deciding what it does to the screen, where
 * anything the player cannot name at a glance is noise. So the profile table
 * stays as it is and the presentation layer folds it down to four physical
 * events that look nothing like each other:
 *
 * <ul>
 * <li><b>Cut</b> — something opened you. Blood on the lens, a smear along the
 * axis of the stroke.</li>
 * <li><b>Pierce</b> — something focal went in. The view pinches shut and
 * springs back; no wash, because a puncture is not a broad event.</li>
 * <li><b>Blow</b> — something heavy moved your head. It keeps moving after: the
 * camera rings, and for a moment you see double.</li>
 * <li><b>Blast</b> — overpressure. The world whites out rather than reddening,
 * and your hearing goes.</li>
 * </ul>
 *
 * <p>Everything not one of those keeps the plain flash it always had, which is
 * the honest answer for poison, drowning or falling: none of them is a blow
 * with a direction, and inventing a look for them would be decoration.
 */
public enum ImpactFamily {
	PLAIN,
	CUT,
	PIERCE,
	BLOW,
	BLAST;

	public static ImpactFamily of(DamageCategory category) {
		return switch (category) {
			case SLASH, HEAVY_SLASH -> CUT;
			case PUNCTURE, HEAVY_PUNCTURE, BALLISTIC, HEAVY_BALLISTIC -> PIERCE;
			case BLUNT, HEAVY_BLUNT -> BLOW;
			case EXPLOSION -> BLAST;
			default -> PLAIN;
		};
	}

	/** True for the categories that arrive as a heavier version of the same event. */
	public static boolean isHeavy(DamageCategory category) {
		return switch (category) {
			case HEAVY_SLASH, HEAVY_PUNCTURE, HEAVY_BALLISTIC, HEAVY_BLUNT, EXPLOSION -> true;
			default -> false;
		};
	}
}
