package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * The stepper (stumbling deaths, IK phase 0) against a fake world: a body
 * held on its feet, shoved, has to catch itself by stepping, with its feet
 * staying where they land, and then go down when it is let go.
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
				// Flat to z = 1.3, then a three-block drop.
				w.box(-6, -1.0, -6.0, 6, 0.0, 1.3);
				w.box(-6, -4.0, 1.3, 6, -3.0, 9.0);
			}
			case "wall" -> {
				w.blockFloor(12, 0.0);
				w.box(-6, 0.0, 1.4, 6, 3.0, 2.4);
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
		HumanoidRagdoll r = new HumanoidRagdoll(HumanoidRagdoll.playerParts(false), new Vector3d(0.5, 0.0, 0.5), 0.0F,
				UNIT, new float[18]);
		r.setMuscles(Muscles.holding(2.5));
		r.setController(new Stand());
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

	@Test
	void unshovedItJustStands() {
		HumanoidRagdollTest.FakeWorld world = scene("flat");
		HumanoidRagdoll r = shoved(world, 0.0, "back");
		Stand stand = run(r, world, 40);
		assertEquals(0, stand.stepper.steps(), "nothing to catch, nothing to step for");
		assertTrue(r.lean() < 5.0, "standing straight: " + r.lean());
		assertTrue(stand.holding());
	}

	@Test
	void aShoveIsCaughtInAFewSteps() {
		for (String dir : new String[] {"back", "forward", "left", "right"}) {
			HumanoidRagdollTest.FakeWorld world = scene("flat");
			HumanoidRagdoll r = shoved(world, 3.0, dir);
			Stand stand = run(r, world, 40);
			int steps = stand.stepper.steps();
			assertTrue(steps >= 1 && steps <= 6, dir + ": " + steps + " steps");
			assertTrue(stand.stepper.standing(), dir + ": standing again after two seconds");
			assertTrue(r.lean() < 12.0, dir + ": upright, lean " + r.lean());
		}
	}

	@Test
	void plantedFeetDoNotSlide() {
		for (String dir : new String[] {"back", "forward", "left", "right"}) {
			HumanoidRagdollTest.FakeWorld world = scene("flat");
			HumanoidRagdoll r = shoved(world, 4.0, dir);
			Stand stand = run(r, world, 60);
			// A fifth of a Minecraft pixel: a foot rolling on the edge of its sole
			// moves its middle that much, and nobody can see it.
			assertTrue(stand.stepper.worstDrift() < 0.015,
					dir + ": a planted foot slid " + stand.stepper.worstDrift() + " blocks");
		}
	}

	@Test
	void itIsLetGoAndGoesDown() {
		HumanoidRagdollTest.FakeWorld world = scene("flat");
		HumanoidRagdoll r = shoved(world, 3.0, "back");
		Stand stand = run(r, world, 200);
		assertTrue(!stand.holding(), "let go after standing still");
		assertTrue(r.lean() > 60.0, "down once let go: lean " + r.lean());
		assertTrue(world.maxPenetration(r) < 0.03, "nothing in the floor: " + world.maxPenetration(r));
	}

	@Test
	void harderShovesTakeMoreSteps() {
		int soft = 0;
		int hard = 0;
		for (String dir : new String[] {"back", "forward", "left", "right"}) {
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
	void itStopsShortOfAWallAndOfStairsUp() {
		for (String scene : new String[] {"wall", "stairs_up"}) {
			HumanoidRagdollTest.FakeWorld world = scene(scene);
			HumanoidRagdoll r = shoved(world, 3.0, "forward");
			Stand stand = run(r, world, 50);
			assertTrue(stand.stepper.standing(), scene + ": standing");
			assertTrue(r.lean() < 12.0, scene + ": upright, lean " + r.lean());
			assertTrue(world.maxPenetration(r) < 0.04, scene + ": nothing in a block: " + world.maxPenetration(r));
			Vector3d sole = r.tip(HumanoidRagdoll.RIGHT_LEG);
			assertTrue(sole.y < 0.1, scene + ": still on the floor, not up a riser: " + sole.y);
		}
	}

	@Test
	void itStepsDownAStairAndStandsOnIt() {
		HumanoidRagdollTest.FakeWorld world = scene("stairs_down");
		HumanoidRagdoll r = shoved(world, 3.0, "forward");
		Stand stand = run(r, world, 90);
		assertTrue(stand.stepper.standing(), "standing");
		assertTrue(r.lean() < 12.0, "upright, lean " + r.lean());
		assertTrue(world.maxPenetration(r) < 0.04, "nothing in a block: " + world.maxPenetration(r));
	}

	@Test
	void sameShoveSameSteps() {
		double[] first = null;
		for (int run = 0; run < 2; run++) {
			HumanoidRagdollTest.FakeWorld world = scene("flat");
			HumanoidRagdoll r = shoved(world, 3.5, "left");
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
