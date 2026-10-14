package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.config.Balance;

/**
 * Drowning and suffocation as what they are: the brain running out of oxygen
 * (1.7). Debt is counted in seconds without air; the consciousness drain grows
 * with it, so the first second is a warning and the seventh is the floor
 * coming up. Pure, so the "how long until you black out" table is a unit test.
 */
public final class HypoxiaMath {
	private HypoxiaMath() {}

	/** Consciousness drain, points per second, after this many seconds without air. */
	public static double drainPerSecond(double debtSeconds) {
		if (!(debtSeconds > 0.0)) {
			return 0.0;
		}
		return Math.min(Balance.HYPOXIA_DRAIN_MAX,
				Balance.HYPOXIA_DRAIN_BASE + Balance.HYPOXIA_DRAIN_PER_SECOND_OF_DEBT * debtSeconds);
	}

	/**
	 * How much of the ordinary recovery a brain still paying back oxygen debt
	 * gets: none of it while the debt is large, all of it once it is gone. You
	 * surface, gasp, and the world takes a while to come back.
	 */
	public static double recoveryFactor(double debtSeconds) {
		if (!(debtSeconds > 0.0)) {
			return 1.0;
		}
		return 1.0 / (1.0 + debtSeconds);
	}

	/** Seconds of no air from {@code consciousness} until collapse, simulated at 20 Hz. */
	public static double secondsToCollapse(double consciousness) {
		double debt = 0.0;
		int ticks = 0;
		while (consciousness > 0.0 && ticks < 20 * 600) {
			debt = Math.min(Balance.HYPOXIA_DEBT_MAX, debt + 1.0 / 20.0);
			consciousness -= drainPerSecond(debt) / 20.0;
			ticks++;
		}
		return ticks / 20.0;
	}
}
