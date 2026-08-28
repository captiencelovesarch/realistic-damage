package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
