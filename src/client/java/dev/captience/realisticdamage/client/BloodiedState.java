package dev.captience.realisticdamage.client;

import org.jspecify.annotations.Nullable;

/** Carried by an item's render state and by each draw it submits: what its blood looks like (2.0). */
public interface BloodiedState {
	BloodiedItems.@Nullable Look realisticdamage$blood();

	void realisticdamage$setBlood(BloodiedItems.@Nullable Look look);
}
