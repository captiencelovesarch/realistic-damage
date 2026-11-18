package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.systems.BodyStainMix;
import org.junit.jupiter.api.Test;

class BodyStainMixTest {
	@Test
	void aCleanBodySplashedBySomeoneElseShowsTheirColour() {
		assertTrue(BodyStainMix.foreignShows(0.3, 0.3));
	}

	@Test
	void aBleedingLimbThatCatchesAFewDropsKeepsItsOwnColour() {
		assertFalse(BodyStainMix.foreignShows(0.8, 0.2));
	}

	@Test
	void aDropAtChestHeightOffToOneSideIsOnThatArm() {
		assertEquals(dev.captience.realisticdamage.body.BodyPart.RIGHT_ARM, BodyStainMix.splashRegion(0.6, 0.8));
		assertEquals(dev.captience.realisticdamage.body.BodyPart.LEFT_ARM, BodyStainMix.splashRegion(0.6, -0.8));
		assertEquals(dev.captience.realisticdamage.body.BodyPart.TORSO, BodyStainMix.splashRegion(0.6, 0.1));
	}

	@Test
	void aDropAtLegHeightIsOnTheLegOfItsOwnSide() {
		assertEquals(dev.captience.realisticdamage.body.BodyPart.RIGHT_LEG, BodyStainMix.splashRegion(0.2, 0.3));
		assertEquals(dev.captience.realisticdamage.body.BodyPart.LEFT_LEG, BodyStainMix.splashRegion(0.2, -0.3));
	}

	@Test
	void theHeadIsTheHeadWhicheverSide() {
		assertEquals(dev.captience.realisticdamage.body.BodyPart.HEAD, BodyStainMix.splashRegion(0.9, 0.9));
	}

	@Test
	void noForeignBloodNeverShows() {
		assertFalse(BodyStainMix.foreignShows(0.5, 0.0));
		assertFalse(BodyStainMix.foreignShows(0.0, 0.0));
	}

	@Test
	void theForeignShareDriesWithTheRest() {
		assertEquals(0.15, BodyStainMix.foreignAfter(0.3, 0.6, 0.3), 1.0e-9);
		assertEquals(0.0, BodyStainMix.foreignAfter(0.3, 0.6, 0.0), 1.0e-9);
	}

	@Test
	void ownBleedingOnTopDoesNotAddToTheForeignShare() {
		assertEquals(0.3, BodyStainMix.foreignAfter(0.3, 0.4, 0.9), 1.0e-9);
	}

	@org.junit.jupiter.api.Test
	void waterRinsesOnlyWhatItReaches() {
		var legs = dev.captience.realisticdamage.body.BodyPart.LEFT_LEG;
		var chest = dev.captience.realisticdamage.body.BodyPart.TORSO;
		var arm = dev.captience.realisticdamage.body.BodyPart.RIGHT_ARM;
		var head = dev.captience.realisticdamage.body.BodyPart.HEAD;
		double water = dev.captience.realisticdamage.config.Balance.BODY_STAIN_WASH_WATER;
		// Dry and under a roof: nothing but the slow drying.
		org.junit.jupiter.api.Assertions.assertEquals(0.0, BodyStainMix.washPerSecond(chest, 0.0, false));
		// Wading to the waist: the legs rinse, the chest and head do not.
		org.junit.jupiter.api.Assertions.assertEquals(water, BodyStainMix.washPerSecond(legs, 0.45, false));
		org.junit.jupiter.api.Assertions.assertEquals(0.0, BodyStainMix.washPerSecond(chest, 0.45, false));
		org.junit.jupiter.api.Assertions.assertEquals(0.0, BodyStainMix.washPerSecond(head, 0.45, false));
		// Up to the neck: everything but the head.
		org.junit.jupiter.api.Assertions.assertEquals(water, BodyStainMix.washPerSecond(arm, 0.8, false));
		org.junit.jupiter.api.Assertions.assertEquals(0.0, BodyStainMix.washPerSecond(head, 0.8, false));
		// Under: all of it.
		org.junit.jupiter.api.Assertions.assertEquals(water, BodyStainMix.washPerSecond(head, 1.0, false));
	}

	@org.junit.jupiter.api.Test
	void rainReachesEverythingButSlowly() {
		double rain = dev.captience.realisticdamage.config.Balance.BODY_STAIN_WASH_RAIN;
		for (var part : dev.captience.realisticdamage.body.BodyPart.values()) {
			org.junit.jupiter.api.Assertions.assertEquals(rain, BodyStainMix.washPerSecond(part, 0.0, true));
		}
		org.junit.jupiter.api.Assertions.assertTrue(
				rain < dev.captience.realisticdamage.config.Balance.BODY_STAIN_WASH_WATER);
	}
}
