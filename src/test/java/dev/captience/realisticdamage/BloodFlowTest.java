package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.WoundSeverity;
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

	/**
	 * Kept pointed at the <em>drop</em> ceiling rather than the pool one after the
	 * two were split apart. What this test is about has not changed — one drop,
	 * one mark, bounded — and asserting it against the pool ceiling instead would
	 * quietly stop testing anything, because the pool ceiling is deliberately the
	 * looser of the two.
	 */
	@Test
	void stainsStayWithinTheirSizeRange() {
		for (double scale = 0.0; scale <= 1.0; scale += 0.01) {
			double size = BloodFlow.stainSize(scale);
			assertTrue(size >= Balance.STAIN_DROP_SIZE_MIN && size <= Balance.STAIN_DROP_SIZE_MAX,
					"stain size " + size + " out of range at scale " + scale);
		}
	}

	/**
	 * The one-line guard that makes the whole class of bug unrepeatable.
	 *
	 * <p>1.5 shipped with a single constant capping both what one drop could mark
	 * and what a mark fed by many drops could grow to. It followed that a
	 * fully-fed pool was exactly the size of one arterial splat: there was no size
	 * the field could reach that a single drop could not have produced on its own,
	 * so pooling was not weakly expressed, it was unexpressible. The playtest
	 * report on it is titled with the author's own words — "blood doesn't really
	 * pool? it just makes these weird splatters".
	 *
	 * <p>Nothing about that needed a running client to catch. This is the
	 * assertion that would have.
	 */
	@Test
	void aPoolCanGetBiggerThanAnySingleDropEverMarks() {
		assertTrue(Balance.STAIN_POOL_SIZE_MAX > Balance.STAIN_DROP_SIZE_MAX,
				"a fed pool cannot grow past what one drop already marks, so pooling has no size to express");
		// ...and it stays inside its own block. A stain is a flat quad of
		// half-extent radius, so a mark of this size lying against a wall pokes
		// exactly this far through it.
		assertTrue(Balance.STAIN_POOL_SIZE_MAX < 0.5,
				"a pool quad this big clips more than half a block into whatever is beside it");
		// The sprite tier can only be reached by accretion: no single drop may
		// land already drawn as a puddle. That sentence is about what ONE DROP
		// marks, and the assertion under it used to compare against the pool
		// ceiling instead — 0.30 < 0.46, true for every value the tier could
		// possibly hold, so it checked nothing the comment claimed. A gout marked
		// at 0.32, over the 0.30 tier, the whole time this test was green.
		assertTrue(Balance.STAIN_POOL_SPRITE_RADIUS > Balance.STAIN_DROP_SIZE_MAX,
				"one drop marks up to " + Balance.STAIN_DROP_SIZE_MAX
						+ ", which is already at the pool sprite tier");
		// ...and the tier still has to be reachable, or the pool sprites are art
		// nothing ever draws. This is what the assertion above used to be, kept
		// for the half of the job it did do.
		assertTrue(Balance.STAIN_POOL_SPRITE_RADIUS < Balance.STAIN_POOL_SIZE_MAX,
				"nothing ever reaches the pool sprite");
	}

	/**
	 * A pool is how many drops built it, not how big it is — and this is the
	 * assertion that makes the alternative unrepeatable without a running client.
	 *
	 * <p>An arterial gout marks at 94% of what any drop may mark, and one
	 * heartbeat throws a whole burst of them from one point on one tick, of which
	 * roughly two in five land near enough to merge. So a size threshold anywhere
	 * below the pool ceiling is a threshold a single beat can cross, and at severe
	 * and critical rates every beat did: a sprinting player laid one "pool" per
	 * heartbeat, evenly spaced three blocks apart, all the same size. The author's
	 * words were "each tick makes this weird pool ... like i was standing still (i
	 * wasn't, i was constantly moving)". The even spacing was his pulse.
	 */
	@Test
	void noSingleHeartbeatCanMintAPool() {
		// A burst of N drops lands one mark and feeds it N-1 times, so a gate at
		// the burst cap clears the largest burst there is with a feed to spare.
		assertTrue(Balance.STAIN_POOL_SPRITE_FEEDS >= Balance.ARTERIAL_SPURT_DROPS_MAX,
				"one heartbeat throws " + Balance.ARTERIAL_SPURT_DROPS_MAX
						+ " drops and they can all land in one mark");

		// Concretely, and this is the part no radius could have survived: feed a
		// critical gout its own worst-case burst and count what it takes.
		double gout = BloodFlow.stainSize(BloodFlow.dropScale(
				BloodFlow.dropSpeed(Balance.BLEED_RATE_CRITICAL, true) / 20.0));
		double radius = gout;
		int feeds = 0;
		for (int i = 1; i < Balance.ARTERIAL_SPURT_DROPS_MAX; i++) {
			radius = Math.min(Balance.STAIN_POOL_SIZE_MAX, radius + gout * Balance.STAIN_MERGE_GROWTH);
			feeds++;
		}
		assertTrue(feeds < Balance.STAIN_POOL_SPRITE_FEEDS,
				"one beat's worth of gouts landing in one mark draws it as a pool");
		// ...and by then it is at the pool CEILING, which is the proof that no
		// radius alone could ever have separated a heartbeat from an accretion:
		// there is no size above this one for a threshold to hide at.
		assertEquals(Balance.STAIN_POOL_SIZE_MAX, radius, 1.0e-9,
				"the burst no longer fills a mark; re-derive the gate above");
	}

	@Test
	void severanceLeavesMoreThanAnOrdinaryWoundOpening() {
		int ordinary = BloodFlow.burstDrops(WoundSeverity.CRITICAL.ordinal(), false);
		int severance = BloodFlow.burstDrops(WoundSeverity.CRITICAL.ordinal(), true);
		assertEquals(Balance.SPRAY_DROPS_PER_SEVERITY
				* (1 + WoundSeverity.CRITICAL.ordinal()), ordinary);
		assertEquals(36, severance, "the severance mark was tuned as a thirty-six-drop event");
		assertTrue(severance > ordinary, "losing a limb left no more trace than opening its wound");
	}

	/** The ordinary cone was live-tuned in 1.5; lifting its maths must not retune it. */
	@Test
	void ordinaryBurstParametersAreBitIdenticalAfterTheSeveranceSplit() {
		for (int i = 0; i < 40; i++) {
			assertEquals(Math.toRadians(BloodFlow.jitterSigned(i, 31) * Balance.SPRAY_SPREAD),
					BloodFlow.burstYaw(i, Balance.SPRAY_SPREAD));
			assertEquals(Math.toRadians(BloodFlow.jitterSigned(i, 32) * Balance.SPRAY_SPREAD),
					BloodFlow.burstPitch(i, Balance.SPRAY_SPREAD));
			assertEquals(0.55 + 0.45 * BloodFlow.jitter(i, 33), BloodFlow.burstSpeed(i));
		}
	}

	@Test
	void aSeveranceConeIsBiasedTowardTheFloor() {
		double ordinaryY = 0.0;
		double severanceY = 0.0;
		for (int i = 0; i < 36; i++) {
			ordinaryY += Math.sin((float) BloodFlow.burstPitch(i, Balance.SPRAY_SPREAD));
			severanceY += Math.sin((float) BloodFlow.burstPitch(i, Balance.SEVERANCE_SPRAY_SPREAD))
					- Balance.SEVERANCE_DOWN_BIAS;
		}
		assertTrue(severanceY / 36.0 < ordinaryY / 36.0 - 0.30,
				"the extra blood was not aimed toward the surface meant to record the loss");
		assertTrue(Math.abs(BloodFlow.burstYaw(3, Balance.SEVERANCE_SPRAY_SPREAD))
				> Math.abs(BloodFlow.burstYaw(3, Balance.SPRAY_SPREAD)),
				"the severance cone did not get wider");
	}

	@Test
	void contactTransferTracksTheWoundWithoutBecomingASecondEmitter() {
		double minor = BloodFlow.contactTransfersPerSecond(Balance.BLEED_RATE_MINOR);
		double critical = BloodFlow.contactTransfersPerSecond(Balance.BLEED_RATE_CRITICAL);
		assertTrue(minor > 0.0, "a bleeding body pressed against stone never leaves a touch");
		assertTrue(critical > minor, "an artery and a scratch transfer at the same rate");
		assertTrue(critical < BloodFlow.dropsPerSecond(Balance.BLEED_RATE_CRITICAL),
				"contact transfer duplicated more blood than the wound visibly emits");
	}

	@Test
	void onlyLargeWetWallMarksRun() {
		assertEquals(0.0, BloodFlow.wallRunDropsPerSecond(
				Balance.STAIN_WALL_RUN_RADIUS - 0.001, 1.0));
		assertEquals(0.0, BloodFlow.wallRunDropsPerSecond(Balance.STAIN_POOL_SIZE_MAX, 0.0));
		double starting = BloodFlow.wallRunDropsPerSecond(Balance.STAIN_WALL_RUN_RADIUS, 1.0);
		double full = BloodFlow.wallRunDropsPerSecond(Balance.STAIN_POOL_SIZE_MAX, 1.0);
		assertTrue(starting > 0.0 && full > starting,
				"a fed wet wall mark never starts moving, or size carries no weight");
		assertTrue(BloodFlow.wallRunDropsPerSecond(Balance.STAIN_POOL_SIZE_MAX, 0.5) < full,
				"drying blood runs as quickly as fresh blood");
	}

	/**
	 * How long a pool takes to fill, which is the difference between watching it
	 * happen and finding it already finished.
	 *
	 * <p>At the old growth of 0.35 a seep mark took five feeds — two and a half
	 * seconds — to saturate, and every drop after that was discarded. Fifteen
	 * feeds is the floor at which the growth is slow enough to be a thing the
	 * player sees rather than a state they arrive at; the value in {@link Balance}
	 * currently buys twenty-three.
	 */
	@Test
	void aPoolFillsSlowlyEnoughToWatch() {
		double seep = BloodFlow.stainSize(BloodFlow.dropScale(Balance.DROP_SEEP_SPEED / 20.0));
		int feeds = 0;
		double radius = seep;
		while (radius < Balance.STAIN_POOL_SIZE_MAX && feeds < 1000) {
			radius = Math.min(Balance.STAIN_POOL_SIZE_MAX, radius + seep * Balance.STAIN_MERGE_GROWTH);
			feeds++;
		}
		assertTrue(feeds >= 15,
				"a seep mark saturates in " + feeds + " drops, which is over before anyone looks down");
		// And it must actually get there: growth that never reaches the ceiling is
		// a pool that stops looking like it is filling.
		assertEquals(Balance.STAIN_POOL_SIZE_MAX, radius, 1.0e-9,
				"feeding a mark forever never fills it");
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
