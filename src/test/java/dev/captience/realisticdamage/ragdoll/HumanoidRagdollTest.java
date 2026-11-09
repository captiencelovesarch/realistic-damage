package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.ragdoll.physics.Body;
import dev.captience.realisticdamage.ragdoll.physics.BodySim;
import dev.captience.realisticdamage.ragdoll.physics.BoxList;
import dev.captience.realisticdamage.ragdoll.physics.Joint;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import java.util.ArrayList;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * The ragdoll solver against a fake world: no game, no window. Every scene a
 * corpse meets in play, in miniature — a floor, stairs, a ledge, water — and
 * the abuse a creeper gives it.
 */
class HumanoidRagdollTest {
	private static final double DT = 0.05;
	/** A player at scale one: the renderer's 0.9375 over sixteen pixels a block. */
	private static final double UNIT = 0.9375 / 16.0;

	/** Boxes and, optionally, a pool. */
	static final class FakeWorld implements PhysicsWorld {
		final List<double[]> boxes = new ArrayList<>();
		double waterMinY = Double.NaN;
		double waterSurface;
		boolean lava;

		FakeWorld box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
			boxes.add(new double[] {minX, minY, minZ, maxX, maxY, maxZ});
			return this;
		}

		/** A floor of whole blocks, the way Minecraft has it: many boxes, not one slab. */
		FakeWorld blockFloor(int radius, double top) {
			for (int x = -radius; x < radius; x++) {
				for (int z = -radius; z < radius; z++) {
					box(x, top - 1.0, z, x + 1, top, z + 1);
				}
			}
			return this;
		}

		@Override
		public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, BoxList out) {
			for (double[] b : boxes) {
				if (b[3] > minX && b[0] < maxX && b[4] > minY && b[1] < maxY && b[5] > minZ && b[2] < maxZ) {
					out.add(b[0], b[1], b[2], b[3], b[4], b[5]);
				}
			}
		}

		@Override
		public Fluid fluid(double x, double y, double z) {
			if (!Double.isNaN(waterMinY) && y >= waterMinY && y < waterSurface) {
				return new Fluid(lava ? Fluid.Kind.LAVA : Fluid.Kind.WATER, waterSurface);
			}
			return Fluid.NONE;
		}

		/** The deepest any of the body's collision points sits inside a box. */
		double maxPenetration(HumanoidRagdoll r) {
			double worst = 0.0;
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				Body b = r.body(i);
				for (double[] p : surfacePoints(b)) {
					for (double[] box : boxes) {
						if (p[0] > box[0] && p[0] < box[3] && p[1] > box[1] && p[1] < box[4] && p[2] > box[2] && p[2] < box[5]) {
							double d = Math.min(Math.min(Math.min(p[0] - box[0], box[3] - p[0]), Math.min(p[1] - box[1], box[4] - p[1])),
									Math.min(p[2] - box[2], box[5] - p[2]));
							worst = Math.max(worst, d);
						}
					}
				}
			}
			return worst;
		}
	}

	/** The box's eight corners, world. */
	static List<double[]> surfacePoints(Body b) {
		List<double[]> out = new ArrayList<>();
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sy = -1; sy <= 1; sy += 2) {
				for (int sz = -1; sz <= 1; sz += 2) {
					Vector3d p = b.q.transform(new Vector3d(sx * b.hx, sy * b.hy, sz * b.hz)).add(b.x);
					out.add(new double[] {p.x, p.y, p.z});
				}
			}
		}
		return out;
	}

	static HumanoidRagdoll standing(double x, double y, double z, float yaw) {
		return new HumanoidRagdoll(HumanoidRagdoll.playerParts(false), new Vector3d(x, y, z), yaw, UNIT, new float[18]);
	}

	static void run(HumanoidRagdoll r, PhysicsWorld world, int ticks) {
		for (int i = 0; i < ticks; i++) {
			r.tick(world, DT);
		}
	}

	@Test
	void theRestPoseStandsWhereTheModelDrawsIt() {
		HumanoidRagdoll r = standing(0.0, 64.0, 0.0, 0.0F);
		// Feet on y=64, the head's top 32 pixels up.
		double lowest = Double.POSITIVE_INFINITY;
		double highest = Double.NEGATIVE_INFINITY;
		for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
			for (double[] p : surfacePoints(r.body(i))) {
				lowest = Math.min(lowest, p[1]);
				highest = Math.max(highest, p[1]);
			}
		}
		assertEquals(64.0 - 0.0005 * 16 * UNIT * 0, lowest, 0.01, "feet");
		assertEquals(64.0 + 32.0 * UNIT, highest, 0.01, "top of the head");
		assertEquals(0.0, r.worstSeparation(), 1.0e-9);
		assertEquals(0.0, r.lean(), 1.0e-6);
		assertEquals(0.0, r.spread(), 1.0e-9);
		// Yaw 0 faces south (+Z): the face is the head's -Z side in the model,
		// which the model's Ry(180) turns to +Z.
		Vector3d face = r.body(HumanoidRagdoll.HEAD).q.transform(new Vector3d(0.0, 0.0, -1.0));
		assertEquals(1.0, face.z, 1.0e-9);
		// The right arm (model -X) is on the body's right: facing +Z, that is -X in the world.
		assertTrue(r.body(HumanoidRagdoll.RIGHT_ARM).x.x < 0.0);
	}

	@Test
	void aBoxSettlesOnTheFloorWithoutSinkingOrBouncingAway() {
		Body box = new Body(0.25, 0.25, 0.25, 10.0);
		box.place(new Vector3d(0.3, 2.0, 0.3), new Quaterniond().rotationXYZ(0.4, 0.2, 0.7));
		BodySim sim = new BodySim(new Body[] {box}, new Joint[0], new int[0]);
		FakeWorld world = new FakeWorld().blockFloor(3, 0.0);
		double highest = 0.0;
		for (int i = 0; i < 80; i++) {
			sim.step(DT, world);
			if (i > 10) {
				highest = Math.max(highest, box.x.y);
			}
		}
		assertTrue(box.x.y > 0.24 && box.x.y < 0.26, "resting height " + box.x.y);
		assertTrue(sim.maxMotion() < 0.05, "still moving " + sim.maxMotion());
		assertTrue(highest < 1.0, "bounced back up to " + highest);
	}

	@Test
	void aQuietDeathGoesOverAndLiesStill() {
		FakeWorld world = new FakeWorld().blockFloor(4, 0.0);
		HumanoidRagdoll r = standing(0.5, 0.0, 0.5, 0.0F);
		// No push at all: a nudge forward is all it takes to go.
		r.spin(new Vector3d(1.2, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		double worstSpread = 0.0;
		for (int i = 0; i < 200; i++) {
			r.tick(world, DT);
			worstSpread = Math.max(worstSpread, r.spread());
		}
		assertTrue(r.lean() > 65.0, "still standing: lean " + r.lean());
		assertTrue(world.maxPenetration(r) < 0.03, "in the floor by " + world.maxPenetration(r));
		assertTrue(r.worstSeparation() < 0.02, "a joint came apart by " + r.worstSeparation());
		assertTrue(r.asleep(), "never came to rest");
		assertTrue(worstSpread < 2.5, "thrashed: spread " + worstSpread);
	}

	@Test
	void aBodyOnStairsEndsUpOnTheStairsNotInThem() {
		FakeWorld world = new FakeWorld().blockFloor(6, 0.0);
		// Five steps going down toward +Z.
		for (int k = 0; k < 5; k++) {
			world.box(-3, 0.0, k, 3, 5.0 - k, k + 1);
		}
		HumanoidRagdoll r = standing(0.0, 5.0, 0.5, 0.0F);
		r.spin(new Vector3d(1.5, 0.0, 0.0), new Vector3d(0.0, 5.0, 0.5));
		r.push(0.0, 0.0, 1.0);
		run(r, world, 300);
		assertTrue(world.maxPenetration(r) < 0.04, "inside the stairs by " + world.maxPenetration(r));
		assertTrue(r.worstSeparation() < 0.03, "a joint came apart by " + r.worstSeparation());
		// It went down at least a step.
		assertTrue(r.centre().y < 5.0, "still on the top step: " + r.centre().y);
	}

	@Test
	void aBlastDoesNotTearItApartOrBreakItsJoints() {
		FakeWorld world = new FakeWorld().blockFloor(30, 0.0);
		HumanoidRagdoll r = standing(0.5, 0.0, 0.5, 30.0F);
		r.push(12.0, 14.0, -6.0);
		r.kick(HumanoidRagdoll.RIGHT_LEG, 15.0, 8.0, 0.0);
		r.spin(new Vector3d(0.0, 4.0, 22.0), new Vector3d(0.5, 1.0, 0.5));
		for (int i = 0; i < 400; i++) {
			r.tick(world, DT);
			assertFalse(r.broken(), "the solver broke at tick " + i);
			assertTrue(r.worstSeparation() < 0.25, "torn apart at tick " + i + ": " + r.worstSeparation());
		}
		assertTrue(r.asleep(), "the blasted body never came to rest");
		// At rest, the joints are closed: a body frozen with a limb pinned under
		// it must not keep a pixel's gap at the shoulder.
		assertTrue(r.worstSeparation() < 0.01, "a joint stayed apart by " + r.worstSeparation());
		assertTrue(world.maxPenetration(r) < 0.04, "in the floor by " + world.maxPenetration(r));
		Vector3d swing = new Vector3d();
		double[] twist = new double[1];
		// A limb pinned under a body at rest may sit past its limit: the floor
		// comes first (never through the ground), then a closed joint (a gap at
		// the shoulder shows), and the limit gives way last, because a few
		// degrees of twist about a square limb's own axis cannot be seen. Past
		// about seventeen degrees it would be a broken limit, not a pinned limb.
		double slack = 0.3;
		for (Joint j : r.joints()) {
			Joint.decompose(j.relative(new Quaterniond()), swing, twist);
			assertTrue(swing.x >= j.swingXMin - slack && swing.x <= j.swingXMax + slack, "swing x " + swing.x);
			assertTrue(swing.z >= j.swingZMin - slack && swing.z <= j.swingZMax + slack, "swing z " + swing.z);
			assertTrue(twist[0] >= j.twistMin - slack && twist[0] <= j.twistMax + slack, "twist " + twist[0]);
		}
		assertTrue(r.centre().distance(new Vector3d(0.5, 0.0, 0.5)) > 3.0, "the blast did not throw it");
	}

	@Test
	void theSameLaunchFallsTheSameWayEveryTime() {
		double[] first = null;
		for (int run = 0; run < 2; run++) {
			FakeWorld world = new FakeWorld().blockFloor(20, 0.0);
			HumanoidRagdoll r = standing(0.5, 0.0, 0.5, 75.0F);
			r.push(6.0, 9.0, 3.0);
			r.kick(HumanoidRagdoll.HEAD, -4.0, 0.0, 2.0);
			run(r, world, 120);
			double[] out = new double[HumanoidRagdoll.PARTS * 7];
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				Body b = r.body(i);
				double[] v = {b.x.x, b.x.y, b.x.z, b.q.x, b.q.y, b.q.z, b.q.w};
				System.arraycopy(v, 0, out, i * 7, 7);
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

	@Test
	void aDrownedBodyFloatsAndALavaBodySinks() {
		FakeWorld water = new FakeWorld().blockFloor(6, 0.0);
		water.waterMinY = 0.0;
		water.waterSurface = 4.0;
		HumanoidRagdoll floater = standing(0.5, 1.0, 0.5, 0.0F);
		floater.spin(new Vector3d(0.8, 0.0, 0.0), new Vector3d(0.5, 1.0, 0.5));
		run(floater, water, 400);
		assertTrue(floater.centre().y > 3.0, "sank in water: torso at " + floater.centre().y);

		FakeWorld lava = new FakeWorld().blockFloor(6, 0.0);
		lava.waterMinY = 0.0;
		lava.waterSurface = 4.0;
		lava.lava = true;
		HumanoidRagdoll sinker = standing(0.5, 3.5, 0.5, 0.0F);
		sinker.spin(new Vector3d(0.8, 0.0, 0.0), new Vector3d(0.5, 3.5, 0.5));
		run(sinker, lava, 400);
		assertTrue(sinker.centre().y < 0.6, "floated in lava: torso at " + sinker.centre().y);
	}

	@Test
	void aPointOnTheStandingBodyFollowsItsPart() {
		FakeWorld world = new FakeWorld().blockFloor(4, 0.0);
		HumanoidRagdoll r = standing(0.5, 0.0, 0.5, 0.0F);
		Vector3d headTop = r.body(HumanoidRagdoll.HEAD).q.transform(new Vector3d(0.0, -r.body(HumanoidRagdoll.HEAD).hy, 0.0))
				.add(r.body(HumanoidRagdoll.HEAD).x);
		r.spin(new Vector3d(1.2, 0.0, 0.0), new Vector3d(0.5, 0.0, 0.5));
		run(r, world, 100);
		Vector3d placed = r.place(headTop);
		// Wherever the head went, the point is on the head's top face.
		Body head = r.body(HumanoidRagdoll.HEAD);
		Vector3d expected = head.q.transform(new Vector3d(0.0, -head.hy, 0.0)).add(head.x);
		assertEquals(0.0, placed.distance(expected), 1.0e-6);
		assertTrue(placed.y < 0.6, "the head did not come down: " + placed.y);
	}

	@Test
	void sixteenBodiesCostLittle() {
		FakeWorld world = new FakeWorld().blockFloor(24, 0.0);
		List<HumanoidRagdoll> all = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			HumanoidRagdoll r = standing((i % 4) * 3.0 - 6.0, 0.0, (i / 4) * 3.0 - 6.0, i * 20.0F);
			r.push(i * 0.5, 4.0, -i * 0.3);
			r.spin(new Vector3d(1.0 + i * 0.1, 0.0, 0.5), new Vector3d(r.centre()));
			all.add(r);
		}
		// Warm up the JIT, then measure the falling part (sleeping bodies cost nothing).
		for (HumanoidRagdoll r : all) {
			run(r, world, 5);
		}
		long start = System.nanoTime();
		int ticks = 40;
		for (int t = 0; t < ticks; t++) {
			for (HumanoidRagdoll r : all) {
				r.tick(world, DT);
			}
		}
		double perBodyTickMs = (System.nanoTime() - start) / 1.0e6 / (ticks * all.size());
		System.out.println("[rd] ragdoll cost " + perBodyTickMs + " ms per body per tick");
		assertTrue(perBodyTickMs < 1.0, "too slow: " + perBodyTickMs + " ms per body per tick");
	}
}
