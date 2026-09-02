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
}
