package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import net.minecraft.util.Mth;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Getting up, as a person does (2.0): not the whole body swinging up about its
 * hips in one move, "straight up like a pipe", but in stages, the way it lies
 * deciding which.
 *
 * <ul>
 *   <li><b>Face down:</b> push up on straight arms, rock back onto the knees,
 *   plant one foot, rise off the knee, stand.</li>
 *   <li><b>Face up:</b> sit up leaning back on the hands, rock forward over the
 *   feet into a squat, rise, stand.</li>
 * </ul>
 *
 * <p>Each stage is a key pose: how far the torso leans forward (radians; the
 * model's own forward lean, as vanilla's crouch), how far it rolls, how high
 * the hips are above the ground (model pixels; 12 standing), and each limb's
 * angles relative to the torso, chosen so the hands and feet that should be on
 * the ground are on it. The body moves from the ragdoll as it lay to the first
 * key, key to key, and from the last into vanilla's own standing pose, easing
 * in and out of each. It faces the way its head pointed (face down) or its
 * feet (face up) while it is on the ground, and turns to face where it is
 * looking only as it rises.
 */
final class GetUp {
	private GetUp() {}

	/** Ticks the whole of it takes. */
	static final int TICKS = 34;

	/** Where the eyes are in the head: the ragdoll view's (0, -4.5, -1) pixels from the neck. */
	private static final double EYE_Y = -4.5;
	private static final double EYE_Z = -1.0;

	/** A key pose. {@code rot} is per part (HumanoidRagdoll order), x y z; the torso's is unused. */
	record Key(double lean, double roll, double hipPx, float[] rot) {
		static Key of(double lean, double roll, double hipPx, float[] head, float[] rightArm, float[] leftArm,
				float[] rightLeg, float[] leftLeg) {
			float[] rot = new float[HumanoidRagdoll.PARTS * 3];
			put(rot, HumanoidRagdoll.HEAD, head);
			put(rot, HumanoidRagdoll.RIGHT_ARM, rightArm);
			put(rot, HumanoidRagdoll.LEFT_ARM, leftArm);
			put(rot, HumanoidRagdoll.RIGHT_LEG, rightLeg);
			put(rot, HumanoidRagdoll.LEFT_LEG, leftLeg);
			return new Key(lean, roll, hipPx, rot);
		}

		private static void put(float[] rot, int part, float[] xyz) {
			rot[part * 3] = xyz[0];
			rot[part * 3 + 1] = xyz[1];
			rot[part * 3 + 2] = xyz[2];
		}
	}

	private static float[] r(double x, double y, double z) {
		return new float[] {(float) x, (float) y, (float) z};
	}

	// A limb's world angle from hanging straight down is its own angle plus the
	// torso's lean (forward is negative, back positive), so a hand or foot on
	// the ground at hip height h sits at 12 * cos(angle) = h.

	/** Face down: on straight arms, the body a plank from shoulders to toes. */
	static final Key PUSH_UP = Key.of(1.05, 0.0, 6.0,
			r(-0.75, 0.0, 0.0), r(-1.05, 0.0, 0.10), r(-1.05, 0.0, -0.10), r(0.0, 0.0, 0.06), r(0.0, 0.0, -0.06));
	/** Rocked back onto the heels, the legs folded along the ground, hands still down in front. */
	static final Key KNEEL = Key.of(0.60, 0.0, 5.0,
			r(-0.35, 0.0, 0.0), r(-1.05, 0.0, 0.08), r(-1.05, 0.0, -0.08), r(0.85, 0.0, 0.10), r(0.85, 0.0, -0.10));
	/** Up on one knee: the right foot planted in front, a hand on that knee. */
	static final Key ONE_KNEE = Key.of(0.30, 0.05, 7.0,
			r(-0.15, 0.0, 0.0), r(-0.95, 0.0, -0.10), r(-0.20, 0.0, -0.15), r(-1.30, 0.0, 0.05), r(0.65, 0.0, -0.05));
	/** Rising off the knee, arms out a little for the balance. */
	static final Key RISING = Key.of(0.18, 0.02, 10.9,
			r(0.0, 0.0, 0.0), r(-0.35, 0.0, 0.18), r(-0.10, 0.0, -0.25), r(-0.65, 0.0, 0.02), r(0.20, 0.0, -0.02));
	/** Face up: sat up, leaning back on the hands, legs out along the ground. */
	static final Key SITTING = Key.of(-0.35, 0.0, 2.6,
			r(0.25, 0.0, 0.0), r(0.95, 0.0, 0.25), r(0.95, 0.0, -0.25), r(-1.20, 0.0, 0.08), r(-1.20, 0.0, -0.08));
	/** Rocked forward over the feet into a squat, arms out in front. */
	static final Key SQUAT = Key.of(0.65, 0.0, 7.5,
			r(-0.50, 0.0, 0.0), r(-1.30, 0.0, -0.10), r(-1.30, 0.0, 0.10), r(-1.55, 0.0, 0.12), r(-1.55, 0.0, -0.12));
	/** Rising out of the squat. */
	static final Key RISING_EVEN = Key.of(0.25, 0.0, 11.2,
			r(-0.10, 0.0, 0.0), r(-0.50, 0.0, 0.15), r(-0.50, 0.0, -0.15), r(-0.60, 0.0, 0.05), r(-0.60, 0.0, -0.05));

	/** A way up: its keys, the progress (0-1) at which each is reached, and how far each has turned to face ahead. */
	record Route(Key[] keys, float[] at, float[] turned) {}

	static final Route FACE_DOWN = new Route(new Key[] {PUSH_UP, KNEEL, ONE_KNEE, RISING},
			new float[] {0.18F, 0.40F, 0.62F, 0.82F}, new float[] {0.0F, 0.0F, 0.0F, 0.5F});
	static final Route FACE_UP = new Route(new Key[] {SITTING, SQUAT, RISING_EVEN},
			new float[] {0.24F, 0.52F, 0.78F}, new float[] {0.0F, 0.0F, 0.5F});

	/** How this body is getting up: its route and which way it faces while it is on the ground. */
	record Plan(Route route, float yaw) {}

	/** The plan for a body lying as this torso lies (world orientation, model space). */
	static Plan plan(Quaterniond torso, float fallback) {
		// The model's front is -z; its +y runs from the neck to the hips.
		Vector3d chest = torso.transform(new Vector3d(0.0, 0.0, -1.0));
		Vector3d down = torso.transform(new Vector3d(0.0, 1.0, 0.0));
		boolean faceUp = chest.y > 0.0;
		// Face down it gets up facing where its head was; face up, its feet.
		double dx = faceUp ? down.x : -down.x;
		double dz = faceUp ? down.z : -down.z;
		float yaw = Math.hypot(dx, dz) < 0.2 ? fallback : (float) Math.toDegrees(Math.atan2(-dx, dz));
		return new Plan(faceUp ? FACE_UP : FACE_DOWN, yaw);
	}

	/**
	 * Where the body is in its getting up at {@code progress} (0 lying, 1
	 * standing): which two poses it is between and how far. Index -1 is the
	 * ragdoll as it lay; {@code keys.length} is vanilla standing.
	 */
	record Stage(int from, int to, double t) {}

	static Stage stage(Route route, double progress) {
		float[] at = route.at();
		double before = 0.0;
		for (int i = 0; i <= at.length; i++) {
			double end = i < at.length ? at[i] : 1.0;
			if (progress <= end || i == at.length) {
				double t = Mth.clamp((progress - before) / Math.max(1.0e-6, end - before), 0.0, 1.0);
				return new Stage(i - 1, i, ease(t));
			}
			before = end;
		}
		return new Stage(at.length - 1, at.length, 1.0);
	}

	/** Mostly a smooth start and stop, with a little of the straight line so it does not halt at each pose. */
	static double ease(double t) {
		double smooth = t * t * (3.0 - 2.0 * t);
		return Mth.lerp(0.75, t, smooth);
	}

	/** The torso's world orientation (model space) at key {@code i}, facing {@code yaw}. */
	static Quaterniond keyTurn(Route route, int i, float lyingYaw, float standingYaw) {
		Key key = route.keys()[i];
		float yaw = lerpYaw(lyingYaw, standingYaw, route.turned()[i]);
		return HumanoidRagdoll.modelRotation(yaw).rotateX(key.lean()).rotateZ(key.roll());
	}

	static float lerpYaw(float from, float to, float t) {
		return from + Mth.wrapDegrees(to - from) * t;
	}

	/**
	 * The eyes' world position for your own first-person view: the head of
	 * the pose this frame, from the root the frame puts at the neck.
	 */
	static Vector3d eye(Vector3d neck, Quaterniond torso, Quaterniond headLocal, double unit) {
		Vector3d e = new Vector3d(0.0, EYE_Y, EYE_Z).mul(unit);
		headLocal.transform(e);
		return torso.transform(e).add(neck);
	}
}
