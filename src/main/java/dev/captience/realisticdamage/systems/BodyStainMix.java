package dev.captience.realisticdamage.systems;

/**
 * Whose blood a stain on a body reads as (2.0).
 *
 * <p>A region of a body carries one amount of stain. Some of it may have come
 * from somebody else - a zombie's blood thrown across your chest - and that
 * share is kept beside the amount. The region is drawn in one colour: the
 * other body's when more than half of what is on it came from them, its own
 * otherwise. So a bleeding arm that catches a few drops of green stays red,
 * and a clean chest sprayed by a slime is green.
 *
 * <p>Pure, so the rule is unit tested.
 */
public final class BodyStainMix {
	private BodyStainMix() {}

	/** True when the stain on a region reads as somebody else's blood. */
	public static boolean foreignShows(double level, double foreign) {
		return foreign > 0.0 && foreign > level * 0.5;
	}

	/** The foreign share after the region's stain went from {@code before} to {@code after}: it dries with the rest. */
	public static double foreignAfter(double foreign, double before, double after) {
		if (foreign <= 0.0 || after <= 0.0) {
			return 0.0;
		}
		if (after >= before || before <= 0.0) {
			return Math.min(foreign, after);
		}
		return foreign * (after / before);
	}
}
