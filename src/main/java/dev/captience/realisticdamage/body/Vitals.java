package dev.captience.realisticdamage.body;

import dev.captience.realisticdamage.config.Balance;

/**
 * Band math for the two hidden meters. Pure and Minecraft-free so it unit-tests
 * without a runtime, and shared by the server (which decides when to warn you)
 * and the client (which names the band on the inventory panel) so the two can
 * never end up telling you different things.
 *
 * <p>Bands, never figures: naming a band is the most the presentation layer is
 * allowed to say about Blood and Consciousness (spec §42).
 */
public final class Vitals {
	private Vitals() {}

	/** 0 stable, 1 mild, 2 significant, 3 critical, 4 catastrophic. */
	public static int bloodBand(double blood) {
		if (blood < Balance.BLOOD_CRITICAL) {
			return 4;
		}
		if (blood < Balance.BLOOD_SERIOUS) {
			return 3;
		}
		if (blood < Balance.BLOOD_MILD) {
			return 2;
		}
		return blood < Balance.BLOOD_STABLE ? 1 : 0;
	}

	/**
	 * 0 clear, 1 woozy, 2 fading, 3 barely there, 4 out cold. Cut at the points
	 * on the symptom ladder where the player can actually feel a change, so the
	 * word matches what the screen is doing.
	 */
	public static int consciousnessBand(double consciousness, boolean unconscious) {
		if (unconscious || consciousness <= 0.0) {
			return 4;
		}
		if (consciousness < Balance.SYMPTOM_SPRINT_LOSS) {
			return 3;
		}
		if (consciousness < Balance.SYMPTOM_EXPOSURE) {
			return 2;
		}
		return consciousness < Balance.SYMPTOM_VIGNETTE ? 1 : 0;
	}

	/**
	 * Consciousness as the body actually experiences it: a concussion moves the
	 * whole symptom ladder down (spec §21), so a concussed player at 40 is as
	 * impaired as a clear-headed one at 22.
	 *
	 * <p>Lives here rather than on either side alone because 1.4 gave the shift
	 * teeth. It used to change only what the screen did, and the client owned it;
	 * now it decides when you black out and when your legs stop obeying, so the
	 * server needs the same answer the client is drawing.
	 *
	 * <p>Clamped at zero, and that clamp is load-bearing. The penalty curves are
	 * piecewise-linear between anchors and happily extrapolate past them, so an
	 * unclamped shift handed them a negative consciousness and got back an action
	 * penalty of 1.42 — which, applied as ADD_MULTIPLIED_TOTAL, does not mean
	 * "very slow" but "negative attack speed". Nothing is more impaired than
	 * completely gone, so the floor belongs here, once, rather than in every
	 * consumer.
	 */
	public static double effectiveConsciousness(double consciousness, boolean concussed) {
		double shifted = concussed ? consciousness - Balance.CONCUSSION_SYMPTOM_SHIFT : consciousness;
		return Math.clamp(shifted, 0.0, Balance.CONSCIOUSNESS_MAX);
	}

	/**
	 * Ticks between microsleeps at this consciousness, and how long each one
	 * lasts. Both interpolate over the range below {@link
	 * Balance#SYMPTOM_MICROSLEEP}: rare and blink-short at the top, frequent and
	 * long enough to lose a fight at the bottom.
	 */
	public static int microsleepPeriod(double effectiveConsciousness) {
		double t = microsleepSeverity(effectiveConsciousness);
		return (int) Math.round(Balance.MICROSLEEP_PERIOD_TICKS_MAX
				+ t * (Balance.MICROSLEEP_PERIOD_TICKS_MIN - Balance.MICROSLEEP_PERIOD_TICKS_MAX));
	}

	public static int microsleepDuration(double effectiveConsciousness) {
		double t = microsleepSeverity(effectiveConsciousness);
		return (int) Math.round(Balance.MICROSLEEP_DURATION_TICKS_MIN
				+ t * (Balance.MICROSLEEP_DURATION_TICKS_MAX - Balance.MICROSLEEP_DURATION_TICKS_MIN));
	}

	/**
	 * The three separate causes of distress, 0 (fine) to 1 (about to die).
	 *
	 * <p>Kept apart rather than collapsed, which is the 1.5 correction. Everything
	 * downstream used to take the worst of the three and work from that single
	 * number, so bleeding out and being beaten unconscious drove identical
	 * effects — the screen and the heartbeat could say how bad things were but
	 * never what was wrong. Split, the heart can be fast and faint for one and
	 * slow and heavy for the other, and the colour drain can mean blood loss
	 * while the warping means the head.
	 */
	public static double distressFromBlood(double blood) {
		if (blood >= Balance.BLOOD_SERIOUS) {
			return 0.0;
		}
		return Math.clamp((Balance.BLOOD_SERIOUS - blood) / Balance.BLOOD_SERIOUS, 0.0, 1.0);
	}

	/** From fading consciousness, beginning at the heartbeat symptom threshold. */
	public static double distressFromMind(double effectiveConsciousness) {
		if (effectiveConsciousness >= Balance.SYMPTOM_HEARTBEAT) {
			return 0.0;
		}
		if (effectiveConsciousness <= 0.0) {
			return 1.0;
		}
		return (Balance.SYMPTOM_HEARTBEAT - effectiveConsciousness) / Balance.SYMPTOM_HEARTBEAT;
	}

	/** From the ordinary health bar, as a fraction of maximum. */
	public static double distressFromHealth(double healthFraction) {
		if (healthFraction >= Balance.LOW_HP_FRACTION) {
			return 0.0;
		}
		return Math.clamp(1.0 - healthFraction / Balance.LOW_HP_FRACTION, 0.0, 1.0);
	}

	/** 0 at the microsleep threshold, 1 at zero consciousness. */
	public static double microsleepSeverity(double effectiveConsciousness) {
		if (effectiveConsciousness >= Balance.SYMPTOM_MICROSLEEP) {
			return 0.0;
		}
		if (effectiveConsciousness <= 0.0) {
			return 1.0;
		}
		return (Balance.SYMPTOM_MICROSLEEP - effectiveConsciousness) / Balance.SYMPTOM_MICROSLEEP;
	}

	/**
	 * How blown-out the world looks, 0 (normal) to 1 (a full milky veil), from
	 * {@link Balance#SYMPTOM_EXPOSURE} down.
	 *
	 * <p>Two corrections to the curve this replaces, both bought with a beta
	 * playtest that reported the symptom simply did not work. The old ramp ran
	 * 35 → 0 linearly, which put full strength at zero consciousness — underneath
	 * the blackout that has already covered the screen by then. So the effect's
	 * best frame was the one frame nobody could see, and the band a conscious
	 * player actually walks around in got its weakest sixth. Full strength now
	 * lands at {@link Balance#SYMPTOM_SEVERE}, which is the lowest rung the
	 * player is still looking at the world through.
	 *
	 * <p>And it is square-rooted rather than linear, so the wash announces itself
	 * at the threshold instead of arriving twenty points later. The band is
	 * 35 − 10 = 25 points wide, so one point below the threshold a linear ramp is
	 * at 1/25 = 0.04 — nothing — where the square root is at sqrt(0.04) = 0.20,
	 * a fifth of full strength straight away.
	 *
	 * <p>Perceptibility is not a nicety here. Blood and Consciousness are hidden
	 * on purpose (spec §42) on the argument that the symptoms are the readout; a
	 * symptom emitting one unit of signal does not make the mod subtler, it makes
	 * the hidden meter unknowable, which is the one thing that design must not do.
	 */
	public static double exposureStrength(double effectiveConsciousness) {
		if (effectiveConsciousness >= Balance.SYMPTOM_EXPOSURE) {
			return 0.0;
		}
		double t = Math.clamp((Balance.SYMPTOM_EXPOSURE - effectiveConsciousness)
				/ (Balance.SYMPTOM_EXPOSURE - Balance.SYMPTOM_SEVERE), 0.0, 1.0);
		return Math.sqrt(t);
	}

	/**
	 * How doubled the world looks, 0 (single) to 1 (a full second copy), over the
	 * band the ghost actually owns: {@link Balance#SYMPTOM_AFTERIMAGE} down to
	 * {@link Balance#SYMPTOM_BLUR}, where the real gaussian takes over.
	 *
	 * <p>The rung is 45 and always has been, in the spec and in the renderer's own
	 * comment, but the code underneath was squaring a 45 → 0 ramp: at 44 that is
	 * (1/45)² = 0.0005, and it does not clear even a hundredth until 40. Twenty-seven
	 * points of the ladder were emitting nothing, and then the deep-end clamp at
	 * {@link Balance#SYMPTOM_BLUR} slammed it to 0.95 in one step. That is not a
	 * ladder, it is a cliff with a long flat approach. Ending the band at 18
	 * rather than 0 and taking the square root instead of the square turns it back
	 * into rungs: 45 − 18 = 27 points wide, so one point below the threshold gives
	 * sqrt(1/27) = 0.19 rather than 0.0005.
	 *
	 * <p>A concussion holds a floor of it at any consciousness — seeing double is
	 * what being dazed looks like, and it is a floor rather than a precondition:
	 * a clear-headed player losing consciousness ghosts too.
	 */
	public static double afterimageStrength(double effectiveConsciousness, boolean concussed) {
		double floor = concussed ? Balance.CONCUSSION_GHOST : 0.0;
		if (effectiveConsciousness >= Balance.SYMPTOM_AFTERIMAGE) {
			return floor;
		}
		double t = Math.clamp((Balance.SYMPTOM_AFTERIMAGE - effectiveConsciousness)
				/ (Balance.SYMPTOM_AFTERIMAGE - Balance.SYMPTOM_BLUR), 0.0, 1.0);
		return Math.max(floor, Math.sqrt(t));
	}
}
