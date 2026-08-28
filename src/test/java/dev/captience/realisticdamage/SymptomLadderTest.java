package dev.captience.realisticdamage;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the consciousness/blood response curves (spec §17, §23). */
class SymptomLadderTest {
	@Test
	void sluggishnessOnlyStartsBelowTheAttackRecoveryThreshold() {
		assertEquals(0.0, ImpairmentSystem.sluggishPenalty(100.0));
		assertEquals(0.0, ImpairmentSystem.sluggishPenalty(Balance.SYMPTOM_ATTACK_RECOVERY));
		assertTrue(ImpairmentSystem.sluggishPenalty(Balance.SYMPTOM_ATTACK_RECOVERY - 1.0) > 0.0);
	}

	@Test
	void sluggishnessIsMonotonicAndBounded() {
		double previous = -1.0;
		for (double consciousness = 100.0; consciousness >= 0.0; consciousness -= 1.0) {
			double penalty = ImpairmentSystem.sluggishPenalty(consciousness);
			assertTrue(penalty >= previous, "penalty must never improve as consciousness falls");
			assertTrue(penalty <= Balance.SLUGGISH_PENALTY_AT_0 + 1e-9, "penalty must stay bounded");
			previous = penalty;
		}
		assertEquals(Balance.SLUGGISH_PENALTY_AT_0, ImpairmentSystem.sluggishPenalty(0.0), 1e-9);
	}

	@Test
	void bloodBandsMatchSpec() {
		// Above 30: blood loss costs no hearts yet — it buffers first (spec §17).
		assertEquals(0.0, BleedingSystem.bleedOutHpPerSecond(100.0));
		assertEquals(0.0, BleedingSystem.bleedOutHpPerSecond(Balance.BLOOD_SERIOUS));
		assertEquals(Balance.BLEEDOUT_HP_PER_SEC_SERIOUS, BleedingSystem.bleedOutHpPerSecond(20.0));
		assertEquals(Balance.BLEEDOUT_HP_PER_SEC_CRITICAL, BleedingSystem.bleedOutHpPerSecond(10.0));
		assertEquals(Balance.BLEEDOUT_HP_PER_SEC_ZERO, BleedingSystem.bleedOutHpPerSecond(0.0));
	}

	@Test
	void bleedOutRateNeverDecreasesAsBloodFalls() {
		double previous = -1.0;
		for (double blood = 100.0; blood >= 0.0; blood -= 0.5) {
			double rate = BleedingSystem.bleedOutHpPerSecond(blood);
			assertTrue(rate >= previous, "bleed-out rate must worsen monotonically at blood " + blood);
			previous = rate;
		}
	}

	@Test
	void catastrophicBleedOutIsFastButNotInstant() {
		// Spec §17: blood 0 is "very rapid HP loss" but must never instakill.
		double secondsToDie = 20.0 / Balance.BLEEDOUT_HP_PER_SEC_ZERO;
		assertTrue(secondsToDie > 1.5, "bleeding out must not be an instant kill, got " + secondsToDie + "s");
		assertTrue(secondsToDie < 8.0, "bleeding out at empty must actually feel fatal, got " + secondsToDie + "s");
	}

	@Test
	void youBlackOutBeforeYouBleedToDeath() {
		// The dramatic ordering the design wants: consciousness crashes first, so
		// you go under and then die, rather than dying wide awake.
		double secondsToCollapse = Balance.CONSCIOUSNESS_MAX / Balance.CONSCIOUSNESS_DRAIN_BLOOD_ZERO;
		double secondsToDie = 20.0 / Balance.BLEEDOUT_HP_PER_SEC_ZERO;
		assertTrue(secondsToCollapse < secondsToDie,
				"collapse (" + secondsToCollapse + "s) must precede death (" + secondsToDie + "s)");
	}
}
