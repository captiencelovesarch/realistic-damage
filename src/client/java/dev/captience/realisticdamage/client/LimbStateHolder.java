package dev.captience.realisticdamage.client;

/**
 * Carries the synced condition of a body's limbs — what is gone, and what is
 * broken right now — from the entity onto its render state. Duck-typed onto
 * {@code AvatarRenderState}.
 *
 * <p>Was {@code DestroyedLimbHolder} until 1.6 put a second mask on it. Both
 * are the same kind of datum: an {@code int} bitmask lifted straight off
 * {@code BodySnapshot}, describing the limb rather than anything painted on it.
 * {@link BloodStainHolder} is the other half of that split and carries the
 * paint — blood and scars.
 *
 * <p>The rule {@link BloodStainHolder} states is one carrier per
 * <em>consumer</em>, and the broken mask is the case where that rule runs out:
 * it has two. {@link BloodBodyLayer} reads it to suppress a scar seam on a limb
 * that has not healed yet, and the injury pose — deferred, and to be built
 * alongside the injury-aware mob AI so that what a mob reads and what the
 * player sees come from one definition — will read it from the model side,
 * where it needs {@code destroyedMask} in the same breath to skip a limb that
 * is not being drawn at all. Grouping it with the destroyed mask costs the
 * layer one extra cast today and saves the model a second carrier tomorrow,
 * which is the cheaper of the two, and it keeps "what state is this limb in"
 * answerable from one interface.
 */
public interface LimbStateHolder {
	int realisticdamage$destroyedMask();

	void realisticdamage$setDestroyedMask(int mask);

	/**
	 * One bit per {@code BodyPart} ordinal, set while that region's bone is
	 * broken. Unlike the scar mask this is a <em>current</em> condition: it
	 * clears the moment the region mends.
	 */
	int realisticdamage$brokenMask();

	void realisticdamage$setBrokenMask(int mask);
}
