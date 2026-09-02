package dev.captience.realisticdamage.systems;

import java.util.Arrays;

import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;

/** Pure math for bleeding wounds: how they stack (spec §16), and what a stump opens (1.6). */
public final class WoundMath {
	private WoundMath() {}

	/**
	 * How hard a stump bleeds: <b>critical for a blow, severe for a grind.</b>
	 * A limb that still had condition to lose when the blow landed was taken off
	 * by that blow and opens a {@link WoundSeverity#CRITICAL} wound; a limb
	 * already sitting at zero — ground down over a whole fight and then finished
	 * off — was dead weight before this hit arrived, and detaching it opens a
	 * {@link WoundSeverity#SEVERE} one.
	 *
	 * <p>The seam is the difference {@code AppliedTrauma} already records. Its
	 * {@code attempted} is what the blow was worth and is what the destruction
	 * threshold reads; its {@code conditionDamage} is what the part actually
	 * lost, and on a part at zero that is structurally 0.0 no matter how big the
	 * blow was, because {@code BodyPartData.damage} clamps at zero. So "did this
	 * blow take anything off a limb that was still standing?" is answerable from
	 * one number, with no state and no clock — which is what keeps it
	 * deterministic and keeps identical hits identical.
	 *
	 * <p>Not derived from <em>how much</em> was lost: the residual condition of a
	 * limb about to come off is a fact about armour and about how ground down it
	 * already was, not about the size of the hole. Only the yes/no is read.
	 *
	 * @param conditionLost condition the part actually gave up to the killing blow
	 */
	public static WoundSeverity stumpSeverity(double conditionLost) {
		return conditionLost > Balance.CONDITION_EPSILON
				? WoundSeverity.CRITICAL
				: WoundSeverity.SEVERE;
	}

	/**
	 * Effective total bleed rate with strong diminishing returns: wounds sorted
	 * strongest-first contribute 100% / 50% / 25% / 12.5%... and the total is
	 * capped. E.g. rates equivalent to 40 + 20 yield ~50, never 60.
	 */
	public static double effectiveBleedRate(double[] ratesPerSecond) {
		if (ratesPerSecond.length == 0) {
			return 0.0;
		}
		double[] sorted = ratesPerSecond.clone();
		Arrays.sort(sorted);
		double total = 0.0;
		double[] weights = Balance.WOUND_STACK_WEIGHTS;
		for (int i = 0; i < sorted.length; i++) {
			double rate = sorted[sorted.length - 1 - i];
			double weight = weights[Math.min(i, weights.length - 1)];
			total += rate * weight;
		}
		return Math.min(total, Balance.WOUND_STACK_CAP);
	}
}
