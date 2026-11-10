package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.ragdoll.HumanoidRagdoll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.jspecify.annotations.Nullable;

/**
 * Your own death seen from inside your ragdoll (active ragdolls, phase 4).
 *
 * <p>Your client runs the same ragdoll for your body as everyone else's does
 * for theirs, and the camera rides its head: placed at the eyes, turned the
 * way the face is turned. Not strapped to it: a tumbling skull would be
 * unwatchable, so the view chases the head through a smoother with a capped
 * turn rate, and Death Intensity says how much of the head's turn it follows
 * at all (the position always follows; a camera left floating where you stood
 * while your body flew away would be worse than none).
 *
 * <p>A death that stumbles is the exception while the body is still on its
 * feet. Its head hangs loose and whips about on the neck with every step
 * (right for a body seen from outside, a blur from inside), so until it goes
 * down the view is carried by the chest instead: it turns as the chest has
 * turned since you died, from where you were looking, and sits where your
 * eyes were on it. That leaves what a stumble is, the lurch, the steps and
 * the going over, and drops the whipping. Once the body is let go of the view
 * eases back to the head for the fall.
 *
 * <p>{@link DeathCamera} still decides everything else (the blackout held
 * while the body moves, the death screen fading in) and still runs the classic
 * view when Death View is set to Classic. Never in VR: the headset is the view
 * there, and Vivecraft's rule is that nothing else turns it.
 */
public final class RagdollView {
	private RagdollView() {}

	/** The fastest the view turns, radians a second. */
	private static final double MAX_TURN = 4.5;
	/** Seconds for the view to close most of the gap to where the head is pointing. */
	private static final double LAG = 0.07;
	/** The eyes, from the head's centre, in model pixels: a little up and forward, inside the head. */
	private static final double EYE_Y = -0.5;
	private static final double EYE_Z = -1.0;
	/** The same two for a body still on its feet, carried by its chest: slower, to read as a lurch. */
	private static final double STEADY_TURN = 3.0;
	private static final double STEADY_LAG = 0.16;
	/** Seconds for the view to go from the chest back to the head once the body is let go of. */
	private static final double STEADY_EASE = 0.3;

	private static final Quaterniond view = new Quaterniond();
	private static final Quaterniond start = new Quaterniond();
	private static boolean started;
	private static double roll;
	/** 1 while the chest carries the view (a body still on its feet), 0 riding the head. */
	private static double steady;
	/** The chest's turn when it began to carry the view, and where the eyes were on it. */
	private static final Quaterniond chestStart = new Quaterniond();
	private static final Vector3d eyeOnChest = new Vector3d();

	/** Whether the camera is riding the ragdoll this frame. */
	public static boolean active() {
		// First person only: in third person vanilla's camera already looks at
		// the body, and riding the head there would put the view inside it.
		return (DeathCamera.active() || LiveRagdolls.own() != null) && ClientEffectsConfig.get().ownRagdoll
				&& !dev.captience.realisticdamage.client.compat.Vr.active()
				&& !Minecraft.getInstance().gameRenderer.mainCamera().isDetached() && own() != null;
	}

	/**
	 * Third person while your ragdoll is down: vanilla's camera, but orbiting
	 * the ragdoll rather than the unseen vanilla body underneath it.
	 */
	public static boolean orbiting() {
		return (DeathCamera.active() || LiveRagdolls.own() != null) && ClientEffectsConfig.get().ownRagdoll
				&& !dev.captience.realisticdamage.client.compat.Vr.active()
				&& Minecraft.getInstance().gameRenderer.mainCamera().isDetached() && own() != null;
	}

	/** How far the orbit moves: from the vanilla body's eyes to the ragdoll's. */
	public static @Nullable Vec3 orbitShift(float partial) {
		LocalPlayer player = Minecraft.getInstance().player;
		Vec3 eye = eye(partial);
		if (player == null || eye == null) {
			return null;
		}
		return eye.subtract(player.getEyePosition(partial));
	}

	/** Your own body's ragdoll, while you are dead. */
	static @Nullable RagdollCorpse own() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return null;
		}
		LiveRagdolls.Live live = LiveRagdolls.own();
		return live != null ? live.ragdoll : RemoteCorpses.ownRagdoll(player);
	}

	/** 1 while the body lies, falling to 0 as a knocked-down body gets up. */
	private static float down(float partial) {
		LiveRagdolls.Live live = LiveRagdolls.own();
		return live == null ? 1.0F : live.blend(partial);
	}

	/**
	 * Whether the body is still going anywhere: the blackout waits for it. A
	 * body still on its feet (a death that stumbles) has yet to fall, however
	 * still it stands.
	 */
	public static boolean moving() {
		RagdollCorpse r = own();
		if (r == null) {
			return false;
		}
		if (r.body.controller() != null && r.body.controller().holding()) {
			return true;
		}
		return !r.body.asleep() && r.body.sim.maxMotion() > 0.4;
	}

	/** Once a frame: the view chases the head. */
	public static void frame(double deltaTicks) {
		RagdollCorpse r = own();
		LocalPlayer player = Minecraft.getInstance().player;
		if (r == null || player == null || !active()) {
			started = false;
			roll = 0.0;
			steady = 0.0;
			return;
		}
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double dt = deltaTicks / 20.0;
		boolean onFeet = r.body.controller() != null && r.body.controller().holding();
		if (!started) {
			look(player.getYRot(), player.getXRot(), start);
			view.set(start);
			started = true;
			steady = onFeet ? 1.0 : 0.0;
			if (onFeet) {
				r.body.orientation(HumanoidRagdoll.TORSO, partial, chestStart);
				chestStart.transformInverse(headEye(r, partial, eyeOnChest).sub(centre(r, HumanoidRagdoll.TORSO, partial)));
			}
		} else if (!onFeet) {
			steady = Math.max(0.0, steady - dt / STEADY_EASE);
		}
		// Where the face points, as a camera orientation (forward -Z, up +Y).
		Quaterniond head = r.body.orientation(HumanoidRagdoll.HEAD, partial, new Quaterniond());
		Vector3d forward = head.transform(new Vector3d(0.0, 0.0, -1.0));
		Vector3d up = head.transform(new Vector3d(0.0, -1.0, 0.0));
		Quaterniond target = basis(forward, up, new Quaterniond());
		if (steady > 0.0) {
			// Where you were looking, turned as the chest has turned since.
			Quaterniond chest = r.body.orientation(HumanoidRagdoll.TORSO, partial, new Quaterniond());
			Quaterniond carried = chest.mul(new Quaterniond(chestStart).conjugate()).mul(start);
			target.slerp(carried, steady);
		}
		double follow = Math.max(0.3, Math.min(1.0, ClientEffectsConfig.get().deathIntensity()));
		new Quaterniond(start).slerp(target, follow, target);
		// Getting up: back to where you are looking.
		float down = down(partial);
		if (down < 1.0F) {
			Quaterniond looking = look(player.getYRot(), player.getXRot(), new Quaterniond());
			looking.slerp(target, down, target);
		}
		// Chase it: smoothed, and never faster than a head can be watched turning.
		double angle = view.angle() == 0.0 && target.angle() == 0.0 ? 0.0 : angleBetween(view, target);
		if (angle > 1.0e-6) {
			double t = 1.0 - Math.exp(-dt / (LAG + (STEADY_LAG - LAG) * steady));
			t = Math.min(t, (MAX_TURN + (STEADY_TURN - MAX_TURN) * steady) * dt / angle);
			view.slerp(target, Math.min(1.0, t));
		}
		roll = rollOf(view);
	}

	/** The camera's position this frame: the ragdoll's eyes. */
	public static @Nullable Vec3 eye(float partial) {
		RagdollCorpse r = own();
		if (r == null) {
			return null;
		}
		Vector3d eye = headEye(r, partial, new Vector3d());
		if (steady > 0.0) {
			// Where the eyes were on the chest, wherever the head has lolled to since.
			Quaterniond chest = r.body.orientation(HumanoidRagdoll.TORSO, partial, new Quaterniond());
			Vector3d onChest = chest.transform(new Vector3d(eyeOnChest)).add(centre(r, HumanoidRagdoll.TORSO, partial));
			eye.lerp(onChest, steady);
		}
		Vec3 ragdollEye = new Vec3(eye.x, eye.y, eye.z);
		float down = down(partial);
		LocalPlayer player = Minecraft.getInstance().player;
		if (down < 1.0F && player != null) {
			return player.getEyePosition(partial).lerp(ragdollEye, down);
		}
		return ragdollEye;
	}

	/** The eyes in the ragdoll's head, wherever it is between the last two ticks. */
	private static Vector3d headEye(RagdollCorpse r, float partial, Vector3d out) {
		Quaterniond head = r.body.orientation(HumanoidRagdoll.HEAD, partial, new Quaterniond());
		return head.transform(out.set(0.0, EYE_Y, EYE_Z).mul(r.body.unit)).add(centre(r, HumanoidRagdoll.HEAD, partial));
	}

	/** A part's centre between the last two ticks. */
	private static Vector3d centre(RagdollCorpse r, int part, float partial) {
		return new Vector3d(r.body.body(part).tickX).lerp(r.body.body(part).x, partial);
	}

	/** Whether the chest is carrying the view (a body still on its feet), for the tests. */
	public static boolean steady() {
		return steady >= 1.0;
	}

	/** The camera's yaw and pitch this frame, degrees, Minecraft's conventions. */
	public static float yaw() {
		Vector3d f = view.transform(new Vector3d(0.0, 0.0, -1.0));
		return (float) Math.toDegrees(Math.atan2(-f.x, f.z));
	}

	public static float pitch() {
		Vector3d f = view.transform(new Vector3d(0.0, 0.0, -1.0));
		return (float) Math.max(-89.0, Math.min(89.0, Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, f.y))))));
	}

	/** The roll about the line of sight, degrees, for the view pose stack. */
	public static double roll() {
		return roll;
	}

	public static void reset() {
		started = false;
		roll = 0.0;
		steady = 0.0;
	}

	/** A camera orientation for a yaw and pitch (forward -Z, up +Y). */
	private static Quaterniond look(float yawDegrees, float pitchDegrees, Quaterniond out) {
		double yaw = Math.toRadians(yawDegrees);
		double pitch = Math.toRadians(pitchDegrees);
		Vector3d forward = new Vector3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		return basis(forward, new Vector3d(0.0, 1.0, 0.0), out);
	}

	/**
	 * The orientation whose -Z is {@code forward} and whose +Y is as near
	 * {@code up} as a right angle to forward allows: built from the axes, so
	 * there is no question which way a library's look-along points.
	 */
	static Quaterniond basis(Vector3d forward, Vector3d up, Quaterniond out) {
		Vector3d f = new Vector3d(forward).normalize();
		Vector3d r = new Vector3d(f).cross(up);
		if (r.lengthSquared() < 1.0e-10) {
			// Looking straight along up: any right will do.
			r.set(1.0, 0.0, 0.0).cross(f);
			if (r.lengthSquared() < 1.0e-10) {
				r.set(0.0, 0.0, 1.0).cross(f);
			}
		}
		r.normalize();
		Vector3d u = new Vector3d(r).cross(f).normalize();
		org.joml.Matrix3d m = new org.joml.Matrix3d(r, u, new Vector3d(f).negate());
		return out.setFromNormalized(m);
	}

	private static double angleBetween(Quaterniond a, Quaterniond b) {
		double dot = Math.abs(a.dot(b));
		return 2.0 * Math.acos(Math.min(1.0, dot));
	}

	/**
	 * How far the view's up is turned about its line of sight from the up a
	 * camera with this yaw and pitch would have; positive rolls the picture
	 * counter-clockwise, as the view pose stack's Z turn does.
	 */
	private static double rollOf(Quaterniond q) {
		Vector3d f = q.transform(new Vector3d(0.0, 0.0, -1.0));
		Vector3d u = q.transform(new Vector3d(0.0, 1.0, 0.0));
		Quaterniond plain = look(yawOf(f), pitchOf(f), new Quaterniond());
		Vector3d u0 = plain.transform(new Vector3d(0.0, 1.0, 0.0));
		double sin = new Vector3d(u0).cross(u).dot(f);
		double cos = u0.dot(u);
		return Math.toDegrees(Math.atan2(sin, cos));
	}

	private static float yawOf(Vector3d f) {
		return (float) Math.toDegrees(Math.atan2(-f.x, f.z));
	}

	private static float pitchOf(Vector3d f) {
		return (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, f.y))));
	}
}
