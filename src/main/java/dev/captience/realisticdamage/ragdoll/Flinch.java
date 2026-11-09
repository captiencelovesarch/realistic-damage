package dev.captience.realisticdamage.ragdoll;

import org.joml.Vector3d;

/**
 * A living body's flinch (active ragdolls, phase 5): the struck part jerks
 * with the blow and the body rocks, then it all springs back, inside half a
 * second. Played on top of vanilla's animation, never instead of it.
 *
 * <p>Each channel is a damped spring kicked by the hit: the body's lean (about
 * a world axis, away from the blow), a dip (a leg giving for a step, pixels),
 * and the head's, arms' and legs' angles (radians, the model's own signs:
 * positive head X looks down, positive arm and leg X swing back, positive Z
 * takes the right side out). Deterministic, plain Java, unit-tested.
 */
public final class Flinch {
	public static final int LEAN = 0;
	public static final int DIP = 1;
	public static final int HEAD_X = 2;
	public static final int HEAD_Y = 3;
	public static final int RIGHT_ARM_X = 4;
	public static final int RIGHT_ARM_Z = 5;
	public static final int LEFT_ARM_X = 6;
	public static final int LEFT_ARM_Z = 7;
	public static final int RIGHT_LEG_X = 8;
	public static final int LEFT_LEG_X = 9;
	public static final int CHANNELS = 10;

	/** Stiffness (per second squared) and damping (per second): just under critical, a jolt and a settle. */
	private static final double STIFFNESS = 180.0;
	private static final double DAMPING = 19.0;
	private static final int SUBSTEPS = 5;
	/** The most any angle may be thrown, radians, however hard the hits stack. */
	private static final double MAX_ANGLE = 0.9;

	private final double[] pos = new double[CHANNELS];
	private final double[] vel = new double[CHANNELS];
	private final double[] prev = new double[CHANNELS];
	/** The lean's axis, world, unit: the turn that tips the body's top along the blow. */
	private final Vector3d leanAxis = new Vector3d(1.0, 0.0, 0.0);
	private double age;

	/**
	 * A hit.
	 *
	 * @param part     the ragdoll part struck ({@link HumanoidRagdoll#HEAD} and so on)
	 * @param blow     the way the blow travelled, world (zero for none)
	 * @param strength 0..1
	 * @param bodyYaw  the body's yaw, degrees
	 */
	public void hit(int part, Vector3d blow, double strength, float bodyYaw) {
		double s = Math.max(0.0, Math.min(1.0, strength));
		double yaw = Math.toRadians(bodyYaw);
		Vector3d facing = new Vector3d(-Math.sin(yaw), 0.0, Math.cos(yaw));
		Vector3d right = new Vector3d(facing).cross(0.0, 1.0, 0.0);
		Vector3d flat = new Vector3d(blow.x, 0.0, blow.z);
		if (flat.lengthSquared() < 1.0e-8) {
			// No direction (a fall, a blast from nowhere): from the front.
			flat.set(facing).negate();
		}
		flat.normalize();
		double ahead = flat.dot(facing);
		double side = flat.dot(right);
		// The turn that tips the top of the body along the blow: up x blow.
		Vector3d axis = new Vector3d(0.0, 1.0, 0.0).cross(flat);
		if (axis.lengthSquared() > 1.0e-8) {
			leanAxis.set(axis.normalize());
		}
		double lean = switch (part) {
			case HumanoidRagdoll.TORSO -> 4.5;
			case HumanoidRagdoll.HEAD -> 2.0;
			case HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG -> 2.5;
			default -> 1.5;
		};
		vel[LEAN] += lean * s;
		switch (part) {
			case HumanoidRagdoll.HEAD -> {
				vel[HEAD_X] += 13.0 * s * ahead;
				vel[HEAD_Y] -= 6.0 * s * side;
			}
			case HumanoidRagdoll.TORSO -> {
				vel[HEAD_X] += 4.0 * s * ahead;
				vel[RIGHT_ARM_X] -= 3.0 * s * ahead;
				vel[LEFT_ARM_X] -= 3.0 * s * ahead;
			}
			case HumanoidRagdoll.RIGHT_ARM -> {
				vel[RIGHT_ARM_X] -= 14.0 * s * ahead;
				vel[RIGHT_ARM_Z] += 5.0 * s * side;
			}
			case HumanoidRagdoll.LEFT_ARM -> {
				vel[LEFT_ARM_X] -= 14.0 * s * ahead;
				vel[LEFT_ARM_Z] += 5.0 * s * side;
			}
			case HumanoidRagdoll.RIGHT_LEG -> {
				vel[RIGHT_LEG_X] += 7.0 * s * (0.5 - ahead);
				vel[DIP] += 22.0 * s;
			}
			case HumanoidRagdoll.LEFT_LEG -> {
				vel[LEFT_LEG_X] += 7.0 * s * (0.5 - ahead);
				vel[DIP] += 22.0 * s;
			}
			default -> {}
		}
		age = 0.0;
	}

	/** One tick of {@code dt} seconds. */
	public void tick(double dt) {
		System.arraycopy(pos, 0, prev, 0, CHANNELS);
		double h = dt / SUBSTEPS;
		for (int k = 0; k < SUBSTEPS; k++) {
			for (int i = 0; i < CHANNELS; i++) {
				vel[i] += (-STIFFNESS * pos[i] - DAMPING * vel[i]) * h;
				pos[i] += vel[i] * h;
				if (i != DIP) {
					pos[i] = Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, pos[i]));
				}
			}
		}
		age += dt;
	}

	/** A channel between the last two ticks. */
	public double value(int channel, float partial) {
		return prev[channel] + (pos[channel] - prev[channel]) * partial;
	}

	public Vector3d leanAxis() {
		return leanAxis;
	}

	/** Whether it has all sprung back. */
	public boolean done() {
		if (age < 0.15) {
			return false;
		}
		for (int i = 0; i < CHANNELS; i++) {
			if (Math.abs(pos[i]) > 0.005 || Math.abs(vel[i]) > 0.06) {
				return false;
			}
		}
		return true;
	}

	public double age() {
		return age;
	}
}
