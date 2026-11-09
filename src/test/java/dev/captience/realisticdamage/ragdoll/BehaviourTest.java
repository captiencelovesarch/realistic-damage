package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.Behaviour.Reaction;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import dev.captience.realisticdamage.ragdoll.physics.BoxList;
import dev.captience.realisticdamage.ragdoll.physics.Joint;
import java.util.EnumSet;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Phase 3: what a dying body still does. The table of reactions first, then
 * each reflex in the fake world, measured against the same death without it.
 */
class BehaviourTest {
	private static final double DT = 0.05;
	private static final double UNIT = 0.9375 / 16.0;

	private static Behaviour choose(DeathKind kind, double violence, int hit, int wound, Muscles m, Vector3d push) {
		return Behaviour.choose(new Behaviour.Input(kind, violence, hit, wound, m, push, 7));
	}

	private static Muscles healthy(DeathKind kind, double violence, int hit) {
		return Muscles.forDeath(Muscles.Input.healthy(kind, violence, hit));
	}

	@Test
	void theTable() {
		Vector3d back = new Vector3d(0.0, 0.0, 1.0);
		// Shot in the head: nothing but the twitch afterwards.
		Behaviour headshot = choose(DeathKind.PROJECTILE, 0.5, HumanoidRagdoll.HEAD, -1,
				healthy(DeathKind.PROJECTILE, 0.5, HumanoidRagdoll.HEAD), back);
		assertEquals(EnumSet.of(Reaction.TWITCH), headshot.reactions());
		// A sword in the gut, still standing on two good legs.
		Behaviour gut = choose(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO, -1,
				healthy(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO), back);
		assertEquals(EnumSet.of(Reaction.CLUTCH, Reaction.DOUBLE_OVER, Reaction.STAGGER, Reaction.BRACE), gut.reactions());
		assertEquals(HumanoidRagdoll.TORSO, gut.clutchPart());
		// The same blow on a broken leg: no stagger.
		boolean[] broken = new boolean[6];
		broken[HumanoidRagdoll.LEFT_LEG] = true;
		Muscles lame = Muscles.forDeath(new Muscles.Input(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO, 100.0, false,
				broken, new boolean[6], 0, false, false));
		assertFalse(choose(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO, -1, lame, back).has(Reaction.STAGGER));
		// A blast: arms up; a violent one twitches after.
		Behaviour blast = choose(DeathKind.EXPLOSION, 0.7, HumanoidRagdoll.LEFT_LEG, -1,
				healthy(DeathKind.EXPLOSION, 0.7, HumanoidRagdoll.LEFT_LEG), back);
		assertEquals(EnumSet.of(Reaction.PROTECT_HEAD, Reaction.TWITCH), blast.reactions());
		// A bleed-out holds its worst wound and does not brace.
		Behaviour bleed = choose(DeathKind.FADE, 0.1, -1, HumanoidRagdoll.RIGHT_LEG,
				healthy(DeathKind.FADE, 0.1, -1), new Vector3d());
		assertEquals(EnumSet.of(Reaction.CLUTCH), bleed.reactions());
		assertEquals(HumanoidRagdoll.RIGHT_LEG, bleed.clutchPart());
		assertTrue(choose(DeathKind.FIRE, 0.3, -1, -1, healthy(DeathKind.FIRE, 0.3, -1), back).has(Reaction.WRITHE));
		assertTrue(choose(DeathKind.SHOCK, 0.3, -1, -1, healthy(DeathKind.SHOCK, 0.3, -1), back).has(Reaction.CONVULSE));
		assertTrue(choose(DeathKind.DROWNING, 0.1, -1, -1, healthy(DeathKind.DROWNING, 0.1, -1), back)
				.has(Reaction.REACH_UP));
		assertTrue(choose(DeathKind.FREEZE, 0.9, -1, -1, healthy(DeathKind.FREEZE, 0.9, -1), back).reactions().isEmpty());
		// Already unconscious: nothing at all, however it died.
		Muscles out = Muscles.forDeath(new Muscles.Input(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO, 0.0, true,
				new boolean[6], new boolean[6], 0, false, false));
		assertTrue(choose(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO, -1, out, back).reactions().isEmpty());
	}

	// --- In the world -----------------------------------------------------------------

	private static HumanoidRagdoll body(Muscles m, Behaviour b, float yaw) {
		HumanoidRagdoll r = new HumanoidRagdoll(HumanoidRagdoll.playerParts(false), new Vector3d(0.5, 0.0, 0.5), yaw,
				UNIT, new float[18]);
		r.setMuscles(m);
		r.setBehaviour(b);
		return r;
	}

	/** Which part touches the ground first (its lowest corner within 3 cm of the floor at y=0). */
	private static int firstDown(HumanoidRagdoll r, HumanoidRagdollTest.FakeWorld world, int ticks) {
		for (int t = 0; t < ticks; t++) {
			r.tick(world, DT);
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				if (i == HumanoidRagdoll.RIGHT_LEG || i == HumanoidRagdoll.LEFT_LEG) {
					continue;
				}
				for (double[] p : HumanoidRagdollTest.surfacePoints(r.body(i))) {
					if (p[1] < 0.03) {
						return i;
					}
				}
			}
		}
		return -1;
	}

	@Test
	void aBodyGoingOverForwardLandsOnItsHands() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(8, 0.0);
		// Facing +Z (yaw 0); going over forward is a turn about +X.
		Muscles m = healthy(DeathKind.BLOW, 0.3, -1);
		Behaviour braced = choose(DeathKind.BLOW, 0.3, -1, -1, m, new Vector3d());
		HumanoidRagdoll withBrace = body(m, braced, 0.0F);
		HumanoidRagdoll without = body(healthy(DeathKind.BLOW, 0.3, -1), null, 0.0F);
		withBrace.spin(new Vector3d(1.4, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		without.spin(new Vector3d(1.4, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		int first = firstDown(withBrace, world, 60);
		int firstWithout = firstDown(without, world, 60);
		System.out.println("[rd] first down: braced " + first + ", unbraced " + firstWithout);
		assertTrue(first == HumanoidRagdoll.RIGHT_ARM || first == HumanoidRagdoll.LEFT_ARM,
				"a braced fall did not land on a hand first, but on part " + first);
		assertFalse(firstWithout == HumanoidRagdoll.RIGHT_ARM || firstWithout == HumanoidRagdoll.LEFT_ARM,
				"an unbraced fall landed on its hands anyway: the test proves nothing");
	}

	@Test
	void aHandGoesToTheGut() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(8, 0.0);
		Muscles m = healthy(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO);
		Behaviour b = choose(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO, -1, m, new Vector3d());
		HumanoidRagdoll clutching = body(m, b, 0.0F);
		HumanoidRagdoll plain = body(healthy(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO), null, 0.0F);
		double[] closest = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY};
		double across = 0.0;
		HumanoidRagdoll[] both = {clutching, plain};
		for (HumanoidRagdoll r : both) {
			r.spin(new Vector3d(0.5, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		}
		for (int t = 0; t < 16; t++) {
			for (int k = 0; k < 2; k++) {
				HumanoidRagdoll r = both[k];
				r.tick(world, DT);
				// The gut, on the torso's front: model (0, 8, -3.5) is torso-local (0, 2, -3.5) px from its centre.
				Body torso = r.body(HumanoidRagdoll.TORSO);
				Vector3d gut = torso.q.transform(new Vector3d(0.0, 2.0 * UNIT, -3.5 * UNIT)).add(torso.x);
				// A straight arm cannot put its hand on a point nearer than its own
				// length: it comes across the belly. How near the arm passes is the test.
				Vector3d tip = r.tip(HumanoidRagdoll.RIGHT_ARM);
				Vector3d shoulder = r.pivot(HumanoidRagdoll.RIGHT_ARM, 1.0F, new Vector3d());
				closest[k] = Math.min(closest[k], segmentDistance(gut, shoulder, tip));
				if (k == 0) {
					Vector3d sw = new Vector3d();
					double[] tw = new double[1];
					Joint.decompose(r.jointOf(HumanoidRagdoll.RIGHT_ARM).relative(new Quaterniond()), sw, tw);
					across = Math.min(across, sw.z);
				}
			}
		}
		System.out.println("[rd] right arm to gut, closest " + closest[0] + " (without reflexes " + closest[1]
				+ "), across " + across);
		assertTrue(across < -0.3, "the arm never came across the body: " + across);
		assertTrue(closest[0] < closest[1] - 0.06, "no nearer the gut than a limp arm: " + closest[0] + " vs " + closest[1]);
	}

	private static double segmentDistance(Vector3d p, Vector3d a, Vector3d b) {
		Vector3d ab = new Vector3d(b).sub(a);
		double t = Math.max(0.0, Math.min(1.0, new Vector3d(p).sub(a).dot(ab) / ab.lengthSquared()));
		return new Vector3d(a).fma(t, ab).distance(p);
	}

	@Test
	void theArmsComeUpAgainstABlast() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(30, 0.0);
		Muscles m = healthy(DeathKind.EXPLOSION, 0.5, HumanoidRagdoll.LEFT_LEG);
		Behaviour b = choose(DeathKind.EXPLOSION, 0.5, HumanoidRagdoll.LEFT_LEG, -1, m, new Vector3d(1.0, 0.0, 0.0));
		HumanoidRagdoll r = body(m, b, 0.0F);
		r.push(8.0, 9.0, 0.0);
		double highest = 0.0;
		Vector3d swing = new Vector3d();
		double[] twist = new double[1];
		for (int t = 0; t < 12; t++) {
			r.tick(world, DT);
			Joint j = r.jointOf(HumanoidRagdoll.RIGHT_ARM);
			Joint.decompose(j.relative(new Quaterniond()), swing, twist);
			highest = Math.max(highest, -swing.x);
		}
		System.out.println("[rd] blast arm raise " + highest);
		assertTrue(highest > 1.5, "the arms did not come up: " + highest);
	}

	/** How long the torso stays within 25 degrees of upright, seconds. */
	private static double upright(HumanoidRagdoll r, HumanoidRagdollTest.FakeWorld world) {
		for (int t = 0; t < 60; t++) {
			r.tick(world, DT);
			if (System.getenv("RD_TRACE") != null) {
				System.out.printf("[rd] stagger t%02d lean %.1f hold %.0f torso y %.2f legs y %.2f%n", t, r.lean(),
						r.sim.uprightStiffness, r.body(HumanoidRagdoll.TORSO).x.y, r.body(HumanoidRagdoll.RIGHT_LEG).x.y);
			}
			if (r.lean() > 25.0) {
				return t * DT;
			}
		}
		return 3.0;
	}

	@Test
	void aStruckBodyStaggersBeforeItFalls() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(12, 0.0);
		Vector3d back = new Vector3d(0.0, 0.0, -1.0);
		Muscles m = healthy(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO);
		Behaviour b = choose(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO, -1, m, back);
		assertTrue(b.has(Reaction.STAGGER));
		HumanoidRagdoll staggers = body(m, b, 0.0F);
		HumanoidRagdoll falls = body(healthy(DeathKind.BLOW, 0.3, HumanoidRagdoll.TORSO), null, 0.0F);
		for (HumanoidRagdoll r : new HumanoidRagdoll[] {staggers, falls}) {
			r.push(0.0, 0.0, -4.0);
			r.spin(new Vector3d(-0.6, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		}
		double held = upright(staggers, world);
		double not = upright(falls, world);
		System.out.println("[rd] upright: staggering " + held + " s, falling " + not + " s");
		assertTrue(held > not + 0.2, "staggering " + held + " falling " + not);
		assertTrue(held < 1.5, "a stagger held the body up for good: " + held);
	}

	@Test
	void aBurningBodyThrashesThenStops() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(8, 0.0);
		Muscles m = healthy(DeathKind.FIRE, 0.3, -1);
		HumanoidRagdoll writhes = body(m, choose(DeathKind.FIRE, 0.3, -1, -1, m, new Vector3d()), 0.0F);
		HumanoidRagdoll still = body(healthy(DeathKind.FIRE, 0.3, -1), null, 0.0F);
		for (HumanoidRagdoll r : new HumanoidRagdoll[] {writhes, still}) {
			r.spin(new Vector3d(1.0, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		}
		double movingAt2 = 0.0;
		double stillAt2 = 0.0;
		for (int t = 0; t < 220; t++) {
			writhes.tick(world, DT);
			still.tick(world, DT);
			if (t >= 36 && t <= 44) {
				movingAt2 = Math.max(movingAt2, writhes.sim.maxMotion());
				stillAt2 = Math.max(stillAt2, still.sim.maxMotion());
			}
		}
		System.out.println("[rd] motion at 2 s: writhing " + movingAt2 + ", plain " + stillAt2);
		assertTrue(movingAt2 > 0.5 && movingAt2 > stillAt2 * 3.0, "writhing " + movingAt2 + " plain " + stillAt2);
		assertTrue(writhes.asleep(), "it never stopped writhing");
	}

	@Test
	void aViolentDeathTwitchesAfterItSettles() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(8, 0.0);
		Muscles m = healthy(DeathKind.PROJECTILE, 0.6, HumanoidRagdoll.HEAD);
		HumanoidRagdoll r = body(m, choose(DeathKind.PROJECTILE, 0.6, HumanoidRagdoll.HEAD, -1, m, new Vector3d()), 0.0F);
		r.spin(new Vector3d(0.35, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		boolean settled = false;
		double afterSettling = 0.0;
		for (int t = 0; t < 240; t++) {
			r.tick(world, DT);
			if (!settled && t > 20 && r.sim.maxMotion() < 0.25) {
				settled = true;
			} else if (settled) {
				afterSettling = Math.max(afterSettling, r.sim.maxMotion());
			}
		}
		System.out.println("[rd] twitch motion after settling " + afterSettling);
		assertTrue(settled, "never settled");
		assertTrue(afterSettling > 1.0, "no twitch: " + afterSettling);
		assertTrue(r.asleep(), "it never settled after twitching");
	}

	@Test
	void reflexesAreDeterministic() {
		double[] first = null;
		for (int run = 0; run < 2; run++) {
			var world = new HumanoidRagdollTest.FakeWorld().blockFloor(10, 0.0);
			Muscles m = healthy(DeathKind.FIRE, 0.3, -1);
			HumanoidRagdoll r = body(m, choose(DeathKind.FIRE, 0.3, -1, -1, m, new Vector3d()), 30.0F);
			r.spin(new Vector3d(1.0, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
			for (int t = 0; t < 80; t++) {
				r.tick(world, DT);
			}
			double[] out = new double[HumanoidRagdoll.PARTS * 3];
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				out[i * 3] = r.body(i).x.x;
				out[i * 3 + 1] = r.body(i).x.y;
				out[i * 3 + 2] = r.body(i).x.z;
			}
			if (first == null) {
				first = out;
			} else {
				for (int i = 0; i < out.length; i++) {
					assertEquals(Double.doubleToLongBits(first[i]), Double.doubleToLongBits(out[i]), "diverged at " + i);
				}
			}
		}
	}
}
