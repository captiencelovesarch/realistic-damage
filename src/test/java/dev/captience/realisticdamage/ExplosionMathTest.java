package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ExplosionMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the 1.6 blast math: vanilla's curve restated, and the bar a limb has to
 * clear to come off.
 *
 * <p>The bar is {@code EXPLOSIVE_DESTROY_FRACTION} of the limb — 5.85 on an arm,
 * 7.2 on a leg — and the fraction is 0.45 because the true gate on this feature
 * is <em>survival</em>: Fabric's {@code AFTER_DAMAGE} never fires on a killing
 * blow, so a limb only comes off a body that lived. Several tests here therefore
 * restate vanilla's own survival chain ({@link #hpLostOnNormal}) alongside the
 * trauma, because a threshold read only against the trauma curve is a threshold
 * nobody has checked against the thing that actually gates it.
 *
 * <p>The live path never calls {@link ExplosionMath#intensity} — it uses the
 * amount vanilla itself computed, captured by the mixin — so the restatement
 * exists to be checked, and this is where it is checked. The peaks below are
 * vanilla's own {@code 14r + 1}.
 *
 * <p>Every threshold here is read at the <em>ends</em> of its range and not only
 * in the middle: the edge of a blast, the far edge of the survivable window, the
 * armour cap, and the multiplier that flips the closest call in the table. A
 * constant derived from the middle of a range is a constant nobody has looked at
 * where it actually bites.
 */
class ExplosionMathTest {
	/** Trauma on the primary region, at default multipliers: {@code I x 1.35 x 0.50 x (1 - reduction)}. */
	private static double primary(double intensity, double reduction) {
		return ExplosionMath.limbTrauma(intensity, Balance.EXPLOSION_PRIMARY_SHARE, reduction, 1.0);
	}

	@Test
	void vanillaPeaksAreRestatedExactly() {
		// 14r + 1, straight out of ExplosionDamageCalculator: creeper, TNT,
		// charged creeper / end crystal, a firework-sized pop, a ghast fireball.
		assertEquals(43.0, ExplosionMath.intensity(3.0, 0.0, 1.0), 1e-9);
		assertEquals(57.0, ExplosionMath.intensity(4.0, 0.0, 1.0), 1e-9);
		assertEquals(85.0, ExplosionMath.intensity(6.0, 0.0, 1.0), 1e-9);
		assertEquals(15.0, ExplosionMath.intensity(1.0, 0.0, 1.0), 1e-9);
		assertEquals(71.0, ExplosionMath.intensity(5.0, 0.0, 1.0), 1e-9);
	}

	/**
	 * The end of the range, in every direction it has one. Vanilla's curve ends in
	 * "+ 1", so the very edge of a blast is worth exactly one point and nothing
	 * past it is worth anything at all — and full cover is the same answer arrived
	 * at from the other side.
	 */
	@Test
	void theEdgeOfTheBlastIsWorthOne() {
		assertEquals(1.0, ExplosionMath.intensity(3.0, 6.0, 1.0), 1e-9, "exactly 2r is the last point inside");
		assertEquals(0.0, ExplosionMath.intensity(3.0, 6.001, 1.0), 1e-9, "and a millimetre past it is nothing");
		assertEquals(0.0, ExplosionMath.intensity(0.0, 0.0, 1.0), 1e-9, "a blast with no radius is not a blast");
		assertEquals(1.0, ExplosionMath.intensity(3.0, 0.0, 0.0), 1e-9, "and behind full cover you only hear it");
		// Which is to say: nothing at the edge of any blast can take anything.
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG, primary(1.0, 0.0)));
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_ARM, primary(1.0, 0.0)));
	}

	@Test
	void intensityFallsWithDistance() {
		for (double radius : new double[] {3.0, 4.0, 6.0}) {
			double previous = Double.MAX_VALUE;
			for (double distance = 0.0; distance <= radius * 2.0; distance += 0.1) {
				double here = ExplosionMath.intensity(radius, distance, 1.0);
				assertTrue(here < previous,
						"r=" + radius + " must keep falling at d=" + distance + " (" + here + " vs " + previous + ")");
				previous = here;
			}
		}
	}

	/**
	 * A wall is worth more than a chestplate, and it costs one block — but at 0.45
	 * half a wall is no longer enough. Half exposure at point blank is 16.75, worth
	 * 11.31 on a bare leg against a bar of 7.2, so the leg still goes; a bare leg
	 * needs the intensity under 7.2 / 0.675 = 10.67, which at point blank means an
	 * exposure under 0.343. Read at both sides of that line rather than in the
	 * middle, because "cover saves you" is only a true sentence with a number on it.
	 */
	@Test
	void coverMustBeMoreThanHalfToKeepABareLeg() {
		assertEquals(16.75, ExplosionMath.intensity(3.0, 0.0, 0.5), 1e-9);
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG, primary(16.75, 0.0)),
				"half a block of wall no longer keeps a bare leg through a point-blank creeper: "
						+ primary(16.75, 0.0) + " against " + Balance.EXPLOSIVE_DESTROY_LEG);
		// 21(p^2 + p) + 1 = 10.67 solves at p = 0.3428, so the flip is between these.
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				primary(ExplosionMath.intensity(3.0, 0.0, 0.34), 0.0)), "7.13 at exposure 0.34");
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				primary(ExplosionMath.intensity(3.0, 0.0, 0.35), 0.0)), "7.37 at exposure 0.35");
	}

	@Test
	void limbTraumaIsTheSharesFormula() {
		// 43 x 1.35 x 0.50 = 29.025, then x 0.55 for a plain diamond set's 0.45.
		assertEquals(29.025, primary(43.0, 0.0), 1e-9);
		assertEquals(15.96375, primary(43.0, 0.45), 1e-9);
		assertEquals(0.0, ExplosionMath.limbTrauma(43.0, 0.5, 0.0, 0.0), 1e-9,
				"a server that scales trauma to nothing takes no limbs");
		assertEquals(0.0, ExplosionMath.limbTrauma(43.0, 0.5, 1.4, 1.0), 1e-9,
				"and armour that somehow over-reduces gives back nothing rather than healing you");
	}

	/**
	 * Both limbs are the same fraction of their own maximum, and that is asserted
	 * as a ratio rather than as two literals: the arm bar and the leg bar exist so
	 * that one number moves the feature, and the failure this guards against is
	 * somebody retuning a leg and leaving an arm behind.
	 */
	@Test
	void bothLimbBarsAreTheOneFraction() {
		assertEquals(0.45, Balance.EXPLOSIVE_DESTROY_FRACTION, 1e-9);
		assertEquals(Balance.EXPLOSIVE_DESTROY_FRACTION,
				ExplosionMath.threshold(BodyPart.LEFT_ARM) / Balance.ARM_MAX, 1e-9);
		assertEquals(Balance.EXPLOSIVE_DESTROY_FRACTION,
				ExplosionMath.threshold(BodyPart.LEFT_LEG) / Balance.LEG_MAX, 1e-9);
		assertEquals(5.85, ExplosionMath.threshold(BodyPart.RIGHT_ARM), 1e-9);   // 13.0 x 0.45
		assertEquals(7.2, ExplosionMath.threshold(BodyPart.RIGHT_LEG), 1e-9);    // 16.0 x 0.45
		// The core is not eligible at any number the game can produce, which is a
		// rule and not a tuning value: a blast that takes a skull already killed
		// you, and a mod core-kill would fire one call after a totem popped.
		for (BodyPart core : new BodyPart[] {BodyPart.HEAD, BodyPart.TORSO}) {
			assertEquals(Double.POSITIVE_INFINITY, ExplosionMath.threshold(core), core.id());
			assertFalse(ExplosionMath.dismembers(core, 1.0e9), core.id() + " is vanilla's to kill");
		}
	}

	/**
	 * At the line, yes; one representable step below it, no.
	 *
	 * <p>Read off the constants rather than off decimal literals, and deliberately:
	 * {@code 13.0 x 0.45} is 5.850000000000001 in binary floating point, so the
	 * literal {@code 5.85} sits a hair <em>under</em> the arm bar and an assertion
	 * written that way would be testing the decimal system rather than the rule.
	 * {@code Math.nextDown} is the honest way to say "a hair below".
	 */
	@Test
	void dismembersAtTheLineNotBelowIt() {
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG, Balance.EXPLOSIVE_DESTROY_LEG));
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				Math.nextDown(Balance.EXPLOSIVE_DESTROY_LEG)));
		assertTrue(ExplosionMath.dismembers(BodyPart.RIGHT_ARM, Balance.EXPLOSIVE_DESTROY_ARM));
		assertFalse(ExplosionMath.dismembers(BodyPart.RIGHT_ARM,
				Math.nextDown(Balance.EXPLOSIVE_DESTROY_ARM)));
		// And the values those constants hold, to a tolerance that does not care
		// which side of 5.85 the product lands on.
		assertEquals(7.2, Balance.EXPLOSIVE_DESTROY_LEG, 1e-9);
		assertEquals(5.85, Balance.EXPLOSIVE_DESTROY_ARM, 1e-9);
	}

	/**
	 * Vanilla's survival chain, restated for the cases below:
	 * {@code Player.hurtServer}'s difficulty scaling (Normal is x1, which is why
	 * this method names Normal in its own name), then
	 * {@code CombatRules.getDamageAfterAbsorb}, then
	 * {@code getDamageAfterMagicAbsorb}. Full diamond is 20 armour and 8 toughness;
	 * full netherite with Blast Protection IV is 20 armour, 12 toughness and 4 x 8
	 * = 32 protection points, which {@code getDamageAfterMagicAbsorb} clamps to 20.
	 *
	 * <p>It lives in the test rather than in the mod because the mod never asks
	 * this question — the pipeline is handed what the health bar already lost. The
	 * threshold, however, was chosen against it, so the choice is only checkable
	 * if the arithmetic is here to check.
	 *
	 * @return hit points the blast takes off, which kills at or above the health
	 *         the victim had
	 */
	private static double hpLostOnNormal(double intensity, double armour, double toughness, double protection) {
		double damage = intensity;
		if (armour > 0.0) {
			double t = 2.0 + toughness / 4.0;
			double real = Math.min(Math.max(armour - damage / t, armour * 0.2), 20.0);
			damage *= 1.0 - real / 25.0;
		}
		double magic = Math.min(protection, 20.0);
		if (magic > 0.0) {
			damage *= 1.0 - magic / 25.0;
		}
		return damage;
	}

	/** Trauma on a plain-diamond leg (reduction 0.45) from a creeper at this distance. */
	private static double diamondLegAt(double distance) {
		return primary(ExplosionMath.intensity(3.0, distance, 1.0), 0.45);
	}

	/** What a creeper at this distance takes off a full-diamond player on Normal. */
	private static double diamondHpAt(double distance) {
		return hpLostOnNormal(ExplosionMath.intensity(3.0, distance, 1.0), 20.0, 8.0, 0.0);
	}

	/**
	 * <b>The case the fraction was chosen for.</b> A plain-armoured player at ten
	 * hearts, on Normal, meeting a creeper: the leg comes off and they walk away.
	 *
	 * <p>The window is (furthest stand that still maims) - (closest stand you
	 * survive) = 2.633 - 2.144 = 0.49 blocks, and both edges are read here rather
	 * than the middle. Inside it at 2.4 blocks the blast is worth 21.10, which is
	 * 7.83 of leg trauma against a bar of 7.2 and 8.67 hit points against ten. A
	 * quarter of a block nearer, at 2.1, it is 10.24 hit points and you are dead
	 * before the mod is ever called. A tenth of a block further, at 2.7, the leg
	 * stays on.
	 */
	@Test
	void aPlainArmouredPlayerAtTenHeartsLosesTheLegAndLives() {
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG, diamondLegAt(2.4)),
				"7.83 of leg trauma at 2.4 blocks: " + diamondLegAt(2.4));
		assertTrue(diamondHpAt(2.4) < 10.0,
				"and it costs 8.67 of ten hearts, so there is a body left to maim: " + diamondHpAt(2.4));

		assertTrue(diamondHpAt(2.1) >= 10.0,
				"a quarter block nearer is 10.24 and AFTER_DAMAGE never fires: " + diamondHpAt(2.1));
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG, diamondLegAt(2.7)),
				"a tenth of a block further and the leg stays on: " + diamondLegAt(2.7));
	}

	/**
	 * Why 0.45 and not the 0.75 that shipped. At the closest a ten-heart plain
	 * diamond player can stand and live — 2.144 blocks, where vanilla's chain takes
	 * exactly ten hit points — the blast is worth 8.60 of leg trauma. That clears
	 * 7.2 and it does not clear 12.0, so at 0.75 the survivable window for this
	 * player was not thin, it was empty: every distance at which the leg could come
	 * off was a distance at which they were already dead.
	 *
	 * <p>Asserted against the literal 12.0 rather than against a constant, because
	 * the point is a comparison with a value the mod no longer holds.
	 */
	@Test
	void theOldThresholdGaveThatPlayerNoWindowAtAll() {
		double atTheSurvivalEdge = diamondLegAt(2.144);
		assertEquals(10.0, diamondHpAt(2.144), 0.01, "2.144 blocks is exactly ten hit points");
		assertTrue(atTheSurvivalEdge >= Balance.EXPLOSIVE_DESTROY_LEG,
				"8.60 clears today's 7.2: " + atTheSurvivalEdge);
		assertTrue(atTheSurvivalEdge < 16.0 * 0.75,
				"and never reached the old 12.0, which is what made the feature netherite-only: "
						+ atTheSurvivalEdge);
	}

	/**
	 * The cost of that, stated as the truth it now is: Blast Protection IV is a
	 * shorter reach, not immunity. A point-blank creeper is worth 11.61 through the
	 * 0.60 cap against a bar of 7.2, so the leg goes; past 1.72 blocks it does not.
	 * The old sentence — "Blast IV never loses a leg to a creeper" — was true only
	 * because the cap needed an intensity of 44.44 and a creeper peaks at 43.
	 */
	@Test
	void blastFourIsAShorterReachNotImmunity() {
		assertEquals(11.61, primary(43.0, Balance.ARMOR_REDUCTION_CAP), 1e-9);
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG, primary(43.0, Balance.ARMOR_REDUCTION_CAP)),
				"a point-blank creeper goes through the cap now");
		// The far edges: 1.72 blocks at the cap against 2.63 in plain diamond.
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				primary(ExplosionMath.intensity(3.0, 1.7, 1.0), Balance.ARMOR_REDUCTION_CAP)),
				"still inside the cap's reach at 1.7");
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				primary(ExplosionMath.intensity(3.0, 1.75, 1.0), Balance.ARMOR_REDUCTION_CAP)),
				"and outside it at 1.75");
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG, diamondLegAt(1.75)),
				"which is protection worth wearing: plain diamond still loses the leg there");
		// And a stick of TNT was never stopped by it at any threshold.
		assertEquals(15.39, primary(57.0, Balance.ARMOR_REDUCTION_CAP), 1e-9);
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG, primary(57.0, Balance.ARMOR_REDUCTION_CAP)));
	}

	/**
	 * ...and by how much, which is the assertion that moved furthest. At 0.75 the
	 * cap sat 0.39 under the bar and a {@code bodyDamageMultiplier} of 1.035 flipped
	 * it; at 0.45 it sits 61% <em>over</em> the bar — 11.61 against 7.2 — and it
	 * takes a server running blast trauma at 62% of stock to hand Blast IV its
	 * immunity back. "By a hair" has become "not close", and the number is what
	 * makes either wording honest.
	 */
	@Test
	void theBlastFourMarginIsNoLongerThin() {
		// 7.2 / 11.61 = 0.6202, so the flip lives between these two.
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				ExplosionMath.limbTrauma(43.0, Balance.EXPLOSION_PRIMARY_SHARE, Balance.ARMOR_REDUCTION_CAP, 0.61)),
				"a server at 61% of stock blast trauma keeps the Blast IV leg");
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				ExplosionMath.limbTrauma(43.0, Balance.EXPLOSION_PRIMARY_SHARE, Balance.ARMOR_REDUCTION_CAP, 0.63)),
				"and 63% does not");
	}

	/** The cave creeper: diamond, no enchants, and a cliff two and a half blocks wide. */
	@Test
	void diamondLosesTheLegOutToTwoAndAHalfBlocks() {
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG, diamondLegAt(2.6)),
				"7.29 at 2.6 blocks: " + diamondLegAt(2.6));
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG, diamondLegAt(2.7)),
				"7.02 at 2.7 — past the far edge: " + diamondLegAt(2.7));
	}

	/**
	 * The share gate used to be a belt over braces; at 0.45 it is the belt. A 0.05
	 * remainder is worth {@code I x 1.35 x 0.05}, so it crosses the 5.85 arm bar at
	 * an intensity of 86.67 — and a wither's death blast is 14 x 7 + 1 = 99, the
	 * biggest number vanilla can hand this pipeline. Without the gate, a wither
	 * dying next to you would cost four limbs at stock settings.
	 */
	@Test
	void theShareGateIsLoadBearingAtStockSettings() {
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_ARM,
				ExplosionMath.limbTrauma(99.0, 0.05, 0.0, 1.0)),
				"6.68 from a wither's remainder share, against an arm bar of 5.85");
		// A charged creeper or end crystal (85) is 5.74 and just under it...
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_ARM,
				ExplosionMath.limbTrauma(85.0, 0.05, 0.0, 1.0)));
		// ...until 5.85 / 5.7375 = 1.0196 of bodyDamageMultiplier.
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_ARM,
				ExplosionMath.limbTrauma(85.0, 0.05, 0.0, 1.02)),
				"one end crystal must not cost four limbs on a server two percent above default");
	}

	/** A server that softens PvP softens what player-lit TNT is allowed to mean. */
	@Test
	void pvpMultiplierScalesTheBlast() {
		// A bare leg against point-blank TNT is 38.475 at full strength, so the
		// 7.2 bar is crossed at 7.2 / 38.475 = 0.187 of the PvP multiplier.
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				ExplosionMath.limbTrauma(57.0, Balance.EXPLOSION_PRIMARY_SHARE, 0.0, 0.18)));
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				ExplosionMath.limbTrauma(57.0, Balance.EXPLOSION_PRIMARY_SHARE, 0.0, 0.20)));
	}

	/**
	 * The unarmoured end of the same range, and the row of the table that reads as
	 * a failure and is not one. A bare leg goes out to 3.943 blocks from a creeper,
	 * but a bare player at ten hearts on Normal is only standing from 4.06 — so at
	 * half health with nothing on, every creeper close enough to take your leg
	 * kills you instead. That is the honest answer: armour is what buys you the
	 * right to be maimed rather than killed.
	 */
	@Test
	void theBareSurvivorsWindowExistsAtFullHealthAndNotAtHalf() {
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				primary(ExplosionMath.intensity(3.0, 3.9, 1.0), 0.0)), "7.37 at 3.9 blocks");
		assertFalse(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				primary(ExplosionMath.intensity(3.0, 4.0, 1.0), 0.0)), "6.98 at 4.0 — past the far edge");

		// A bare player takes the blast whole: 20 armour of nothing, no protection.
		double bareHpAtTheFarEdge = hpLostOnNormal(ExplosionMath.intensity(3.0, 3.9, 1.0), 0.0, 0.0, 0.0);
		assertTrue(bareHpAtTheFarEdge < 20.0,
				"at full health there is a window: 10.92 of twenty hearts at the far edge");
		assertTrue(bareHpAtTheFarEdge >= 10.0,
				"and at ten hearts there is none, which is the rule working: " + bareHpAtTheFarEdge);
	}

	/**
	 * Where the armour ladder actually sits now. At the 0.60 cap a leg needs an
	 * intensity of 7.2 / 0.27 = 26.67; a creeper peaks at 43 and a charged creeper
	 * at 85, so the top of the ladder shortens a creeper's reach rather than ending
	 * it. The old assertion here — that the cap needed 44.44, which a creeper's 43
	 * never reached — was the arithmetic that made this feature netherite-only.
	 */
	@Test
	void theArmourCapShortensACreepersReachRatherThanEndingIt() {
		double needed = Balance.EXPLOSIVE_DESTROY_LEG
				/ (Balance.TRAUMA_EXPLOSION * Balance.EXPLOSION_PRIMARY_SHARE
						* (1.0 - Balance.ARMOR_REDUCTION_CAP));
		assertEquals(26.666666, needed, 1e-6);
		assertTrue(needed < ExplosionMath.intensity(3.0, 0.0, 1.0),
				"which a creeper's 43 now clears");
		assertTrue(needed > ExplosionMath.intensity(3.0, 2.0, 1.0),
				"but only close in — 24.33 at two blocks does not");
		assertTrue(ExplosionMath.dismembers(BodyPart.LEFT_LEG,
				primary(ExplosionMath.intensity(6.0, 0.0, 1.0), Balance.ARMOR_REDUCTION_CAP)),
				"and an end crystal at point blank is worth 22.95 through the cap");
	}
}
