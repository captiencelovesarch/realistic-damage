package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.LimpMath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The one definition of a limp, pinned at both ends of every range.
 *
 * <p>Ends rather than middles, which is the lesson this release has now learned
 * three times: a band cut tested at 0.30 and 0.10 passes whether the boundary
 * is at 0.25 or at 0.20, and every regression that got through in 1.6 got
 * through on a value nobody wrote a case for — the exact boundary, the top rung,
 * the rung that is deliberately not posed at all.
 *
 * <p>The pose and the mob AI's prey score both read this class, so the
 * assertions here are the only place the two are held to the same sentence.
 */
class LimpMathTest {

	// --- The band cut: exactly where it opens, and exactly where it does not.

	@Test
	@DisplayName("a whole leg is on no rung at all")
	void soundIsZero() {
		assertEquals(LimpMath.RUNG_SOUND, LimpMath.limpBand(1.0));
		assertEquals(LimpMath.RUNG_SOUND, LimpMath.rung(false, false, 1.0));
		assertFalse(LimpMath.visible(LimpMath.rung(false, false, 1.0)));
	}

	@Test
	@DisplayName("the band opens at Critical and not one step above it")
	void theBandCutIsExact() {
		// The boundaries are exclusive-above, so each of these three sits ON a
		// rung edge: 0.75 is the last Hurt, 0.50 the last Injured, 0.25 the
		// first Critical. Testing 0.40 instead would pass for any cut in the band.
		assertEquals(LimpMath.RUNG_SOUND, LimpMath.limpBand(Balance.HEALTHY_ABOVE));
		assertEquals(LimpMath.RUNG_SOUND, LimpMath.limpBand(Balance.HURT_ABOVE));
		assertEquals(LimpMath.RUNG_FAVOURED, LimpMath.limpBand(Balance.INJURED_ABOVE));
		// And the far end of Critical: the smallest condition that is still
		// something. Disabled is exactly zero and nothing else.
		assertEquals(LimpMath.RUNG_FAVOURED, LimpMath.limpBand(Double.MIN_VALUE));
		assertEquals(LimpMath.RUNG_RUINED, LimpMath.limpBand(0.0));
	}

	@Test
	@DisplayName("a fraction below zero is still only Disabled")
	void belowZeroSaturates() {
		assertEquals(LimpMath.RUNG_RUINED, LimpMath.limpBand(-1.0));
		assertEquals(LimpMath.RUNG_RUINED, LimpMath.rung(false, false, -1.0));
	}

	// --- The ladder's own two ends.

	@Test
	@DisplayName("a break outranks any amount of wear, at any condition")
	void aBreakOutranksTheBands() {
		// Including at full condition, which is reachable: /rdmg break sets the
		// flag without touching the figure, and a mended-then-rebroken limb can
		// sit anywhere. A break is an event, not a threshold.
		assertEquals(LimpMath.RUNG_FRACTURED, LimpMath.rung(false, true, 1.0));
		assertEquals(LimpMath.RUNG_FRACTURED, LimpMath.rung(false, true, 0.0));
		assertTrue(LimpMath.RUNG_FRACTURED > LimpMath.RUNG_RUINED);
	}

	@Test
	@DisplayName("a lost limb is the top of the ladder and nothing below it matters")
	void lostIsTheTop() {
		assertEquals(LimpMath.RUNG_LOST, LimpMath.rung(true, true, 0.0));
		assertEquals(LimpMath.RUNG_LOST, LimpMath.rung(true, false, 1.0));
		assertEquals(LimpMath.RUNG_LOST, LimpMath.RUNG_POSED_MAX + 1);
	}

	@Test
	@DisplayName("visible is exactly the rungs a mob scores and the model shows")
	void visibleIsTheWholeLadderAboveSound() {
		assertFalse(LimpMath.visible(LimpMath.RUNG_SOUND));
		assertTrue(LimpMath.visible(LimpMath.RUNG_FAVOURED));
		assertTrue(LimpMath.visible(LimpMath.RUNG_RUINED));
		assertTrue(LimpMath.visible(LimpMath.RUNG_FRACTURED));
		assertTrue(LimpMath.visible(LimpMath.RUNG_LOST));
	}

	@Test
	@DisplayName("the worse of two legs is the max, which is the prey score's own sentence")
	void worseIsMaxAndAgreesWithTheLadder() {
		// The prey score says "either leg destroyed, else either leg broken,
		// else the worse leg's band". That reads as three branches and is one
		// max, because the ladder is ordered — and if it ever stops being one
		// max, the model and the mob are looking at different legs.
		assertEquals(LimpMath.RUNG_LOST,
				LimpMath.worse(LimpMath.rung(true, false, 0.0), LimpMath.rung(false, true, 0.0)));
		assertEquals(LimpMath.RUNG_FRACTURED,
				LimpMath.worse(LimpMath.rung(false, true, 1.0), LimpMath.rung(false, false, 0.0)));
		assertEquals(LimpMath.RUNG_RUINED,
				LimpMath.worse(LimpMath.rung(false, false, 0.0), LimpMath.rung(false, false, 0.2)));
		assertEquals(LimpMath.RUNG_SOUND,
				LimpMath.worse(LimpMath.rung(false, false, 1.0), LimpMath.rung(false, false, 1.0)));
	}

	// --- The pose weight, at both ends and at the hole in the far one.

	@Test
	@DisplayName("the pose is nothing at a whole limb and everything at a broken one")
	void poseWeightHitsBothEndsExactly() {
		assertEquals(0.0F, LimpMath.poseWeight(LimpMath.RUNG_SOUND));
		assertEquals(1.0F, LimpMath.poseWeight(LimpMath.RUNG_FRACTURED));
	}

	@Test
	@DisplayName("a limb that is gone is not posed weakly, it is not posed")
	void aLostLimbIsNotPosed() {
		// The one non-monotonic point in the class, and it is deliberate: there
		// is no limb to angle, and the empty space says it louder than any pose
		// could. The prey score still ranks this rung highest, which is the same
		// body read the same way.
		assertEquals(0.0F, LimpMath.poseWeight(LimpMath.RUNG_LOST));
		assertEquals(0.0F, LimpMath.poseWeight(LimpMath.RUNG_LOST + 7));
		assertEquals(0.0F, LimpMath.poseWeight(-1));
	}

	@Test
	@DisplayName("the two posed rungs below a break are honest thirds of it")
	void theMiddleRungsAreSharesOfTheTop() {
		assertEquals(1.0F / 3.0F, LimpMath.poseWeight(LimpMath.RUNG_FAVOURED));
		assertEquals(2.0F / 3.0F, LimpMath.poseWeight(LimpMath.RUNG_RUINED));
	}

	// --- The offsets: both ends, both signs.

	@Test
	@DisplayName("a sound leg's step is untouched and a broken one's is what the constant says")
	void legSwingScaleHitsBothEnds() {
		assertEquals(1.0F, LimpMath.legSwingScale(LimpMath.RUNG_SOUND));
		assertEquals(1.0F - LimpMath.LEG_SWING_DAMP, LimpMath.legSwingScale(LimpMath.RUNG_FRACTURED));
		// A limb that is gone is not drawn, so vanilla's own step is left exactly
		// as written — the same 1.0 a sound leg gets, by a different route.
		assertEquals(1.0F, LimpMath.legSwingScale(LimpMath.RUNG_LOST));
	}

	@Test
	@DisplayName("left and right are exact mirrors, never the same rotation twice")
	void theSidesMirror() {
		// Stated as "the two sides cancel" rather than "one is the negation of
		// the other", which is the same claim and survives the rungs where both
		// are zero: IEEE addition folds a signed zero away, and assertEquals on
		// floats does not.
		for (int rung = 0; rung <= LimpMath.RUNG_LOST; rung++) {
			assertEquals(0.0F, LimpMath.legTurnout(rung, true) + LimpMath.legTurnout(rung, false),
					"leg turnout at rung " + rung);
			assertEquals(0.0F, LimpMath.armCradleRoll(rung, true) + LimpMath.armCradleRoll(rung, false),
					"arm cradle at rung " + rung);
		}
		// And the mirror is not two zeroes: at the top rung both sides are the
		// full constant, pointing opposite ways.
		assertEquals(LimpMath.LEG_TURNOUT, LimpMath.legTurnout(LimpMath.RUNG_FRACTURED, false));
		assertEquals(-LimpMath.ARM_CRADLE_ROLL, LimpMath.armCradleRoll(LimpMath.RUNG_FRACTURED, false));
	}

	@Test
	@DisplayName("a whole limb and a lost one are given no offset at all")
	void theSilentRungsAreSilentEverywhere() {
		for (int rung : new int[] {LimpMath.RUNG_SOUND, LimpMath.RUNG_LOST}) {
			assertEquals(0.0F, LimpMath.legTurnout(rung, true), "leg turnout at rung " + rung);
			assertEquals(0.0F, LimpMath.legTurnout(rung, false), "leg turnout at rung " + rung);
			assertEquals(0.0F, LimpMath.legTrail(rung), "leg trail at rung " + rung);
			assertEquals(0.0F, LimpMath.armCradleRoll(rung, true), "arm roll at rung " + rung);
			assertEquals(0.0F, LimpMath.armCradleLift(rung), "arm lift at rung " + rung);
		}
	}

	@Test
	@DisplayName("the cradle is forward and the trail is backward, at full size")
	void theSignsAndTheTopOfEveryRange() {
		// Negative xRot raises a limb forward — vanilla adds -PI/5 to put a
		// rider's arms on the reins — so a carried arm is negative and a leg
		// left behind is positive. Getting either sign wrong reads as a strut.
		assertEquals(-LimpMath.ARM_CRADLE_LIFT, LimpMath.armCradleLift(LimpMath.RUNG_FRACTURED));
		assertEquals(LimpMath.LEG_TRAIL, LimpMath.legTrail(LimpMath.RUNG_FRACTURED));
		assertTrue(LimpMath.LEG_TURNOUT > 0.0F && LimpMath.ARM_CRADLE_ROLL > 0.0F);
	}

	@Test
	@DisplayName("a worse limb is never posed less")
	void theOffsetsGrowWithTheLadder() {
		for (int rung = LimpMath.RUNG_SOUND; rung < LimpMath.RUNG_POSED_MAX; rung++) {
			assertTrue(LimpMath.poseWeight(rung) < LimpMath.poseWeight(rung + 1),
					"pose weight did not grow from rung " + rung);
			assertTrue(LimpMath.legSwingScale(rung) > LimpMath.legSwingScale(rung + 1),
					"the step did not shorten from rung " + rung);
		}
	}

	// --- The packing.

	@Test
	@DisplayName("every limb keeps its own rung, including the top one")
	void packingRoundTrips() {
		int mask = 0;
		mask = LimpMath.withRung(mask, BodyPart.LEFT_ARM, LimpMath.RUNG_FRACTURED);
		mask = LimpMath.withRung(mask, BodyPart.RIGHT_LEG, LimpMath.RUNG_LOST);
		mask = LimpMath.withRung(mask, BodyPart.LEFT_LEG, LimpMath.RUNG_FAVOURED);
		assertEquals(LimpMath.RUNG_FRACTURED, LimpMath.rungAt(mask, BodyPart.LEFT_ARM));
		assertEquals(LimpMath.RUNG_LOST, LimpMath.rungAt(mask, BodyPart.RIGHT_LEG));
		assertEquals(LimpMath.RUNG_FAVOURED, LimpMath.rungAt(mask, BodyPart.LEFT_LEG));
		assertEquals(LimpMath.RUNG_SOUND, LimpMath.rungAt(mask, BodyPart.RIGHT_ARM));
	}

	@Test
	@DisplayName("writing a rung twice leaves the second one, and nothing of the first")
	void withRungReplacesRatherThanAccumulates() {
		int mask = LimpMath.withRung(0, BodyPart.RIGHT_ARM, LimpMath.RUNG_LOST);
		mask = LimpMath.withRung(mask, BodyPart.RIGHT_ARM, LimpMath.RUNG_SOUND);
		assertEquals(LimpMath.RUNG_SOUND, LimpMath.rungAt(mask, BodyPart.RIGHT_ARM));
		assertEquals(0, mask, "clearing the only rung set should leave an empty mask");
	}

	@Test
	@DisplayName("the last region still fits, and the top bit is not a sign bit")
	void theWholeBodyFitsInAnInt() {
		int mask = 0;
		for (BodyPart part : BodyPart.values()) {
			mask = LimpMath.withRung(mask, part, LimpMath.RUNG_LOST);
		}
		for (BodyPart part : BodyPart.values()) {
			assertEquals(LimpMath.RUNG_LOST, LimpMath.rungAt(mask, part), part.id());
		}
		assertTrue(mask > 0, "six rungs packed into a negative int");
		assertTrue(BodyPart.values().length * LimpMath.BITS_PER_PART <= 31);
	}

	@Test
	@DisplayName("a rung nobody can produce still lands on one that draws nothing")
	void rungAtSaturates() {
		// 0b111 is the widest a three-bit field holds and there is no rung 7.
		// Saturating is what keeps a bad value out of an array index, and it
		// lands on the rung whose pose weight is zero, which is the quiet one.
		int mask = 0b111 << (BodyPart.LEFT_LEG.ordinal() * LimpMath.BITS_PER_PART);
		assertEquals(LimpMath.RUNG_LOST, LimpMath.rungAt(mask, BodyPart.LEFT_LEG));
		assertEquals(0.0F, LimpMath.poseWeight(LimpMath.rungAt(mask, BodyPart.LEFT_LEG)));
	}
}
