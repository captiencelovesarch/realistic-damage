package dev.captience.realisticdamage.client;

/**
 * Carries the destroyed-limb mask from the entity onto its render state, so the
 * model can hide limbs that are gone. Duck-typed onto {@code AvatarRenderState}.
 */
public interface DestroyedLimbHolder {
	int realisticdamage$destroyedMask();

	void realisticdamage$setDestroyedMask(int mask);
}
