package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.Body;
import org.joml.Vector3d;

/**
 * Where a standing body is falling (stumbling deaths, IK phase 0).
 *
 * <p>A body on its feet is an inverted pendulum: its centre of mass on top of
 * the leg it stands on, falling away from it faster the further it leans. The
 * one number that says where it is going is the <em>capture point</em>: the
 * spot on the ground it would have to step on to come to a stop there
 * (Pratt et al. 2006, "Capture Point: A Step toward Humanoid Push Recovery").
 * For a pendulum of height {@code h} under gravity {@code g} it is the centre
 * of mass plus its velocity over {@code ω = √(g/h)}. A body standing still has
 * it under itself; one shoved has it out ahead of the shove.
 *
 * <p>Plain Java, no Minecraft.
 */
public final class Balance {
	private Balance() {}

	/** The body's centre of mass, world: its present parts weighted by mass. */
	public static Vector3d centreOfMass(HumanoidRagdoll r, Vector3d out) {
		out.zero();
		double total = 0.0;
		for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
			if (!r.present(i)) {
				continue;
			}
			Body b = r.body(i);
			out.fma(b.mass, b.x);
			total += b.mass;
		}
		return out.div(total);
	}

	/** The centre of mass's velocity, blocks a second. */
	public static Vector3d velocity(HumanoidRagdoll r, Vector3d out) {
		out.zero();
		double total = 0.0;
		for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
			if (!r.present(i)) {
				continue;
			}
			Body b = r.body(i);
			out.fma(b.mass, b.v);
			total += b.mass;
		}
		return out.div(total);
	}

	/** The pendulum's rate, per second, for a centre of mass {@code height} above the ground. */
	public static double omega(double gravity, double height) {
		return Math.sqrt(Math.max(0.1, gravity) / Math.max(0.2, height));
	}

	/**
	 * The capture point along the ground: where to stand to come to rest. Its y
	 * is the ground's, {@code groundY}.
	 */
	public static Vector3d capturePoint(Vector3d com, Vector3d velocity, double omega, double groundY, Vector3d out) {
		return out.set(com.x + velocity.x / omega, groundY, com.z + velocity.z / omega);
	}

	/**
	 * Where the capture point will be {@code seconds} from now while the body
	 * stands on the point {@code stance}: it runs away from the foot it stands
	 * on, exponentially ({@code ξ' = ω(ξ - p)}).
	 */
	public static Vector3d capturePointLater(Vector3d capture, Vector3d stance, double omega, double seconds, Vector3d out) {
		double grow = Math.exp(omega * Math.max(0.0, seconds));
		return out.set(stance.x + (capture.x - stance.x) * grow, capture.y, stance.z + (capture.z - stance.z) * grow);
	}

	/**
	 * How far a point along the ground is outside the region between two feet
	 * (the segment joining them, widened by {@code halfWidth}); 0 or less is
	 * inside, where the body can stand without stepping.
	 */
	public static double outside(Vector3d point, Vector3d footA, Vector3d footB, double halfWidth) {
		double ax = footA.x;
		double az = footA.z;
		double bx = footB.x - ax;
		double bz = footB.z - az;
		double px = point.x - ax;
		double pz = point.z - az;
		double len2 = bx * bx + bz * bz;
		double t = len2 < 1.0e-12 ? 0.0 : Math.max(0.0, Math.min(1.0, (px * bx + pz * bz) / len2));
		double dx = px - bx * t;
		double dz = pz - bz * t;
		return Math.sqrt(dx * dx + dz * dz) - halfWidth;
	}
}
