package dev.captience.realisticdamage.fieldkit;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath.Stage;
import dev.captience.realisticdamage.fieldkit.medicine.SaltsRule;
import dev.captience.realisticdamage.fieldkit.medicine.SaltsRule.Why;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Who smelling salts bring round (2.0, phase 4 round 2). */
class SaltsRuleTest {
	@Test
	void aKnockoutComesRound() {
		assertNull(SaltsRule.why(true, 100.0, false, Stage.NONE));
		assertNull(SaltsRule.why(true, Balance.BLOOD_MILD, false, Stage.NONE), "enough blood that it stops draining");
	}

	@Test
	void theAwakeNeedNone() {
		assertEquals(Why.AWAKE, SaltsRule.why(false, 100.0, false, Stage.NONE));
	}

	@Test
	void bloodLossAirAndTheDrugHoldThemDown() {
		assertEquals(Why.BLOOD, SaltsRule.why(true, Balance.BLOOD_MILD - 0.5, false, Stage.NONE));
		assertEquals(Why.AIR, SaltsRule.why(true, 100.0, true, Stage.NONE));
		assertEquals(Why.SEDATED, SaltsRule.why(true, 100.0, false, Stage.OUT));
		assertEquals(Why.SEDATED, SaltsRule.why(true, 100.0, false, Stage.OVERDOSE));
	}

	/** A drowsy or stumbling body knocked out by a blow: the drug isn't what holds it. */
	@Test
	void aLighterDoseDoesNotStopThem() {
		assertNull(SaltsRule.why(true, 100.0, false, Stage.DROWSY));
		assertNull(SaltsRule.why(true, 100.0, false, Stage.STUMBLING));
	}
}
