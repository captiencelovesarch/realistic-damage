package dev.captience.realisticdamage.fieldkit;

import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.damage.DamageProfile;
import dev.captience.realisticdamage.damage.DamageProfiles;
import dev.captience.realisticdamage.fieldkit.brew.HaemorrhageMath;
import dev.captience.realisticdamage.systems.WoundMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Haemorrhage's thresholds (2.0, phase 3): the same hit always gives the same
 * wound, so every number here is a threshold or a factor, and these pin them.
 */
class HaemorrhageMathTest {
	@Test
	void theThresholdDropsByLevelAndHasAFloor() {
		assertEquals(0.6, HaemorrhageMath.thresholdFactor(0), 1e-9);
		assertEquals(0.45, HaemorrhageMath.thresholdFactor(1), 1e-9);
		assertEquals(Balance.HAEMORRHAGE_THRESHOLD_FLOOR, HaemorrhageMath.thresholdFactor(9), 1e-9,
				"however strong the brew, a hit still has to be a hit");
	}

	/** A slash under the minor threshold opens nothing normally and something under Haemorrhage. */
	@Test
	void aGlancingSlashOpensUnderIt() {
		DamageProfile slash = DamageProfiles.of(DamageCategory.SLASH);
		assertNull(slash.woundFor(1.5), "1.5 is under the slash minor threshold");
		assertNotNull(slash.woundFor(HaemorrhageMath.judgedDamage(1.5, 0)), "under Haemorrhage it opens");
		assertNull(slash.woundFor(HaemorrhageMath.judgedDamage(1.0, 0)), "but a mere touch still does not");
	}

	/** Blunt has no bleed threshold to lower: no factor makes a club cut. */
	@Test
	void bluntStillNeverBleeds() {
		DamageProfile blunt = DamageProfiles.of(DamageCategory.BLUNT);
		assertNull(blunt.woundFor(HaemorrhageMath.judgedDamage(50.0, 1)));
	}

	@Test
	void woundsOpenAStepWorseAndStopAtCritical() {
		assertEquals(WoundSeverity.MODERATE, HaemorrhageMath.worse(WoundSeverity.MINOR));
		assertEquals(WoundSeverity.SEVERE, HaemorrhageMath.worse(WoundSeverity.MODERATE));
		assertEquals(WoundSeverity.CRITICAL, HaemorrhageMath.worse(WoundSeverity.CRITICAL));
		assertNull(HaemorrhageMath.worse(null), "no wound stays no wound (armour took it)");
	}

	/** Under the usual cap the stacking is exactly the core's; only the cap moves. */
	@Test
	void stackingAgreesWithTheCoreBelowTheCap() {
		double[] rates = {
				WoundSeverity.SEVERE.bloodPerSecond(), WoundSeverity.MINOR.bloodPerSecond(),
				WoundSeverity.MODERATE.bloodPerSecond()};
		assertEquals(WoundMath.effectiveBleedRate(rates), HaemorrhageMath.stackedRate(rates, HaemorrhageMath.cap(0)),
				1e-9);
	}

	@Test
	void eightCriticalWoundsRunPastTheCap() {
		double[] rates = new double[8];
		java.util.Arrays.fill(rates, WoundSeverity.CRITICAL.bloodPerSecond());
		assertEquals(Balance.WOUND_STACK_CAP, WoundMath.effectiveBleedRate(rates), 1e-9, "the core caps them");
		assertEquals(Balance.WOUND_STACK_CAP * 1.5, HaemorrhageMath.stackedRate(rates, HaemorrhageMath.cap(0)), 1e-9);
		assertTrue(HaemorrhageMath.stackedRate(rates, HaemorrhageMath.cap(1))
				> HaemorrhageMath.stackedRate(rates, HaemorrhageMath.cap(0)), "level II runs further still");
	}

	@Test
	void aHandHoldsBackHalfAsMuch() {
		assertEquals(0.7, HaemorrhageMath.pressureMultiplier(0.4), 1e-9, "your own hand: 60% held back becomes 30%");
		assertEquals(1.0, HaemorrhageMath.pressureMultiplier(1.0), 1e-9, "no hand, no change");
	}
}
