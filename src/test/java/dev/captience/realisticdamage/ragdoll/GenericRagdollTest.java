package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.ragdoll.physics.Body;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Phase 4: a cow, as vanilla's quadruped model builds it, falling as a ragdoll. */
class GenericRagdollTest {
	private static final double UNIT = 1.0 / 16.0;

	/** Vanilla's CowModel (26.2): a twelve-wide body, horns on the head, legs four either side. */
	static List<GenericRagdoll.Part> cow() {
		return List.of(
				new GenericRagdoll.Part(0, 4, -8, 0, 0, 0, -5, -5, -7, 5, 4, 0),
				new GenericRagdoll.Part(0, 5, 2, Math.PI / 2, 0, 0, -6, -10, -8, 6, 8, 3),
				new GenericRagdoll.Part(-4, 12, 7, 0, 0, 0, -2, 0, -2, 2, 12, 2),
				new GenericRagdoll.Part(4, 12, 7, 0, 0, 0, -2, 0, -2, 2, 12, 2),
				new GenericRagdoll.Part(-4, 12, -5, 0, 0, 0, -2, 0, -2, 2, 12, 2),
				new GenericRagdoll.Part(4, 12, -5, 0, 0, 0, -2, 0, -2, 2, 12, 2));
	}

	static GenericRagdoll standing(float yaw) {
		Quaterniond model = HumanoidRagdoll.modelRotation(yaw);
		Vector3d origin = model.transform(new Vector3d(0.0, -24.016 * UNIT, 0.0)).add(0.5, 0.0, 0.5);
		return new GenericRagdoll(cow(), origin, model, UNIT, 0.05);
	}

	@Test
	void aCowStandsOnItsLegsAndTheBodyIsTheTorso() {
		GenericRagdoll r = standing(0.0F);
		assertTrue(r.torso == 1, "the body is not the biggest part: " + r.torso);
		double lowest = Double.POSITIVE_INFINITY;
		for (int i = 0; i < r.parts(); i++) {
			for (double[] p : HumanoidRagdollTest.surfacePoints(r.body(i))) {
				lowest = Math.min(lowest, p[1]);
			}
		}
		assertTrue(Math.abs(lowest) < 0.01, "the hooves are not on the ground: " + lowest);
		assertTrue(r.worstSeparation() < 1.0e-9, "a joint starts apart");
		// The model frame read back from the torso is the frame it was built in.
		Quaterniond q = r.modelRotation(1.0F, new Quaterniond());
		Quaterniond expected = HumanoidRagdoll.modelRotation(0.0F);
		assertTrue(Math.abs(Math.abs(q.dot(expected)) - 1.0) < 1.0e-9, "the model frame did not round-trip");
	}

	@Test
	void aDeadCowKeelsOverOntoItsSide() {
		var world = new HumanoidRagdollTest.FakeWorld().blockFloor(8, 0.0);
		GenericRagdoll r = standing(0.0F);
		// Facing +Z: over onto its side is a roll about the facing axis.
		r.keel(new Vector3d(0.0, 0.0, 1.0), new Vector3d(0.5, 0.0, 0.5));
		for (int t = 0; t < 200; t++) {
			r.tick(world, 0.05);
			assertFalse(r.broken(), "broke at tick " + t);
		}
		System.out.println("[rd] cow: lean " + r.lean() + ", gap " + r.worstSeparation() + ", inside "
				+ maxPenetration(world, r) + ", asleep " + r.asleep());
		assertTrue(r.lean() > 60.0, "the cow did not go over: " + r.lean());
		assertTrue(r.worstSeparation() < 0.02, "came apart by " + r.worstSeparation());
		assertTrue(maxPenetration(world, r) < 0.04, "in the floor by " + maxPenetration(world, r));
		assertTrue(r.asleep(), "never came to rest");
	}

	private static double maxPenetration(HumanoidRagdollTest.FakeWorld world, GenericRagdoll r) {
		double worst = 0.0;
		for (int i = 0; i < r.parts(); i++) {
			Body b = r.body(i);
			for (double[] p : HumanoidRagdollTest.surfacePoints(b)) {
				if (p[1] < 0.0) {
					worst = Math.max(worst, -p[1]);
				}
			}
		}
		return worst;
	}
}
