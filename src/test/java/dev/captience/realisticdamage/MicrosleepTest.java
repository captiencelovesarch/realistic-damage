package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ImpairmentMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.4 answer to "you can still do so much when you're about to lose
 * consciousness": blackouts that arrive before the collapse does, hands that
 * stop working, and a meal that stops being free.
 */
class MicrosleepTest {
	@Test
	void nothingHappensUntilYouAreNearlyGone() {
		assertEquals(0.0, Vitals.microsleepSeverity(100.0));
		assertEquals(0.0, Vitals.microsleepSeverity(Balance.SYMPTOM_MICROSLEEP));
		assertTrue(Vitals.microsleepSeverity(Balance.SYMPTOM_MICROSLEEP - 1.0) > 0.0);
		assertEquals(1.0, Vitals.microsleepSeverity(0.0));
	}

	@Test
	void blackoutsGetCloserTogetherAndLongerAsYouFade() {
		int previousPeriod = Integer.MAX_VALUE;
		int previousDuration = -1;
		for (double c = Balance.SYMPTOM_MICROSLEEP; c >= 0.0; c -= 0.5) {
			int period = Vitals.microsleepPeriod(c);
			int duration = Vitals.microsleepDuration(c);
			assertTrue(period <= previousPeriod, "blackouts must never get rarer as you fade, at " + c);
			assertTrue(duration >= previousDuration, "...nor shorter, at " + c);
			previousPeriod = period;
			previousDuration = duration;
		}
		assertEquals(Balance.MICROSLEEP_PERIOD_TICKS_MIN, Vitals.microsleepPeriod(0.0));
		assertEquals(Balance.MICROSLEEP_DURATION_TICKS_MAX, Vitals.microsleepDuration(0.0));
	}

	/**
	 * The softlock guard. Food is the only cure, so however bad it gets there has
	 * to be a waking window long enough to start a meal in — otherwise a player
	 * who fades out alone can never recover, and "eating always works" becomes a
	 * promise the mod does not keep.
	 */
	@Test
	void thereIsAlwaysAWakingWindowToEatIn() {
		for (double c = Balance.SYMPTOM_MICROSLEEP; c >= 0.0; c -= 0.5) {
			int awake = Vitals.microsleepPeriod(c) - Vitals.microsleepDuration(c);
			assertTrue(awake >= 10, "need at least half a second awake to act, got " + awake + " ticks at " + c);
		}
	}

	@Test
	void youAreNeverBlackedOutMoreThanYouArePresent() {
		int period = Vitals.microsleepPeriod(0.0);
		int duration = Vitals.microsleepDuration(0.0);
		assertTrue(duration < period / 2.0,
				"even at zero, blackouts must be episodes rather than the default state");
	}

	/**
	 * The concussion shift subtracts from consciousness, and the penalty curves
	 * are piecewise-linear between anchors — so an unclamped shift handed them a
	 * negative and they cheerfully extrapolated, returning an action penalty of
	 * 1.42. Applied as ADD_MULTIPLIED_TOTAL that is not "very slow", it is
	 * negative attack speed. Nothing may be more impaired than completely gone.
	 */
	@Test
	void theConcussionShiftCannotDriveConsciousnessNegative() {
		for (double raw = 0.0; raw <= Balance.CONSCIOUSNESS_MAX; raw += 0.5) {
			for (boolean concussed : new boolean[] {false, true}) {
				double effective = Vitals.effectiveConsciousness(raw, concussed);
				assertTrue(effective >= 0.0, "effective consciousness went negative at raw " + raw);
				assertTrue(effective <= Balance.CONSCIOUSNESS_MAX, "...or above the maximum at raw " + raw);
			}
		}
	}

	/** The property that actually matters: no penalty may ever invert a multiplier. */
	@Test
	void penaltiesStayWithinTheirAnchorsHoweverConcussed() {
		for (double raw = 0.0; raw <= Balance.CONSCIOUSNESS_MAX; raw += 0.5) {
			double effective = Vitals.effectiveConsciousness(raw, true);
			double sluggish = ImpairmentMath.eatingStallPenalty(effective);
			double action = dev.captience.realisticdamage.systems.ImpairmentSystem.sluggishPenalty(effective);
			assertTrue(action <= Balance.SLUGGISH_PENALTY_AT_0 + 1e-9,
					"action penalty overshot its anchor at raw " + raw + ": " + action);
			assertTrue(action < 1.0, "an action penalty of 1 or more inverts the attribute, at raw " + raw);
			assertTrue(sluggish <= Balance.EAT_STALL_AT_0 + 1e-9,
					"eating penalty overshot its anchor at raw " + raw + ": " + sluggish);
		}
	}

	/** A concussion is supposed to bring the whole ladder forward, not just the visuals. */
	@Test
	void concussionMakesYouBlackOutSooner() {
		double raw = Balance.SYMPTOM_MICROSLEEP + 5.0;
		assertEquals(0.0, Vitals.microsleepSeverity(Vitals.effectiveConsciousness(raw, false)),
				"clear-headed at this level, nothing should happen");
		assertTrue(Vitals.microsleepSeverity(Vitals.effectiveConsciousness(raw, true)) > 0.0,
				"concussed at the same level, the blackouts should have started");
	}

	@Test
	void handsGiveOutBeforeConsciousnessDoes() {
		assertTrue(Balance.SYMPTOM_FEEBLE > 0.0,
				"there must be a band where you are awake and cannot fight");
		assertTrue(Balance.SYMPTOM_FEEBLE < Balance.SYMPTOM_MICROSLEEP,
				"and it must sit below the blackouts, so the blackouts are the warning");
	}

	/**
	 * The complaint that started 1.4: at the bottom of the ladder you could still
	 * mine at roughly two-thirds speed.
	 */
	@Test
	void theBottomOfTheLadderIsNoPlaceToWork() {
		double atZero = ImpairmentMath.eatingStallPenalty(0.0);
		assertTrue(Balance.SLUGGISH_PENALTY_AT_0 >= 0.8,
				"actions must be nearly impossible at zero, not merely slow");
		assertTrue(atZero >= 3.0, "eating must take at least four times as long at zero, got " + atZero);
	}

	@Test
	void eatingSlowsSmoothlyAndOnlyWhenFading() {
		assertEquals(0.0, ImpairmentMath.eatingStallPenalty(100.0));
		assertEquals(0.0, ImpairmentMath.eatingStallPenalty(Balance.SYMPTOM_ATTACK_RECOVERY));
		double previous = -1.0;
		for (double c = Balance.SYMPTOM_ATTACK_RECOVERY; c >= 0.0; c -= 0.5) {
			double penalty = ImpairmentMath.eatingStallPenalty(c);
			assertTrue(penalty >= previous, "eating must never speed up as you fade, at " + c);
			previous = penalty;
		}
		assertEquals(Balance.EAT_STALL_AT_0, ImpairmentMath.eatingStallPenalty(0.0), 1e-9);
	}

	/**
	 * Slower, never refused. A stall penalty is extra ticks on the meal; nothing
	 * in the curve may become large enough to be indistinguishable from "you
	 * cannot eat", because food is the only cure in this mod.
	 */
	@Test
	void eatingStaysPossibleHoweverBadItGets() {
		int normalEatTicks = 32;
		double worstCase = normalEatTicks * (1.0 + ImpairmentMath.eatingStallPenalty(0.0));
		assertTrue(worstCase / 20.0 < 12.0,
				"a meal at zero consciousness must still be finishable, got " + worstCase / 20.0 + "s");
	}
}
