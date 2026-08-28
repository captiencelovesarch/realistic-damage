package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.api.ExternalClassification;
import dev.captience.realisticdamage.damage.ClassifiedDamage;
import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.damage.DamageProfile;
import dev.captience.realisticdamage.damage.DamageProfiles;
import org.junit.jupiter.api.Test;

/** 1.3: firearms, and the shape an external mod hands classification over in. */
class BallisticProfileTest {
	private static final DamageProfile BALLISTIC = DamageProfiles.of(DamageCategory.BALLISTIC);
	private static final DamageProfile HEAVY_BALLISTIC = DamageProfiles.of(DamageCategory.HEAVY_BALLISTIC);
	private static final DamageProfile HEAVY_PUNCTURE = DamageProfiles.of(DamageCategory.HEAVY_PUNCTURE);

	/** A gunshot opens a wound at damage a blade would only graze with. */
	@Test
	void bulletsBleedEasierThanBlades() {
		assertTrue(BALLISTIC.bleedMinor() < HEAVY_PUNCTURE.bleedMinor(),
				"a bullet must start bleeding sooner than a heavy puncture");
		assertTrue(BALLISTIC.bleedCritical() < HEAVY_PUNCTURE.bleedCritical(),
				"and reach a critical wound sooner");
		assertTrue(HEAVY_BALLISTIC.bleedCritical() < BALLISTIC.bleedCritical(),
				"a rifle round worse still");
	}

	/**
	 * A bullet passes through; it does not club. Small arms must not snap a
	 * healthy bone on impact the way a mace does — that is the line between
	 * ballistic and blunt, and it is the reason they are separate categories.
	 */
	@Test
	void smallArmsCannotBreakBonesOnImpact() {
		assertFalse(BALLISTIC.breakOnImpact(),
				"small arms must not break a healthy limb outright");
		assertTrue(BALLISTIC.breakOnAccumulation(),
				"but a wrecked limb still gives way");
		assertTrue(HEAVY_BALLISTIC.breakOnImpact(),
				"rifle calibres carry enough energy to shatter what they hit");
	}

	@Test
	void ballisticsHitHarderThanPunctures() {
		assertTrue(BALLISTIC.traumaMultiplier() > HEAVY_PUNCTURE.traumaMultiplier());
		assertTrue(HEAVY_BALLISTIC.traumaMultiplier() > BALLISTIC.traumaMultiplier());
	}

	/** Every category needs a profile, or the pipeline has a hole in it. */
	@Test
	void everyCategoryHasAProfile() {
		for (DamageCategory category : DamageCategory.values()) {
			assertTrue(DamageProfiles.of(category) != null, category + " has no profile");
		}
	}

	@Test
	void externalClassificationMapsOntoThePipelineShape() {
		ClassifiedDamage located = ExternalClassification.of(DamageCategory.BALLISTIC).toClassified();
		assertEquals(DamageCategory.BALLISTIC, located.category());
		assertEquals(ClassifiedDamage.Routing.LOCATIONAL, located.routing(),
				"a weapon hit resolves against body regions by default");
		assertEquals(1.0, located.extraMultiplier());

		ClassifiedDamage systemic = new ExternalClassification(
				DamageCategory.SYSTEMIC, false, 2.0, DamageCategory.BALLISTIC).toClassified();
		assertEquals(ClassifiedDamage.Routing.SPREAD, systemic.routing());
		assertEquals(2.0, systemic.extraMultiplier());
		assertEquals(DamageCategory.BALLISTIC, systemic.woundAs(),
				"a compound source must keep bleeding as the category it was given");
	}
}
