package dev.captience.realisticdamage.ragdoll.ik;

import org.joml.Vector3d;

/**
 * Inverse kinematics for a Minecraft limb (stumbling deaths, IK phase 0).
 *
 * <p>Every limb here is one straight bone, with no knee or elbow, so the
 * answer is exact: point the limb at the target. What a straight limb cannot
 * do is get shorter. A foot target nearer the hip than a leg's length is met
 * by the ground stopping the foot (the body rises over the leg as it vaults);
 * one further away cannot be reached at all, and the hips have to come to it.
 * So the leg is aimed at the target's spot along the ground, and the ground
 * decides the height.
 *
 * <p>Plain Java, no Minecraft.
 */
public final class Reach {
	private Reach() {}

	/**
	 * The direction, unit, a straight leg from {@code hip} must point for its
	 * foot to come down on {@code target}'s spot along the ground. The
	 * horizontal reach is capped at {@code maxReach} (the leg's angle limit);
	 * beyond it the leg points as far as it goes.
	 */
	public static Vector3d legToward(Vector3d hip, Vector3d target, double length, double maxReach, Vector3d out) {
		double dx = target.x - hip.x;
		double dz = target.z - hip.z;
		double flat = Math.hypot(dx, dz);
		double cap = Math.min(maxReach, length * 0.98);
		if (flat > cap) {
			dx *= cap / flat;
			dz *= cap / flat;
			flat = cap;
		}
		double down = Math.sqrt(Math.max(0.0, length * length - flat * flat));
		return out.set(dx, -down, dz).div(length);
	}

	/**
	 * A straight leg from {@code hip} whose foot just touches a floor at
	 * {@code floorY}, leaning along {@code along} (horizontal) as far as it must
	 * to keep the foot out of it; straight down when the floor is a leg's
	 * length or more below.
	 */
	public static Vector3d skim(Vector3d hip, Vector3d along, double length, double floorY, Vector3d out) {
		double down = Math.min(length, Math.max(0.0, hip.y - floorY));
		double flat = Math.sqrt(Math.max(0.0, length * length - down * down));
		double h = Math.hypot(along.x, along.z);
		double ax = h < 1.0e-9 ? 0.0 : along.x / h;
		double az = h < 1.0e-9 ? 0.0 : along.z / h;
		return out.set(ax * flat, -down, az * flat).div(length);
	}

	/** Where a straight limb's far end is when it points along {@code dir} (unit) from its joint. */
	public static Vector3d end(Vector3d joint, Vector3d dir, double length, Vector3d out) {
		return out.set(dir).mul(length).add(joint);
	}

	/** How far a target is beyond a limb's reach from its joint, blocks; 0 or less is within it. */
	public static double shortBy(Vector3d joint, Vector3d target, double length) {
		return joint.distance(target) - length;
	}
}
