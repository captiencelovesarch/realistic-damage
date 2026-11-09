package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import org.joml.Vector3d;

/**
 * The stepper's test body (stumbling deaths, IK phase 0): a body held on its
 * feet at full strength, shoved, catching itself, and then let go.
 *
 * <p>{@code /rdmg ragdoll shove} kills a body this way so the steps can be
 * judged on their own, before any death decides how many it gets. Once it has
 * stood still for {@link #STILL_FOR} seconds (or at the latest after
 * {@link #MAX_HELD}) its muscles let go and it falls like any other body.
 */
public final class Stand implements Controller {
	/** Seconds standing still before it drops. */
	public static final double STILL_FOR = 3.0;
	/** It drops after this long whatever it is doing, seconds. */
	public static final double MAX_HELD = 20.0;
	/** Slower than this along the ground, blocks a second, and it is standing still. */
	private static final double STILL_SPEED = 0.15;

	public final Stepper stepper = new Stepper();
	private double stillSince = -1.0;
	private boolean letGo;
	private double letGoAt = -1.0;

	@Override
	public void apply(HumanoidRagdoll r, PhysicsWorld world, double t, double dt) {
		if (letGo) {
			return;
		}
		stepper.apply(r, world, t, dt);
		if (stepper.standing() && stepper.speed() < STILL_SPEED) {
			if (stillSince < 0.0) {
				stillSince = t;
			}
		} else {
			stillSince = -1.0;
		}
		if ((stillSince >= 0.0 && t - stillSince >= STILL_FOR) || t >= MAX_HELD) {
			letGo(r, t);
		}
	}

	private void letGo(HumanoidRagdoll r, double t) {
		letGo = true;
		letGoAt = t;
		stepper.release(r);
		r.setMuscles(r.muscles().lettingGo(t, 0.3));
		// A nudge the way it faces, so it goes down rather than balancing on its feet.
		Vector3d f = r.facing();
		r.push(f.x * 0.6, 0.0, f.z * 0.6);
		r.wake();
	}

	@Override
	public boolean holding() {
		return !letGo;
	}

	/** Seconds since death at which it was let go, or -1 while it is still held. */
	public double letGoAt() {
		return letGoAt;
	}
}
