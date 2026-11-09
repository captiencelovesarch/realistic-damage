package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import org.joml.Vector3d;

/**
 * The stepper's test body (stumbling deaths, IK phase 0): a body held on its
 * feet, shoved, catching itself, and going down.
 *
 * <p>{@code /rdmg ragdoll shove} kills a body this way so the steps can be
 * judged on their own, before any death decides how many it gets. It is
 * dying, so over its {@link #onFeet} seconds it weakens. Its balance goes
 * first (the hold that keeps its torso up): the torso pitches the way the
 * body is going and the legs chase it, each step catching less, until it is
 * over and falls the way it was going. Its strength goes after (its legs, and
 * the weight carried for it), so one that was hardly shoved, and caught
 * itself, sags and buckles where it stands. With
 * {@code onFeet} zero it does not weaken: it stands at full strength until it
 * has stood still for {@link #STILL_FOR} seconds (or at the latest after
 * {@link #MAX_HELD}), and then its muscles give way, legs first. Either way,
 * one that loses its footing before that (shoved too hard to catch, into a
 * hole, off an edge) is let go of the moment it does.
 */
public final class Stand implements Controller {
	/** Seconds on its feet when nothing says otherwise. */
	public static final double ON_FEET = 1.6;
	/** Seconds standing still before it drops, when it does not weaken. */
	public static final double STILL_FOR = 1.2;
	/** It drops after this long whatever it is doing, seconds. */
	public static final double MAX_HELD = 8.0;
	/** Slower than this along the ground, blocks a second, and it is standing still. */
	private static final double STILL_SPEED = 0.15;

	/** What it has of its balance to begin with: a dying body is not steady on its feet even at first. */
	public double balanceStart = 0.35;
	/** Its balance starts to go this far into its time on its feet, and has gone by this far, as shares of it. */
	public double balanceFrom = 0.0;
	public double balanceGone = 0.4;
	/** What is left of its balance once it has gone. */
	public double balanceLeft = 0.05;
	/** Its strength starts to go this far into its time on its feet, as a share of it. */
	public double strengthFrom = 0.3;
	/** What is left of its strength at the end of its time on its feet. */
	public double strengthLeft = 0.25;

	public final Stepper stepper = new Stepper();
	/** Seconds on its feet before its strength has gone; 0 and it stands until it is still. */
	public final double onFeet;
	private double stillSince = -1.0;
	private boolean letGo;
	private double letGoAt = -1.0;

	public Stand() {
		this(ON_FEET);
	}

	public Stand(double onFeet) {
		this.onFeet = Math.max(0.0, onFeet);
	}

	/** Its strength (its legs, arms and neck, and the weight carried for it), 1 full, at a time since death. */
	public double strength(double t) {
		return onFeet <= 0.0 ? 1.0 : fade(t / onFeet, strengthFrom, 1.0, strengthLeft);
	}

	/** Its balance (the hold on its torso), 1 full, at a time since death: the first thing to go. */
	public double balance(double t) {
		return onFeet <= 0.0 ? 1.0 : balanceStart * fade(t / onFeet, balanceFrom, balanceGone, balanceLeft / Math.max(1.0e-6, balanceStart));
	}

	/** 1 until {@code from}, {@code left} from {@code to} on, eased between. */
	private static double fade(double x, double from, double to, double left) {
		double u = Math.max(0.0, Math.min(1.0, (x - from) / Math.max(1.0e-6, to - from)));
		return 1.0 - (1.0 - left) * u * u * (3.0 - 2.0 * u);
	}

	@Override
	public void apply(HumanoidRagdoll r, PhysicsWorld world, double t, double dt) {
		if (letGo) {
			stepper.settle(r, dt);
			return;
		}
		stepper.strength = strength(t);
		stepper.balance = balance(t);
		stepper.apply(r, world, t, dt);
		if (stepper.released()) {
			// It lost its footing: it is already falling, the way it was going.
			letGo = true;
			letGoAt = t;
			r.setMuscles(r.muscles().crumpling(t));
			r.wake();
			return;
		}
		if (stepper.standing() && stepper.speed() < STILL_SPEED) {
			if (stillSince < 0.0) {
				stillSince = t;
			}
		} else {
			stillSince = -1.0;
		}
		boolean spent = onFeet > 0.0 && t >= onFeet;
		boolean still = onFeet <= 0.0 && stillSince >= 0.0 && t - stillSince >= STILL_FOR;
		if (spent || still || t >= MAX_HELD) {
			letGo(r, t);
		}
	}

	private void letGo(HumanoidRagdoll r, double t) {
		letGo = true;
		letGoAt = t;
		boolean moving = stepper.speed() > STILL_SPEED;
		stepper.release(r);
		r.setMuscles(r.muscles().crumpling(t));
		if (!moving) {
			// A nudge the way it faces, so it goes down rather than balancing on its feet.
			Vector3d f = r.facing();
			r.push(f.x * 0.6, 0.0, f.z * 0.6);
		}
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
