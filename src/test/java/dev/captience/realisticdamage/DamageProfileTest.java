package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.damage.DamageProfile;
import dev.captience.realisticdamage.damage.DamageProfiles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageProfileTest {
	@Test
	void everyCategoryHasAProfile() {
		for (DamageCategory category : DamageCategory.values()) {
			assertNotNull(DamageProfiles.of(category), category + " missing profile");
		}
	}

	@Test
	void traumaMultipliersMatchSpec() {
		assertEquals(0.75, DamageProfiles.of(DamageCategory.GENERIC).traumaMultiplier());
		assertEquals(1.00, DamageProfiles.of(DamageCategory.SLASH).traumaMultiplier());
		assertEquals(1.40, DamageProfiles.of(DamageCategory.HEAVY_BLUNT).traumaMultiplier());
		assertEquals(1.35, DamageProfiles.of(DamageCategory.EXPLOSION).traumaMultiplier());
		assertEquals(1.25, DamageProfiles.of(DamageCategory.FALL).traumaMultiplier());
		assertEquals(0.60, DamageProfiles.of(DamageCategory.FIRE).traumaMultiplier());
		assertEquals(0.10, DamageProfiles.of(DamageCategory.SYSTEMIC).traumaMultiplier());
	}

	@Test
	void bluntDoesNotBleedSlashDoes() {
		assertFalse(DamageProfiles.of(DamageCategory.BLUNT).canBleed());
		assertFalse(DamageProfiles.of(DamageCategory.HEAVY_BLUNT).canBleed());
		assertFalse(DamageProfiles.of(DamageCategory.FALL).canBleed());
		assertTrue(DamageProfiles.of(DamageCategory.SLASH).canBleed());
		assertTrue(DamageProfiles.of(DamageCategory.PUNCTURE).canBleed());
		assertTrue(DamageProfiles.of(DamageCategory.EXPLOSION).canBleed());
	}

	@Test
	void woundSeverityThresholdsAreDeterministic() {
		DamageProfile slash = DamageProfiles.of(DamageCategory.SLASH);
		assertNull(slash.woundFor(1.9), "weak glancing slash bleeds nothing");
		assertEquals(WoundSeverity.MINOR, slash.woundFor(2.0));
		assertEquals(WoundSeverity.MODERATE, slash.woundFor(4.0));
		assertEquals(WoundSeverity.SEVERE, slash.woundFor(7.0));
		assertEquals(WoundSeverity.CRITICAL, slash.woundFor(11.0));
		assertEquals(WoundSeverity.CRITICAL, slash.woundFor(50.0));
	}

	@Test
	void onlyHeavyCategoriesBreakOnImpact() {
		assertTrue(DamageProfiles.of(DamageCategory.HEAVY_BLUNT).breakOnImpact());
		assertTrue(DamageProfiles.of(DamageCategory.EXPLOSION).breakOnImpact());
		assertTrue(DamageProfiles.of(DamageCategory.FALL).breakOnImpact());
		assertFalse(DamageProfiles.of(DamageCategory.SLASH).breakOnImpact());
		assertFalse(DamageProfiles.of(DamageCategory.PUNCTURE).breakOnImpact());
		// ...but accumulated severe slash/puncture damage can still break (spec §15).
		assertTrue(DamageProfiles.of(DamageCategory.SLASH).breakOnAccumulation());
		assertTrue(DamageProfiles.of(DamageCategory.PUNCTURE).breakOnAccumulation());
	}
}
