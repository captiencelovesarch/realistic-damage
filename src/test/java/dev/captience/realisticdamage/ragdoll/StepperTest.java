package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * The stepper (stumbling deaths, IK phase 0) against a fake world. At full
 * strength a body on its feet, shoved, has to catch itself by stepping, its
 * feet staying where they land. A dying one, its balance going, has to stumble
 * after it and go over by itself the way it was shoved. Neither is ever held
 * up with nothing to stand on.
 */
class StepperTest {
	private static final double DT = 0.05;
	private static final double UNIT = 0.9375 / 16.0;

	/**
	 * The scenes the traces and the tests share. The body stands at (0.5, 0,
	 * 0.5) facing +Z; everything interesting is in front of it, for a shove
	 * "forward".
	 */
	static HumanoidRagdollTest.FakeWorld scene(String name) {
		HumanoidRagdollTest.FakeWorld w = new HumanoidRagdollTest.FakeWorld();
		switch (name) {
			case "stairs_up" -> {
				// Minecraft stairs: half a block up every half block, from z = 1.2.
				w.blockFloor(12, 0.0);
				for (int i = 1; i <= 6; i++) {
					w.box(-3, 0.0, 1.2 + (i - 1) * 0.5, 4, i * 0.5, 9.0);
				}
			}
			case "stairs_down" -> {
				// Flat to z = 1.2, then half a block down every half block.
				for (int i = 0; i <= 6; i++) {
					double top = -i * 0.5;
					double from = i == 0 ? -6.0 : 1.2 + (i - 1) * 0.5;
					w.box(-6, top - 1.0, from, 6, top, i == 0 ? 1.2 : from + 0.5);
				}
				w.box(-6, -5.0, 4.2, 6, -3.0, 9.0);
			}
			case "ledge" -> {
				// Flat to z = 1.3, then a three-block drop down a cliff.
				w.box(-6, -4.0, -6.0, 6, 0.0, 1.3);
				w.box(-6, -4.0, 1.3, 6, -3.0, 9.0);
			}
			case "wall" -> {
				w.blockFloor(12, 0.0);
				w.box(-6, 0.0, 1.4, 6, 3.0, 2.4);
			}
			case "pit" -> {
				// A hole one block square and three deep, a step and a half in front.
				w.box(-6, -3.0, -6.0, 6, 0.0, 1.5);
				w.box(-6, -3.0, 2.5, 6, 0.0, 9.0);
				w.box(-6, -3.0, 1.5, 0, 0.0, 2.5);
				w.box(1, -3.0, 1.5, 6, 0.0, 2.5);
				w.box(-6, -4.0, -6.0, 6, -3.0, 9.0);
			}
			default -> w.blockFloor(12, 0.0);
		}
		return w;
	}

	/**
	 * A player-sized body standing at (0.5, 0, 0.5) facing +Z, held up by a
	 * {@link Stand}, shoved at {@code speed} blocks a second: "back" is the
	 * way its back faces, "forward" its front, "left" and "right" its own.
	 */
	static HumanoidRagdoll shoved(HumanoidRagdollTest.FakeWorld world, double speed, String dir) {
		return shoved(world, speed, dir, 0.0);
	}

	/** The same, on its feet for {@code onFeet} seconds (0: at full strength until it has stood still). */
	static HumanoidRagdoll shoved(HumanoidRagdollTest.FakeWorld world, double speed, String dir, double onFeet) {
		HumanoidRagdoll r = new HumanoidRagdoll(HumanoidRagdoll.playerParts(false), new Vector3d(0.5, 0.0, 0.5), 0.0F,
				UNIT, new float[18]);
		r.setMuscles(Muscles.holding(2.5));
		r.setController(new Stand(onFeet));
		Vector3d f = r.facing();
		Vector3d front = new Vector3d(f.x, 0.0, f.z).normalize();
		Vector3d right = new Vector3d(front).cross(0.0, 1.0, 0.0).normalize();
		Vector3d push = switch (dir) {
			case "forward" -> front;
			case "left" -> right.negate();
			case "right" -> right;
			default -> front.negate();
		};
		r.push(push.x * speed, 0.0, push.z * speed);
		return r;
	}

	private static Stand run(HumanoidRagdoll r, HumanoidRagdollTest.FakeWorld world, int ticks) {
		for (int i = 0; i < ticks; i++) {
			r.tick(world, DT);
		}
		return (Stand) r.controller();
	}

	/** How long a dying body has on its feet in these tests, seconds: what the game gives it. */
	private static final double DYING = Stand.ON_FEET;
	private static final String[] ALL_WAYS = {"back", "forward", "left", "right"};

	/** Ticks until the body is let go of, or -1 if it still is not after {@code ticks}. */
	private static int ticksHeld(HumanoidRagdoll r, HumanoidRagdollTest.FakeWorld world, int ticks) {
		Stand stand = (Stand) r.controller();
		for (int i = 0; i < ticks; i++) {
			r.tick(world, DT);
			if (!stand.holding()) {
				return i + 1;
			}
		}
		return -1;
	}

	/** Where the body is: the middle of its torso. */
	private static Vector3d torso(HumanoidRagdoll r) {
		return new Vector3d(r.body(HumanoidRagdoll.TORSO).x);
	}

	/** The way a shove "back", "forward", "left" or "right" goes for the body of {@link #shoved}: it faces +Z. */
	private static Vector3d way(HumanoidRagdoll r, String dir) {
		Vector3d f = r.facing();
		Vector3d front = new Vector3d(f.x, 0.0, f.z).normalize();
		Vector3d right = new Vector3d(front).cross(0.0, 1.0, 0.0).normalize();
		return switch (dir) {
			case "forward" -> front;
			case "left" -> right.negate();
			case "right" -> right;
			default -> front.negate();
		};
	}

	// --- At full strength: the stepping itself. ---

	@Test
	void unshovedItJustStands() {
		HumanoidRagdollTest.FakeWorld world = scene("flat");
		HumanoidRagdoll r = shoved(world, 0.0, "back");
		Stand stand = run(r, world, 20);
		assertEquals(0, stand.stepper.steps(), "nothing to catch, nothing to step for");
		assertTrue(r.lean() < 5.0, "standing straight: " + r.lean());
		assertTrue(stand.holding());
	}

	@Test
	void aShoveIsCaughtInAFewSteps() {
		for (String dir : ALL_WAYS) {
			HumanoidRagdollTest.FakeWorld world = scene("flat");
			HumanoidRagdoll r = shoved(world, 3.0, dir);
			Stand stand = (Stand) r.controller();
			// Standing again, upright and at rest, within two seconds; it is let go
			// of a little after that, so the moment has to be looked for.
			int caught = -1;
			for (int i = 0; i < 40 && caught < 0 && stand.holding(); i++) {
				r.tick(world, DT);
				if (stand.stepper.steps() >= 1 && stand.stepper.standing() && stand.stepper.speed() < 0.3
						&& r.lean() < 12.0) {
					caught = i + 1;
				}
			}
			int steps = stand.stepper.steps();
			assertTrue(steps >= 1 && steps <= 6, dir + ": " + steps + " steps");
			assertTrue(caught > 0, dir + ": standing again, upright, within two seconds; lean " + r.lean());
		}
	}

	@Test
	void plantedFeetDoNotSlide() {
		for (double onFeet : new double[] {0.0, DYING}) {
			for (String dir : ALL_WAYS) {
				HumanoidRagdollTest.FakeWorld world = scene("flat");
				HumanoidRagdoll r = shoved(world, 4.0, dir, onFeet);
				Stand stand = run(r, world, 60);
				// A fifth of a Minecraft pixel: a foot rolling on the edge of its sole
				// moves its middle that much, and nobody can see it.
				assertTrue(stand.stepper.worstDrift() < 0.015,
						dir + ", " + onFeet + " s: a planted foot slid " + stand.stepper.worstDrift() + " blocks");
			}
		}
	}

	@Test
	void caughtAndStillItIsLetGoAndGoesDown() {
		HumanoidRagdollTest.FakeWorld world = scene("flat");
		HumanoidRagdoll r = shoved(world, 3.0, "back");
		Stand stand = run(r, world, 200);
		assertTrue(!stand.holding(), "let go after standing still");
		assertTrue(r.lean() > 60.0, "down once let go: lean " + r.lean());
		assertTrue(world.maxPenetration(r) < 0.035, "nothing in the floor: " + world.maxPenetration(r));
	}

	@Test
	void harderShovesTakeMoreSteps() {
		int soft = 0;
		int hard = 0;
		for (String dir : ALL_WAYS) {
			HumanoidRagdollTest.FakeWorld world = scene("flat");
			HumanoidRagdoll r = shoved(world, 1.0, dir);
			soft += run(r, world, 40).stepper.steps();
			world = scene("flat");
			r = shoved(world, 4.0, dir);
			hard += run(r, world, 40).stepper.steps();
		}
		assertTrue(hard > soft + 4, "1 block a second: " + soft + " steps in four shoves; 4: " + hard);
	}

	@Test
	void itDoesNotClimbAWallOrStairs() {
		for (String scene : new String[] {"wall", "stairs_up"}) {
			HumanoidRagdollTest.FakeWorld world = scene(scene);
			HumanoidRagdoll r = shoved(world, 3.0, "forward");
			Stand stand = run(r, world, 30);
			assertTrue(stand.stepper.steps() >= 1, scene + ": it stepped");
			assertTrue(world.maxPenetration(r) < 0.04, scene + ": nothing in a block: " + world.maxPenetration(r));
			for (int leg : new int[] {HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG}) {
				assertTrue(r.tip(leg).y < 0.1, scene + ": on the floor, not up a riser: " + r.tip(leg).y);
			}
		}
	}

	@Test
	void flatGroundIsNeverTakenForAnEdge() {
		for (double onFeet : new double[] {0.0, DYING}) {
			for (String dir : ALL_WAYS) {
				for (double speed : new double[] {3.0, 6.0}) {
					HumanoidRagdollTest.FakeWorld world = scene("flat");
					HumanoidRagdoll r = shoved(world, speed, dir, onFeet);
					String lost = run(r, world, 60).stepper.lost();
					assertTrue(lost == null || !lost.startsWith("a step"),
							dir + " at " + speed + ", " + onFeet + " s: lost its footing to \"" + lost + "\"");
				}
			}
		}
	}

	// --- Dying on its feet: the stumble. ---

	@Test
	void dyingItStumblesAndGoesOverTheWayItWasShoved() {
		for (String dir : new String[] {"back", "forward"}) {
			for (double speed : new double[] {2.0, 3.0, 4.0, 5.0}) {
				HumanoidRagdollTest.FakeWorld world = scene("flat");
				HumanoidRagdoll r = shoved(world, speed, dir, DYING);
				String what = dir + " at " + speed;
				Vector3d way = way(r, dir);
				Vector3d from = torso(r);
				int held = ticksHeld(r, world, 60);
				Stand stand = (Stand) r.controller();
				assertTrue(held > 0 && held * DT < DYING - 0.2,
						what + ": it goes over by itself, before its time is up: " + held + " ticks");
				assertEquals("over", stand.stepper.lost(), what);
				assertTrue(held * DT >= 0.4, what + ": on its feet for a while first: " + held + " ticks");
				assertTrue(stand.stepper.steps() >= 1, what + ": it stepped");
				// Over means its torso was well on the way down when its legs gave up;
				// that way is the way it was shoved.
				Vector3d up = new Vector3d(r.body(HumanoidRagdoll.HEAD).x).sub(torso(r)).normalize();
				assertTrue(up.x * way.x + up.z * way.z > 0.5, what + ": pitching the way it was shoved: " + up);
				run(r, world, 60);
				double went = torso(r).sub(from).dot(way);
				assertTrue(went > 0.9, what + ": it ends up where it was going: " + went + " blocks along");
				assertTrue(torso(r).y < 0.45, what + ": down: torso at " + torso(r).y);
				assertTrue(world.maxPenetration(r) < 0.035, what + ": nothing in the floor: " + world.maxPenetration(r));
			}
		}
	}

	@Test
	void dyingItIsNeverOnItsFeetPastItsTime() {
		for (String scene : new String[] {"flat", "wall", "stairs_up", "stairs_down", "ledge", "pit"}) {
			for (String dir : scene.equals("flat") ? ALL_WAYS : new String[] {"forward"}) {
				for (double speed = 0.0; speed <= 6.0; speed += 1.5) {
					HumanoidRagdollTest.FakeWorld world = scene(scene);
					HumanoidRagdoll r = shoved(world, speed, dir, DYING);
					int held = ticksHeld(r, world, 40);
					assertTrue(held > 0 && held * DT <= DYING + DT + 1.0e-9,
							scene + ", " + dir + " at " + speed + ": let go after " + held + " ticks");
				}
			}
		}
	}

	@Test
	void dyingAndUnshovedItStandsThenDrops() {
		HumanoidRagdollTest.FakeWorld world = scene("flat");
		HumanoidRagdoll r = shoved(world, 0.0, "back", DYING);
		Stand stand = run(r, world, 24);
		assertTrue(stand.holding(), "still on its feet at 1.2 seconds");
		assertEquals(0, stand.stepper.steps(), "nothing to step for");
		assertTrue(r.lean() < 8.0, "standing, if not steadily: " + r.lean());
		run(r, world, 76);
		assertTrue(!stand.holding(), "let go when its time is up");
		assertTrue(r.lean() > 60.0, "down: lean " + r.lean());
		assertTrue(world.maxPenetration(r) < 0.035, "nothing in the floor: " + world.maxPenetration(r));
	}

	@Test
	void itIsNotHeldOverAHoleOrAnEdge() {
		for (String scene : new String[] {"pit", "ledge", "stairs_down"}) {
			for (double speed : new double[] {4.0, 5.0, 6.0}) {
				for (double onFeet : new double[] {0.0, DYING}) {
					HumanoidRagdollTest.FakeWorld world = scene(scene);
					HumanoidRagdoll r = shoved(world, speed, "forward", onFeet);
					String what = scene + " at " + speed + ", " + onFeet + " s";
					int held = ticksHeld(r, world, 40);
					Stand stand = (Stand) r.controller();
					assertTrue(held > 0 && held <= 12, what + ": let go at the edge, after " + held + " ticks");
					String lost = stand.stepper.lost();
					assertTrue(lost != null && lost.startsWith("a step"), what + ": lost its footing to \"" + lost + "\"");
					run(r, world, 100);
					// Off the ledge and down the stairs it goes; the hole is a block
					// square, and a body can as well end up lying across it.
					assertTrue(scene.equals("pit") ? torso(r).y < 0.45 : torso(r).y < 0.0,
							what + ": it went in, or off, or down: torso at " + torso(r).y);
					assertTrue(world.maxPenetration(r) < 0.05, what + ": nothing in a block: " + world.maxPenetration(r));
				}
			}
		}
	}

	@Test
	void fallenAgainstStairsItIsLetGo() {
		for (double speed : new double[] {3.0, 4.0, 5.0}) {
			HumanoidRagdollTest.FakeWorld world = scene("stairs_up");
			HumanoidRagdoll r = shoved(world, speed, "forward", DYING);
			int held = ticksHeld(r, world, 40);
			assertTrue(held > 0 && held * DT < DYING - 0.2,
					speed + ": lying on the stairs is not standing: let go after " + held + " ticks");
			assertTrue(world.maxPenetration(r) < 0.04, speed + ": nothing in a block: " + world.maxPenetration(r));
		}
	}

	@Test
	void sameShoveSameSteps() {
		for (double onFeet : new double[] {0.0, DYING}) {
			double[] first = null;
			for (int run = 0; run < 2; run++) {
				HumanoidRagdollTest.FakeWorld world = scene("flat");
				HumanoidRagdoll r = shoved(world, 3.5, "left", onFeet);
				run(r, world, 50);
				double[] now = new double[HumanoidRagdoll.PARTS * 3];
				for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
					now[i * 3] = r.body(i).x.x;
					now[i * 3 + 1] = r.body(i).x.y;
					now[i * 3 + 2] = r.body(i).x.z;
				}
				if (first == null) {
					first = now;
				} else {
					for (int i = 0; i < now.length; i++) {
						assertEquals(first[i], now[i], 0.0, "bit for bit");
					}
				}
			}
		}
	}
}
