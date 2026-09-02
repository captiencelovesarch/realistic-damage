package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.WoundSeverity;
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

	/** Critical for a blow, severe for a grind — the whole rule, in both directions. */
	@Test
	void aLimbStillStandingWhenTheBlowLandsBleedsWorse() {
		// The killing blow found condition to take: the limb was on the body.
		assertEquals(WoundSeverity.CRITICAL, WoundMath.stumpSeverity(4.0));
		assertEquals(WoundSeverity.CRITICAL, WoundMath.stumpSeverity(0.5));
		// It found nothing: the fight had already emptied the limb, and this hit
		// only detached it. BodyPartData.damage() clamps at zero, so this is the
		// exact figure the finish-off path reports for every ground-down limb.
		assertEquals(WoundSeverity.SEVERE, WoundMath.stumpSeverity(0.0));
	}

	/**
	 * The classification is a yes/no about the blow, never a measurement of it.
	 * Deriving severity from the size of the residual would make the same lost
	 * leg bleed differently depending on the armour worn and the weapon used.
	 */
	@Test
	void howMuchWasLeftToLoseChangesNothing() {
		WoundSeverity first = WoundMath.stumpSeverity(0.01);
		for (double lost : new double[]{0.01, 1.0, 6.0, 16.0, 1000.0}) {
			assertEquals(first, WoundMath.stumpSeverity(lost),
					lost + " must classify the same as any other blow that took something");
		}
	}

	/**
	 * Floating-point dust is not a standing limb. The finish-off path subtracts
	 * doubles, so "nothing left" has to mean the same thing here as it does to
	 * every other condition comparison in the mod.
	 */
	@Test
	void aResidualTooSmallToSeeIsStillNothing() {
		assertEquals(WoundSeverity.SEVERE, WoundMath.stumpSeverity(Balance.CONDITION_EPSILON));
		assertEquals(WoundSeverity.SEVERE, WoundMath.stumpSeverity(-0.0));
	}

	/** A stump always bleeds hard: never a scratch, whichever way the limb went. */
	@Test
	void neitherStumpIsEverMinorOrModerate() {
		for (double lost : new double[]{0.0, 0.001, 3.0}) {
			WoundSeverity severity = WoundMath.stumpSeverity(lost);
			assertTrue(severity.ordinal() >= WoundSeverity.SEVERE.ordinal(),
					"a lost limb is never a scratch, got " + severity);
		}
	}
}
