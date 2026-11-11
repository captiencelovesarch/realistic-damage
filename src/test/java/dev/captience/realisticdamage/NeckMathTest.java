package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import dev.captience.realisticdamage.ragdoll.Muscles;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.systems.NeckMath;
import org.junit.jupiter.api.Test;

/** Broken necks and whiplash (2.0): the bars, the table and the curve. */
class NeckMathTest {
	@Test
	void eachCauseBreaksAtItsBarAndWrenchesUnderIt() {
		// Head first: nine blocks over the safe distance snaps it, three wrenches it.
		assertTrue(NeckMath.breaks(NeckMath.limpLanding(Balance.NECK_LIMP_HEIGHT)));
		assertFalse(NeckMath.breaks(NeckMath.limpLanding(Balance.NECK_LIMP_HEIGHT - 0.01)));
		assertTrue(NeckMath.wrenches(NeckMath.limpLanding(Balance.NECK_LIMP_HEIGHT * 0.5)));
		assertFalse(NeckMath.wrenches(NeckMath.limpLanding(1.0)), "a short limp drop is nothing");
		// Into a wall.
		assertTrue(NeckMath.breaks(NeckMath.wallImpact(Balance.NECK_WALL_DAMAGE)));
		assertTrue(NeckMath.wrenches(NeckMath.wallImpact(6.0)));
		assertFalse(NeckMath.wrenches(NeckMath.wallImpact(2.0)));
		// A heavy blunt blow to the head snaps it; a plain one never can.
		assertTrue(NeckMath.breaks(NeckMath.headBlow(DamageCategory.HEAVY_BLUNT, Balance.NECK_BLUNT_TRAUMA)));
		assertFalse(NeckMath.breaks(NeckMath.headBlow(DamageCategory.BLUNT, 1000.0)));
		assertTrue(NeckMath.wrenches(NeckMath.headBlow(DamageCategory.BLUNT, 1000.0)));
		// An edge or a point does not wrench a neck at all.
		assertEquals(0.0, NeckMath.headBlow(DamageCategory.SLASH, 1000.0));
		assertEquals(0.0, NeckMath.headBlow(DamageCategory.PUNCTURE, 1000.0));
		assertEquals(0.0, NeckMath.headBlow(null, 1000.0));
	}

	@Test
	void feetFirstAndKnockdownsOnlyEverWrench() {
		for (double h = 0.0; h <= 200.0; h += 0.5) {
			assertFalse(NeckMath.breaks(NeckMath.uprightLanding(h)), "upright at " + h);
		}
		assertEquals(0.0, NeckMath.uprightLanding(Balance.KNOCKDOWN_MIN_HEIGHT), "no knockdown, no jolt");
		assertTrue(NeckMath.wrenches(NeckMath.uprightLanding(Balance.KNOCKDOWN_FULL_HEIGHT)),
				"a landing that knocks you flat hard jars the neck");
		for (double v = 0.0; v <= 1.0; v += 0.01) {
			assertFalse(NeckMath.breaks(NeckMath.knockdown(v)), "knockdown at " + v);
		}
		assertEquals(0.0, NeckMath.knockdown(Balance.WHIPLASH_KNOCKDOWN_VIOLENCE - 0.01));
		assertTrue(NeckMath.wrenches(NeckMath.knockdown(1.0)));
	}

	@Test
	void whiplashLastsThirtyToNinetySecondsAndStiffensWithSeverity() {
		assertEquals(600, NeckMath.whiplashTicks(0.0));
		assertEquals(1800, NeckMath.whiplashTicks(1.0));
		assertEquals(0.0, NeckMath.severity(Balance.WHIPLASH_MIN_STRAIN));
		assertEquals(1.0, NeckMath.severity(1.0));
		double last = -1.0;
		for (double s = 0.0; s <= 1.0; s += 0.05) {
			double start = NeckMath.startingStiffness(s);
			assertTrue(start > last);
			last = start;
		}
		assertEquals(Balance.WHIPLASH_STIFFNESS_MAX, NeckMath.startingStiffness(1.0));
	}

	@Test
	void stiffnessEasesEvenlyToNothing() {
		assertEquals(0.8, NeckMath.stiffness(0.8, 1000, 1000), 1.0e-9);
		assertEquals(0.4, NeckMath.stiffness(0.8, 500, 1000), 1.0e-9);
		assertEquals(0.0, NeckMath.stiffness(0.8, 0, 1000));
		assertEquals(0.0, NeckMath.stiffness(0.8, 10, 0));
	}

	@Test
	void theTurnCapTightensSmoothlyAndIsGoneWithASoundNeck() {
		assertEquals(Double.POSITIVE_INFINITY, NeckMath.turnCap(0.0));
		assertEquals(Balance.WHIPLASH_TURN_WORST, NeckMath.turnCap(1.0), 1.0e-9);
		double last = Double.POSITIVE_INFINITY;
		for (double s = 0.01; s <= 1.0; s += 0.01) {
			double cap = NeckMath.turnCap(s);
			assertTrue(cap < last, "the cap tightens as the neck stiffens");
			assertTrue(cap <= Balance.WHIPLASH_TURN_MILD && cap >= Balance.WHIPLASH_TURN_WORST);
			last = cap;
		}
		// A quarter turn at the worst takes over a second.
		assertTrue(90.0 / NeckMath.turnCap(1.0) > 1.0);
		assertEquals(1.0, NeckMath.mobTurnFactor(0.0));
		assertTrue(NeckMath.mobTurnFactor(1.0) < 0.2);
		assertEquals(1.0, NeckMath.mobAimSpread(0.0));
		assertEquals(Balance.MOB_WHIPLASH_AIM_SPREAD, NeckMath.mobAimSpread(1.0), 1.0e-9);
	}

	@Test
	void theNecksGiveIsResistanceNotAWall() {
		// Small turns pass almost whole, big ones saturate at the budget, and the
		// curve never gives more than asked or more than there is.
		assertTrue(NeckMath.give(0.5, 10.0) > 0.49);
		assertTrue(NeckMath.give(1000.0, 10.0) <= 10.0);
		assertTrue(NeckMath.give(1000.0, 10.0) > 9.99);
		double last = 0.0;
		for (double asked = 0.1; asked < 50.0; asked += 0.1) {
			double given = NeckMath.give(asked, 10.0);
			assertTrue(given <= asked + 1.0e-12);
			assertTrue(given > last, "more asked is always more given");
			last = given;
		}
		assertEquals(0.0, NeckMath.give(5.0, 0.0));
		assertEquals(0.0, NeckMath.give(0.0, 5.0));
	}

	@Test
	void onlyWaterOneBlockDeepFailsToStopAFall() {
		assertFalse(NeckMath.shallow(0));
		assertTrue(NeckMath.shallow(1));
		assertFalse(NeckMath.shallow(2));
	}

	@Test
	void theWorseWrenchWinsAndItHealsWithTime() {
		RealisticDamageData data = new RealisticDamageData();
		assertTrue(data.wrench(0.2));
		double mild = data.whiplash();
		assertFalse(data.wrench(0.1), "a milder jolt changes nothing");
		assertEquals(mild, data.whiplash());
		assertTrue(data.wrench(0.9));
		double bad = data.whiplash();
		assertTrue(bad > mild);
		int ticks = data.whiplashTicks();
		for (int i = 0; i < ticks / 2; i++) {
			data.tickWhiplash();
		}
		assertTrue(data.whiplash() < bad && data.whiplash() > 0.0);
		for (int i = 0; i < ticks; i++) {
			data.tickWhiplash();
		}
		assertFalse(data.whiplashed());
		assertEquals(0.0, data.whiplash());
	}

	@Test
	void aSnappedNeckHangsLooseAndChangesNothingElse() {
		Muscles plain = Muscles.forDeath(Muscles.Input.healthy(DeathKind.BLOW, 0.3, -1));
		Muscles snapped = plain.withBrokenNeck();
		int head = HumanoidRagdoll.HEAD;
		assertEquals(0.0, snapped.start[Muscles.NECK]);
		assertEquals(0.0, snapped.limb[head]);
		assertEquals(Muscles.BROKEN_NECK_FLOOR, snapped.limbFloor[head]);
		assertTrue(snapped.looseness[head] > 1.5);
		assertTrue(snapped.tone(head, 0.0) < 0.01, "nothing holds the head, from the first moment");
		for (int p = 0; p < HumanoidRagdoll.PARTS; p++) {
			if (p != head) {
				assertEquals(plain.limb[p], snapped.limb[p]);
				assertEquals(plain.looseness[p], snapped.looseness[p]);
				if (Muscles.group(p) >= 0) {
					assertEquals(plain.tone(p, 0.3), snapped.tone(p, 0.3), 1.0e-12);
				}
			}
		}
		// And the original is untouched.
		assertEquals(1.0, plain.looseness[head]);
	}
}
