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
	/**
	 * Small-arms fire (1.3). A puncture arriving far faster than an arrow: it
	 * bleeds harder than anything else in the table, and cannot break a bone on
	 * impact alone because a bullet passes through rather than clubbing.
	 */
	BALLISTIC,
	/** Rifle and magnum calibres — enough energy to shatter what they hit. */
	HEAVY_BALLISTIC,
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
