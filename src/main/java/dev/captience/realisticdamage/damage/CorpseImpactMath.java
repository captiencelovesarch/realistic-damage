package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;

/**
 * What a dead body's landing does to it, as pure arithmetic (2.0). No
 * Minecraft types, like {@link FallMath}, so the thresholds can be tested and
 * retuned without a running game.
 *
 * <p>A living landing is scored on how far the body fell. A dead one has no
 * fall distance worth the name (it may have been thrown, or have died halfway
 * down), so it is scored on how fast it was going when it struck, turned back
 * into the drop that would have got it to that speed, and from there on the
 * living rule's own scale: {@link FallMath#traumaEquivalent}, the surface it
 * struck, the armour over the limb that struck.
 */
public final class CorpseImpactMath {
	private CorpseImpactMath() {}

	/** The ragdoll's gravity, blocks a second squared: Minecraft's own 0.08 a tick squared. */
	static final double GRAVITY = 32.0;

	/**
	 * The drop, blocks, that would have a body arriving at this speed, blocks a
	 * second: v squared over 2g. Air drag is left out, so a very long real fall
	 * reads a little shorter than it was; the bars are set on this scale.
	 */
	public static double equivalentDrop(double speed) {
		return speed <= 0.0 ? 0.0 : speed * speed / (2.0 * GRAVITY);
	}

	/**
	 * Fall-trauma of one part striking something.
	 *
	 * @param speed   how fast it was closing on the surface, blocks a second
	 * @param surface what the surface gives back (see {@link LandingSurface})
	 * @param armour  the share the armour over that part takes, 0..1
	 * @param scale   the server's body-damage multiplier over the body's vitality
	 */
	public static double trauma(double speed, double surface, double armour, double scale) {
		if (speed < Balance.CORPSE_IMPACT_MIN_SPEED) {
			return 0.0;
		}
		return FallMath.traumaEquivalent(equivalentDrop(speed), surface)
				* (1.0 - Math.clamp(armour, 0.0, 1.0)) * Math.max(0.0, scale);
	}

	/** Whether a strike worth this much breaks the limb's bone. Only limbs have one to break here. */
	public static boolean breaks(BodyPart part, double trauma) {
		if (!part.isLimb()) {
			return false;
		}
		return trauma >= (part.isArm() ? Balance.CORPSE_IMPACT_BREAK_ARM : Balance.CORPSE_IMPACT_BREAK_LEG);
	}

	/**
	 * Whether a strike worth this much takes a limb off a body that has already
	 * lost {@code alreadyTaken} this way. Past the last bar nothing more comes
	 * off, whatever the strike.
	 */
	public static boolean severs(BodyPart part, double trauma, int alreadyTaken) {
		if (!part.isLimb() || alreadyTaken < 0 || alreadyTaken >= Balance.CORPSE_IMPACT_SEVER.length) {
			return false;
		}
		return trauma >= Balance.CORPSE_IMPACT_SEVER[alreadyTaken];
	}

	/**
	 * The parts that struck hard enough to be looked at, hardest first; equal
	 * strikes in index order, so the same landing always judges the same limb
	 * first. {@code strikes} is indexed however the caller indexes its parts,
	 * blocks a second; the result holds those indices.
	 */
	public static int[] hardestFirst(double[] strikes) {
		int n = 0;
		int[] order = new int[strikes.length];
		for (int i = 0; i < strikes.length; i++) {
			if (strikes[i] >= Balance.CORPSE_IMPACT_MIN_SPEED) {
				// Insertion sort: six parts at most.
				int at = n++;
				while (at > 0 && strikes[order[at - 1]] < strikes[i]) {
					order[at] = order[at - 1];
					at--;
				}
				order[at] = i;
			}
		}
		return java.util.Arrays.copyOf(order, n);
	}

	/**
	 * Whether a death is worth following at all: the body was really travelling
	 * when it died, or it is about to be (nothing under it for a long way).
	 *
	 * @param speed     how fast it was going, blocks a tick
	 * @param clearDrop the clear air under its feet, blocks
	 */
	public static boolean worthFollowing(double speed, double clearDrop) {
		return speed >= Balance.CORPSE_IMPACT_FOLLOW_SPEED || clearDrop >= Balance.CORPSE_IMPACT_FOLLOW_DROP;
	}
}
