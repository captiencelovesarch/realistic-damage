package dev.captience.realisticdamage;

import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.FallMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the 1.4 fall rework. Heights here are in blocks <em>beyond the safe fall
 * distance</em>, which is the unit the pipeline works in — add three to read
 * them as drops.
 */
class FallMathTest {
	/** A fall's trauma must track height, which is the whole point of the rework. */
	@Test
	void traumaScalesWithHeightNotDamage() {
		double shortFall = FallMath.traumaEquivalent(5.0, 1.0);
		double longFall = FallMath.traumaEquivalent(30.0, 1.0);
		assertTrue(longFall > shortFall * 5.0, "a six-times-longer drop must hurt at least six times as much");
		assertEquals(0.0, FallMath.traumaEquivalent(0.0, 1.0));
		assertEquals(0.0, FallMath.traumaEquivalent(-4.0, 1.0), "a fall inside the safe distance is not a fall");
	}

	@Test
	void softGroundGivesBack() {
		double onStone = FallMath.traumaEquivalent(20.0, Balance.LANDING_SURFACE_HARD);
		double onHay = FallMath.traumaEquivalent(20.0, Balance.LANDING_SURFACE_SOFT);
		assertTrue(onHay < onStone * 0.6, "hay must be worth substantially more than a rounding error");
	}

	@Test
	void sharesAlwaysSumToOne() {
		for (double height = 0.5; height <= 60.0; height += 0.5) {
			double sum = FallMath.shares(height, true).values().stream()
					.mapToDouble(Double::doubleValue).sum();
			assertEquals(1.0, sum, 1e-9, "shares must be normalized at height " + height);
		}
	}

	/**
	 * The bug the rework exists to fix: a short drop is a leg injury and nothing
	 * else. Head and torso have to stay out of it until the height earns them.
	 */
	@Test
	void shortFallsAreLegsOnly() {
		Map<BodyPart, Double> shares = FallMath.shares(5.0, true);
		assertFalse(shares.containsKey(BodyPart.HEAD), "a five-block-over-safe drop must not touch the head");
		assertFalse(shares.containsKey(BodyPart.TORSO), "...nor fold the torso");
		assertEquals(1.0, shares.get(BodyPart.LEFT_LEG) + shares.get(BodyPart.RIGHT_LEG), 1e-9);
	}

	@Test
	void theHeadOnlyJoinsInOnSeriousDrops() {
		assertFalse(FallMath.shares(Balance.FALL_SECONDARY_HEAD_HEIGHT - 0.1, true).containsKey(BodyPart.HEAD));
		assertTrue(FallMath.shares(Balance.FALL_SECONDARY_HEAD_HEIGHT + 2.0, true).containsKey(BodyPart.HEAD));
	}

	/**
	 * A share that snapped on at a threshold would make one extra block of fall
	 * feel like a different kind of accident. Measured against the share's own
	 * eventual value rather than an absolute figure: a step function would arrive
	 * at full strength immediately, so "still nowhere near full, one block in" is
	 * the property that actually distinguishes a ramp from a cliff.
	 */
	@Test
	void secondarySharesRampRatherThanSnap() {
		double oneBlockIn = share(FallMath.shares(Balance.FALL_SECONDARY_TORSO_HEIGHT + 1.0, true), BodyPart.TORSO);
		double fullyIn = share(FallMath.shares(Balance.FALL_SECONDARY_TORSO_FULL_HEIGHT, true), BodyPart.TORSO);
		assertTrue(oneBlockIn > 0.0, "the torso must start taking a share as soon as the threshold passes");
		assertTrue(oneBlockIn < fullyIn * 0.3,
				"one block past the threshold must be a hint, not the whole share: "
						+ oneBlockIn + " of " + fullyIn);
		// And it must be continuous across the threshold itself.
		double justUnder = share(FallMath.shares(Balance.FALL_SECONDARY_TORSO_HEIGHT - 0.01, true), BodyPart.TORSO);
		double justOver = share(FallMath.shares(Balance.FALL_SECONDARY_TORSO_HEIGHT + 0.01, true), BodyPart.TORSO);
		assertTrue(justOver - justUnder < 0.01, "the share must not jump at the threshold");
	}

	@Test
	void oneLegTakesTheBrunt() {
		Map<BodyPart, Double> shares = FallMath.shares(20.0, true);
		double lead = shares.get(BodyPart.LEFT_LEG);
		double trail = shares.get(BodyPart.RIGHT_LEG);
		assertTrue(lead > trail * 1.5, "the leading leg must clearly take the worse of it");

		// ...and it is genuinely the leading one, not always the same side.
		Map<BodyPart, Double> mirrored = FallMath.shares(20.0, false);
		assertEquals(lead, mirrored.get(BodyPart.RIGHT_LEG), 1e-9);
		assertEquals(trail, mirrored.get(BodyPart.LEFT_LEG), 1e-9);
	}

	@Test
	void gentleLandingsAreNearlyEven() {
		// You settle onto both feet from a hop; you land on one from a cliff.
		assertEquals(0.5, FallMath.leadingLegShare(0.0), 1e-9);
		assertEquals(Balance.FALL_LEADING_LEG_SHARE,
				FallMath.leadingLegShare(Balance.FALL_ASYMMETRY_FULL_HEIGHT * 2.0), 1e-9);
	}

	@Test
	void routineDropsDoNotKnockYouOver() {
		assertEquals(0, FallMath.knockdownTicks(0.0));
		assertEquals(0, FallMath.knockdownTicks(Balance.KNOCKDOWN_MIN_HEIGHT - 0.01),
				"jumping around must stay unremarkable");
		assertTrue(FallMath.knockdownTicks(Balance.KNOCKDOWN_MIN_HEIGHT) > 0);
	}

	@Test
	void knockdownGrowsWithHeightAndIsBounded() {
		int previous = -1;
		for (double height = 0.0; height <= 80.0; height += 0.5) {
			int ticks = FallMath.knockdownTicks(height);
			assertTrue(ticks >= previous, "a longer fall must never put you down for less time");
			assertTrue(ticks <= Balance.KNOCKDOWN_TICKS_MAX, "knockdown must stay bounded at height " + height);
			previous = ticks;
		}
		assertEquals(Balance.KNOCKDOWN_TICKS_MAX, FallMath.knockdownTicks(Balance.KNOCKDOWN_FULL_HEIGHT));
	}

	/** Even the worst landing has to hand control back quickly enough to fight for it. */
	@Test
	void knockdownIsMeasuredInSecondsNotMinutes() {
		double seconds = Balance.KNOCKDOWN_TICKS_MAX / 20.0;
		assertTrue(seconds <= 3.0, "being face-down must not outlast the player's patience, got " + seconds + "s");
		assertTrue(seconds >= 1.0, "...but must be long enough to be a real cost, got " + seconds + "s");
	}

	@Test
	void shortFallsKeepVanillaLethalityExactly() {
		for (int damage = 0; damage <= (int) Balance.FALL_HP_SOFT_CAP; damage++) {
			assertEquals(damage, FallMath.compressLethality(damage),
					"vanilla owns short falls; " + damage + " must pass through untouched");
		}
	}

	@Test
	void longFallsMaimRatherThanDelete() {
		// Vanilla fall damage is (distance - safe), so these are raw HP figures.
		// A 35-block drop: fatal in vanilla, survivable-from-full here.
		assertTrue(FallMath.compressLethality(32) < 20,
				"a 35-block drop should be survivable from full health after the rework");
		// ...but height still wins eventually.
		assertTrue(FallMath.compressLethality(47) >= 20,
				"a fifty-block drop must still kill outright");
	}

	@Test
	void lethalityCompressionIsMonotonic() {
		int previous = -1;
		for (int damage = 0; damage <= 500; damage++) {
			int softened = FallMath.compressLethality(damage);
			assertTrue(softened >= previous, "a longer fall must never hurt less, at " + damage);
			assertTrue(softened <= damage, "compression must never make a fall worse than vanilla, at " + damage);
			previous = softened;
		}
	}

	private static double share(Map<BodyPart, Double> shares, BodyPart part) {
		return shares.getOrDefault(part, 0.0);
	}
}
