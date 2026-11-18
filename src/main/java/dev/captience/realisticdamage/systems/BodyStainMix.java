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

	/**
	 * Which region a drop landing on a body stains.
	 *
	 * <p>By height alone there are only three answers - head, chest, legs - and
	 * for a long time that was the whole rule, with "legs" meaning the left one:
	 * a body drenched in somebody else's blood had clean arms and one clean leg.
	 * The side the drop struck on settles the rest. A body's arms hang outside
	 * its chest, so a drop at chest height that lands off to one side is on that
	 * arm; a drop at leg height is on the leg of the side it landed on.
	 *
	 * @param height  0 at the feet, 1 at the top of the head
	 * @param toRight how far to the body's own right the drop landed, as a share
	 *                of half its width: -1 the left edge, 0 dead centre, 1 the right edge
	 */
	public static dev.captience.realisticdamage.body.BodyPart splashRegion(double height, double toRight) {
		if (height > 0.82) {
			return dev.captience.realisticdamage.body.BodyPart.HEAD;
		}
		boolean right = toRight > 0.0;
		if (height > 0.44) {
			if (Math.abs(toRight) > ARM_FROM) {
				return right ? dev.captience.realisticdamage.body.BodyPart.RIGHT_ARM
						: dev.captience.realisticdamage.body.BodyPart.LEFT_ARM;
			}
			return dev.captience.realisticdamage.body.BodyPart.TORSO;
		}
		return right ? dev.captience.realisticdamage.body.BodyPart.RIGHT_LEG
				: dev.captience.realisticdamage.body.BodyPart.LEFT_LEG;
	}

	/** Past this share of the half-width a chest-high drop is on an arm: the chest is the middle half of the body. */
	static final double ARM_FROM = 0.5;

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
