package dev.captience.realisticdamage.client;

import org.jspecify.annotations.Nullable;

/** The simulated corpse's pose on a living render state; see {@link RemoteCorpses}. */
public interface CorpseHolder {
	RemoteCorpses.@Nullable Frame realisticdamage$corpse();

	void realisticdamage$setCorpse(RemoteCorpses.@Nullable Frame pose);
}
