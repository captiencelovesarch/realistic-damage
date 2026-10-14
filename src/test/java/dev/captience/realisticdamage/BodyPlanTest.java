package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPlan;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ImpairmentMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BodyPlanTest {
	@Test
	void classifyNeverAnswersWithARegionThePlanLacks() {
		for (BodyPlan plan : BodyPlan.values()) {
			for (double f = -1.2; f <= 1.2; f += 0.1) {
				for (double r = -1.2; r <= 1.2; r += 0.1) {
					for (double h = 0.0; h <= 1.0; h += 0.05) {
						BodyPart part = plan.classify(f, r, h);
						assertTrue(plan.has(part), plan + " answered " + part + " at " + f + "," + r + "," + h);
					}
				}
			}
		}
	}

	/** Where a region bleeds from must be a place that is that region when hit. */
	@Test
	void everyAnchorLiesInsideItsOwnRegion() {
		for (BodyPlan plan : BodyPlan.values()) {
			for (BodyPart part : BodyPart.values()) {
				if (!plan.has(part)) {
					continue;
				}
				double[] a = plan.anchor(part);
				assertEquals(part, plan.classify(a[0], a[1], a[2]), plan + " anchor of " + part);
			}
		}
	}

	@Test
	void everyPlanCanBeHitInEveryRegionItHas() {
		for (BodyPlan plan : BodyPlan.values()) {
			java.util.EnumSet<BodyPart> seen = java.util.EnumSet.noneOf(BodyPart.class);
			for (double f = -1.0; f <= 1.0; f += 0.05) {
				for (double r = -1.0; r <= 1.0; r += 0.05) {
					for (double h = 0.0; h <= 1.0; h += 0.02) {
						seen.add(plan.classify(f, r, h));
					}
				}
			}
			for (BodyPart part : BodyPart.values()) {
				assertEquals(plan.has(part), seen.contains(part), plan + " reachability of " + part);
			}
		}
	}

	@Test
	void aCowIsHitWhereACowIs() {
		BodyPlan cow = BodyPlan.QUADRUPED;
		assertEquals(BodyPart.HEAD, cow.classify(1.0, 0.0, 0.75), "the face");
		assertEquals(BodyPart.TORSO, cow.classify(0.0, 1.0, 0.7), "the flank");
		assertEquals(BodyPart.LEFT_ARM, cow.classify(0.6, -0.6, 0.2), "front left leg");
		assertEquals(BodyPart.RIGHT_ARM, cow.classify(0.6, 0.6, 0.2), "front right leg");
		assertEquals(BodyPart.LEFT_LEG, cow.classify(-0.6, -0.6, 0.2), "hind left leg");
		assertEquals(BodyPart.RIGHT_LEG, cow.classify(-0.6, 0.6, 0.2), "hind right leg");
	}

	@Test
	void theRolesAreWhatTheBodiesDo() {
		assertTrue(BodyPlan.HUMANOID.hands(BodyPart.LEFT_ARM));
		assertTrue(BodyPlan.QUADRUPED.walks(BodyPart.LEFT_ARM));
		assertTrue(BodyPlan.FLYER.wing(BodyPart.RIGHT_ARM));
		assertTrue(BodyPlan.AVIAN.walks(BodyPart.LEFT_LEG));
		assertFalse(BodyPlan.LONG.has(BodyPart.LEFT_LEG));
		assertFalse(BodyPlan.BLOB.has(BodyPart.HEAD));
		assertEquals(4, BodyPlan.ARTHROPOD.walkingLimbs());
		assertEquals(2, BodyPlan.HUMANOID.walkingLimbs());
		assertEquals(0, BodyPlan.FLOATER.walkingLimbs());
		assertEquals(BodyPart.TORSO, BodyPlan.LONG.redirect(BodyPart.LEFT_LEG));
	}

	@Test
	void everyPlanHasATagName() {
		for (BodyPlan plan : BodyPlan.values()) {
			assertNotNull(BodyPlan.byId(plan.id()));
			assertEquals(plan, BodyPlan.byId(plan.id()));
		}
	}

	@Test
	void oneLegOfFourCostsLessThanOneLegOfTwo() {
		double gone = Balance.LEG_PENALTY_DESTROYED;
		double fourOne = ImpairmentMath.fourLegPenalty(gone, 0.0, 0.0, 0.0);
		double twoOne = ImpairmentMath.combinedLegPenalty(gone, 0.0);
		assertTrue(fourOne < twoOne, fourOne + " vs " + twoOne);
		assertTrue(fourOne > 0.15, "a lost leg is still felt: " + fourOne);
	}

	@Test
	void fourLegPenaltyGrowsWithEveryLegAndIsCapped() {
		double gone = Balance.LEG_PENALTY_DESTROYED;
		double one = ImpairmentMath.fourLegPenalty(gone, 0.0, 0.0, 0.0);
		double two = ImpairmentMath.fourLegPenalty(gone, gone, 0.0, 0.0);
		double three = ImpairmentMath.fourLegPenalty(gone, gone, gone, 0.0);
		assertTrue(one < two && two < three);
		assertTrue(ImpairmentMath.fourLegPenalty(1.0, 1.0, 1.0, 1.0) <= Balance.LEG_PENALTY_TOTAL_CAP + 1e-9);
		// Order of the arguments must not matter.
		assertEquals(ImpairmentMath.fourLegPenalty(0.1, 0.4, 0.2, 0.3),
				ImpairmentMath.fourLegPenalty(0.3, 0.2, 0.4, 0.1), 1e-12);
	}
}
