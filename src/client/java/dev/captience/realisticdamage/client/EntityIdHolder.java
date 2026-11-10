package dev.captience.realisticdamage.client;

/** Which entity a living render state was taken from, -1 for none (kept on every living render state). */
public interface EntityIdHolder {
	int realisticdamage$entityId();

	void realisticdamage$setEntityId(int id);
}
