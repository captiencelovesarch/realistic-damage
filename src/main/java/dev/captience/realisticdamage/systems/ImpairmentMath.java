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

	/**
	 * Jump-strength penalty derived from the combined movement penalty, bounded so
	 * a wrecked or lost leg can still climb one block.
	 *
	 * <p>The cap belongs here and not at the call site. ImpairmentSystem assigns a
	 * penalty of 1.0 outright for crawling, dragging, pressing a wound, knockdown,
	 * microsleep and unconsciousness, and it does so <em>after</em> this call — a cap
	 * applied downstream of those would hand an unconscious player their jump back.
	 * What is bounded here is what an injury costs; a state that forbids jumping is
	 * still allowed to forbid it absolutely.
	 *
	 * <p>See {@link Balance#JUMP_PENALTY_INJURY_CAP} for the vanilla arithmetic the
	 * bound is derived from.
	 */
	public static double jumpPenalty(double combinedLegPenalty) {
		return Math.min(combinedLegPenalty * Balance.JUMP_PENALTY_FACTOR,
				Balance.JUMP_PENALTY_INJURY_CAP);
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
	 * How much longer a meal takes as consciousness fades (1.4), as extra
	 * duration on the food's own: 0 is normal speed, 1.0 is twice as long, 4.0 is
	 * five times — before {@link #useStallPerTick} caps the whole meal at
	 * {@link Balance#EAT_MAX_TICKS}.
	 *
	 * <p>Food is the only cure in this mod, so eating is the one action that is
	 * never refused for weakness — but it should not stay a free instant either.
	 * Slowing it turns the last of your consciousness into something you have to
	 * spend deliberately: getting bread down while greying out means finding
	 * eight seconds where nothing is hitting you, which is a decision, where
	 * tapping a key and being fine was not.
	 */
	public static double eatingStallPenalty(double effectiveConsciousness) {
		if (effectiveConsciousness >= Balance.SYMPTOM_ATTACK_RECOVERY) {
			return 0.0;
		}
		if (effectiveConsciousness <= 0.0) {
			return Balance.EAT_STALL_AT_0;
		}
		if (effectiveConsciousness >= Balance.SYMPTOM_SPRINT_LOSS) {
			double t = (Balance.SYMPTOM_ATTACK_RECOVERY - effectiveConsciousness)
					/ (Balance.SYMPTOM_ATTACK_RECOVERY - Balance.SYMPTOM_SPRINT_LOSS);
			return t * Balance.EAT_STALL_AT_20;
		}
		double t = (Balance.SYMPTOM_SPRINT_LOSS - effectiveConsciousness) / Balance.SYMPTOM_SPRINT_LOSS;
		return Balance.EAT_STALL_AT_20 + t * (Balance.EAT_STALL_AT_0 - Balance.EAT_STALL_AT_20);
	}

	/**
	 * Turns an extra-duration factor into the stalled use-ticks to add per tick
	 * (1.6.2). A use that counts down one tick per tick and is handed back
	 * {@code s} of a tick each time takes {@code 1 / (1 - s)} times as long, so
	 * a meal that should take {@code 1 + extra} times its length needs
	 * {@code s = 1 - 1 / (1 + extra)} — always under one, so it always ends.
	 *
	 * <p>Capped so the whole meal never exceeds {@link Balance#EAT_MAX_TICKS}:
	 * the factor is cut to {@code EAT_MAX_TICKS / baseTicks} when the food's own
	 * duration would carry it past five seconds, and never below one.
	 *
	 * <p>The bug this replaces: the curve's value was added directly as ticks,
	 * so at an extra of 1.0 the countdown gained a tick for every tick it lost
	 * and never reached zero. Food is the only cure in this mod, and below
	 * consciousness 20 it had quietly stopped working.
	 */
	public static double useStallPerTick(double extra, int baseTicks) {
		double factor = 1.0 + Math.max(0.0, extra);
		if (baseTicks > 0) {
			factor = Math.min(factor, Math.max(1.0, Balance.EAT_MAX_TICKS / (double) baseTicks));
		}
		return 1.0 - 1.0 / factor;
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
