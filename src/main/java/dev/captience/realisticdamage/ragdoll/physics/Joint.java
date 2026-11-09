package dev.captience.realisticdamage.ragdoll.physics;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * A ball joint between a parent and a child body: a shoulder, a hip, the neck.
 *
 * <p>The joint frame is the parent's own frame, and the child's relative
 * rotation is split into a <em>twist</em> about the limb's long axis (local Y,
 * the way a Minecraft limb hangs) and a <em>swing</em> of that axis. The swing
 * is limited per component of its rotation vector — about local X (forward and
 * back, the model's {@code xRot}) and local Z (out to the side, {@code zRot}) —
 * which, unlike a pair of Euler angles, stays continuous all the way to an arm
 * raised overhead, whichever way it got there.
 *
 * <p>A joint can also carry a <em>drive</em>: a pull toward a target relative
 * rotation with a compliance (the inverse of its stiffness). That is a muscle.
 * Phase 0 only uses it for the little stiffness a dead body still has.
 */
public final class Joint {
	public final Body parent;
	public final Body child;
	/** Where the joint is, in each body's own frame. */
	public final Vector3d anchorParent;
	public final Vector3d anchorChild;

	public double swingXMin;
	public double swingXMax;
	public double swingZMin;
	public double swingZMax;
	public double twistMin;
	public double twistMax;

	/**
	 * The relative rotation the limits are measured from (child relative to
	 * parent). Identity for a humanoid, whose parts all rest square to the
	 * torso; a cow's body is modelled upright and turned flat, so its legs and
	 * head rest at a right angle to it, and that is where their range is centred.
	 */
	public final Quaterniond rest = new Quaterniond();

	/** The drive's target, child relative to parent. Identity: the rest pose. */
	public final Quaterniond target = new Quaterniond();
	/** The drive's stiffness, torque per radian; 0 is no drive. */
	public double stiffness;
	/** Relative angular velocity lost per second (joint friction). */
	public double damping;

	public Joint(Body parent, Body child, Vector3d anchorParent, Vector3d anchorChild,
			double swingXMin, double swingXMax, double swingZMin, double swingZMax,
			double twistMin, double twistMax) {
		this.parent = parent;
		this.child = child;
		this.anchorParent = new Vector3d(anchorParent);
		this.anchorChild = new Vector3d(anchorChild);
		this.swingXMin = swingXMin;
		this.swingXMax = swingXMax;
		this.swingZMin = swingZMin;
		this.swingZMax = swingZMax;
		this.twistMin = twistMin;
		this.twistMax = twistMax;
	}

	/** The child's rotation relative to the parent: parent^-1 * child. */
	public Quaterniond relative(Quaterniond out) {
		return parent.q.conjugate(out).mul(child.q);
	}

	/**
	 * The swing of a relative rotation as a rotation vector (y is always ~0),
	 * and its twist angle in {@code twist[0]}.
	 */
	public static void decompose(Quaterniond rel, Vector3d swingOut, double[] twist) {
		double x = rel.x;
		double y = rel.y;
		double z = rel.z;
		double w = rel.w;
		if (w < 0.0) {
			x = -x;
			y = -y;
			z = -z;
			w = -w;
		}
		double tl = Math.sqrt(y * y + w * w);
		double ty;
		double tw;
		if (tl < 1.0e-9) {
			// A half turn of swing: the twist is undefined, call it none.
			ty = 0.0;
			tw = 1.0;
		} else {
			ty = y / tl;
			tw = w / tl;
		}
		twist[0] = 2.0 * Math.atan2(ty, tw);
		// swing = rel * twist^-1, with twist = (0, ty, 0, tw)
		double sx = x * tw + z * ty;
		double sy = y * tw - w * ty;
		double sz = z * tw - x * ty;
		double sw = w * tw + y * ty;
		rotationVector(sx, sy, sz, sw, swingOut);
	}

	/** The rotation vector (axis times angle) of a unit quaternion, shortest way round. */
	static void rotationVector(double x, double y, double z, double w, Vector3d out) {
		if (w < 0.0) {
			x = -x;
			y = -y;
			z = -z;
			w = -w;
		}
		double s = Math.sqrt(x * x + y * y + z * z);
		if (s < 1.0e-9) {
			out.set(2.0 * x, 2.0 * y, 2.0 * z);
			return;
		}
		double angle = 2.0 * Math.atan2(s, w);
		out.set(x / s * angle, y / s * angle, z / s * angle);
	}

	/** The unit quaternion for rotation vector r. */
	static Quaterniond fromRotationVector(double rx, double ry, double rz, Quaterniond out) {
		double angle = Math.sqrt(rx * rx + ry * ry + rz * rz);
		if (angle < 1.0e-12) {
			return out.identity();
		}
		double s = Math.sin(angle * 0.5) / angle;
		return out.set(rx * s, ry * s, rz * s, Math.cos(angle * 0.5));
	}

	/** The relative rotation for a swing vector and a twist angle: swing * twist. */
	public static Quaterniond compose(double swingX, double swingZ, double twistAngle, Quaterniond out) {
		fromRotationVector(swingX, 0.0, swingZ, out);
		return out.mul(new Quaterniond().rotationY(twistAngle));
	}
}
