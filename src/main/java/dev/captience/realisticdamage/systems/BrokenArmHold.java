package dev.captience.realisticdamage.systems;

/**
 * How a broken arm holds something, seen from behind your own eyes (2.0).
 *
 * <p>Third person already hangs a fractured arm; in first person a broken arm
 * held a sword exactly as a sound one did. This is the missing half, as plain
 * arithmetic so it can be tested without a game and tuned in one place:
 *
 * <ul>
 * <li><b>The sag.</b> The hand sits lower, rolled in toward the middle of the
 * view and tipped forward, as if the wrist cannot carry what it holds. Heavier
 * things sag more.</li>
 * <li><b>The tremor.</b> A small constant shake, stronger while sprinting and
 * while swinging. A sum of sines of the clock, no random numbers, so the same
 * moment always draws the same hand.</li>
 * <li><b>The laboured swing.</b> Vanilla's swing is one number running 0 to 1.
 * A broken arm gets only the first part of that motion (the lift), then comes
 * back the way it went while the hand drops, instead of following through.
 * Heavier things get less of the lift and more of the drop.</li>
 * </ul>
 *
 * <p>Picture only. Nothing here is read by the server, and the attack itself
 * (damage, cooldown, reach) is whatever the server says it is.
 *
 * <p>Units are the first-person view's own: blocks for offsets, degrees for
 * angles, game ticks for time. Sideways values are for a RIGHT hand; the caller
 * mirrors them for a left one.
 */
public final class BrokenArmHold {
	private BrokenArmHold() {}

	/** How heavy the held thing is to a wrist that cannot carry weight. */
	public enum Weight {
		/** Nothing held: the bare arm. */
		EMPTY,
		/** Food, blocks, torches, anything small. */
		LIGHT,
		/** Swords, picks, shovels, hoes, a bow. */
		MEDIUM,
		/** Axes, the mace, a shield, a trident, a spear, a crossbow. */
		HEAVY
	}

	// ---- Tuning. Indexed by Weight.ordinal(): EMPTY, LIGHT, MEDIUM, HEAVY. ----

	/** How far the hand sinks, blocks. */
	static final float[] SAG_DROP = {0.04F, 0.06F, 0.10F, 0.15F};
	/** How far the held thing rolls in toward the middle of the view, degrees. */
	static final float[] SAG_ROLL = {5.0F, 7.0F, 12.0F, 18.0F};
	/** How far its tip falls forward, degrees. */
	static final float[] SAG_TIP = {5.0F, 7.0F, 13.0F, 20.0F};

	/** The resting shake: offset in blocks, twist in degrees. */
	static final float TREMOR_OFFSET = 0.0035F;
	static final float TREMOR_TWIST = 0.7F;
	/** Added on top of the resting shake (as a multiple of it) while sprinting, and at the height of a swing. */
	static final float TREMOR_SPRINT = 0.8F;
	static final float TREMOR_SWING = 1.4F;
	/** A heavier thing shakes a little more. */
	static final float[] TREMOR_WEIGHT = {0.8F, 1.0F, 1.15F, 1.3F};

	/**
	 * How much of vanilla's swing the arm manages: the largest swing progress
	 * it is ever shown at. Vanilla's swing is front-loaded (the hand is already
	 * at its furthest by a quarter of the way through), so these are small.
	 */
	static final float[] SWING_REACH = {0.07F, 0.07F, 0.05F, 0.035F};
	/** The share of the swing's time spent on the lift; the rest is the hand coming back down. */
	static final float SWING_LIFT_TIME = 0.30F;
	/** How far the hand drops after the lift, blocks, and how far the tip falls with it, degrees. */
	static final float[] SWING_DROP = {0.05F, 0.06F, 0.10F, 0.15F};
	static final float[] SWING_DROP_TIP = {6.0F, 7.0F, 11.0F, 16.0F};

	/**
	 * One frame of the hold.
	 *
	 * @param drop how far below its usual place the hand sits, blocks (0 or more)
	 * @param roll roll in toward the middle of the view, degrees, right hand
	 * @param tip forward fall of the tip, degrees
	 * @param shakeX sideways shake, blocks
	 * @param shakeY vertical shake, blocks
	 * @param shakeTwist shake about the view axis, degrees
	 * @param swing the swing progress to hand vanilla in place of the real one
	 */
	public record Pose(float drop, float roll, float tip, float shakeX, float shakeY, float shakeTwist, float swing) {}

	/**
	 * @param weight what is held
	 * @param swing vanilla's swing progress, 0 (not swinging) to 1
	 * @param sprinting whether the player is sprinting
	 * @param timeTicks a running clock in game ticks (partial ticks included)
	 * @param hanging true if the arm is already drawn hanging by another effect
	 *        (the worn-out "will not grip" droop): the sag is left out so the two
	 *        do not stack, the tremor and the swing stay
	 */
	public static Pose pose(Weight weight, float swing, boolean sprinting, double timeTicks, boolean hanging) {
		int w = weight.ordinal();
		float s = Math.clamp(swing, 0.0F, 1.0F);
		float fall = swingDrop(s);
		float sag = hanging ? 0.0F : 1.0F;
		float drop = sag * SAG_DROP[w] + fall * SWING_DROP[w];
		float roll = sag * SAG_ROLL[w];
		float tip = sag * SAG_TIP[w] + fall * SWING_DROP_TIP[w];
		float strength = tremorStrength(weight, s, sprinting);
		// Three rates that never line up (about 8, 11 and 14 times a second), so
		// the shake does not read as a loop.
		float x = (float) (Math.sin(timeTicks * 2.51) + 0.5 * Math.sin(timeTicks * 4.37 + 1.3));
		float y = (float) (Math.sin(timeTicks * 3.46 + 0.7) + 0.5 * Math.sin(timeTicks * 2.83 + 2.1));
		float twist = (float) (Math.sin(timeTicks * 3.05 + 4.0) + 0.5 * Math.sin(timeTicks * 4.71));
		return new Pose(drop, roll, tip,
				x / 1.5F * TREMOR_OFFSET * strength,
				y / 1.5F * TREMOR_OFFSET * strength,
				twist / 1.5F * TREMOR_TWIST * strength,
				labouredSwing(weight, s));
	}

	/**
	 * The swing progress the arm is drawn at: up to the weight's reach during
	 * the lift, then back to rest the way it came. Zero at both ends, so the
	 * swing starts and ends where vanilla's does.
	 */
	public static float labouredSwing(Weight weight, float swing) {
		float s = Math.clamp(swing, 0.0F, 1.0F);
		float reach = SWING_REACH[weight.ordinal()];
		if (s < SWING_LIFT_TIME) {
			return reach * easeOut(s / SWING_LIFT_TIME);
		}
		float back = (s - SWING_LIFT_TIME) / (1.0F - SWING_LIFT_TIME);
		return reach * (1.0F - smooth(back));
	}

	/**
	 * How far through its fall the hand is, 0 to 1 and back to 0: nothing during
	 * the lift, deepest soon after it, recovered by the end of the swing.
	 */
	public static float swingDrop(float swing) {
		float s = Math.clamp(swing, 0.0F, 1.0F);
		if (s <= SWING_LIFT_TIME) {
			return 0.0F;
		}
		float back = (s - SWING_LIFT_TIME) / (1.0F - SWING_LIFT_TIME);
		// The square root puts the bottom of the fall early (a third of the way
		// back) and the slow part on the recovery.
		return (float) Math.sin(Math.PI * Math.sqrt(back));
	}

	/** How many times the resting shake the hand shakes right now (before the weight's own factor: at least 1). */
	public static float tremorStrength(Weight weight, float swing, boolean sprinting) {
		float s = Math.clamp(swing, 0.0F, 1.0F);
		// Strongest in the middle of the swing, nothing extra at either end, so
		// the shake does not jump when a swing starts or stops.
		float effort = (float) Math.sin(Math.PI * s);
		return TREMOR_WEIGHT[weight.ordinal()] * (1.0F + (sprinting ? TREMOR_SPRINT : 0.0F) + TREMOR_SWING * effort);
	}

	private static float easeOut(float t) {
		return 1.0F - (1.0F - t) * (1.0F - t);
	}

	private static float smooth(float t) {
		return t * t * (3.0F - 2.0F * t);
	}
}
