package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.config.Balance;

/**
 * Pure penalty curves for limb impairment (spec §11-12). Penalties are
 * fractions in [0,1) applied multiplicatively as (1 - penalty). Curves are
 * piecewise-linear over condition fraction so degradation is smooth, with a
 * distinct (larger) value at exactly DISABLED.
 */
public final class ImpairmentMath {
	private ImpairmentMath() {}

	/** Piecewise-linear through (0.75,0) (0.5,p50) (0.25,p25) (0+,p0); exact 0 returns pDisabled. */
	private static double curve(double fraction, double p50, double p25, double p0, double pDisabled) {
		if (fraction <= 0.0) {
			return pDisabled;
		}
		if (fraction > 0.75) {
			return 0.0;
		}
		if (fraction > 0.5) {
			return lerp((0.75 - fraction) / 0.25, 0.0, p50);
		}
		if (fraction > 0.25) {
			return lerp((0.5 - fraction) / 0.25, p50, p25);
		}
		return lerp((0.25 - fraction) / 0.25, p25, p0);
	}

	private static double lerp(double t, double from, double to) {
		return from + (to - from) * t;
	}

	/** Action penalty for a single arm: mining/attack/item-use speed, melee damage share. */
	public static double armPenalty(double conditionFraction, boolean broken) {
		double penalty = curve(conditionFraction,
				Balance.ARM_PENALTY_AT_50, Balance.ARM_PENALTY_AT_25,
				Balance.ARM_PENALTY_NEAR_0, Balance.ARM_PENALTY_DISABLED);
		if (broken) {
			penalty += Balance.ARM_PENALTY_BROKEN_BONUS;
		}
		return Math.min(penalty, Balance.ARM_PENALTY_TOTAL_CAP);
	}

	/** Movement penalty for a single destroyed leg: permanent and heavy. */
	public static double legPenaltyDestroyed() {
		return Balance.LEG_PENALTY_DESTROYED;
	}

	/** Movement penalty for a single leg. */
	public static double legPenalty(double conditionFraction, boolean broken) {
		double penalty = curve(conditionFraction,
				Balance.LEG_PENALTY_AT_50, Balance.LEG_PENALTY_AT_25,
				Balance.LEG_PENALTY_NEAR_0, Balance.LEG_PENALTY_DISABLED);
		if (broken) {
			penalty += Balance.LEG_PENALTY_BROKEN_BONUS;
		}
		return Math.min(penalty, Balance.LEG_PENALTY_TOTAL_CAP);
	}

	/** Combined movement penalty, weighting the worse leg more heavily (spec §12). */
	public static double combinedLegPenalty(double leftPenalty, double rightPenalty) {
		double worse = Math.max(leftPenalty, rightPenalty);
		double better = Math.min(leftPenalty, rightPenalty);
		double combined = worse * Balance.LEG_WORSE_WEIGHT + better * (1.0 - Balance.LEG_WORSE_WEIGHT);
		return Math.min(combined, Balance.LEG_PENALTY_TOTAL_CAP);
	}

	/** Jump-strength penalty derived from the combined movement penalty. */
	public static double jumpPenalty(double combinedLegPenalty) {
		return combinedLegPenalty * Balance.JUMP_PENALTY_FACTOR;
	}

	/**
	 * Two-handed action penalty (bows, crossbows, rowing): dominated by the worse
	 * arm but the better arm helps (same weighting philosophy as legs).
	 */
	public static double twoHandedPenalty(double mainPenalty, double offPenalty) {
		double worse = Math.max(mainPenalty, offPenalty);
		double better = Math.min(mainPenalty, offPenalty);
		double combined = worse * 0.65 + better * 0.35;
		return Math.min(combined, Balance.ARM_PENALTY_TOTAL_CAP);
	}

	/**
	 * Fall-trauma amplification from already-injured legs (spec §13): 1.0 for
	 * healthy legs, up to FALL_TRAUMA_INJURED_LEG_MAX_AMP as the worse leg
	 * approaches zero condition.
	 */
	public static double fallTraumaAmplifier(double worseLegFraction) {
		double clamped = Math.clamp(worseLegFraction, 0.0, 1.0);
		double severity = 1.0 - clamped;
		return 1.0 + severity * (Balance.FALL_TRAUMA_INJURED_LEG_MAX_AMP - 1.0);
	}
}
