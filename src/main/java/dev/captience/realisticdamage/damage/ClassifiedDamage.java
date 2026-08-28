package dev.captience.realisticdamage.damage;

import org.jspecify.annotations.Nullable;

/**
 * Result of classifying one damage event: what kind of trauma it is and where
 * it goes. {@code woundAs} lets compound sources (pointed dripstone) bleed like
 * a different category than they bruise (spec §7, §30).
 */
public record ClassifiedDamage(
		DamageCategory category,
		Routing routing,
		double extraMultiplier,
		@Nullable DamageCategory woundAs
) {
	public enum Routing {
		/** Resolve the actual impact point against body-region hitboxes (spec §6). */
		LOCATIONAL,
		/** Both legs, with deterministic secondary torso/head shares for big falls. */
		FALL_LEGS,
		/** Head-primary, from above (anvils, falling blocks, stalactites). */
		HEAD_HEAVY,
		LEGS,
		TORSO,
		HEAD_TORSO,
		LEGS_TORSO,
		/** All regions, proportional to their condition maxima (fire, systemic). */
		SPREAD,
		/** Primary region chosen by explosion geometry (spec §29). */
		EXPLOSION_DIRECTIONAL,
		/** No body trauma (drowning, sonic boom — consciousness handles these). */
		NONE
	}

	public static ClassifiedDamage of(DamageCategory category, Routing routing) {
		return new ClassifiedDamage(category, routing, 1.0, null);
	}

	public static ClassifiedDamage of(DamageCategory category, Routing routing, double extraMultiplier) {
		return new ClassifiedDamage(category, routing, extraMultiplier, null);
	}
}
