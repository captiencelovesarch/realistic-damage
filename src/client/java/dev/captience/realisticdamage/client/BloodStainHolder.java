package dev.captience.realisticdamage.client;

/**
 * Carries per-region blood staining from the entity onto its render state, so
 * the model layer can draw a bloody left arm without going looking for the
 * player it belongs to. Duck-typed onto {@code AvatarRenderState}, the same way
 * {@link DestroyedLimbHolder} carries the destroyed-limb mask.
 *
 * <p>Kept separate from that interface rather than bolted onto it because the
 * two are wanted in different places — the limb mask is read by the model to
 * hide parts, this is read by a render layer to paint them — and merging them
 * would make either one drag the other into scope.
 */
public interface BloodStainHolder {
	/** One stain level per {@code BodyPart}, in ordinal order, 0 to 1. */
	float[] realisticdamage$bloodStains();

	void realisticdamage$setBloodStains(float[] stains);

	/**
	 * The synced scar mask (1.6), two bits per part holding a
	 * {@code ScarLevel} ordinal; 0 means unmarked everywhere.
	 *
	 * <p>The rule this interface states is one carrier per <em>consumer</em>,
	 * not one per datum: the body layer paints both blood and scars, so both
	 * ride here, while the destroyed mask stays on {@link DestroyedLimbHolder}
	 * because it is the model rather than the layer that reads it. Splitting
	 * these two apart would give one class two casts to make on the way to one
	 * draw.
	 */
	int realisticdamage$scarMask();

	void realisticdamage$setScarMask(int mask);
}
