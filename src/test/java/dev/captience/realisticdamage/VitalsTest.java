package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.config.Balance;
import org.junit.jupiter.api.Test;

class VitalsTest {
	@Test
	void bloodBandsFollowTheSpecThresholds() {
		assertEquals(0, Vitals.bloodBand(100.0));
		assertEquals(0, Vitals.bloodBand(Balance.BLOOD_STABLE));
		assertEquals(1, Vitals.bloodBand(Balance.BLOOD_STABLE - 0.1));
		assertEquals(1, Vitals.bloodBand(Balance.BLOOD_MILD));
		assertEquals(2, Vitals.bloodBand(Balance.BLOOD_MILD - 0.1));
		assertEquals(2, Vitals.bloodBand(Balance.BLOOD_SERIOUS));
		assertEquals(3, Vitals.bloodBand(Balance.BLOOD_SERIOUS - 0.1));
		assertEquals(3, Vitals.bloodBand(Balance.BLOOD_CRITICAL));
		assertEquals(4, Vitals.bloodBand(Balance.BLOOD_CRITICAL - 0.1));
		assertEquals(4, Vitals.bloodBand(0.0));
	}

	/**
	 * The panel's wording and the action-bar warnings have to come from the same
	 * place, or the mod tells you two different things about the same blood.
	 */
	@Test
	void theDataObjectAgreesWithTheSharedBandMath() {
		RealisticDamageData data = new RealisticDamageData();
		for (double blood = 0.0; blood <= 100.0; blood += 0.5) {
			data.setBlood(blood);
			assertEquals(Vitals.bloodBand(blood), data.bloodBand(), "blood " + blood);
		}
	}

	@Test
	void consciousnessBandsRunClearToOutCold() {
		assertEquals(0, Vitals.consciousnessBand(100.0, false));
		assertEquals(0, Vitals.consciousnessBand(Balance.SYMPTOM_VIGNETTE, false));
		assertEquals(1, Vitals.consciousnessBand(Balance.SYMPTOM_VIGNETTE - 0.1, false));
		assertEquals(1, Vitals.consciousnessBand(Balance.SYMPTOM_EXPOSURE, false));
		assertEquals(2, Vitals.consciousnessBand(Balance.SYMPTOM_EXPOSURE - 0.1, false));
		assertEquals(2, Vitals.consciousnessBand(Balance.SYMPTOM_SPRINT_LOSS, false));
		assertEquals(3, Vitals.consciousnessBand(Balance.SYMPTOM_SPRINT_LOSS - 0.1, false));
		assertEquals(4, Vitals.consciousnessBand(0.0, false));
	}

	/** Being out cold outranks whatever the meter says. */
	@Test
	void unconsciousReadsOutColdAtAnyLevel() {
		assertEquals(4, Vitals.consciousnessBand(100.0, true));
		assertEquals(4, Vitals.consciousnessBand(30.0, true));
	}

	/**
	 * The perceptibility floor, and the assertion that would have caught the beta
	 * bug: within a point of its threshold a symptom must already be carrying a
	 * fifth of its strength. The curves this replaced were at 0.04 and 0.0005
	 * there, which is why the author could not tell either effect was on.
	 */
	@Test
	void bothSymptomsAnnounceThemselvesAtTheirThreshold() {
		assertEquals(0.0, Vitals.exposureStrength(Balance.SYMPTOM_EXPOSURE));
		assertTrue(Vitals.exposureStrength(Balance.SYMPTOM_EXPOSURE - 1.0) >= 0.19,
				"exposure one point in: " + Vitals.exposureStrength(Balance.SYMPTOM_EXPOSURE - 1.0));

		assertEquals(0.0, Vitals.afterimageStrength(Balance.SYMPTOM_AFTERIMAGE, false));
		assertTrue(Vitals.afterimageStrength(Balance.SYMPTOM_AFTERIMAGE - 1.0, false) >= 0.18,
				"afterimage one point in: "
						+ Vitals.afterimageStrength(Balance.SYMPTOM_AFTERIMAGE - 1.0, false));
	}

	/**
	 * Full strength has to land where the player can still see out. Both curves
	 * used to reach 1.0 only at zero consciousness — underneath the blackout —
	 * which is the whole reason their useful range was never experienced.
	 */
	@Test
	void fullStrengthLandsAboveTheBlackout() {
		assertEquals(1.0, Vitals.exposureStrength(Balance.SYMPTOM_SEVERE));
		assertEquals(1.0, Vitals.exposureStrength(0.0));
		assertEquals(1.0, Vitals.afterimageStrength(Balance.SYMPTOM_BLUR, false));
		assertEquals(1.0, Vitals.afterimageStrength(0.0, false));
	}

	/**
	 * A concussion is a floor at any consciousness and never a precondition — the
	 * dazed double-vision {@link Balance#CONCUSSION_GHOST} was always meant to buy,
	 * and a clear head losing consciousness still ghosts on its own.
	 */
	@Test
	void concussionFloorsTheGhostWithoutGatingIt() {
		assertEquals(Balance.CONCUSSION_GHOST, Vitals.afterimageStrength(100.0, true));
		assertEquals(Balance.CONCUSSION_GHOST, Vitals.afterimageStrength(60.0, true));
		assertEquals(0.0, Vitals.afterimageStrength(100.0, false));
		// Below the threshold the real curve outgrows the floor rather than being
		// capped by it.
		assertTrue(Vitals.afterimageStrength(20.0, true) > Balance.CONCUSSION_GHOST);
	}

	/**
	 * A ladder, not a cliff. This is the property the old code violated most
	 * plainly: 27 points emitting under a hundredth, then a single step from 0.23
	 * to 0.95 at the blur threshold. Nothing here may move more than a quarter of
	 * full strength in one point of consciousness, and nothing may go backwards
	 * as the player gets worse.
	 */
	@Test
	void neitherCurveHasACliffInIt() {
		double previousExposure = 0.0;
		double previousGhost = 0.0;
		for (double c = 100.0; c >= 0.0; c -= 1.0) {
			double exposure = Vitals.exposureStrength(c);
			double ghost = Vitals.afterimageStrength(c, false);
			assertTrue(exposure >= previousExposure - 1e-9, "exposure went backwards at " + c);
			assertTrue(ghost >= previousGhost - 1e-9, "afterimage went backwards at " + c);
			// A square root is steepest at its own threshold, so the worst step in
			// either curve is its first: sqrt(1/25) = 0.20 and sqrt(1/27) = 0.19.
			assertTrue(exposure - previousExposure <= 0.25 + 1e-9, "exposure jumped at " + c);
			assertTrue(ghost - previousGhost <= 0.25 + 1e-9, "afterimage jumped at " + c);
			previousExposure = exposure;
			previousGhost = ghost;
		}
		assertEquals(1.0, previousExposure);
		assertEquals(1.0, previousGhost);
	}
}
