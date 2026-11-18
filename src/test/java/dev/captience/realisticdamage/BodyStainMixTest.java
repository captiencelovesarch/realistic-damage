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
}
