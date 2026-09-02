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
}
