package dev.captience.realisticdamage;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ImpairmentMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImpairmentMathTest {
	@Test
	void healthyArmHasNoPenalty() {
		assertEquals(0.0, ImpairmentMath.armPenalty(1.0, false));
		assertEquals(0.0, ImpairmentMath.armPenalty(0.76, false));
	}

	@Test
	void armPenaltyIsMonotonicallyWorsening() {
		double last = -1.0;
		for (double f = 1.0; f >= 0.0; f -= 0.01) {
			double penalty = ImpairmentMath.armPenalty(f, false);
			assertTrue(penalty >= last, "penalty decreased at fraction " + f);
			last = penalty;
		}
	}

	@Test
	void disabledAndBrokenAreCapped() {
		double disabledBroken = ImpairmentMath.armPenalty(0.0, true);
		assertEquals(Balance.ARM_PENALTY_TOTAL_CAP, disabledBroken, 1e-9);
		assertTrue(ImpairmentMath.legPenalty(0.0, true) <= Balance.LEG_PENALTY_TOTAL_CAP);
	}

	@Test
	void brokenAlwaysWorseThanNotBroken() {
		for (double f = 1.0; f >= 0.0; f -= 0.05) {
			assertTrue(ImpairmentMath.armPenalty(f, true) > ImpairmentMath.armPenalty(f, false));
			assertTrue(ImpairmentMath.legPenalty(f, true) > ImpairmentMath.legPenalty(f, false));
		}
	}

	@Test
	void worseLegDominatesCombinedPenalty() {
		double combined = ImpairmentMath.combinedLegPenalty(0.4, 0.0);
		assertEquals(0.4 * Balance.LEG_WORSE_WEIGHT, combined, 1e-9);
		// Symmetric.
		assertEquals(combined, ImpairmentMath.combinedLegPenalty(0.0, 0.4), 1e-9);
	}

	@Test
	void bothLegsWreckedStillLeavesLimpSpeed() {
		double worst = ImpairmentMath.combinedLegPenalty(
				ImpairmentMath.legPenalty(0.0, true),
				ImpairmentMath.legPenalty(0.0, true));
		assertTrue(worst <= Balance.LEG_PENALTY_TOTAL_CAP, "must never be a zero-speed softlock");
		assertTrue(1.0 - worst >= 0.25, "player must keep >= ~25% walk speed");
	}

	@Test
	void fallAmplifierRangesFromOneToMax() {
		assertEquals(1.0, ImpairmentMath.fallTraumaAmplifier(1.0), 1e-9);
		assertEquals(Balance.FALL_TRAUMA_INJURED_LEG_MAX_AMP, ImpairmentMath.fallTraumaAmplifier(0.0), 1e-9);
		assertTrue(ImpairmentMath.fallTraumaAmplifier(0.5) > 1.0);
	}

	// --- The one-block step-up ---
	//
	// These assertions are about the end of the leg range rather than the middle,
	// because that is where this curve was wrong: a penalty that behaves as a dial
	// through the healthy range became a wall past mid-INJURED, and nothing in
	// 164 unit tests or 113 gametests looked. Vanilla's integrator is rewritten here
	// as plain arithmetic on purpose — asking the mod whether the mod's own number is
	// big enough would be circular, so the test asks the game's physics instead, and
	// vanillaJumpIsTheYardstickTheOthersMeasureAgainst pins that the copy is faithful.

	/** Vanilla default JUMP_STRENGTH (Attributes.java) — the yardstick, not a mod value. */
	private static final double VANILLA_JUMP_STRENGTH = 0.42;

	/**
	 * LivingEntity.travelInAir moves first and applies gravity afterwards, so a
	 * jumping player's flight is exactly {@code y += v; v = (v - 0.08) * 0.98} —
	 * GRAVITY 0.08 and BASE_VERTICAL_AIR_DRAG 0.98. Positions are sampled at tick
	 * boundaries because that is where collision resolves; there is no sub-tick
	 * ledge to catch.
	 */
	private static double[] jumpArc(double jumpStrength) {
		double[] heights = new double[64];
		double y = 0.0;
		double v = jumpStrength;
		for (int tick = 0; tick < heights.length; tick++) {
			y += v;
			v = (v - 0.08) * 0.98;
			heights[tick] = y;
		}
		return heights;
	}

	private static double peakJumpHeight(double jumpStrength) {
		double peak = 0.0;
		for (double y : jumpArc(jumpStrength)) {
			peak = Math.max(peak, y);
		}
		return peak;
	}

	/** How many ticks the player spends at or above a ledge — the window to walk onto it. */
	private static int ticksAtOrAbove(double jumpStrength, double height) {
		int ticks = 0;
		for (double y : jumpArc(jumpStrength)) {
			if (y >= height) {
				ticks++;
			}
		}
		return ticks;
	}

	/** Effective JUMP_STRENGTH: the modifier is ADD_MULTIPLIED_TOTAL, so 0.42 * (1 - penalty). */
	private static double jumpStrengthFor(double leftLegPenalty, double rightLegPenalty) {
		double penalty = ImpairmentMath.jumpPenalty(
				ImpairmentMath.combinedLegPenalty(leftLegPenalty, rightLegPenalty));
		return VANILLA_JUMP_STRENGTH * (1.0 - penalty);
	}

	@Test
	void vanillaJumpIsTheYardstickTheOthersMeasureAgainst() {
		// If this fails, Mojang changed the physics and every number below is stale.
		assertEquals(1.2522, peakJumpHeight(VANILLA_JUMP_STRENGTH), 1.0e-3);
		assertEquals(6, ticksAtOrAbove(VANILLA_JUMP_STRENGTH, 1.0));
		// The cliff: 0.36813 peaks at exactly 1.0, so anything below it cannot climb.
		assertEquals(1.0, peakJumpHeight(0.36813), 1.0e-4);
		assertTrue(peakJumpHeight(0.3681) < 1.0, "just under the cliff must fall short");
	}

	@Test
	void noLegInjuryEverTakesAwayTheOneBlockStepUp() {
		double[] otherLeg = {
			0.0,
			ImpairmentMath.legPenalty(0.0, false),
			ImpairmentMath.legPenalty(0.0, true),
			ImpairmentMath.legPenaltyDestroyed(),
		};
		for (int i = 0; i <= 100; i++) {
			double fraction = i / 100.0;
			for (boolean broken : new boolean[] {false, true}) {
				double leg = ImpairmentMath.legPenalty(fraction, broken);
				for (double other : otherLeg) {
					double strength = jumpStrengthFor(leg, other);
					String where = "fraction " + fraction + (broken ? " broken" : "") + " vs " + other;
					assertTrue(peakJumpHeight(strength) >= 1.02,
							"must still clear a one-block step-up: " + where);
					assertTrue(ticksAtOrAbove(strength, 1.0) >= 3,
							"and with room to walk onto it, not on one frame: " + where);
				}
			}
		}
		// A lost leg is for the rest of that character's life. Permanence is where a
		// wall is least forgivable, so it gets the same floor as an injury.
		double lost = jumpStrengthFor(ImpairmentMath.legPenaltyDestroyed(), 0.0);
		assertTrue(peakJumpHeight(lost) >= 1.02, "a lost leg must not be a permanent wall");
	}

	@Test
	void theWorstPossibleLegStateLandsExactlyOnTheFloor() {
		// The end of the range, stated as a number rather than an inequality. Even a
		// combined penalty past anything the curves can produce stops at the cap.
		assertEquals(Balance.JUMP_PENALTY_INJURY_CAP,
				ImpairmentMath.jumpPenalty(Balance.LEG_PENALTY_TOTAL_CAP), 1e-9);
		assertEquals(Balance.JUMP_PENALTY_INJURY_CAP, ImpairmentMath.jumpPenalty(1.0), 1e-9);
		double worst = VANILLA_JUMP_STRENGTH * (1.0 - Balance.JUMP_PENALTY_INJURY_CAP);
		assertEquals(0.378, worst, 1e-9);
		assertEquals(1.047, peakJumpHeight(worst), 1.0e-3);
		assertEquals(3, ticksAtOrAbove(worst, 1.0));
	}

	@Test
	void jumpPenaltyStillWorsensWithTheLegUntilItCaps() {
		// Bounding it must not flatten it: the gradient survives everywhere it is safe.
		// A rising combined leg penalty is a worsening leg, so walk the range upward.
		double previous = -1.0;
		for (int i = 0; i <= 100; i++) {
			double penalty = ImpairmentMath.jumpPenalty(i / 100.0 * Balance.LEG_PENALTY_TOTAL_CAP);
			assertTrue(penalty >= previous, "jump penalty must never improve as the leg worsens");
			previous = penalty;
		}
		assertEquals(0.0, ImpairmentMath.jumpPenalty(0.0), 1e-9);
		assertTrue(ImpairmentMath.jumpPenalty(0.05) > 0.0, "a hurt leg still costs something");
		assertTrue(ImpairmentMath.jumpPenalty(0.05) < Balance.JUMP_PENALTY_INJURY_CAP,
				"and less than a wrecked one");
	}

	@Test
	void theCapBoundsWhatAnInjuryCostsNotWhatAStateForbids() {
		// ImpairmentSystem assigns jump = 1.0 outright for crawling, dragging, rooted
		// rescue, knockdown, its recovery tail, microsleep and unconsciousness — and it
		// does so AFTER calling jumpPenalty. Move this cap downstream of those and an
		// unconscious player gets 90% of their jump back. The end-to-end proof is
		// ImpairmentGameTest.wreckedLegsStillClearOneBlockButVetoesStillStop; this is
		// the statement of the invariant next to the code that has to keep it.
		assertTrue(Balance.JUMP_PENALTY_INJURY_CAP < 1.0,
				"a veto is a full stop and the cap must be strictly weaker than one");
		assertEquals(0.0, VANILLA_JUMP_STRENGTH * (1.0 - 1.0), 1e-9,
				"the veto value must still mean no jump at all");
	}
}
