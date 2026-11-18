package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.systems.BrokenLegLimp;
import dev.captience.realisticdamage.systems.BrokenLegLimp.Step;
import org.junit.jupiter.api.Test;

class BrokenLegLimpTest {
	private static final double LEFT_STEP = Math.PI / 2.0;
	private static final double RIGHT_STEP = -Math.PI / 2.0;

	@Test
	void soundLegsAndStandingStillDoNothing() {
		assertSame(Step.NONE, BrokenLegLimp.step(LEFT_STEP, 1.0F, false, false));
		assertSame(Step.NONE, BrokenLegLimp.step(LEFT_STEP, 0.0F, true, true));
		assertSame(Step.NONE, BrokenLegLimp.step(LEFT_STEP, -1.0F, true, false));
	}

	@Test
	void theDipIsOnTheBrokenLegsStepOnly() {
		Step onIt = BrokenLegLimp.step(LEFT_STEP, 1.0F, true, false);
		Step offIt = BrokenLegLimp.step(RIGHT_STEP, 1.0F, true, false);
		assertTrue(onIt.dip() > 0.0F);
		assertEquals(0.0F, offIt.dip());
		assertEquals(0.0F, offIt.roll());
		// And level at the moment the feet change over.
		assertEquals(0.0F, BrokenLegLimp.step(0.0, 1.0F, true, false).dip(), 1.0e-6F);
	}

	@Test
	void theHeadLeansTowardTheBrokenLeg() {
		assertTrue(BrokenLegLimp.step(LEFT_STEP, 1.0F, true, false).roll() < 0.0F, "left leg leans left");
		assertTrue(BrokenLegLimp.step(RIGHT_STEP, 1.0F, false, true).roll() > 0.0F, "right leg leans right");
	}

	@Test
	void theLegsMirrorEachOther() {
		for (int i = 0; i < 64; i++) {
			double phase = i * 0.1;
			Step left = BrokenLegLimp.step(phase, 0.8F, true, false);
			Step right = BrokenLegLimp.step(phase + Math.PI, 0.8F, false, true);
			assertEquals(left.dip(), right.dip(), 1.0e-5F);
			assertEquals(left.roll(), -right.roll(), 1.0e-5F);
		}
	}

	@Test
	void bothLegsBrokenDipOnEveryStepAndLeanEachWayInTurn() {
		Step left = BrokenLegLimp.step(LEFT_STEP, 1.0F, true, true);
		Step right = BrokenLegLimp.step(RIGHT_STEP, 1.0F, true, true);
		assertTrue(left.dip() > 0.0F && right.dip() > 0.0F);
		assertEquals(left.dip(), right.dip(), 1.0e-6F);
		assertTrue(left.roll() < 0.0F && right.roll() > 0.0F);
	}

	@Test
	void aSlowerWalkLimpsLessAndARunNoMoreThanAWalk() {
		Step slow = BrokenLegLimp.step(LEFT_STEP, 0.3F, true, false);
		Step full = BrokenLegLimp.step(LEFT_STEP, 1.0F, true, false);
		Step run = BrokenLegLimp.step(LEFT_STEP, 2.5F, true, false);
		assertTrue(slow.dip() < full.dip());
		assertEquals(full, run);
	}

	@Test
	void itNeverGetsInTheWayOfAiming() {
		float lastDip = 0.0F;
		float lastRoll = 0.0F;
		for (int i = 0; i <= 2000; i++) {
			Step step = BrokenLegLimp.step(i * 0.01, 1.0F, true, true);
			// A tenth of a block and two degrees are the ceiling, whatever the tuning.
			assertTrue(step.dip() >= 0.0F && step.dip() <= 0.1F, "dip " + step.dip());
			assertTrue(Math.abs(step.roll()) <= 2.0F, "roll " + step.roll());
			// Smooth: one hundredth of a radian of walk never moves the view visibly.
			assertTrue(Math.abs(step.dip() - lastDip) < 0.002F || i == 0);
			assertTrue(Math.abs(step.roll() - lastRoll) < 0.05F || i == 0);
			lastDip = step.dip();
			lastRoll = step.roll();
		}
	}

	@Test
	void aLegCarriesWeightForHalfTheCycle() {
		assertEquals(1.0F, BrokenLegLimp.stance(LEFT_STEP, true), 1.0e-6F);
		assertEquals(0.0F, BrokenLegLimp.stance(LEFT_STEP, false));
		assertEquals(1.0F, BrokenLegLimp.stance(RIGHT_STEP, false), 1.0e-6F);
		assertEquals(0.0F, BrokenLegLimp.stance(RIGHT_STEP, true));
	}
}
