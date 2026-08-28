package dev.captience.realisticdamage.body;

import dev.captience.realisticdamage.config.Balance;

/**
 * Functional state of a body region, derived from condition as a fraction of max.
 * Boundaries are exclusive-above per spec §3: exactly 75% is HURT, exactly 0 is DISABLED.
 */
public enum BodyState {
	HEALTHY,
	HURT,
	INJURED,
	CRITICAL,
	DISABLED;

	public static BodyState fromFraction(double fraction) {
		if (fraction <= 0.0) {
			return DISABLED;
		}
		if (fraction > Balance.HEALTHY_ABOVE) {
			return HEALTHY;
		}
		if (fraction > Balance.HURT_ABOVE) {
			return HURT;
		}
		if (fraction > Balance.INJURED_ABOVE) {
			return INJURED;
		}
		return CRITICAL;
	}

	public boolean isAtLeastAsBadAs(BodyState other) {
		return ordinal() >= other.ordinal();
	}
}
