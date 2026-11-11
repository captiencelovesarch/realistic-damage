package dev.captience.realisticdamage.fieldkit.brew;

import dev.captience.realisticdamage.config.Balance;

/**
 * Sedation as numbers (2.0, phase 3). No dice: the same dose on the same body
 * always lands the same way.
 *
 * <p>Each application is a dose. A dose waits in the body ({@code pending})
 * and takes hold over {@link Balance#SEDATION_ABSORB_TICKS}, so the eyelids get
 * heavy before the lights go out; what has taken hold ({@code level}) clears
 * over {@link Balance#SEDATION_CLEAR_TICKS} per dose. The stage is the level
 * over the body's size, so one arrow drops a zombie or a player and a ravager
 * takes five.
 */
public final class SedationMath {
	private SedationMath() {}

	public enum Stage {
		NONE, DROWSY, STUMBLING, OUT, OVERDOSE;

		private static final Stage[] ALL = values();

		public static Stage byOrdinal(int ordinal) {
			return ordinal >= 0 && ordinal < ALL.length ? ALL[ordinal] : NONE;
		}
	}

	/**
	 * How much one application adds: a whole dose per level of the brew for
	 * anything at least as long as a tipped arrow's share of the potion, and a
	 * proportion of one for less (the edge of a splash).
	 */
	public static double dose(int amplifier, int durationTicks) {
		return (Math.max(0, amplifier) + 1) * Math.min(1.0, Math.max(0, durationTicks)
				/ (double) Balance.SEDATION_DOSE_TICKS);
	}

	/** How much of what is waiting takes hold this tick. */
	public static double absorbed(double pending) {
		return Math.min(Math.max(0.0, pending), 1.0 / Balance.SEDATION_ABSORB_TICKS);
	}

	/** How much the body clears this tick: nothing while a dose is still taking hold. */
	public static double cleared(double pending, double level) {
		return pending > 0.0 ? 0.0 : Math.min(Math.max(0.0, level), 1.0 / Balance.SEDATION_CLEAR_TICKS);
	}

	public static Stage stage(double level, double vitalityScale) {
		double s = level / Math.max(1.0, vitalityScale);
		if (s >= Balance.SEDATION_OVERDOSE) {
			return Stage.OVERDOSE;
		}
		if (s >= Balance.SEDATION_OUT) {
			return Stage.OUT;
		}
		if (s >= Balance.SEDATION_STUMBLING) {
			return Stage.STUMBLING;
		}
		if (s >= Balance.SEDATION_DROWSY) {
			return Stage.DROWSY;
		}
		return Stage.NONE;
	}

	/** The highest consciousness a stage allows; {@code Double.MAX_VALUE} for none. */
	public static double cap(Stage stage) {
		return switch (stage) {
			case NONE -> Double.MAX_VALUE;
			case DROWSY -> Balance.SEDATION_DROWSY_CAP;
			case STUMBLING -> Balance.SEDATION_STUMBLING_CAP;
			case OUT, OVERDOSE -> 0.0;
		};
	}

	/** The share of movement a stage takes away. */
	public static double slow(Stage stage) {
		return switch (stage) {
			case DROWSY -> Balance.SEDATION_DROWSY_SLOW;
			case STUMBLING -> Balance.SEDATION_STUMBLING_SLOW;
			default -> 0.0;
		};
	}

	/** Ticks until a body with this much in it is clear: what is waiting, then what has taken hold. */
	public static int ticksToClear(double pending, double level) {
		return (int) Math.ceil(Math.max(0.0, pending) * Balance.SEDATION_ABSORB_TICKS
				+ Math.max(0.0, pending + level) * Balance.SEDATION_CLEAR_TICKS);
	}
}
