package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.physics.Joint;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Phase 2: the muscles a body dies with, and what they do to its fall. The
 * table first, then the physics: each difference here is one a person
 * watching would see.
 */
class MusclesTest {
	private static final double DT = 0.05;
	private static final double UNIT = 0.9375 / 16.0;

	private static Muscles.Input input(DeathKind kind, double violence, int hit) {
		return Muscles.Input.healthy(kind, violence, hit);
	}

	@Test
	void aHeadshotCutsTheStrings() {
		Muscles m = Muscles.forDeath(input(DeathKind.PROJECTILE, 0.4, HumanoidRagdoll.HEAD));
		assertTrue(m.stringsCut);
		for (int p : new int[] {HumanoidRagdoll.HEAD, HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.RIGHT_LEG}) {
			assertEquals(m.floor, m.tone(p, 0.0), 1.0e-9, "no muscle at all in part " + p);
		}
		// A shot to the chest is not that: it crumples, but it holds a moment.
		Muscles chest = Muscles.forDeath(input(DeathKind.PROJECTILE, 0.4, HumanoidRagdoll.TORSO));
		assertFalse(chest.stringsCut);
		assertTrue(chest.tone(HumanoidRagdoll.RIGHT_LEG, 0.0) > 1.5);
	}

	@Test
	void anUnconsciousBodyHasNothingLeftToHoldWith() {
		Muscles.Input out = new Muscles.Input(DeathKind.BLOW, 0.3, -1, 0.0, true,
				new boolean[6], new boolean[6], 0, false, false);
		assertTrue(Muscles.forDeath(out).stringsCut);
		Muscles.Input dim = new Muscles.Input(DeathKind.BLOW, 0.3, -1, 40.0, false,
				new boolean[6], new boolean[6], 0, false, false);
		Muscles half = Muscles.forDeath(dim);
		Muscles full = Muscles.forDeath(input(DeathKind.BLOW, 0.3, -1));
		assertEquals(full.tone(HumanoidRagdoll.LEFT_LEG, 0.0) - full.floor,
				(half.tone(HumanoidRagdoll.LEFT_LEG, 0.0) - half.floor) / 0.4, 1.0e-9);
	}

	@Test
	void aFadingBodyLetsGoLegsFirstAndHeadLast() {
		Muscles m = Muscles.forDeath(input(DeathKind.FADE, 0.0, -1));
		double t = 0.6;
		double legs = m.tone(HumanoidRagdoll.RIGHT_LEG, t) / m.tone(HumanoidRagdoll.RIGHT_LEG, 0.0);
		double arms = m.tone(HumanoidRagdoll.RIGHT_ARM, t) / m.tone(HumanoidRagdoll.RIGHT_ARM, 0.0);
		double neck = m.tone(HumanoidRagdoll.HEAD, t) / m.tone(HumanoidRagdoll.HEAD, 0.0);
		assertTrue(legs < arms && arms < neck, "legs " + legs + " arms " + arms + " neck " + neck);
		// And it is all gone within three seconds.
		assertTrue(m.tone(HumanoidRagdoll.HEAD, 3.0) < m.floor + 0.2);
	}

	@Test
	void injuriesAndAdrenalineChangeTheMuscles() {
		boolean[] broken = new boolean[6];
		broken[HumanoidRagdoll.LEFT_ARM] = true;
		boolean[] limp = new boolean[6];
		limp[HumanoidRagdoll.RIGHT_ARM] = true;
		Muscles hurt = Muscles.forDeath(new Muscles.Input(DeathKind.BLOW, 0.3, -1, 100.0, false, broken, limp, 2,
				false, false));
		Muscles sound = Muscles.forDeath(input(DeathKind.BLOW, 0.3, -1));
		assertTrue(hurt.tone(HumanoidRagdoll.LEFT_ARM, 0.0) < sound.floor, "a broken arm keeps muscle");
		assertTrue(hurt.looseness[HumanoidRagdoll.LEFT_ARM] > 1.0, "a broken arm is no looser");
		assertEquals(hurt.floor, hurt.tone(HumanoidRagdoll.RIGHT_ARM, 0.0) / 0.7, 1.0e-9);
		assertTrue(hurt.tone(HumanoidRagdoll.HEAD, 0.0) < sound.tone(HumanoidRagdoll.HEAD, 0.0), "concussion");
		Muscles surge = Muscles.forDeath(new Muscles.Input(DeathKind.BLOW, 0.3, -1, 100.0, false, new boolean[6],
				new boolean[6], 0, true, false));
		assertTrue(surge.tone(HumanoidRagdoll.LEFT_LEG, 0.4) > sound.tone(HumanoidRagdoll.LEFT_LEG, 0.4),
				"adrenaline does not hold on longer");
	}

	// --- What the muscles do to a fall -------------------------------------------

	private static HumanoidRagdoll body(Muscles m) {
		HumanoidRagdoll r = new HumanoidRagdoll(HumanoidRagdoll.playerParts(false), new Vector3d(0.5, 0.0, 0.5), 0.0F,
				UNIT, new float[18]);
		r.setMuscles(m);
		return r;
	}

	/** The worst hip fold (forward swing of either leg), radians, over a fall. */
	private static double worstFold(HumanoidRagdoll r, HumanoidRagdollTest.FakeWorld world, int ticks) {
		double worst = 0.0;
		Vector3d swing = new Vector3d();
		double[] twist = new double[1];
		for (int t = 0; t < ticks; t++) {
			r.tick(world, DT);
			Joint[] js = r.joints();
			for (int i = 0; i < js.length; i++) {
				int part = r.jointPart(i);
				if (part == HumanoidRagdoll.RIGHT_LEG || part == HumanoidRagdoll.LEFT_LEG) {
					Joint.decompose(js[i].relative(new Quaterniond()), swing, twist);
					worst = Math.max(worst, -swing.x);
				}
			}
		}
		return worst;
	}

	@Test
	void aBodyWithTheStringsCutFoldsAtOnceWhereAHeldOneGoesOverWholeFirst() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(6, 0.0);
		HumanoidRagdoll cut = body(Muscles.forDeath(input(DeathKind.PROJECTILE, 0.5, HumanoidRagdoll.HEAD)));
		HumanoidRagdoll held = body(Muscles.forDeath(input(DeathKind.BLOW, 0.2, HumanoidRagdoll.TORSO)));
		// The same small nudge forward for both.
		cut.spin(new Vector3d(0.35, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		held.spin(new Vector3d(0.35, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		// The first 0.4 s: one has already folded in half, the other is still a
		// plank going over. (Both end up folded once they hit the ground; that
		// is a body landing, and not the difference.)
		double cutFold = worstFold(cut, world, 8);
		double heldFold = worstFold(held, world, 8);
		System.out.println("[rd] hip fold in 0.4 s: strings cut " + cutFold + ", held " + heldFold);
		assertTrue(cutFold > heldFold + 0.5, "cut " + cutFold + " held " + heldFold);
		worstFold(cut, world, 60);
		worstFold(held, world, 60);
		assertTrue(cut.lean() > 45.0 && held.lean() > 45.0, "one of them is still up");
	}

	@Test
	void aStringsCutBodyIsDownBeforeAFadingOne() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(6, 0.0);
		HumanoidRagdoll cut = body(Muscles.forDeath(input(DeathKind.CRUSH, 0.5, -1)));
		HumanoidRagdoll fading = body(Muscles.forDeath(input(DeathKind.FADE, 0.0, -1)));
		cut.spin(new Vector3d(0.35, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		fading.spin(new Vector3d(0.5, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		int cutDown = -1;
		int fadeDown = -1;
		for (int t = 0; t < 120 && (cutDown < 0 || fadeDown < 0); t++) {
			cut.tick(world, DT);
			fading.tick(world, DT);
			// "Down": the neck below hip height of a standing body.
			if (cutDown < 0 && cut.pivot(HumanoidRagdoll.HEAD, 1.0F, new Vector3d()).y < 0.7) {
				cutDown = t;
			}
			if (fadeDown < 0 && fading.pivot(HumanoidRagdoll.HEAD, 1.0F, new Vector3d()).y < 0.7) {
				fadeDown = t;
			}
		}
		System.out.println("[rd] down at tick: strings cut " + cutDown + ", fading " + fadeDown);
		assertTrue(cutDown >= 0 && fadeDown >= 0, "one never went down: " + cutDown + ", " + fadeDown);
		assertTrue(cutDown < fadeDown, "cut " + cutDown + " fading " + fadeDown);
	}

	@Test
	void aBrokenArmSwingsFreerThanASoundOne() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(20, 0.0);
		boolean[] broken = new boolean[6];
		broken[HumanoidRagdoll.LEFT_ARM] = true;
		HumanoidRagdoll r = body(Muscles.forDeath(new Muscles.Input(DeathKind.BLOW, 0.3, -1, 100.0, false, broken,
				new boolean[6], 0, false, false)));
		// Thrown and spun about its long axis, so both arms are flung outward alike.
		r.push(0.0, 9.0, 0.0);
		r.spin(new Vector3d(0.0, 9.0, 0.0), new Vector3d(r.centre()));
		double worstSound = 0.0;
		double worstBroken = 0.0;
		Vector3d swing = new Vector3d();
		double[] twist = new double[1];
		for (int t = 0; t < 14; t++) {
			r.tick(world, DT);
			Joint[] js = r.joints();
			for (int i = 0; i < js.length; i++) {
				Joint.decompose(js[i].relative(new Quaterniond()), swing, twist);
				double angle = swing.length();
				if (r.jointPart(i) == HumanoidRagdoll.LEFT_ARM) {
					worstBroken = Math.max(worstBroken, angle);
				} else if (r.jointPart(i) == HumanoidRagdoll.RIGHT_ARM) {
					worstSound = Math.max(worstSound, angle);
				}
			}
		}
		System.out.println("[rd] arm swing: broken " + worstBroken + ", sound " + worstSound);
		assertTrue(worstBroken > worstSound + 0.3, "broken " + worstBroken + " sound " + worstSound);
	}

	@Test
	void aFrozenBodyGoesOverStraight() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(6, 0.0);
		HumanoidRagdoll r = body(Muscles.forDeath(input(DeathKind.FREEZE, 0.3, -1)));
		r.spin(new Vector3d(1.4, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		double worst = 0.0;
		for (int t = 0; t < 80; t++) {
			r.tick(world, DT);
			worst = Math.max(worst, r.spread());
		}
		System.out.println("[rd] frozen spread " + worst + ", lean " + r.lean());
		assertTrue(r.lean() > 70.0, "a frozen body stayed up: " + r.lean());
		// Summed over five joints: under ten degrees each, from the landing.
		assertTrue(worst < 0.8, "a frozen body bent: " + worst);
	}
}
