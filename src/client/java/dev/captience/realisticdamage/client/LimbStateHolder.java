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
 * that has not healed yet, and {@link InjuryPose} reads it from the model side,
 * where it needs {@code destroyedMask} in the same breath to skip a limb that
 * is not being drawn at all. Grouping it with the destroyed mask costs the
 * layer one extra cast and saves the model a second carrier, which is the
 * cheaper of the two, and it keeps "what state is this limb in" answerable from
 * one interface.
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

	/**
	 * Every limb's limp rung, three bits each, packed by
	 * {@code LimpMath.withRung} (1.6).
	 *
	 * <p>A derived field rather than a fourth raw mask, and that is the point:
	 * the rung is what "how badly is this limb hurt" <em>means</em>, and it is
	 * computed at exactly one place on this side — the renderer mixin, straight
	 * off the snapshot — so the model cannot arrive at a different answer from
	 * the one the server's prey score reaches from the same body. It is packed
	 * rather than carried as six numbers because a condition fraction on the
	 * render state would be a second copy of the truth, and the one thing worth
	 * copying here is the conclusion.
	 *
	 * <p>Local to the client and never written to disk or to the wire, so unlike
	 * a scar ordinal its width is free to change.
	 */
	int realisticdamage$limbRungs();

	void realisticdamage$setLimbRungs(int rungs);

	/**
	 * Bit 1: unconscious; bit 2: knocked down (1.7). The client lays a mob out
	 * on either; players carry their own prone pose from the server.
	 */
	int realisticdamage$downMask();

	void realisticdamage$setDownMask(int mask);
}
