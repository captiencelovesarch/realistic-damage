package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.config.Balance;

/**
 * The concussion as a graded, living injury (1.7 rework). No Minecraft types,
 * so every curve here is pinned by unit tests.
 *
 * <p>Before 1.7 a concussion was a boolean: everything slightly worse until a
 * timer ran out. That made it a number to wait on rather than a thing to play
 * around. Now it has three parts:
 *
 * <ul>
 * <li><b>Severity</b>, 0-100, read as a grade (mild, moderate, severe). Set by
 * the hit, deepened by any further head hit (second impact), and healed by
 * time — fast at rest, slowly on the move, not at all while exerting.</li>
 * <li><b>Flare</b>, 0-100: how hard the injured brain is being pushed right
 * now. Sprinting, jumping, fighting, taking hits and bright light raise it;
 * stillness and darkness let it fall. A high flare brings the whole symptom
 * ladder forward, takes away sprinting, and at the top you vomit.</li>
 * <li><b>Vertigo</b>: spells of the room spinning, on a deterministic phase
 * accumulator whose rate rises with grade and flare.</li>
 * </ul>
 *
 * <p>No dice anywhere: two identical knocks followed by identical play produce
 * identical spells, flares and retches.
 */
public final class ConcussionMath {
	private ConcussionMath() {}

	public static final int NONE = 0;
	public static final int MILD = 1;
	public static final int MODERATE = 2;
	public static final int SEVERE = 3;

	/** The band a severity reads as. Zero only when there is no concussion at all. */
	public static int grade(double severity) {
		if (!(severity > 0.0)) {
			return NONE;
		}
		if (severity >= Balance.CONCUSSION_GRADE_SEVERE) {
			return SEVERE;
		}
		return severity >= Balance.CONCUSSION_GRADE_MODERATE ? MODERATE : MILD;
	}

	/** Severity of a first concussion from this much head trauma. */
	public static double freshSeverity(double headTrauma) {
		return clampSeverity(Balance.CONCUSSION_FRESH_BASE + Balance.CONCUSSION_FRESH_PER_TRAUMA * headTrauma);
	}

	/** Severity added by a head hit on an already-concussed brain. */
	public static double secondImpactSeverity(double headTrauma) {
		return Balance.SECOND_IMPACT_BASE + Balance.SECOND_IMPACT_PER_TRAUMA * headTrauma;
	}

	/** Does this head hit, on a concussion of this grade, put you straight out? */
	public static boolean secondImpactKnocksOut(int grade, double headTrauma) {
		double bar = switch (grade) {
			case SEVERE -> Balance.SECOND_IMPACT_KO_SEVERE;
			case MODERATE -> Balance.SECOND_IMPACT_KO_MODERATE;
			case MILD -> Balance.SECOND_IMPACT_KO_MILD;
			default -> Double.POSITIVE_INFINITY;
		};
		return headTrauma >= bar;
	}

	public static double clampSeverity(double severity) {
		return Math.clamp(severity, 0.0, Balance.CONCUSSION_SEVERITY_MAX);
	}

	/** How far the symptom ladder is brought forward: the grade's shift plus the flare's. */
	public static double symptomShift(int grade, double flare) {
		double base = switch (grade) {
			case SEVERE -> Balance.CONCUSSION_SHIFT_SEVERE;
			case MODERATE -> Balance.CONCUSSION_SHIFT_MODERATE;
			case MILD -> Balance.CONCUSSION_SHIFT_MILD;
			default -> 0.0;
		};
		if (grade == NONE) {
			return 0.0;
		}
		return base + Balance.CONCUSSION_FLARE_SHIFT * Math.clamp(flare / Balance.FLARE_MAX, 0.0, 1.0);
	}

	/** How much lower the consciousness ceiling sits, by grade. */
	public static double capDrop(int grade) {
		return switch (grade) {
			case SEVERE -> Balance.CONCUSSION_CAP_DROP_SEVERE;
			case MODERATE -> Balance.CONCUSSION_CAP_DROP;
			case MILD -> Balance.CONCUSSION_CAP_DROP_MILD;
			default -> 0.0;
		};
	}

	/** What the player is doing, as far as a bruised brain is concerned. */
	public enum Activity {
		/** Standing still, riding, lying down, out cold. */
		RESTING,
		/** Walking, swimming gently, mining at a walk. */
		LIGHT,
		/** Sprinting, or anything that raised the flare this tick. */
		EXERTING
	}

	/**
	 * Severity healed per second.
	 *
	 * @param badHead the head itself is still INJURED or worse
	 * @param dark    standing somewhere dim
	 */
	public static double healPerSecond(Activity activity, boolean badHead, boolean dark) {
		double rate = switch (activity) {
			case RESTING -> Balance.CONCUSSION_HEAL_RESTING;
			case LIGHT -> Balance.CONCUSSION_HEAL_LIGHT;
			case EXERTING -> 0.0;
		};
		if (badHead) {
			rate *= Balance.CONCUSSION_HEAL_BAD_HEAD;
		}
		if (dark) {
			rate *= Balance.CONCUSSION_HEAL_DARK_BONUS;
		}
		return rate;
	}

	/** How strongly exertion lands on this grade. */
	public static double flareGain(int grade) {
		return switch (grade) {
			case SEVERE -> Balance.FLARE_GAIN_SEVERE;
			case MODERATE -> 1.0;
			case MILD -> Balance.FLARE_GAIN_MILD;
			default -> 0.0;
		};
	}

	/** Flare per second from light this bright (0-15). Zero below the line. */
	public static double lightFlarePerSecond(int brightness) {
		int over = brightness - Balance.CONCUSSION_BRIGHT_LIGHT;
		return over < 0 ? 0.0 : (over + 1) * Balance.FLARE_LIGHT_PER_SEC_PER_LEVEL;
	}

	/** Flare shed per second when not exerting. */
	public static double flareDecayPerSecond(Activity activity, boolean dark) {
		double decay = switch (activity) {
			case RESTING -> Balance.FLARE_DECAY_RESTING;
			case LIGHT -> Balance.FLARE_DECAY_LIGHT;
			case EXERTING -> 0.0;
		};
		return dark && activity != Activity.EXERTING ? decay + Balance.FLARE_DECAY_DARK_EXTRA : decay;
	}

	/** Ticks between vertigo spells. Infinite with no concussion. */
	public static double vertigoPeriod(int grade, double flare) {
		double period = switch (grade) {
			case SEVERE -> Balance.VERTIGO_PERIOD_SEVERE;
			case MODERATE -> Balance.VERTIGO_PERIOD_MODERATE;
			case MILD -> Balance.VERTIGO_PERIOD_MILD;
			default -> Double.POSITIVE_INFINITY;
		};
		double f = Math.clamp(flare / Balance.FLARE_MAX, 0.0, 1.0);
		return period / (1.0 + (Balance.VERTIGO_FLARE_RATE - 1.0) * f);
	}

	/** How long one spell lasts, in ticks. */
	public static int vertigoDuration(int grade, double flare) {
		int base = switch (grade) {
			case SEVERE -> Balance.VERTIGO_TICKS_SEVERE;
			case MODERATE -> Balance.VERTIGO_TICKS_MODERATE;
			case MILD -> Balance.VERTIGO_TICKS_MILD;
			default -> 0;
		};
		return base == 0 ? 0 : base + (int) Math.round(Math.max(0.0, flare) * Balance.VERTIGO_TICKS_PER_FLARE);
	}
}
