package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * 2.0: what the ragdoll had to learn so a dead body could still be hurt. It
 * reports how hard each part strikes the world, and a limb can leave it, or
 * break, in the middle of a fall.
 */
class CorpseLimbLossTest {
	private static final double DT = 0.05;

	private static HumanoidRagdollTest.FakeWorld floor() {
		return new HumanoidRagdollTest.FakeWorld().blockFloor(8, 0.0);
	}

	@Test
	void aFallingBodyReportsItsLandingAndALyingOneReportsNothing() {
		HumanoidRagdoll r = HumanoidRagdollTest.standing(0.5, 10.0, 0.5, 0.0F);
		r.setMuscles(Muscles.forDeath(Muscles.Input.healthy(DeathKind.BLOW, 0.5, -1)));
		HumanoidRagdollTest.FakeWorld world = floor();
		double hardest = 0.0;
		int landedAt = -1;
		for (int t = 0; t < 60; t++) {
			r.tick(world, DT);
			for (int leg : new int[] {HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG}) {
				if (r.strike(leg) > hardest) {
					hardest = r.strike(leg);
					landedAt = t;
				}
			}
			if (landedAt < 0) {
				assertEquals(0.0, r.impact(HumanoidRagdoll.TORSO), 0.0, "nothing is struck in the air");
			}
		}
		// Ten blocks under gravity of 32 with a little drag: a little over 22 blocks a second.
		assertTrue(hardest > 20.0 && hardest < 25.0, "the legs land at the speed of a ten-block fall, struck at " + hardest);
		assertTrue(landedAt >= 14 && landedAt <= 17, "after about three quarters of a second, at tick " + landedAt);
		// Once it lies still nothing reads as a strike, part by part.
		for (int t = 0; t < 200 && !r.asleep(); t++) {
			r.tick(world, DT);
		}
		assertTrue(r.asleep(), "it comes to rest");
		r.tick(world, DT);
		for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
			assertEquals(0.0, r.strike(i), 0.0, "part " + i + " of a body at rest");
		}
	}

	@Test
	void aFlailingLimbIsNeverJudgedFasterThanTheBodyArrived() {
		// Just above the ground with only its legs flung down at it: the legs
		// strike at their own speed, but they are a third of the body, and the
		// body as a whole was arriving at a third of that.
		HumanoidRagdoll r = HumanoidRagdollTest.standing(0.5, 0.05, 0.5, 0.0F);
		r.setMuscles(Muscles.forDeath(Muscles.Input.healthy(DeathKind.BLOW, 1.0, -1)));
		r.kick(HumanoidRagdoll.LEFT_LEG, 0.0, -40.0, 0.0);
		r.kick(HumanoidRagdoll.RIGHT_LEG, 0.0, -40.0, 0.0);
		HumanoidRagdollTest.FakeWorld world = floor();
		double own = 0.0;
		double judged = 0.0;
		for (int t = 0; t < 10; t++) {
			r.tick(world, DT);
			own = Math.max(own, r.impact(HumanoidRagdoll.LEFT_LEG));
			judged = Math.max(judged, r.strike(HumanoidRagdoll.LEFT_LEG));
			assertTrue(r.strike(HumanoidRagdoll.LEFT_LEG) <= r.impact(HumanoidRagdoll.LEFT_LEG) + 1.0e-9);
		}
		assertTrue(own > 25.0, "the leg itself does hit the floor hard, at " + own);
		assertTrue(judged < 16.0, "but the body arrived at a third of that, so it is judged at " + judged);
	}

	@Test
	void slidingOverTheSeamsOfAFlatFloorIsNotStrikingAnything() {
		// Lying on a floor of separate blocks and sent skidding: every block's
		// edge is a seam the solver nudges the body over, and none is a wall.
		HumanoidRagdoll r = HumanoidRagdollTest.standing(0.5, 0.0, 0.5, 0.0F);
		r.setMuscles(Muscles.forDeath(Muscles.Input.healthy(DeathKind.BLOW, 1.0, -1)));
		HumanoidRagdollTest.FakeWorld world = new HumanoidRagdollTest.FakeWorld().blockFloor(40, 0.0);
		HumanoidRagdollTest.run(r, world, 60);
		r.wake();
		r.push(28.0, 0.0, 0.0);
		double hardest = 0.0;
		for (int t = 0; t < 30; t++) {
			r.tick(world, DT);
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				hardest = Math.max(hardest, r.impact(i));
			}
		}
		assertTrue(hardest < 16.0, "a body skidding across flat ground read a strike of " + hardest);
	}

	@Test
	void aLimbLeavesTheFallAndTheRestGoesOn() {
		HumanoidRagdoll r = HumanoidRagdollTest.standing(0.5, 6.0, 0.5, 0.0F);
		r.setMuscles(Muscles.standard());
		HumanoidRagdollTest.FakeWorld world = floor();
		HumanoidRagdollTest.run(r, world, 5);
		assertTrue(r.present(HumanoidRagdoll.LEFT_ARM));
		assertTrue(r.detach(HumanoidRagdoll.LEFT_ARM), "the arm comes off");
		assertFalse(r.present(HumanoidRagdoll.LEFT_ARM));
		assertFalse(r.detach(HumanoidRagdoll.LEFT_ARM), "and cannot come off twice");
		assertFalse(r.detach(HumanoidRagdoll.TORSO), "the torso is the body: it cannot be taken");
		assertNull(r.jointOf(HumanoidRagdoll.LEFT_ARM), "its joint no longer exists for the reflexes");
		assertFalse(r.usable(HumanoidRagdoll.LEFT_ARM));

		// It rides on the torso from here: the same place in the torso's frame, tick after tick.
		Body torso = r.body(HumanoidRagdoll.TORSO);
		Body arm = r.body(HumanoidRagdoll.LEFT_ARM);
		Vector3d rode = torso.q.conjugate(new org.joml.Quaterniond()).transform(new Vector3d(arm.x).sub(torso.x));
		for (int t = 0; t < 120; t++) {
			r.tick(world, DT);
			Vector3d now = torso.q.conjugate(new org.joml.Quaterniond()).transform(new Vector3d(arm.x).sub(torso.x));
			assertEquals(0.0, now.distance(rode), 1.0e-6, "the lost arm rides where it was, tick " + t);
			assertEquals(0.0, r.strike(HumanoidRagdoll.LEFT_ARM), 0.0, "and strikes nothing");
		}
		// The rest of the body lands, holds together and comes to rest as it always did.
		assertFalse(r.broken());
		assertTrue(r.asleep(), "the body comes to rest without it");
		assertTrue(r.worstSeparation() < 0.05, "no joint that is left has come apart: " + r.worstSeparation());
		assertTrue(world.maxPenetration(present(r)) < 0.08, "and nothing that is left is in the floor");
	}

	/** The same ragdoll, for the penetration check: it reads every part, and a lost one is wherever the torso carries it. */
	private static HumanoidRagdoll present(HumanoidRagdoll r) {
		return r;
	}

	@Test
	void takingALimbOffWakesABodyThatWasResting() {
		HumanoidRagdoll r = HumanoidRagdollTest.standing(0.5, 0.0, 0.5, 0.0F);
		r.setMuscles(Muscles.forDeath(Muscles.Input.healthy(DeathKind.BLOW, 1.0, -1)));
		HumanoidRagdollTest.FakeWorld world = floor();
		for (int t = 0; t < 300 && !r.asleep(); t++) {
			r.tick(world, DT);
		}
		assertTrue(r.asleep());
		r.detach(HumanoidRagdoll.RIGHT_LEG);
		assertFalse(r.asleep(), "what was lying on that leg has to settle again");
	}

	@Test
	void aBoneBrokenMidFallGoesSlackLikeOneBrokenBefore() {
		HumanoidRagdoll r = HumanoidRagdollTest.standing(0.5, 6.0, 0.5, 0.0F);
		Muscles sound = Muscles.forDeath(Muscles.Input.healthy(DeathKind.BLOW, 0.2, -1));
		r.setMuscles(sound);
		assertEquals(1.0, r.muscles().looseness[HumanoidRagdoll.RIGHT_LEG], 0.0);
		double limitBefore = r.jointOf(HumanoidRagdoll.RIGHT_LEG).swingXMax;
		r.breakLimb(HumanoidRagdoll.RIGHT_LEG);
		assertEquals(0.0, r.muscles().limb[HumanoidRagdoll.RIGHT_LEG], 0.0, "nothing holds it");
		assertEquals(Muscles.BROKEN_LIMB_LOOSENESS, r.muscles().looseness[HumanoidRagdoll.RIGHT_LEG], 0.0);
		assertEquals(limitBefore * Muscles.BROKEN_LIMB_LOOSENESS, r.jointOf(HumanoidRagdoll.RIGHT_LEG).swingXMax, 1.0e-9,
				"and it bends further than it should");
		// The other limbs are as they were, and breaking it twice loosens it once.
		assertEquals(1.0, r.muscles().limb[HumanoidRagdoll.LEFT_LEG], 0.0);
		r.breakLimb(HumanoidRagdoll.RIGHT_LEG);
		assertEquals(limitBefore * Muscles.BROKEN_LIMB_LOOSENESS, r.jointOf(HumanoidRagdoll.RIGHT_LEG).swingXMax, 1.0e-9);
		// The same as a limb that was broken before the death.
		boolean[] broken = new boolean[HumanoidRagdoll.PARTS];
		broken[HumanoidRagdoll.RIGHT_LEG] = true;
		Muscles before = Muscles.forDeath(new Muscles.Input(DeathKind.BLOW, 0.2, -1, 100.0, false, broken,
				new boolean[HumanoidRagdoll.PARTS], 0, false, false));
		assertEquals(before.limbFloor[HumanoidRagdoll.RIGHT_LEG], r.muscles().limbFloor[HumanoidRagdoll.RIGHT_LEG], 0.0);
		assertEquals(before.looseness[HumanoidRagdoll.RIGHT_LEG], r.muscles().looseness[HumanoidRagdoll.RIGHT_LEG], 0.0);
		// A head has a neck, not a bone this rule breaks.
		r.breakLimb(HumanoidRagdoll.HEAD);
		assertEquals(1.0, r.muscles().looseness[HumanoidRagdoll.HEAD], 0.0);
	}

	@Test
	void theServersCopyIsTheSameFallEveryTime() {
		// No RNG: two copies of one death are the same to the last bit.
		double[] a = fall();
		double[] b = fall();
		for (int i = 0; i < a.length; i++) {
			assertEquals(a[i], b[i], 0.0, "value " + i);
		}
	}

	private static double[] fall() {
		dev.captience.realisticdamage.net.CorpsePayload payload = new dev.captience.realisticdamage.net.CorpsePayload(
				7, DeathKind.PROJECTILE, 0.5F, 0.05F, -1.5F, 0.0F, 0, 0.0F, 0.0F, 1.0F, 0.2F);
		HumanoidRagdoll r = CorpseLaunch.serverBody(new Vector3d(0.5, 12.0, 0.5), 30.0F, 1.8, payload, null,
				new Vector3d(1.0, -30.0, 0.0), 7);
		HumanoidRagdollTest.FakeWorld world = floor();
		double[] out = new double[60 * HumanoidRagdoll.PARTS + 3];
		for (int t = 0; t < 60; t++) {
			r.tick(world, DT);
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				out[t * HumanoidRagdoll.PARTS + i] = r.strike(i);
			}
		}
		Vector3d c = r.centre();
		out[out.length - 3] = c.x;
		out[out.length - 2] = c.y;
		out[out.length - 1] = c.z;
		return out;
	}
}
