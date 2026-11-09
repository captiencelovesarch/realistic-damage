package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Phase 5: a flinch jolts the struck part the way the blow went, and springs back within half a second. */
class FlinchTest {
	@Test
	void anArmHitFromTheFrontSwingsTheArmBackAndSettles() {
		Flinch f = new Flinch();
		// Facing +Z (yaw 0); a blow from the front travels -Z.
		f.hit(HumanoidRagdoll.RIGHT_ARM, new Vector3d(0.0, 0.0, -1.0), 1.0, 0.0F);
		double peak = 0.0;
		for (int t = 0; t < 14; t++) {
			f.tick(0.05);
			peak = Math.max(peak, f.value(Flinch.RIGHT_ARM_X, 1.0F));
			assertTrue(Double.isFinite(f.value(Flinch.RIGHT_ARM_X, 1.0F)));
		}
		System.out.println("[rd] arm flinch peak " + peak);
		assertTrue(peak > 0.3, "the arm barely moved: " + peak);
		assertTrue(f.done(), "still flinching after 0.7 s");
		assertTrue(Math.abs(f.value(Flinch.LEFT_ARM_X, 1.0F)) < 1.0e-9, "the other arm moved");
	}

	@Test
	void aHeadshotFromTheFrontSnapsTheHeadBack() {
		Flinch f = new Flinch();
		f.hit(HumanoidRagdoll.HEAD, new Vector3d(0.0, 0.0, -1.0), 0.8, 0.0F);
		double lowest = 0.0;
		for (int t = 0; t < 6; t++) {
			f.tick(0.05);
			lowest = Math.min(lowest, f.value(Flinch.HEAD_X, 1.0F));
		}
		assertTrue(lowest < -0.2, "the head did not go back: " + lowest);
	}

	@Test
	void theBodyLeansAwayFromABlowAndALegHitDips() {
		Flinch f = new Flinch();
		// Blow travelling +X: the top of the body tips toward +X, a turn about up x X = -Z.
		f.hit(HumanoidRagdoll.LEFT_LEG, new Vector3d(1.0, 0.0, 0.0), 1.0, 0.0F);
		assertTrue(f.leanAxis().z < -0.99, "lean axis " + f.leanAxis());
		double dip = 0.0;
		for (int t = 0; t < 6; t++) {
			f.tick(0.05);
			dip = Math.max(dip, f.value(Flinch.DIP, 1.0F));
		}
		assertTrue(dip > 0.5, "a leg hit did not dip the body: " + dip);
	}

	@Test
	void hitsStackButStayBounded() {
		Flinch f = new Flinch();
		for (int i = 0; i < 20; i++) {
			f.hit(HumanoidRagdoll.TORSO, new Vector3d(0.0, 0.0, 1.0), 1.0, 0.0F);
			f.tick(0.05);
		}
		assertTrue(Math.abs(f.value(Flinch.LEAN, 1.0F)) <= 0.9 + 1.0e-9, "lean ran away: " + f.value(Flinch.LEAN, 1.0F));
	}
}
