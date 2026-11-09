package dev.captience.realisticdamage.client;

import org.jspecify.annotations.Nullable;

/** A living body's flinch this frame on its render state; see {@link Flinches}. */
public interface FlinchHolder {
	float @Nullable [] realisticdamage$flinch();

	void realisticdamage$setFlinch(float @Nullable [] flinch);
}
