package dev.captience.realisticdamage.fieldkit;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath.Stage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sedation's stages for each body size, and the dose a delivery is (2.0,
 * phase 3). The plan's own sentences are the tests: one arrow drops a zombie,
 * a ravager takes several, and enough of it stops the breathing.
 */
class SedationMathTest {
	private static final double PLAYER = 1.0;
	private static final double ZOMBIE = 1.0;
	/** A ravager's 100 health over the 20-health baseline. */
	private static final double RAVAGER = 5.0;

	@Test
	void anArrowADrinkAndACloudBreathAreEachOneDose() {
		int arrow = Balance.SEDATION_TICKS / 8;
		assertEquals(1.0, SedationMath.dose(0, arrow), 1e-9, "a tipped arrow carries one dose");
		assertEquals(1.0, SedationMath.dose(0, Balance.SEDATION_TICKS), 1e-9, "a drink is one dose, not eight");
		assertEquals(1.0, SedationMath.dose(0, Balance.SEDATION_TICKS / 4), 1e-9, "one breath of the cloud");
		assertEquals(2.0, SedationMath.dose(1, arrow), 1e-9, "the strong brew is two");
		assertEquals(0.4, SedationMath.dose(0, 40), 1e-9, "the edge of a splash is part of one");
		assertEquals(0.0, SedationMath.dose(0, 0), 1e-9);
	}

	@Test
	void oneDoseDropsAZombieOrAPlayer() {
		assertEquals(Stage.OUT, SedationMath.stage(1.0, ZOMBIE));
		assertEquals(Stage.OUT, SedationMath.stage(1.0, PLAYER));
	}

	@Test
	void aRavagerTakesFive() {
		assertEquals(Stage.NONE, SedationMath.stage(1.0, RAVAGER), "one arrow barely registers");
		assertEquals(Stage.DROWSY, SedationMath.stage(2.0, RAVAGER));
		assertEquals(Stage.STUMBLING, SedationMath.stage(3.0, RAVAGER));
		assertEquals(Stage.STUMBLING, SedationMath.stage(4.0, RAVAGER));
		assertEquals(Stage.OUT, SedationMath.stage(5.0, RAVAGER));
	}

	@Test
	void threeDosesStopABreathing() {
		assertEquals(Stage.OUT, SedationMath.stage(2.0, PLAYER), "two is a long sleep");
		assertEquals(Stage.OVERDOSE, SedationMath.stage(3.0, PLAYER), "three is too many");
	}

	/** The stages, in order, as a dose takes hold: no jump straight to out. */
	@Test
	void aDoseTakesHoldThroughEveryStage() {
		double pending = 1.0;
		double level = 0.0;
		int drowsyAt = -1;
		int stumblingAt = -1;
		int outAt = -1;
		for (int tick = 1; tick <= 400; tick++) {
			double taken = SedationMath.absorbed(pending);
			pending -= taken;
			level += taken;
			level -= SedationMath.cleared(pending, level, PLAYER);
			Stage stage = SedationMath.stage(level, PLAYER);
			if (drowsyAt < 0 && stage == Stage.DROWSY) {
				drowsyAt = tick;
			}
			if (stumblingAt < 0 && stage == Stage.STUMBLING) {
				stumblingAt = tick;
			}
			if (outAt < 0 && stage == Stage.OUT) {
				outAt = tick;
			}
		}
		assertTrue(drowsyAt > 0 && drowsyAt < stumblingAt && stumblingAt < outAt,
				"drowsy, then stumbling, then out: " + drowsyAt + ", " + stumblingAt + ", " + outAt);
		assertEquals(Math.round(Balance.SEDATION_OUT * Balance.SEDATION_ABSORB_TICKS), outAt, 1,
				"out once 0.85 of the dose has taken hold");
	}

	/** Nothing clears while a dose is still taking hold; after that, half of it every half-life. */
	@Test
	void itWearsOffWithTime() {
		assertEquals(0.0, SedationMath.cleared(0.5, 1.0, PLAYER), 1e-12);
		double level = 1.0;
		for (int tick = 0; tick < Balance.SEDATION_HALF_LIFE_TICKS; tick++) {
			level -= SedationMath.cleared(0.0, level, PLAYER);
		}
		assertEquals(0.5, level, 1e-9, "half of it gone in a half-life");
		assertEquals(0.01, SedationMath.cleared(0.0, 0.01, PLAYER), 1e-12, "the last scrap clears at once");
		assertEquals(0, SedationMath.ticksToClear(0.0, 0.0, PLAYER));
	}

	/**
	 * How long one body is on the floor, the way the game runs it: absorbed,
	 * cleared, and out while the stage says so. The user's playtest: a strong
	 * arrow had them down for well over two minutes, and the effect said four.
	 */
	@Test
	void oneArrowIsAShortSleepAndAStrongOneNotMuchLonger() {
		int oneOut = ticksOut(1.0);
		int strongOut = ticksOut(2.0);
		assertTrue(oneOut >= 40 && oneOut <= 120, "one arrow holds a player out 2-6 s, then they come round: " + oneOut);
		assertTrue(strongOut <= 500, "a strong one, under half a minute held out: " + strongOut);
		assertTrue(strongOut > oneOut, "but longer than one");
		int effect = SedationMath.ticksToClear(1.0, 0.0, PLAYER);
		assertTrue(effect <= 1200, "one arrow's effect is gone within a minute: " + effect);
		assertTrue(SedationMath.ticksToClear(2.0, 0.0, PLAYER) <= 1600, "a strong one within 80 s");
	}

	/** An overdose barely clears, so the hypoxia debt (20 s) runs all the way up. */
	@Test
	void anOverdoseOutlastsTheBreath() {
		double pending = 3.0;
		double level = 0.0;
		int overdosed = 0;
		for (int tick = 0; tick < 6000; tick++) {
			double taken = SedationMath.absorbed(pending);
			pending -= taken;
			level += taken;
			level -= SedationMath.cleared(pending, level, PLAYER);
			if (SedationMath.stage(level, PLAYER) == Stage.OVERDOSE) {
				overdosed++;
			}
		}
		assertTrue(overdosed > Balance.HYPOXIA_DEBT_MAX * 20 + 400,
				"overdosed long enough to run up the debt and then some: " + overdosed + " ticks");
	}

	/** Ticks a player is held out (the stage, not the coming round after) from this many doses at once. */
	private static int ticksOut(double doses) {
		double pending = doses;
		double level = 0.0;
		int out = 0;
		for (int tick = 0; tick < 6000; tick++) {
			double taken = SedationMath.absorbed(pending);
			pending -= taken;
			level += taken;
			level -= SedationMath.cleared(pending, level, PLAYER);
			if (SedationMath.stage(level, PLAYER) == Stage.OUT) {
				out++;
			}
		}
		return out;
	}

	/**
	 * Each stage's hold on consciousness sits where the mod's existing
	 * symptoms are: stumbling between the wake bar and the microsleep bar, so
	 * a body can wake into it and still nod off; out at zero, the collapse.
	 */
	@Test
	void theHoldsSitAmongTheExistingSymptoms() {
		assertTrue(SedationMath.cap(Stage.STUMBLING) >= Balance.WAKE_THRESHOLD);
		assertTrue(SedationMath.cap(Stage.STUMBLING) < Balance.SYMPTOM_MICROSLEEP);
		assertEquals(0.0, SedationMath.cap(Stage.OUT), 1e-12);
		assertEquals(0.0, SedationMath.cap(Stage.OVERDOSE), 1e-12);
		assertTrue(SedationMath.cap(Stage.DROWSY) < Balance.SYMPTOM_VIGNETTE);
		assertTrue(SedationMath.slow(Stage.STUMBLING) > SedationMath.slow(Stage.DROWSY));
		assertEquals(0.0, SedationMath.slow(Stage.OUT), 1e-12, "out cold, there is no walking to slow");
	}
}
