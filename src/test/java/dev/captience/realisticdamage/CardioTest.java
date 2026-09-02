package dev.captience.realisticdamage;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.Cardio;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 1.5 answer to "shouldn't the heartbeat be irregular at times". Three
 * separate defects in the old model, one test group each.
 */
class CardioTest {
	/**
	 * How long the loudest beat this heart produces stays audible, in ticks.
	 *
	 * <p>Deliberately the longest over many beats rather than the first one seen.
	 * Above the ectopy threshold the first beat may well be a misfire, which is
	 * quiet by design and drops under the audibility floor almost immediately —
	 * measuring that and calling it "the shape of the beat" tests the arrhythmia
	 * rather than the envelope. Ticks are counted rather than required to be
	 * contiguous because the envelope dips between the first sound decaying and
	 * the second arriving, and a sample can land in that gap.
	 */
	private static int thumpTicks(double fromHealth, double fromBlood, double fromMind) {
		Cardio heart = new Cardio();
		int longest = 0;
		int current = 0;
		boolean started = false;
		for (int i = 0; i < 6000; i++) {
			heart.tick(fromHealth, fromBlood, fromMind, 0.5);
			if (heart.beatJustFired()) {
				if (started) {
					longest = Math.max(longest, current);
				}
				started = true;
				current = 0;
			}
			if (started && heart.envelope() > 0.05) {
				current++;
			}
		}
		return longest;
	}

	private static double averageBpm(double fromHealth, double fromBlood, double fromMind) {
		Cardio heart = new Cardio();
		double total = 0.0;
		int samples = 0;
		for (int i = 0; i < 600; i++) {
			heart.tick(fromHealth, fromBlood, fromMind, 0.5);
			total += heart.bpm();
			samples++;
		}
		return total / samples;
	}

	/**
	 * The bug that made a racing heart sound like a click track. The envelope's
	 * decay constants used to be fractions of a <em>beat</em>, so the thump was
	 * 60ms at rest and 20ms at 165bpm: the closer to death you were, the tighter
	 * and more mechanical it got. Both figures are in seconds now, so the shape
	 * of a beat cannot depend on how fast they are arriving.
	 */
	@Test
	void theBeatKeepsItsShapeHoweverFastItGets() {
		int resting = thumpTicks(0.0, 0.0, 0.0);
		int racing = thumpTicks(0.34, 0.0, 0.0);
		assertTrue(resting > 3, "the beat is too short to measure, which makes this test meaningless");
		assertEquals(resting, racing,
				"the thump lasted " + resting + " ticks at rest and " + racing + " racing");
	}

	/** ...and it must genuinely have been racing, or the test above proves nothing. */
	@Test
	void theRateReallyDoesClimb() {
		double resting = averageBpm(0.0, 0.0, 0.0);
		double racing = averageBpm(0.34, 0.0, 0.0);
		assertEquals(Balance.HEART_REST_BPM, resting, 1.0);
		assertTrue(racing > resting * 1.4, "only got from " + resting + " to " + racing + " bpm");
	}

	/**
	 * The second defect: distress was one number, so every cause of it produced
	 * the same racing beat. A bled-out heart and a failing one do not feel alike
	 * and now do not sound alike either.
	 */
	@Test
	void bloodLossRacesAndHeadTraumaSlows() {
		double thready = averageBpm(0.0, 1.0, 0.0);
		double agonal = averageBpm(0.0, 0.0, 1.0);
		double pounding = averageBpm(1.0, 0.0, 0.0);
		assertTrue(thready > pounding, "bleeding out should be the fastest of the three");
		assertTrue(agonal < Balance.HEART_REST_BPM,
				"a failing brainstem should slow the heart, not speed it up — got " + agonal);
		assertTrue(agonal < pounding * 0.5, "the two must be unmistakably different");
	}

	@Test
	void theRateNeverLeavesItsBounds() {
		for (double a = 0.0; a <= 1.0; a += 0.1) {
			for (double b = 0.0; b <= 1.0; b += 0.1) {
				for (double c = 0.0; c <= 1.0; c += 0.1) {
					Cardio heart = new Cardio();
					for (int i = 0; i < 60; i++) {
						heart.tick(a, b, c, 0.5);
						assertTrue(heart.bpm() >= Balance.HEART_MIN_BPM
										&& heart.bpm() <= Balance.HEART_MAX_BPM,
								"bpm " + heart.bpm() + " at " + a + "/" + b + "/" + c);
					}
				}
			}
		}
	}

	/**
	 * The third defect, and the one actually asked about: the beat could get
	 * faster but it could never falter. Nothing may falter while you are fine,
	 * though — an irregular pulse at full health would be a false alarm.
	 */
	@Test
	void aHealthyHeartIsRegular() {
		assertEquals(0.0, Cardio.ectopyChance(0.0));
		assertEquals(0.0, Cardio.ectopyChance(Balance.ECTOPY_THRESHOLD));
		for (long beat = 0; beat < 2000; beat++) {
			assertTrue(!Cardio.isEctopic(beat, Balance.ECTOPY_THRESHOLD),
					"beat " + beat + " misfired while the player was fine");
		}
	}

	@Test
	void theWorseItGetsTheMoreItStumbles() {
		double previous = -1.0;
		for (double distress = Balance.ECTOPY_THRESHOLD; distress <= 1.0; distress += 0.01) {
			double chance = Cardio.ectopyChance(distress);
			assertTrue(chance >= previous, "ectopy became less likely as things got worse, at " + distress);
			previous = chance;
		}
		assertEquals(Balance.ECTOPY_CHANCE_MAX, Cardio.ectopyChance(1.0), 1.0e-9);

		int misfires = 0;
		for (long beat = 0; beat < 4000; beat++) {
			if (Cardio.isEctopic(beat, 1.0)) {
				misfires++;
			}
		}
		// The never-two-in-a-row rule suppresses some, so the realised share sits
		// below the nominal one — but it has to be a real share, not a rarity.
		double share = misfires / 4000.0;
		assertTrue(share > Balance.ECTOPY_CHANCE_MAX * 0.5 && share <= Balance.ECTOPY_CHANCE_MAX,
				"one beat in " + (misfires == 0 ? "never" : String.valueOf(4000 / misfires)) + " misfired");
	}

	/**
	 * Back-to-back ectopics read as the beat breaking down rather than as it
	 * stumbling, and leave no compensatory pause between them for either one to
	 * land against.
	 */
	@Test
	void itStumblesButNeverCollapses() {
		for (double distress : new double[] {0.5, 0.75, 1.0}) {
			for (long beat = 1; beat < 3000; beat++) {
				if (Cardio.isEctopic(beat, distress)) {
					assertTrue(!Cardio.isEctopic(beat + 1, distress),
							"beats " + beat + " and " + (beat + 1) + " both misfired at " + distress);
				}
			}
		}
	}

	/**
	 * The whole point of an ectopic beat is what comes after it: a pause longer
	 * than an ordinary interval, then a beat that lands harder than any normal
	 * one. That "skipped a beat, then a thump" is the thing people describe.
	 */
	@Test
	void aMisfireIsFollowedByAPauseAndThenAHarderBeat() {
		assertTrue(Balance.ECTOPY_EARLY_SCALE < 1.0, "an ectopic beat arrives early");
		assertTrue(Balance.ECTOPY_WEAK_AMPLITUDE < 1.0, "...and weakly");
		assertTrue(Balance.ECTOPY_PAUSE_SCALE > 1.0, "the pause after it is longer than normal");
		assertTrue(Balance.ECTOPY_THUMP_AMPLITUDE > 1.0, "and the beat that ends it lands harder");

		Cardio heart = new Cardio();
		double weakest = Double.MAX_VALUE;
		double strongest = 0.0;
		int beats = 0;
		for (int i = 0; i < 20_000 && beats < 300; i++) {
			heart.tick(1.0, 0.0, 0.0, 0.5);
			if (heart.beatJustFired()) {
				beats++;
				weakest = Math.min(weakest, heart.beatAmplitude());
				strongest = Math.max(strongest, heart.beatAmplitude());
			}
		}
		assertTrue(beats > 100, "only saw " + beats + " beats");
		assertTrue(strongest > 1.2, "no compensatory thump ever landed; strongest was " + strongest);
		assertTrue(weakest < 0.6, "no beat ever misfired; weakest was " + weakest);
	}

	/** A thready pulse is fast and faint. Racing alone would only be half of it. */
	@Test
	void bleedingOutMakesThePulseFaintAsWellAsFast() {
		Cardio bleeding = new Cardio();
		Cardio beaten = new Cardio();
		double bleedingPeak = 0.0;
		double beatenPeak = 0.0;
		for (int i = 0; i < 2000; i++) {
			bleeding.tick(0.0, 1.0, 0.0, 0.5);
			beaten.tick(1.0, 0.0, 0.0, 0.5);
			bleedingPeak = Math.max(bleedingPeak, bleeding.envelope());
			beatenPeak = Math.max(beatenPeak, beaten.envelope());
		}
		assertTrue(bleedingPeak < beatenPeak,
				"a bled-out pulse peaked at " + bleedingPeak + " against " + beatenPeak + " for a beaten one");
	}

	/** The slow heavy beat has to be heavy, or it is just a slow one. */
	@Test
	void theAgonalBeatIsLongerAsWellAsSlower() {
		int agonal = thumpTicks(0.0, 0.0, 1.0);
		int ordinary = thumpTicks(0.0, 0.0, 0.0);
		assertTrue(agonal > ordinary * 1.5,
				"a failing heart's beat lasted " + agonal + " ticks against " + ordinary + " for a resting one");
	}

	/**
	 * The 1.5 death sequence: the heart winds down rather than being switched
	 * off. Beats spread out as the fade advances and stop before it finishes —
	 * the silence at the end of dying is part of the design, not an accident of
	 * the fade outrunning the audio.
	 */
	@Test
	void aDyingHeartSlowsAndThenStops() {
		Cardio heart = new Cardio();
		double fadePerTick = 1.0 / (Balance.DEATH_FADE_SECONDS * 20.0);
		double fade = 0.0;
		java.util.List<Integer> gaps = new java.util.ArrayList<>();
		int sinceLast = 0;
		int lastBeatTick = -1;
		int totalTicks = (int) (Balance.DEATH_FADE_SECONDS * 20.0) + 100;
		for (int tick = 0; tick < totalTicks; tick++) {
			fade = Math.min(1.0, fade + fadePerTick);
			heart.setMoribund(fade);
			heart.tick(0.0, 0.0, 1.0, 0.5);
			sinceLast++;
			if (heart.beatJustFired()) {
				if (lastBeatTick >= 0) {
					gaps.add(sinceLast);
				}
				lastBeatTick = tick;
				sinceLast = 0;
			}
		}
		assertTrue(gaps.size() >= 2, "a dying heart should still manage a few beats, got " + gaps.size());
		// The trend, not each step: full agonal distress means the dying heart is
		// also ectopic, so an early beat and its compensatory pause make single
		// gaps jump around. What must hold is that the second half of the beats
		// are further apart than the first half — it is running down, stumbles
		// and all.
		double firstHalf = gaps.subList(0, gaps.size() / 2).stream()
				.mapToInt(Integer::intValue).average().orElse(0.0);
		double secondHalf = gaps.subList(gaps.size() / 2, gaps.size()).stream()
				.mapToInt(Integer::intValue).average().orElse(0.0);
		assertTrue(secondHalf > firstHalf, "the gaps should stretch as it fails: " + gaps);
		assertTrue(lastBeatTick < totalTicks - 60,
				"the heart must fall silent before the end, last beat at tick " + lastBeatTick
						+ " of " + totalTicks);
	}

	/**
	 * The dying beats drain away. Not strictly one by one — a compensatory thump
	 * after an ectopic stumble may land harder than the weak beat before it, and
	 * that stumble is wanted — but every beat lives under a ceiling that falls
	 * with the fade, and the loudest early beat must dwarf the loudest late one.
	 */
	@Test
	void theDyingBeatsDrainAway() {
		Cardio heart = new Cardio();
		double fadePerTick = 1.0 / (Balance.DEATH_FADE_SECONDS * 20.0);
		double fade = 0.0;
		double loudestEarly = 0.0;
		double loudestLate = 0.0;
		int totalTicks = (int) (Balance.DEATH_FADE_SECONDS * 20.0);
		for (int tick = 0; tick < totalTicks; tick++) {
			fade = Math.min(1.0, fade + fadePerTick);
			heart.setMoribund(fade);
			heart.tick(0.0, 0.0, 1.0, 0.5);
			if (heart.beatJustFired()) {
				double life = Math.clamp(1.0 - fade / Balance.DEATH_HEART_SILENT, 0.0, 1.0);
				double ceiling = Balance.ECTOPY_THUMP_AMPLITUDE * life + 1.0e-9;
				assertTrue(heart.beatAmplitude() <= ceiling,
						"beat at fade " + fade + " broke its ceiling: "
								+ heart.beatAmplitude() + " > " + ceiling);
				if (fade < 0.25) {
					loudestEarly = Math.max(loudestEarly, heart.beatAmplitude());
				} else if (fade > 0.4) {
					loudestLate = Math.max(loudestLate, heart.beatAmplitude());
				}
			}
		}
		assertTrue(loudestEarly > 0.5, "the first dying beats should still land, got " + loudestEarly);
		assertTrue(loudestLate < loudestEarly * 0.6,
				"the late beats should be a fraction of the early ones: "
						+ loudestLate + " against " + loudestEarly);
	}

	/**
	 * The realistic death style's one non-negotiable: hearing outlasts sight.
	 * Vision is fully gone at {@code DEATH_BLACK_AT}; with the realistic silent
	 * point the heart must still land audible beats after that, and must still
	 * be silent before the fade ends — the last stretch belongs to nothing.
	 */
	@Test
	void theRealisticHeartBeatsInTheDark() {
		Cardio heart = new Cardio();
		double fadePerTick = 1.0 / (Balance.DEATH_FADE_SECONDS_REALISTIC * 20.0);
		double fade = 0.0;
		boolean beatAfterBlack = false;
		int lastBeatTick = -1;
		int totalTicks = (int) (Balance.DEATH_FADE_SECONDS_REALISTIC * 20.0);
		for (int tick = 0; tick < totalTicks; tick++) {
			fade = Math.min(1.0, fade + fadePerTick);
			heart.setMoribund(fade, Balance.DEATH_HEART_SILENT_REALISTIC);
			heart.tick(0.0, 0.0, 1.0, 0.5);
			if (heart.beatJustFired()) {
				lastBeatTick = tick;
				if (fade > Balance.DEATH_BLACK_AT) {
					beatAfterBlack = true;
				}
			}
		}
		assertTrue(beatAfterBlack,
				"no beat landed after the blackout — sight outlasted hearing, which is backwards");
		assertTrue(lastBeatTick < totalTicks - 20,
				"the heart must still fall silent before the fade ends, last beat at "
						+ lastBeatTick + " of " + totalTicks);
	}

	/** Dying must not leak into the next life: reset clears the moribund state. */
	@Test
	void respawnGetsAHealthyHeart() {
		Cardio heart = new Cardio();
		heart.setMoribund(1.0);
		for (int i = 0; i < 200; i++) {
			heart.tick(0.0, 0.0, 1.0, 0.5);
		}
		heart.reset();
		double strongest = 0.0;
		for (int i = 0; i < 200; i++) {
			heart.tick(0.0, 0.0, 0.0, 0.5);
			if (heart.beatJustFired()) {
				strongest = Math.max(strongest, heart.beatAmplitude());
			}
		}
		assertEquals(1.0, strongest, 1.0e-9, "the fresh heart is still dying");
	}

	@Test
	void aResetHeartIsAFreshOne() {
		Cardio heart = new Cardio();
		for (int i = 0; i < 500; i++) {
			heart.tick(1.0, 1.0, 1.0, 0.5);
		}
		assertTrue(heart.beatIndex() > 0);
		heart.reset();
		assertEquals(0, heart.beatIndex());
		assertEquals(Balance.HEART_REST_BPM, heart.bpm());
	}
}
