package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.body.WoundSeverity;

/**
 * Deterministic description of how one {@link DamageCategory} turns
 * post-mitigation damage into body consequences (spec §5, §33).
 *
 * <p>Bleed thresholds are localized condition-damage amounts: the strongest
 * threshold at or below the dealt trauma decides the wound severity, or no
 * wound below {@code bleedMinor}. {@code NO_BLEED} (infinity) disables a tier.
 */
public record DamageProfile(
		double traumaMultiplier,
		double bleedMinor,
		double bleedModerate,
		double bleedSevere,
		double bleedCritical,
		boolean breakOnImpact,
		boolean breakOnAccumulation,
		boolean concussionCapable,
		double consciousnessFactor,
		boolean systemic
) {
	public static final double NO_BLEED = Double.POSITIVE_INFINITY;

	public boolean canBleed() {
		return bleedMinor != NO_BLEED || bleedModerate != NO_BLEED
				|| bleedSevere != NO_BLEED || bleedCritical != NO_BLEED;
	}

	/** Wound severity for a localized condition-damage amount, or null for none. */
	public WoundSeverity woundFor(double conditionDamage) {
		if (conditionDamage >= bleedCritical) {
			return WoundSeverity.CRITICAL;
		}
		if (conditionDamage >= bleedSevere) {
			return WoundSeverity.SEVERE;
		}
		if (conditionDamage >= bleedModerate) {
			return WoundSeverity.MODERATE;
		}
		if (conditionDamage >= bleedMinor) {
			return WoundSeverity.MINOR;
		}
		return null;
	}
}
