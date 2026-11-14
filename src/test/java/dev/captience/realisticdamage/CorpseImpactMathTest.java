package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.CorpseImpactMath;
import org.junit.jupiter.api.Test;

/**
 * 2.0: what a dead body's landing does to it. The promises the thresholds in
 * {@code Balance} make, held as arithmetic: an ordinary death never loses a
 * limb, a death at the end of a long fall does, and a blast adds nothing.
 */
class CorpseImpactMathTest {
	private static final double NEUTRAL = Balance.LANDING_SURFACE_NEUTRAL;

	private static double trauma(double speed) {
		return CorpseImpactMath.trauma(speed, NEUTRAL, 0.0, 1.0);
	}

	/** How many limbs a body loses if every limb strikes at this speed, one after another. */
	private static int lostAt(double speed) {
		int lost = 0;
		for (int i = 0; i < 4; i++) {
			if (CorpseImpactMath.severs(BodyPart.LEFT_LEG, trauma(speed), lost)) {
				lost++;
			}
		}
		return lost;
	}

	@Test
	void speedIsReadAsTheDropThatCausesIt() {
		assertEquals(0.0, CorpseImpactMath.equivalentDrop(0.0), 1.0e-9);
		assertEquals(0.0, CorpseImpactMath.equivalentDrop(-5.0), 1.0e-9);
		// v squared over 2g at Minecraft's gravity: 32 blocks a second is a 16-block drop.
		assertEquals(16.0, CorpseImpactMath.equivalentDrop(32.0), 1.0e-9);
		assertEquals(25.0, CorpseImpactMath.equivalentDrop(40.0), 1.0e-9);
	}

	@Test
	void theScaleIsTheLivingFalls() {
		// A strike at 32 blocks a second is a 16-block drop, and a living 16-block
		// landing is worth 16 times FALL_TRAUMA_PER_BLOCK.
		assertEquals(16.0 * Balance.FALL_TRAUMA_PER_BLOCK, trauma(32.0), 1.0e-9);
	}

	@Test
	void anOrdinaryDeathIsNotLookedAt() {
		// The flop of a body going over where it stood tops out near 9 blocks a
		// second, and the hardest lethal blast throws one down at about 15
		// (CorpseAgreementTest measures both).
		for (double speed : new double[] {0.0, 4.0, 9.0, 15.3, Balance.CORPSE_IMPACT_MIN_SPEED - 0.01}) {
			assertEquals(0.0, trauma(speed), 0.0, "a strike at " + speed + " is under the floor");
			assertFalse(CorpseImpactMath.breaks(BodyPart.LEFT_ARM, trauma(speed)));
			assertEquals(0, lostAt(speed));
		}
	}

	@Test
	void bonesBreakBeforeLimbsComeOff() {
		// Between the floor and the first sever bar there is a band where a limb
		// only breaks: arms from about 20.5, legs from about 22.5.
		assertFalse(CorpseImpactMath.breaks(BodyPart.RIGHT_ARM, trauma(20.0)));
		assertTrue(CorpseImpactMath.breaks(BodyPart.RIGHT_ARM, trauma(21.0)));
		assertFalse(CorpseImpactMath.breaks(BodyPart.RIGHT_LEG, trauma(22.0)));
		assertTrue(CorpseImpactMath.breaks(BodyPart.RIGHT_LEG, trauma(23.0)));
		assertEquals(0, lostAt(23.0), "a strike that breaks a leg does not take it");
		assertTrue(Balance.CORPSE_IMPACT_BREAK_LEG < Balance.CORPSE_IMPACT_SEVER[0]);
		assertTrue(Balance.CORPSE_IMPACT_BREAK_ARM < Balance.CORPSE_IMPACT_BREAK_LEG);
	}

	@Test
	void theBarRisesWithEveryLimbLost() {
		for (int i = 1; i < Balance.CORPSE_IMPACT_SEVER.length; i++) {
			assertTrue(Balance.CORPSE_IMPACT_SEVER[i] > Balance.CORPSE_IMPACT_SEVER[i - 1], "bar " + i + " is above the one before");
		}
		assertEquals(0, lostAt(28.0), "29 blocks a second, a sixteen-block fall, is the first that takes a limb");
		assertEquals(1, lostAt(30.0));
		assertEquals(2, lostAt(34.0));
		assertEquals(3, lostAt(38.0));
		assertEquals(4, lostAt(40.0), "only the fastest body the mod launches loses all four");
		// Past the last bar nothing more comes off, whatever the strike.
		assertFalse(CorpseImpactMath.severs(BodyPart.LEFT_ARM, 1.0e9, Balance.CORPSE_IMPACT_SEVER.length));
	}

	@Test
	void aHeadOrATorsoIsNeverTakenOrBroken() {
		for (BodyPart core : new BodyPart[] {BodyPart.HEAD, BodyPart.TORSO}) {
			assertFalse(CorpseImpactMath.severs(core, 1.0e9, 0), core + " cannot come off this way");
			assertFalse(CorpseImpactMath.breaks(core, 1.0e9), core + " has no bone this rule breaks");
		}
	}

	@Test
	void theSurfaceTheArmourAndTheServerAllCount() {
		double hard = trauma(30.0);
		assertTrue(CorpseImpactMath.severs(BodyPart.LEFT_LEG, hard, 0), "thirty onto ordinary ground takes a leg");
		// Hay gives back about half: the same landing keeps the leg.
		double onHay = CorpseImpactMath.trauma(30.0, Balance.LANDING_SURFACE_SOFT, 0.0, 1.0);
		assertFalse(CorpseImpactMath.severs(BodyPart.LEFT_LEG, onHay, 0));
		// Armour over the limb takes its share, to its cap and no further.
		double armoured = CorpseImpactMath.trauma(30.0, NEUTRAL, Balance.ARMOR_FALL_REDUCTION_CAP, 1.0);
		assertEquals(hard * (1.0 - Balance.ARMOR_FALL_REDUCTION_CAP), armoured, 1.0e-9);
		assertFalse(CorpseImpactMath.severs(BodyPart.LEFT_LEG, armoured, 0));
		assertEquals(0.0, CorpseImpactMath.trauma(30.0, NEUTRAL, 5.0, 1.0), 1.0e-9, "armour never takes more than all of it");
		// The server's body-damage multiplier scales it like every other trauma.
		assertEquals(hard * 0.5, CorpseImpactMath.trauma(30.0, NEUTRAL, 0.0, 0.5), 1.0e-9);
		assertEquals(0.0, CorpseImpactMath.trauma(30.0, NEUTRAL, 0.0, -1.0), 1.0e-9);
	}

	@Test
	void theHardestStrikeIsJudgedFirstAndTiesGoInOrder() {
		double floor = Balance.CORPSE_IMPACT_MIN_SPEED;
		assertArrayEquals(new int[0], CorpseImpactMath.hardestFirst(new double[] {0.0, 3.0, floor - 0.1, 0.0, 0.0, 0.0}));
		assertArrayEquals(new int[] {4, 2, 5},
				CorpseImpactMath.hardestFirst(new double[] {0.0, 0.0, 30.0, 1.0, 40.0, floor}));
		// Both legs land together at the same speed: the lower index first, every time.
		assertArrayEquals(new int[] {4, 5, 3},
				CorpseImpactMath.hardestFirst(new double[] {0.0, 0.0, 0.0, 20.0, 40.0, 40.0}));
	}

	@Test
	void onlyDeathsWithEnergyAreFollowed() {
		assertFalse(CorpseImpactMath.worthFollowing(0.0, 0.0), "died standing on the ground");
		assertFalse(CorpseImpactMath.worthFollowing(0.36, 0.0), "a sprint");
		assertFalse(CorpseImpactMath.worthFollowing(0.42, 1.2), "a jump");
		assertTrue(CorpseImpactMath.worthFollowing(0.6, 0.0), "three blocks into a fall");
		assertTrue(CorpseImpactMath.worthFollowing(0.0, Balance.CORPSE_IMPACT_FOLLOW_DROP), "still, over a long drop");
		// A lethal blast's launch is over the line: those bodies are followed, and the bars keep them whole.
		double blast = Math.hypot(Balance.CORPSE_BLAST_SPEED_MIN, Balance.CORPSE_BLAST_LIFT_MIN);
		assertTrue(CorpseImpactMath.worthFollowing(Math.hypot(Balance.CORPSE_BLAST_SPEED_MAX, Balance.CORPSE_BLAST_LIFT_MAX), 0.0));
		assertTrue(blast > 0.0);
	}
}
