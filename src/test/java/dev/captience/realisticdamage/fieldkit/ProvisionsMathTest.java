package dev.captience.realisticdamage.fieldkit;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath.Stage;
import dev.captience.realisticdamage.fieldkit.medicine.ProvisionsMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Phase 4, round 3: what coffee and spirits do to a body, as numbers (2.0). */
class ProvisionsMathTest {
	/** One arrow drops a person; on coffee it leaves them stumbling, and two still drop them. */
	@Test
	void coffeeTakesADoseAStageLessFar() {
		double plain = ProvisionsMath.sedationScale(1.0, false);
		double coffee = ProvisionsMath.sedationScale(1.0, true);
		assertEquals(Stage.OUT, SedationMath.stage(1.0, plain));
		assertEquals(Stage.STUMBLING, SedationMath.stage(1.0, coffee));
		assertEquals(Stage.OUT, SedationMath.stage(2.0, coffee), "coffee is not immunity");
		assertEquals(Stage.OVERDOSE, SedationMath.stage(2.6, plain));
		assertEquals(Stage.OUT, SedationMath.stage(2.6, coffee));
	}

	/** A big body on coffee is bigger again, never smaller than a person. */
	@Test
	void coffeeScalesWithTheBody() {
		assertEquals(4.0 * Balance.COFFEE_SEDATION_RESIST, ProvisionsMath.sedationScale(4.0, true), 1e-12);
		assertEquals(1.0, ProvisionsMath.sedationScale(0.3, false), 1e-12);
	}

	@Test
	void aRacingHeartAndSpiritsBothBleedMore() {
		assertEquals(1.0, ProvisionsMath.bleedFactor(false, false), 1e-12);
		assertEquals(Balance.COFFEE_BLEED_FACTOR, ProvisionsMath.bleedFactor(true, false), 1e-12);
		assertEquals(Balance.SPIRITS_BLEED_FACTOR, ProvisionsMath.bleedFactor(false, true), 1e-12);
		assertEquals(Balance.COFFEE_BLEED_FACTOR * Balance.SPIRITS_BLEED_FACTOR, ProvisionsMath.bleedFactor(true, true),
				1e-12);
	}

	@Test
	void spiritsTakeTheEdgeOffAndNoMore() {
		assertEquals(1.0, ProvisionsMath.painFactor(false), 1e-12);
		assertEquals(Balance.SPIRITS_PAIN_FACTOR, ProvisionsMath.painFactor(true), 1e-12);
	}
}
