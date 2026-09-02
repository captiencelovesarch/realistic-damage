package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.CeilingMath;
import dev.captience.realisticdamage.systems.CeilingMath.Event;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ladder itself (1.6). Everything the rest of the mod believes about bands
 * is asserted here rather than assumed: the rungs are the state boundaries, the
 * band word is the state word, and the sanitiser refuses to invent an injury.
 */
class CeilingMathTest {
	@Test
	void theBandWordIsTheStateWord() {
		// The rungs ARE HEALTHY_ABOVE / HURT_ABOVE / INJURED_ABOVE, and fromFraction
		// is exclusive-above, so a part sitting exactly on a rung reads that band's word.
		assertEquals(1.0, CeilingMath.fraction(0));
		assertEquals(Balance.HEALTHY_ABOVE, CeilingMath.fraction(1));
		assertEquals(Balance.HURT_ABOVE, CeilingMath.fraction(2));
		assertEquals(Balance.INJURED_ABOVE, CeilingMath.fraction(3));

		assertEquals(BodyState.HEALTHY, CeilingMath.pinnedState(0));
		assertEquals(BodyState.HURT, CeilingMath.pinnedState(1));
		assertEquals(BodyState.INJURED, CeilingMath.pinnedState(2));
		assertEquals(BodyState.CRITICAL, CeilingMath.pinnedState(3));

		// Strictly decreasing, so no two bands can ever mean the same ceiling.
		for (int band = 1; band <= Balance.CEILING_BANDS; band++) {
			assertTrue(CeilingMath.fraction(band) < CeilingMath.fraction(band - 1), "band " + band);
		}
	}

	@Test
	void bandOrdinalsMatchBodyState() {
		// BodySnapshot.ceilingState indexes BodyState.values() with the raw band.
		// That identity is asserted here, never assumed there.
		for (int band = 0; band <= Balance.CEILING_BANDS; band++) {
			assertEquals(band, CeilingMath.pinnedState(band).ordinal(), "band " + band);
			assertEquals(CeilingMath.pinnedState(band), BodyState.values()[band], "band " + band);
		}
	}

	@Test
	void theLimbLadderStopsAtCritical() {
		for (BodyPart limb : new BodyPart[] {BodyPart.LEFT_LEG, BodyPart.RIGHT_ARM}) {
			for (Event event : Event.values()) {
				assertEquals(1, CeilingMath.lowered(0, event, limb), limb.id() + " " + event);
				assertEquals(2, CeilingMath.lowered(1, event, limb), limb.id() + " " + event);
				assertEquals(3, CeilingMath.lowered(2, event, limb), limb.id() + " " + event);
				assertEquals(3, CeilingMath.lowered(3, event, limb), limb.id() + " " + event);
			}
		}
	}

	@Test
	void theCoreLadderStopsAtHurtAndCannotBreak() {
		for (BodyPart core : new BodyPart[] {BodyPart.HEAD, BodyPart.TORSO}) {
			for (Event event : new Event[] {Event.DISABLED, Event.DESTROYED}) {
				assertEquals(1, CeilingMath.lowered(0, event, core), core.id() + " " + event);
				assertEquals(1, CeilingMath.lowered(1, event, core), core.id() + " " + event);
			}
			// Heads and chests do not break, so a BROKEN event is not their event.
			assertFalse(CeilingMath.qualifies(Event.BROKEN, core), core.id());
			assertEquals(0, CeilingMath.lowered(0, Event.BROKEN, core), core.id());
			assertEquals(1, CeilingMath.lowered(1, Event.BROKEN, core), core.id());
		}
	}

	@Test
	void everyResultIsMonotoneAndInsideTheFloor() {
		for (BodyPart part : BodyPart.values()) {
			int floor = CeilingMath.floorBand(part);
			for (Event event : Event.values()) {
				for (int band = -2; band <= 6; band++) {
					int result = CeilingMath.lowered(band, event, part);
					assertTrue(result >= CeilingMath.legalBand(part, band),
							part.id() + " " + event + " band " + band + " went up");
					assertTrue(result <= floor,
							part.id() + " " + event + " band " + band + " passed the floor");
				}
			}
		}
	}

	@Test
	void illegalBandsBecomeNoCeilingAndRealOnesAreFloored() {
		// Anything this mod never wrote is "no ceiling": a corrupt or foreign value
		// must never hand a player a permanent injury they did not earn.
		assertEquals(0, CeilingMath.legalBand(BodyPart.TORSO, 99));
		assertEquals(0, CeilingMath.legalBand(BodyPart.LEFT_LEG, -1));
		assertEquals(0, CeilingMath.legalBand(BodyPart.LEFT_LEG, Balance.CEILING_BANDS + 1));
		for (BodyPart part : BodyPart.values()) {
			assertEquals(0, CeilingMath.legalBand(part, 0), part.id());
		}
		// Real bands are clamped to the part's floor instead.
		assertEquals(3, CeilingMath.legalBand(BodyPart.LEFT_LEG, 3));
		assertEquals(1, CeilingMath.legalBand(BodyPart.HEAD, 3));
		assertEquals(1, CeilingMath.legalBand(BodyPart.HEAD, 2));
		assertEquals(1, CeilingMath.legalBand(BodyPart.TORSO, 2));
		assertEquals(Balance.CEILING_FLOOR_BAND_LIMB, CeilingMath.floorBand(BodyPart.RIGHT_ARM));
		assertEquals(Balance.CEILING_FLOOR_BAND_CORE, CeilingMath.floorBand(BodyPart.HEAD));
	}

	@Test
	void everyFloorClearsTheTotemFloor() {
		// totemStabilize writes through setCondition, which now clamps to the ceiling.
		// If the deepest ceiling ever dropped below the totem floor, the totem would
		// silently stop floor-ing that part instead of failing loudly here.
		assertTrue(CeilingMath.fraction(Balance.CEILING_BANDS) > Balance.TOTEM_CONDITION_FLOOR_FRACTION);
		for (BodyPart part : BodyPart.values()) {
			assertTrue(CeilingMath.fraction(CeilingMath.floorBand(part)) > Balance.TOTEM_CONDITION_FLOOR_FRACTION,
					part.id());
		}
	}
}
