package dev.captience.realisticdamage.client;

/**
 * Carries per-region blood staining from the entity onto its render state, so
 * the model layer can draw a bloody left arm without going looking for the
 * player it belongs to. Duck-typed onto {@code AvatarRenderState}, the same way
 * {@link LimbStateHolder} carries the destroyed- and broken-limb masks.
 *
 * <p>Kept separate from that interface rather than bolted onto it because the
 * two are wanted in different places — the limb masks are read by the model to
 * hide parts and to pose them, this is read by a render layer to paint them —
 * and merging them would make either one drag the other into scope.
 */
public interface BloodStainHolder {
	/** One stain level per {@code BodyPart}, in ordinal order, 0 to 1. */
	float[] realisticdamage$bloodStains();

	void realisticdamage$setBloodStains(float[] stains);

	/** Per region, whose blood the stain is where it is not the body's own; null, or a null entry, for its own. */
	BloodTint[] realisticdamage$bloodTints();

	void realisticdamage$setBloodTints(BloodTint[] tints);

	/**
	 * The synced scar mask (1.6), two bits per part holding a
	 * {@code ScarLevel} ordinal; 0 means unmarked everywhere.
	 *
	 * <p>The rule this interface states is one carrier per <em>consumer</em>,
	 * not one per datum: the body layer paints both blood and scars, so both
	 * ride here, while the limb masks stay on {@link LimbStateHolder} because it
	 * is the model rather than the layer that reads them. Splitting these two
	 * apart would give one class two casts to make on the way to one draw.
	 *
	 * <p>The broken mask is where that rule runs out, and it is worth knowing
	 * before the next datum arrives: it has two consumers, the layer here and
	 * the model later, so somebody has to make a second cast whichever interface
	 * it lands on. It went to {@link LimbStateHolder}; the reasoning is written
	 * out there.
	 */
	int realisticdamage$scarMask();

	void realisticdamage$setScarMask(int mask);
}
