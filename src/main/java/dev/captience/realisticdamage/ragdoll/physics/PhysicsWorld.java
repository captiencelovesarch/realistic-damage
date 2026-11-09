package dev.captience.realisticdamage.ragdoll.physics;

/**
 * What a ragdoll can bump into. Minecraft implements it on the client from the
 * level's block collision shapes; the unit tests implement it with a handful of
 * boxes.
 */
public interface PhysicsWorld {
	/** Adds every solid box overlapping the region to {@code out}. */
	void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, BoxList out);

	/** The fluid at a point; {@link Fluid#NONE} when there is none. */
	Fluid fluid(double x, double y, double z);

	/**
	 * A fluid sample: which kind, and the height of its surface in the column at
	 * that point (meaningless for NONE).
	 */
	record Fluid(Kind kind, double surface) {
		public static final Fluid NONE = new Fluid(Kind.NONE, 0.0);

		public enum Kind {
			NONE,
			/** A body floats in it, just. */
			WATER,
			/** Thick: it slows everything down, and a body sinks into it. */
			LAVA
		}
	}

	/** A world with nothing in it. */
	PhysicsWorld EMPTY = new PhysicsWorld() {
		@Override
		public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, BoxList out) {}

		@Override
		public Fluid fluid(double x, double y, double z) {
			return Fluid.NONE;
		}
	};
}
