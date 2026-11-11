package dev.captience.realisticdamage.fieldkit.brew;

import java.util.Arrays;

import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;

/**
 * What Haemorrhage does to a body's bleeding (2.0, phase 3), as pure numbers.
 *
 * <p>Four things, all thresholds and factors, never chances: a hit has to be
 * less hard to open a bleed, the bleed opens a step worse, the stacked bleed
 * may run past the usual cap, and a hand pressed on it holds back less. The
 * level is the effect's amplifier: 0 for the ordinary brew, 1 for the
 * glowstone one.
 */
public final class HaemorrhageMath {
	private HaemorrhageMath() {}

	/** What a hit's damage is measured against: its usual threshold times this. */
	public static double thresholdFactor(int level) {
		return Math.max(Balance.HAEMORRHAGE_THRESHOLD_FLOOR,
				Balance.HAEMORRHAGE_THRESHOLD_FACTOR - Balance.HAEMORRHAGE_THRESHOLD_PER_LEVEL * Math.max(0, level));
	}

	/** The damage a wound profile should judge, so the usual thresholds read as lowered ones. */
	public static double judgedDamage(double conditionDamage, int level) {
		return conditionDamage / thresholdFactor(level);
	}

	/** A wound opened worse, by the configured number of steps, never past Critical. */
	public static WoundSeverity worse(WoundSeverity severity) {
		if (severity == null) {
			return null;
		}
		WoundSeverity[] all = WoundSeverity.values();
		return all[Math.min(all.length - 1, severity.ordinal() + Balance.HAEMORRHAGE_SEVERITY_STEPS)];
	}

	/** The stacked bleed-rate cap under Haemorrhage, in Blood per second. */
	public static double cap(int level) {
		return Balance.WOUND_STACK_CAP
				* (Balance.HAEMORRHAGE_CAP_FACTOR + Balance.HAEMORRHAGE_CAP_PER_LEVEL * Math.max(0, level));
	}

	/**
	 * Wounds stacked strongest-first with the same diminishing weights as
	 * {@code WoundMath.effectiveBleedRate}, against a given cap. Below the usual
	 * cap the two agree exactly (a unit test holds them to it).
	 */
	public static double stackedRate(double[] ratesPerSecond, double cap) {
		if (ratesPerSecond.length == 0) {
			return 0.0;
		}
		double[] sorted = ratesPerSecond.clone();
		Arrays.sort(sorted);
		double total = 0.0;
		double[] weights = Balance.WOUND_STACK_WEIGHTS;
		for (int i = 0; i < sorted.length; i++) {
			double weight = weights[Math.min(i, weights.length - 1)];
			total += sorted[sorted.length - 1 - i] * weight;
		}
		return Math.min(total, cap);
	}

	/**
	 * A hand on a wound under Haemorrhage: of what the hand would hold back
	 * (1 minus the usual multiplier), only {@code HAEMORRHAGE_PRESSURE_HOLD} is.
	 */
	public static double pressureMultiplier(double usual) {
		return 1.0 - (1.0 - usual) * Balance.HAEMORRHAGE_PRESSURE_HOLD;
	}
}
