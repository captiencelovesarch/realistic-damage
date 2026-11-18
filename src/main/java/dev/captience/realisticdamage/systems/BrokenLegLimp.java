package dev.captience.realisticdamage.systems;

/**
 * A broken leg, seen from behind your own eyes (2.0): on every step taken with
 * it the view dips and leans a little toward that side.
 *
 * <p>It rides the walk cycle vanilla already animates. The model swings the
 * right leg by {@code cos(phase)} and the left by {@code cos(phase + pi)}; a leg
 * carries the body while it travels from in front to behind, which for the
 * right leg is the half of the cycle where {@code sin(phase)} is negative and
 * for the left the half where it is positive. So the dip lands on the same
 * steps the third-person model shows the bad leg taking.
 *
 * <p>Picture only, like every view motion in this mod: the aim is not touched,
 * so the crosshair stays exactly where the player put it. The sizes are kept
 * small for the same reason.
 *
 * <p>Units: blocks for the dip, degrees for the roll. A positive roll leans the
 * head to the right (the horizon turns anticlockwise on screen).
 */
public final class BrokenLegLimp {
	private BrokenLegLimp() {}

	// ---- Tuning. ----

	/** How far the eye sinks at the bottom of a step on the broken leg, blocks, at a full walk. */
	static final float DIP = 0.07F;
	/** How far the head leans toward the broken leg at the bottom of that step, degrees. */
	static final float ROLL = 1.6F;
	/** The walk-cycle rate the vanilla leg swing uses; phase = walk position times this. */
	public static final float CYCLE = 0.6662F;
	/**
	 * How sharply the step falls and recovers: 1 is a plain half sine, higher
	 * keeps the view level for longer between steps and makes the dip briefer.
	 */
	static final double SHARPNESS = 1.5;

	/**
	 * One frame of the limp.
	 *
	 * @param dip how far the eye sits below its usual height, blocks (0 or more)
	 * @param roll lean of the head, degrees, positive to the right
	 */
	public record Step(float dip, float roll) {
		public static final Step NONE = new Step(0.0F, 0.0F);
	}

	/**
	 * @param phase the walk cycle, radians: vanilla's walk position times {@link #CYCLE}
	 * @param pace how hard the player is walking, 0 (still) to 1 (a full walk or faster)
	 * @param leftBroken whether the left leg is broken
	 * @param rightBroken whether the right leg is broken
	 */
	public static Step step(double phase, float pace, boolean leftBroken, boolean rightBroken) {
		float p = Math.clamp(pace, 0.0F, 1.0F);
		if (p <= 0.0F || (!leftBroken && !rightBroken)) {
			return Step.NONE;
		}
		float left = leftBroken ? stance(phase, true) : 0.0F;
		float right = rightBroken ? stance(phase, false) : 0.0F;
		// The two legs are never on the ground in the same half of the cycle, so
		// with both broken this is a dip on every step, leaning each way in turn.
		return new Step((left + right) * DIP * p, (right - left) * ROLL * p);
	}

	/** How much of the body's weight is on a leg right now, 0 to 1. */
	public static float stance(double phase, boolean left) {
		double s = Math.sin(phase);
		double load = left ? s : -s;
		return load <= 0.0 ? 0.0F : (float) Math.pow(load, SHARPNESS);
	}
}
