package dev.captience.realisticdamage.fieldkit.brew;

import dev.captience.realisticdamage.config.Balance;

/**
 * Sedation as numbers (2.0, phase 3). No dice: the same dose on the same body
 * always lands the same way.
 *
 * <p>Each application is a dose. A dose waits in the body ({@code pending})
 * and takes hold over {@link Balance#SEDATION_ABSORB_TICKS}, so the eyelids get
 * heavy before the lights go out; what has taken hold ({@code level}) halves
 * every {@link Balance#SEDATION_HALF_LIFE_TICKS}, so a bigger dose is a little
 * longer and not twice as long. The stage is the level
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

	/**
	 * How much the body clears this tick: nothing while a dose is still taking
	 * hold; a share of what is in it after that (a half-life), the last scrap
	 * all at once; and barely anything while it has stopped the breathing.
	 */
	public static double cleared(double pending, double level, double vitalityScale) {
		if (pending > 0.0 || level <= 0.0) {
			return 0.0;
		}
		double scale = Math.max(1.0, vitalityScale);
		double s = level / scale;
		if (s >= Balance.SEDATION_OVERDOSE) {
			return Math.min(level, scale / Balance.SEDATION_OVERDOSE_CLEAR_TICKS);
		}
		if (s < Balance.SEDATION_CLEAR_FLOOR) {
			return level;
		}
		return level * (1.0 - Math.pow(0.5, 1.0 / Balance.SEDATION_HALF_LIFE_TICKS));
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
	public static int ticksToClear(double pending, double level, double vitalityScale) {
		double scale = Math.max(1.0, vitalityScale);
		double s = (Math.max(0.0, pending) + Math.max(0.0, level)) / scale;
		double ticks = Math.max(0.0, pending) * Balance.SEDATION_ABSORB_TICKS;
		if (s > Balance.SEDATION_OVERDOSE) {
			ticks += (s - Balance.SEDATION_OVERDOSE) * Balance.SEDATION_OVERDOSE_CLEAR_TICKS;
			s = Balance.SEDATION_OVERDOSE;
		}
		if (s >= Balance.SEDATION_CLEAR_FLOOR) {
			ticks += Balance.SEDATION_HALF_LIFE_TICKS * Math.log(s / Balance.SEDATION_CLEAR_FLOOR) / Math.log(2.0) + 1.0;
		}
		return (int) Math.ceil(ticks);
	}
}
