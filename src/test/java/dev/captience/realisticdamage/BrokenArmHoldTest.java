package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.systems.BrokenArmHold;
import dev.captience.realisticdamage.systems.BrokenArmHold.Pose;
import dev.captience.realisticdamage.systems.BrokenArmHold.Weight;
import org.junit.jupiter.api.Test;

class BrokenArmHoldTest {
	private static Pose rest(Weight weight) {
		return BrokenArmHold.pose(weight, 0.0F, false, 0.0, false);
	}

	@Test
	void heavierThingsSagMore() {
		Weight[] order = {Weight.EMPTY, Weight.LIGHT, Weight.MEDIUM, Weight.HEAVY};
		for (int i = 1; i < order.length; i++) {
			Pose lighter = rest(order[i - 1]);
			Pose heavier = rest(order[i]);
			assertTrue(heavier.drop() > lighter.drop(), order[i] + " drop");
			assertTrue(heavier.roll() > lighter.roll(), order[i] + " roll");
			assertTrue(heavier.tip() > lighter.tip(), order[i] + " tip");
		}
	}

	@Test
	void evenAnEmptyHandSagsAndNothingSagsOutOfView() {
		assertTrue(rest(Weight.EMPTY).drop() > 0.0F);
		// The hand rests about half a block below the eye line; a sag of a
		// quarter block or more would take the item off the bottom of the view.
		assertTrue(rest(Weight.HEAVY).drop() < 0.25F);
		assertTrue(rest(Weight.HEAVY).roll() <= 25.0F);
	}

	@Test
	void anArmAlreadyHangingDoesNotSagTwiceButStillShakes() {
		Pose hanging = BrokenArmHold.pose(Weight.HEAVY, 0.0F, false, 3.3, true);
		assertEquals(0.0F, hanging.drop());
		assertEquals(0.0F, hanging.roll());
		assertEquals(0.0F, hanging.tip());
		Pose free = BrokenArmHold.pose(Weight.HEAVY, 0.0F, false, 3.3, false);
		assertEquals(free.shakeX(), hanging.shakeX());
		assertEquals(free.shakeTwist(), hanging.shakeTwist());
	}

	@Test
	void theTremorIsAlwaysThereAndStaysSmall() {
		float peak = 0.0F;
		float peakTwist = 0.0F;
		boolean moved = false;
		Pose first = BrokenArmHold.pose(Weight.HEAVY, 0.5F, true, 0.0, false);
		for (int i = 0; i < 4000; i++) {
			Pose pose = BrokenArmHold.pose(Weight.HEAVY, 0.5F, true, i * 0.05, false);
			peak = Math.max(peak, Math.max(Math.abs(pose.shakeX()), Math.abs(pose.shakeY())));
			peakTwist = Math.max(peakTwist, Math.abs(pose.shakeTwist()));
			moved |= pose.shakeX() != first.shakeX();
		}
		assertTrue(moved);
		assertTrue(peak > 0.002F, "visible at its worst: " + peak);
		// The worst case (heavy, sprinting, mid-swing) is still a shake, not a wave.
		assertTrue(peak < 0.03F, "offset " + peak);
		assertTrue(peakTwist < 5.0F, "twist " + peakTwist);
	}

	@Test
	void sprintingAndSwingingShakeHarder() {
		float still = BrokenArmHold.tremorStrength(Weight.MEDIUM, 0.0F, false);
		float sprint = BrokenArmHold.tremorStrength(Weight.MEDIUM, 0.0F, true);
		float swing = BrokenArmHold.tremorStrength(Weight.MEDIUM, 0.5F, false);
		float both = BrokenArmHold.tremorStrength(Weight.MEDIUM, 0.5F, true);
		assertTrue(sprint > still);
		assertTrue(swing > still);
		assertTrue(both > sprint && both > swing);
		// No jump when a swing starts or ends.
		assertEquals(still, BrokenArmHold.tremorStrength(Weight.MEDIUM, 1.0F, false), 1.0e-5F);
	}

	@Test
	void theSameMomentDrawsTheSameHand() {
		assertEquals(BrokenArmHold.pose(Weight.LIGHT, 0.3F, true, 123.45, false),
				BrokenArmHold.pose(Weight.LIGHT, 0.3F, true, 123.45, false));
	}

	@Test
	void theSwingStartsAndEndsAtRest() {
		for (Weight weight : Weight.values()) {
			assertEquals(0.0F, BrokenArmHold.labouredSwing(weight, 0.0F), 1.0e-6F);
			assertEquals(0.0F, BrokenArmHold.labouredSwing(weight, 1.0F), 1.0e-6F);
			assertEquals(0.0F, BrokenArmHold.swingDrop(0.0F), 1.0e-6F);
			assertEquals(0.0F, BrokenArmHold.swingDrop(1.0F), 1.0e-6F);
			assertEquals(rest(weight).drop(), BrokenArmHold.pose(weight, 1.0F, false, 0.0, false).drop(), 1.0e-6F);
		}
	}

	@Test
	void theSwingIsShortAndNeverJumps() {
		for (Weight weight : Weight.values()) {
			float peak = 0.0F;
			float last = 0.0F;
			float lastDrop = 0.0F;
			for (int i = 0; i <= 1000; i++) {
				float s = i / 1000.0F;
				float shown = BrokenArmHold.labouredSwing(weight, s);
				float drop = BrokenArmHold.swingDrop(s);
				assertTrue(shown >= 0.0F && shown <= 1.0F);
				assertTrue(Math.abs(shown - last) < 0.002F, weight + " swing jumps at " + s);
				// The fall is steep where it starts (a square root), so its bound is looser.
				assertTrue(Math.abs(drop - lastDrop) < 0.15F, weight + " drop jumps at " + s);
				peak = Math.max(peak, shown);
				last = shown;
				lastDrop = drop;
			}
			// Vanilla's hand is at its furthest at 0.25; a broken arm never gets there.
			assertTrue(peak < 0.12F, weight + " reach " + peak);
			assertTrue(peak > 0.0F);
		}
		assertTrue(BrokenArmHold.labouredSwing(Weight.HEAVY, 0.3F) < BrokenArmHold.labouredSwing(Weight.LIGHT, 0.3F));
	}

	@Test
	void theHandDropsAfterTheLiftNotDuringIt() {
		assertEquals(0.0F, BrokenArmHold.swingDrop(0.15F));
		assertEquals(0.0F, BrokenArmHold.swingDrop(0.30F), 1.0e-6F);
		assertTrue(BrokenArmHold.swingDrop(0.40F) > 0.8F, "the fall comes at once");
		// Deepest early, then a slow recovery.
		assertTrue(BrokenArmHold.swingDrop(0.45F) > BrokenArmHold.swingDrop(0.85F));
		Pose mid = BrokenArmHold.pose(Weight.HEAVY, 0.45F, false, 0.0, false);
		assertTrue(mid.drop() > rest(Weight.HEAVY).drop() + 0.1F);
		assertTrue(mid.tip() > rest(Weight.HEAVY).tip());
		assertTrue(BrokenArmHold.pose(Weight.HEAVY, 0.45F, false, 0.0, false).drop()
				> BrokenArmHold.pose(Weight.LIGHT, 0.45F, false, 0.0, false).drop());
	}

	@Test
	void aSwingOutOfRangeIsClamped() {
		assertEquals(BrokenArmHold.pose(Weight.MEDIUM, 0.0F, false, 1.0, false),
				BrokenArmHold.pose(Weight.MEDIUM, -3.0F, false, 1.0, false));
		assertEquals(BrokenArmHold.pose(Weight.MEDIUM, 1.0F, false, 1.0, false),
				BrokenArmHold.pose(Weight.MEDIUM, 7.0F, false, 1.0, false));
	}
}
