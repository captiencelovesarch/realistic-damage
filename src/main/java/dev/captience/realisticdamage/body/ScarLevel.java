package dev.captience.realisticdamage.body;

/**
 * What a region has been through, kept after the injury itself has healed
 * (1.6). The ordinal is both the save value and the wire value: append only,
 * never reorder.
 */
public enum ScarLevel {
	NONE, BROKEN, DESTROYED;

	/**
	 * Total on {@code int}, so a hand-edited save or a malformed packet cannot
	 * crash a load: out of range clamps to the nearest end rather than throwing.
	 *
	 * <p>Note the deliberate disagreement with {@code CeilingMath.legalBand},
	 * which sends an out-of-range value to 0 instead of to the nearest end. The
	 * two look like the same function and are not, because they are guarding
	 * different things. A ceiling is gameplay: inventing one out of a corrupt
	 * byte would hand a player a permanent injury they never earned, so the safe
	 * answer there is "no ceiling". A scar is only a mark — it changes no
	 * outcome anywhere in the mod — so the safe answer here is to saturate and
	 * keep loading. Do not "unify" them; the asymmetry is the point.
	 */
	public static ScarLevel byOrdinal(int ordinal) {
		return ordinal <= 0 ? NONE : ordinal >= 2 ? DESTROYED : BROKEN;
	}

	/** History accumulates; it does not average. */
	public ScarLevel max(ScarLevel other) {
		return ordinal() >= other.ordinal() ? this : other;
	}
}
