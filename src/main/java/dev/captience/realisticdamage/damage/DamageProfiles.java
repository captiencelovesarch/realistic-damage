package dev.captience.realisticdamage.damage;

import java.util.EnumMap;
import java.util.Map;

import dev.captience.realisticdamage.config.Balance;

import static dev.captience.realisticdamage.damage.DamageProfile.NO_BLEED;

/**
 * The single authoritative table mapping {@link DamageCategory} to behavior.
 * All bleed thresholds, break capability, and consciousness factors are
 * centralized here (spec §33-34: tune in one place).
 */
public final class DamageProfiles {
	private static final Map<DamageCategory, DamageProfile> PROFILES = new EnumMap<>(DamageCategory.class);

	private static void register(DamageCategory category, DamageProfile profile) {
		PROFILES.put(category, profile);
	}

	static {
		// trauma, bleedMinor/Moderate/Severe/Critical, breakImpact, breakAccum, concussion, consciousness, systemic
		register(DamageCategory.GENERIC, new DamageProfile(
				Balance.TRAUMA_GENERIC, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				false, false, false, 0.3, false));
		register(DamageCategory.SLASH, new DamageProfile(
				Balance.TRAUMA_SLASH, 2.0, 4.0, 7.0, 11.0,
				false, true, false, 0.2, false));
		register(DamageCategory.HEAVY_SLASH, new DamageProfile(
				Balance.TRAUMA_HEAVY_SLASH, 1.5, 3.5, 6.0, 9.0,
				false, true, false, 0.3, false));
		register(DamageCategory.PUNCTURE, new DamageProfile(
				Balance.TRAUMA_PUNCTURE, 1.5, 3.5, 6.5, 10.0,
				false, true, false, 0.2, false));
		register(DamageCategory.HEAVY_PUNCTURE, new DamageProfile(
				Balance.TRAUMA_HEAVY_PUNCTURE, 1.2, 3.0, 5.5, 8.5,
				false, true, false, 0.3, false));
		// Firearms (1.3). Lower bleed thresholds than anything else in the table
		// — a hit that would merely graze with a sword opens a serious wound —
		// but breakImpact is false: a bullet passes through, it does not club.
		// Bones still break by accumulation once a limb is wrecked.
		register(DamageCategory.BALLISTIC, new DamageProfile(
				Balance.TRAUMA_BALLISTIC, 0.8, 2.0, 4.0, 6.5,
				false, true, false, 0.35, false));
		register(DamageCategory.HEAVY_BALLISTIC, new DamageProfile(
				Balance.TRAUMA_HEAVY_BALLISTIC, 0.6, 1.6, 3.2, 5.0,
				true, true, false, 0.5, false));
		register(DamageCategory.BLUNT, new DamageProfile(
				Balance.TRAUMA_BLUNT, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				true, true, true, 0.8, false));
		register(DamageCategory.HEAVY_BLUNT, new DamageProfile(
				Balance.TRAUMA_HEAVY_BLUNT, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				true, true, true, 1.2, false));
		register(DamageCategory.EXPLOSION, new DamageProfile(
				Balance.TRAUMA_EXPLOSION, NO_BLEED, 5.0, 9.0, 14.0,
				true, true, true, 1.0, false));
		register(DamageCategory.FALL, new DamageProfile(
				Balance.TRAUMA_FALL, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				true, true, true, 0.9, false));
		register(DamageCategory.FIRE, new DamageProfile(
				Balance.TRAUMA_FIRE, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				false, false, false, 0.1, false));
		register(DamageCategory.LAVA, new DamageProfile(
				Balance.TRAUMA_LAVA, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				false, false, false, 0.2, false));
		register(DamageCategory.SYSTEMIC, new DamageProfile(
				Balance.TRAUMA_SYSTEMIC, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				false, false, false, 0.15, true));
		register(DamageCategory.DROWNING, new DamageProfile(
				0.0, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				false, false, false, 1.5, true));
		register(DamageCategory.SUFFOCATION, new DamageProfile(
				0.05, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				false, false, false, 1.2, true));
		register(DamageCategory.MAGIC, new DamageProfile(
				Balance.TRAUMA_SYSTEMIC, NO_BLEED, NO_BLEED, NO_BLEED, NO_BLEED,
				false, false, false, 0.4, true));
	}

	public static DamageProfile of(DamageCategory category) {
		return PROFILES.get(category);
	}

	private DamageProfiles() {}
}
