package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.BloodFlow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.5 answer to "it's super hard to tell when you're bleeding". Blood is now
 * a readout of a real number, so these are mostly tests that the readout cannot
 * lie: a worse wound has to look worse, and a slow one still has to look like
 * something.
 */
class BloodFlowTest {
	@Test
	void aWorseWoundVisiblyBleedsHarder() {
		double minor = BloodFlow.dropsPerSecond(Balance.BLEED_RATE_MINOR);
		double moderate = BloodFlow.dropsPerSecond(Balance.BLEED_RATE_MODERATE);
		double severe = BloodFlow.dropsPerSecond(Balance.BLEED_RATE_SEVERE);
		double critical = BloodFlow.dropsPerSecond(Balance.BLEED_RATE_CRITICAL);
		assertTrue(minor < moderate, "a scratch must not look like a gash");
		assertTrue(moderate < severe);
		assertTrue(severe < critical);
	}

	/**
	 * The complaint that started this rework. The old emitter was a fixed burst
	 * every 25 ticks whatever the wound, so severity was invisible and the
	 * weakest wound in the game produced one particle every second and a quarter.
	 */
	@Test
	void evenTheSlowestBleedSaysSomething() {
		assertEquals(0.0, BloodFlow.dropsPerSecond(0.0), "no wound, no blood");
		assertTrue(BloodFlow.dropsPerSecond(Balance.BLEED_RATE_MINOR) >= Balance.DROPS_PER_SECOND_MIN,
				"a minor wound must still be visible at all");
	}

	@Test
	void theEmitterIsCappedHoweverTornUpYouAre() {
		for (double rate = 0.0; rate <= 20.0; rate += 0.05) {
			assertTrue(BloodFlow.dropsPerSecond(rate) <= Balance.DROPS_PER_SECOND_MAX,
					"drop rate ran away at " + rate);
		}
	}

	@Test
	void onlyTheWoundsThatWillKillYouSpurt() {
		assertTrue(!BloodFlow.arterial(Balance.BLEED_RATE_MINOR));
		assertTrue(!BloodFlow.arterial(Balance.BLEED_RATE_MODERATE),
				"a moderate wound seeps; the arterial read must stay meaningful");
		assertTrue(BloodFlow.arterial(Balance.BLEED_RATE_SEVERE));
		assertTrue(BloodFlow.arterial(Balance.BLEED_RATE_CRITICAL));
	}

	/** A spurting wound still leaks between beats. Hearts do not have a diastolic off switch. */
	@Test
	void anArteryStillBleedsBetweenBeats() {
		double share = BloodFlow.spurtShare(Balance.BLEED_RATE_CRITICAL);
		assertTrue(share > 0.0 && share < 1.0, "share was " + share);
		assertEquals(0.0, BloodFlow.spurtShare(Balance.BLEED_RATE_MINOR),
				"a wound that does not spurt holds nothing back");
	}

	@Test
	void spurtsLeaveFasterThanSeeps() {
		double seep = BloodFlow.dropSpeed(Balance.BLEED_RATE_CRITICAL, false);
		double spurt = BloodFlow.dropSpeed(Balance.BLEED_RATE_CRITICAL, true);
		assertTrue(spurt > seep * 4.0, "a spurt must read as pressure, not as a faster drip");
		assertEquals(seep, BloodFlow.dropSpeed(Balance.BLEED_RATE_MINOR, true),
				"a wound with no pressure behind it cannot throw anything, beat or no beat");
	}

	/**
	 * Severity travels in the velocity rather than in a custom particle payload,
	 * so this mapping is the entire channel: if it were flat, every drop on
	 * another player's screen would be the same size.
	 */
	@Test
	void velocityCarriesHowBigTheDropLooks() {
		assertEquals(0.0, BloodFlow.dropScale(0.0));
		assertEquals(1.0, BloodFlow.dropScale(Balance.DROP_SCALE_FULL_SPEED / 20.0), 1.0e-9);
		assertEquals(1.0, BloodFlow.dropScale(50.0), "must clamp rather than growing without bound");
		double slow = BloodFlow.dropScale(Balance.DROP_SEEP_SPEED / 20.0);
		double fast = BloodFlow.dropScale(Balance.ARTERIAL_SPURT_SPEED / 20.0);
		assertTrue(slow < fast, "a seep and a spurt must not look the same");
	}

	@Test
	void stainsStayWithinTheirSizeRange() {
		for (double scale = 0.0; scale <= 1.0; scale += 0.01) {
			double size = BloodFlow.stainSize(scale);
			assertTrue(size >= Balance.STAIN_SIZE_MIN && size <= Balance.STAIN_SIZE_MAX,
					"stain size " + size + " out of range at scale " + scale);
		}
	}

	/**
	 * A stain has to finish drying before it starts disappearing, or the two
	 * phases overlap and the brown stage — the one that says "this happened a
	 * while ago" — never gets drawn at full opacity.
	 */
	@Test
	void bloodDriesBeforeItFades() {
		assertTrue(Balance.STAIN_WET_FRACTION < 1.0 - Balance.STAIN_FADE_FRACTION,
				"the wet and fading phases overlap, so the dried look never lands");
		double dryEnd = 1.0 - Balance.STAIN_FADE_FRACTION;
		assertEquals(0.0, BloodFlow.stainWetness(dryEnd), 1.0e-9);
		assertEquals(1.0, BloodFlow.stainAlpha(dryEnd), 1.0e-9,
				"a stain must still be fully opaque at the moment it finishes drying");
	}

	@Test
	void stainsOnlyEverGetDrierAndFainter() {
		double previousWet = Double.MAX_VALUE;
		double previousAlpha = Double.MAX_VALUE;
		for (double life = 0.0; life <= 1.0; life += 0.005) {
			double wet = BloodFlow.stainWetness(life);
			double alpha = BloodFlow.stainAlpha(life);
			assertTrue(wet <= previousWet + 1.0e-9, "blood re-wetted itself at " + life);
			assertTrue(alpha <= previousAlpha + 1.0e-9, "a stain got darker again at " + life);
			previousWet = wet;
			previousAlpha = alpha;
		}
		assertEquals(0.0, BloodFlow.stainAlpha(1.0), "a stain must be gone at the end of its life");
	}

	/**
	 * The accumulator exists because the obvious alternative — emit when
	 * {@code tickCount % n == 0} — cannot express a rate below one drop per tick
	 * without rounding it to zero or to one, and that is the entire range
	 * ordinary bleeding lives in.
	 */
	@Test
	void theAccumulatorEmitsTheRateItWasGiven() {
		for (double rate : new double[] {0.8, 1.0, 2.4, 4.8, 8.0, 12.0}) {
			double carry = 0.0;
			int emitted = 0;
			int ticks = 20 * 60;
			for (int i = 0; i < ticks; i++) {
				emitted += BloodFlow.dropsThisTick(carry, rate);
				carry = BloodFlow.carryAfter(carry, rate);
				assertTrue(carry >= 0.0 && carry < 1.0, "carry escaped its range: " + carry);
			}
			double expected = rate * ticks / 20.0;
			assertEquals(expected, emitted, 2.0,
					"emitted " + emitted + " drops for a rate of " + rate + "/s over a minute");
		}
	}

	/**
	 * Staining a body has to take long enough to watch. The first version gained
	 * 0.55 per second per Blood/sec, so a critical wound hit the cap inside a
	 * single second and a nick looked the same as a severed artery one tick after
	 * either was inflicted — the gradient existed in the code and never on screen.
	 */
	@Test
	void bloodTakesTimeToSoakIntoSomebody() {
		double criticalSeconds = Balance.BODY_STAIN_MAX
				/ (Balance.BLEED_RATE_CRITICAL * Balance.BODY_STAIN_GAIN);
		assertTrue(criticalSeconds > 8.0,
				"a critical wound soaks a limb in " + criticalSeconds + "s, too fast to read as a change");
		assertTrue(criticalSeconds < 40.0,
				"a critical wound takes " + criticalSeconds + "s to show, which is too long to connect to the cause");
		double minorSeconds = Balance.BODY_STAIN_MAX
				/ (Balance.BLEED_RATE_MINOR * Balance.BODY_STAIN_GAIN);
		assertTrue(minorSeconds > criticalSeconds * 4.0,
				"a scratch and an artery should not stain you at comparable speeds");
	}

	/** Blood dries on rather than rinsing off, so it must outlast the wound. */
	@Test
	void bloodOutlastsTheWoundThatMadeIt() {
		double dryingSeconds = Balance.BODY_STAIN_MAX / Balance.BODY_STAIN_DECAY;
		assertTrue(dryingSeconds > 30.0,
				"blood came off in " + dryingSeconds + "s, which reads as rinsing rather than drying");
	}

	@Test
	void aHeadWoundReachesYourEyesAndAShinWoundDoesNot() {
		assertTrue(BloodFlow.lensWeight(BodyPart.HEAD) > BloodFlow.lensWeight(BodyPart.LEFT_LEG),
				"a cut scalp and a cut shin must not fog the screen equally");
		assertTrue(BloodFlow.lensWeight(BodyPart.LEFT_LEG) > 0.0,
				"...but blood on your hands still gets there eventually");
		assertEquals(BloodFlow.lensWeight(BodyPart.LEFT_LEG), BloodFlow.lensWeight(BodyPart.RIGHT_ARM),
				"everything below the neck is the same story");
	}

	/**
	 * Presentation is allowed variety; it is not allowed to be irreproducible.
	 * A generator with shared mutable state would let two clients draw the same
	 * drop in two places, which is the same reasoning that keeps RNG out of the
	 * injury model.
	 */
	@Test
	void theScatterIsAHashAndNotAGenerator() {
		for (long i = 0; i < 1000; i++) {
			assertEquals(BloodFlow.jitter(i, 1), BloodFlow.jitter(i, 1),
					"the same drop must scatter the same way every time");
			double value = BloodFlow.jitter(i, 1);
			assertTrue(value >= 0.0 && value < 1.0, "jitter left [0,1) at " + i + ": " + value);
		}
		assertNotEquals(BloodFlow.jitter(7, 1), BloodFlow.jitter(7, 2),
				"channels must not agree, or every axis of a spray scatters identically");
	}

	@Test
	void theScatterIsActuallySpreadOut() {
		int[] buckets = new int[10];
		for (long i = 0; i < 10_000; i++) {
			buckets[(int) (BloodFlow.jitter(i, 5) * 10.0)]++;
		}
		for (int i = 0; i < buckets.length; i++) {
			assertTrue(buckets[i] > 800 && buckets[i] < 1200,
					"bucket " + i + " held " + buckets[i] + " of 10000 — the hash is lumpy");
		}
	}

	@Test
	void signedScatterCoversBothDirections() {
		double lowest = Double.MAX_VALUE;
		double highest = -Double.MAX_VALUE;
		for (long i = 0; i < 5000; i++) {
			double value = BloodFlow.jitterSigned(i, 9);
			assertTrue(value >= -1.0 && value < 1.0, "signed jitter left [-1,1) at " + i);
			lowest = Math.min(lowest, value);
			highest = Math.max(highest, value);
		}
		assertTrue(lowest < -0.95 && highest > 0.95, "a spray cone that never reaches its own edges");
	}
}
