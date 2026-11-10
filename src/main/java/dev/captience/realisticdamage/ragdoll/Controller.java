package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;

/**
 * Something that keeps a dying body on its feet for a while (stumbling deaths,
 * IK phase 0): the stepper's owner. Runs every tick after the reflexes
 * ({@link Behaviour}), so it has the last word on the legs and the torso.
 */
public interface Controller {
	/** Once a tick, before the physics step; {@code t} is seconds since death. */
	void apply(HumanoidRagdoll r, PhysicsWorld world, double t, double dt);

	/** Whether it is still holding the body up. A body being held never falls asleep. */
	boolean holding();

	/** Whether it is not done with a body it has let go of: one it is not done with does not fall asleep either. */
	default boolean restless() {
		return false;
	}
}
