package dev.captience.realisticdamage;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ImpairmentMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpairmentMathTest {
	@Test
	void healthyArmHasNoPenalty() {
		assertEquals(0.0, ImpairmentMath.armPenalty(1.0, false));
		assertEquals(0.0, ImpairmentMath.armPenalty(0.76, false));
	}

	@Test
	void armPenaltyIsMonotonicallyWorsening() {
		double last = -1.0;
		for (double f = 1.0; f >= 0.0; f -= 0.01) {
			double penalty = ImpairmentMath.armPenalty(f, false);
			assertTrue(penalty >= last, "penalty decreased at fraction " + f);
			last = penalty;
		}
	}

	@Test
	void disabledAndBrokenAreCapped() {
		double disabledBroken = ImpairmentMath.armPenalty(0.0, true);
		assertEquals(Balance.ARM_PENALTY_TOTAL_CAP, disabledBroken, 1e-9);
		assertTrue(ImpairmentMath.legPenalty(0.0, true) <= Balance.LEG_PENALTY_TOTAL_CAP);
	}

	@Test
	void brokenAlwaysWorseThanNotBroken() {
		for (double f = 1.0; f >= 0.0; f -= 0.05) {
			assertTrue(ImpairmentMath.armPenalty(f, true) > ImpairmentMath.armPenalty(f, false));
			assertTrue(ImpairmentMath.legPenalty(f, true) > ImpairmentMath.legPenalty(f, false));
		}
	}

	@Test
	void worseLegDominatesCombinedPenalty() {
		double combined = ImpairmentMath.combinedLegPenalty(0.4, 0.0);
		assertEquals(0.4 * Balance.LEG_WORSE_WEIGHT, combined, 1e-9);
		// Symmetric.
		assertEquals(combined, ImpairmentMath.combinedLegPenalty(0.0, 0.4), 1e-9);
	}

	@Test
	void bothLegsWreckedStillLeavesLimpSpeed() {
		double worst = ImpairmentMath.combinedLegPenalty(
				ImpairmentMath.legPenalty(0.0, true),
				ImpairmentMath.legPenalty(0.0, true));
		assertTrue(worst <= Balance.LEG_PENALTY_TOTAL_CAP, "must never be a zero-speed softlock");
		assertTrue(1.0 - worst >= 0.25, "player must keep >= ~25% walk speed");
	}

	@Test
	void fallAmplifierRangesFromOneToMax() {
		assertEquals(1.0, ImpairmentMath.fallTraumaAmplifier(1.0), 1e-9);
		assertEquals(Balance.FALL_TRAUMA_INJURED_LEG_MAX_AMP, ImpairmentMath.fallTraumaAmplifier(0.0), 1e-9);
		assertTrue(ImpairmentMath.fallTraumaAmplifier(0.5) > 1.0);
	}
}
