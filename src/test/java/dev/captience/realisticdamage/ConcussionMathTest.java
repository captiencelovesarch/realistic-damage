package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ConcussionMath;
import dev.captience.realisticdamage.systems.ConcussionMath.Activity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The graded concussion (1.7): bands, second impact, healing by rest, flare, vertigo. */
class ConcussionMathTest {
	@Test
	void severityReadsAsThreeBandsAndNothingAtZero() {
		assertEquals(ConcussionMath.NONE, ConcussionMath.grade(0.0));
		assertEquals(ConcussionMath.MILD, ConcussionMath.grade(0.01));
		assertEquals(ConcussionMath.MILD, ConcussionMath.grade(Balance.CONCUSSION_GRADE_MODERATE - 0.01));
		assertEquals(ConcussionMath.MODERATE, ConcussionMath.grade(Balance.CONCUSSION_GRADE_MODERATE));
		assertEquals(ConcussionMath.SEVERE, ConcussionMath.grade(Balance.CONCUSSION_GRADE_SEVERE));
		assertEquals(ConcussionMath.SEVERE, ConcussionMath.grade(100.0));
	}

	/** The two ways in: the INJURED-head path is mild, the 6.0 bar is moderate, a huge blow is severe. */
	@Test
	void aFirstKnockIsGradedByHowHardItWas() {
		assertEquals(ConcussionMath.MILD, ConcussionMath.grade(ConcussionMath.freshSeverity(
				Balance.CONCUSSION_HEAD_TRAUMA_INJURED_THRESHOLD)));
		assertEquals(ConcussionMath.MODERATE, ConcussionMath.grade(ConcussionMath.freshSeverity(
				Balance.CONCUSSION_HEAD_TRAUMA_THRESHOLD)));
		assertEquals(ConcussionMath.SEVERE, ConcussionMath.grade(ConcussionMath.freshSeverity(12.0)));
	}

	@Test
	void secondImpactKnocksOutSoonerTheWorseTheConcussion() {
		double tap = 2.5;
		assertFalse(ConcussionMath.secondImpactKnocksOut(ConcussionMath.MILD, tap));
		assertFalse(ConcussionMath.secondImpactKnocksOut(ConcussionMath.MODERATE, tap));
		assertTrue(ConcussionMath.secondImpactKnocksOut(ConcussionMath.SEVERE, tap));
		assertFalse(ConcussionMath.secondImpactKnocksOut(ConcussionMath.NONE, 100.0),
				"no concussion, no second impact");
		// And it always deepens by more than the minimum hit would.
		assertTrue(ConcussionMath.secondImpactSeverity(Balance.SECOND_IMPACT_MIN_TRAUMA)
				> Balance.SECOND_IMPACT_BASE);
	}

	/** Moderate at zero flare is the 1.6 shift exactly, so nothing tuned against it moved. */
	@Test
	void moderateIsTheOldFlatShiftAndFlareBringsItFurther() {
		assertEquals(Balance.CONCUSSION_SYMPTOM_SHIFT, ConcussionMath.symptomShift(ConcussionMath.MODERATE, 0.0), 1e-9);
		assertEquals(Vitals.effectiveConsciousness(60.0, true),
				Vitals.effectiveConsciousness(60.0, ConcussionMath.MODERATE, 0.0), 1e-9);
		assertTrue(ConcussionMath.symptomShift(ConcussionMath.MILD, 0.0)
				< ConcussionMath.symptomShift(ConcussionMath.SEVERE, 0.0));
		assertTrue(ConcussionMath.symptomShift(ConcussionMath.MODERATE, 100.0)
				> ConcussionMath.symptomShift(ConcussionMath.MODERATE, 0.0));
		assertEquals(0.0, ConcussionMath.symptomShift(ConcussionMath.NONE, 100.0),
				"a flare with no concussion under it shifts nothing");
	}

	@Test
	void restHealsExertionDoesNotAndABadHeadHalvesIt() {
		double rest = ConcussionMath.healPerSecond(Activity.RESTING, false, false);
		double walk = ConcussionMath.healPerSecond(Activity.LIGHT, false, false);
		assertTrue(rest > walk && walk > 0.0);
		assertEquals(0.0, ConcussionMath.healPerSecond(Activity.EXERTING, false, false));
		assertEquals(rest * Balance.CONCUSSION_HEAL_BAD_HEAD, ConcussionMath.healPerSecond(Activity.RESTING, true, false), 1e-12);
		assertTrue(ConcussionMath.healPerSecond(Activity.RESTING, false, true) > rest, "the dark helps");
	}

	/** A mild knock is a couple of minutes' rest; a severe one is minutes more, never an hour. */
	@Test
	void healingTimesAreInTheRightBallpark() {
		double rest = ConcussionMath.healPerSecond(Activity.RESTING, false, false);
		double mild = ConcussionMath.freshSeverity(Balance.CONCUSSION_HEAD_TRAUMA_INJURED_THRESHOLD) / rest;
		double severe = 85.0 / rest;
		assertTrue(mild > 60.0 && mild < 180.0, "mild at rest took " + mild + " s");
		assertTrue(severe > 180.0 && severe < 420.0, "severe at rest took " + severe + " s");
	}

	@Test
	void lightOnlyHurtsPastTheLineAndDarkSoothes() {
		assertEquals(0.0, ConcussionMath.lightFlarePerSecond(Balance.CONCUSSION_BRIGHT_LIGHT - 1));
		assertTrue(ConcussionMath.lightFlarePerSecond(Balance.CONCUSSION_BRIGHT_LIGHT) > 0.0);
		assertTrue(ConcussionMath.lightFlarePerSecond(15) > ConcussionMath.lightFlarePerSecond(13));
		assertTrue(ConcussionMath.flareDecayPerSecond(Activity.RESTING, true)
				> ConcussionMath.flareDecayPerSecond(Activity.RESTING, false));
		assertEquals(0.0, ConcussionMath.flareDecayPerSecond(Activity.EXERTING, true),
				"pushing on never lets it settle, dark or not");
	}

	/** Sprinting in daylight with a moderate concussion gets you to the retch within half a minute. */
	@Test
	void sprintingInTheSunReachesTheRetch() {
		double gain = ConcussionMath.flareGain(ConcussionMath.MODERATE);
		double perSecond = Balance.FLARE_SPRINT_PER_SEC * gain + ConcussionMath.lightFlarePerSecond(15) * gain;
		double seconds = Balance.FLARE_RETCH / perSecond;
		assertTrue(seconds > 5.0 && seconds < 30.0, "took " + seconds + " s");
	}

	@Test
	void vertigoComesOftenerWithGradeAndFlare() {
		assertTrue(ConcussionMath.vertigoPeriod(ConcussionMath.SEVERE, 0.0)
				< ConcussionMath.vertigoPeriod(ConcussionMath.MILD, 0.0));
		assertEquals(ConcussionMath.vertigoPeriod(ConcussionMath.MODERATE, 0.0) / Balance.VERTIGO_FLARE_RATE,
				ConcussionMath.vertigoPeriod(ConcussionMath.MODERATE, 100.0), 1e-9);
		assertEquals(Double.POSITIVE_INFINITY, ConcussionMath.vertigoPeriod(ConcussionMath.NONE, 0.0));
		assertTrue(ConcussionMath.vertigoDuration(ConcussionMath.SEVERE, 50.0)
				> ConcussionMath.vertigoDuration(ConcussionMath.SEVERE, 0.0));
	}

	/** The data model: severity drives the grade and the cap; clearing clears every episode with it. */
	@Test
	void theDataModelKeepsSeverityAndEpisodesTogether() {
		RealisticDamageData data = new RealisticDamageData();
		data.setConcussion(true);
		assertEquals(Balance.CONCUSSION_DEFAULT_SEVERITY, data.concussionSeverity(), 1e-9);
		assertEquals(ConcussionMath.MODERATE, data.concussionGrade());
		data.setConcussionSeverity(90.0);
		data.setConcussion(true);
		assertEquals(90.0, data.concussionSeverity(), 1e-9, "setConcussion(true) never lightens one");
		double severeCap = data.consciousnessCap();
		data.setConcussionSeverity(10.0);
		assertTrue(data.consciousnessCap() > severeCap, "a milder concussion lowers the ceiling less");
		data.setConcussionFlare(80.0);
		data.beginVertigo(40);
		data.beginRetch(20);
		data.setConcussionSeverity(0.0);
		assertFalse(data.concussion());
		assertFalse(data.vertigo());
		assertFalse(data.retching());
		assertEquals(0.0, data.concussionFlare());
	}

	@Test
	void vertigoSpellsAlternateDirection() {
		RealisticDamageData data = new RealisticDamageData();
		data.setConcussion(true);
		boolean first = data.beginVertigo(10);
		boolean second = data.beginVertigo(10);
		assertTrue(first != second);
	}
}
