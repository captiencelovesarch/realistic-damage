package dev.captience.realisticdamage.systems;

import java.util.Arrays;

import dev.captience.realisticdamage.config.Balance;

/** Pure math for bleeding wound stacking (spec §16). */
public final class WoundMath {
	private WoundMath() {}

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
