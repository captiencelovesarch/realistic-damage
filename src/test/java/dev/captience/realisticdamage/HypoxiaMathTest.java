package dev.captience.realisticdamage;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.HypoxiaMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drowning and suffocation take the mind first (1.7). */
class HypoxiaMathTest {
	/**
	 * Vanilla drowning takes 2 HP a second once the air is gone, so a full
	 * twenty-HP player dies about ten seconds in. Blacking out must come first,
	 * with a few seconds to spare for a rescue — and not so early that the first
	 * empty bubble is a death sentence.
	 */
	@Test
	void youBlackOutBeforeDrowningKillsYou() {
		double seconds = HypoxiaMath.secondsToCollapse(Balance.CONSCIOUSNESS_MAX);
		assertTrue(seconds > 5.0 && seconds < 9.0, "collapse came at " + seconds + " s");
	}

	@Test
	void theDrainClimbsWithTheDebtAndIsCapped() {
		assertEquals(0.0, HypoxiaMath.drainPerSecond(0.0));
		assertTrue(HypoxiaMath.drainPerSecond(4.0) > HypoxiaMath.drainPerSecond(1.0));
		assertEquals(Balance.HYPOXIA_DRAIN_MAX, HypoxiaMath.drainPerSecond(Balance.HYPOXIA_DEBT_MAX));
	}

	@Test
	void debtHoldsRecoveryBackUntilItIsRepaid() {
		assertEquals(1.0, HypoxiaMath.recoveryFactor(0.0));
		assertTrue(HypoxiaMath.recoveryFactor(6.0) < 0.2);
		assertTrue(HypoxiaMath.recoveryFactor(1.0) > HypoxiaMath.recoveryFactor(3.0));
	}
}
