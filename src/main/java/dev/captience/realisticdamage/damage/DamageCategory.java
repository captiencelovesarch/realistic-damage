package dev.captience.realisticdamage.damage;

/**
 * Reusable damage classification (spec §33). A classified category plus the
 * {@link DamageProfile} it maps to drive all body-trauma consequences; nothing
 * downstream inspects entities or damage sources directly.
 */
public enum DamageCategory {
	GENERIC,
	SLASH,
	HEAVY_SLASH,
	PUNCTURE,
	HEAVY_PUNCTURE,
	BLUNT,
	HEAVY_BLUNT,
	EXPLOSION,
	FALL,
	FIRE,
	LAVA,
	SYSTEMIC,
	DROWNING,
	SUFFOCATION,
	MAGIC
}
