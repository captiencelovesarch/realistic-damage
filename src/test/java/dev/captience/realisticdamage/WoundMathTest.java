package dev.captience.realisticdamage;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.WoundMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WoundMathTest {
	@Test
	void specExampleFortyPlusTwentyIsFifty() {
		// Spec §16: wounds of strength 40 + 20 must contribute ~50, not 60.
		// (Scaled into real Blood/sec units: 0.40 + 0.20 -> 0.50.)
		assertEquals(0.50, WoundMath.effectiveBleedRate(new double[]{0.40, 0.20}), 1e-9);
		// Order must not matter.
		assertEquals(0.50, WoundMath.effectiveBleedRate(new double[]{0.20, 0.40}), 1e-9);
	}

	@Test
	void weightsAreStrongestFirst() {
		// 1.0 + 0.5x + 0.25x + 0.125x + 0.125x ...
		double rate = WoundMath.effectiveBleedRate(new double[]{1.0, 1.0, 1.0, 1.0, 1.0});
		assertEquals(1.0 + 0.5 + 0.25 + 0.125 + 0.125, rate, 1e-9);
	}

	@Test
	void totalIsCapped() {
		double rate = WoundMath.effectiveBleedRate(new double[]{2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0, 2.0});
		assertEquals(Balance.WOUND_STACK_CAP, rate, 1e-9);
	}

	@Test
	void emptyAndSingle() {
		assertEquals(0.0, WoundMath.effectiveBleedRate(new double[]{}));
		assertEquals(0.6, WoundMath.effectiveBleedRate(new double[]{0.6}), 1e-9);
	}

	@Test
	void addingAWoundNeverReducesBleeding() {
		double base = WoundMath.effectiveBleedRate(new double[]{1.2, 0.6});
		double more = WoundMath.effectiveBleedRate(new double[]{1.2, 0.6, 0.25});
		assertTrue(more >= base);
	}
}
