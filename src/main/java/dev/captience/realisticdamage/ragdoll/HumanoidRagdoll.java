package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.Body;
import dev.captience.realisticdamage.ragdoll.physics.BodySim;
import dev.captience.realisticdamage.ragdoll.physics.Joint;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * A humanoid's six model boxes as six rigid bodies (1.7, active ragdolls
 * phase 0): head, torso, two arms, two legs, joined at the neck, shoulders and
 * hips where the model pivots them, with human ranges of motion.
 *
 * <p>Built from the model's own geometry, in the model's own units (pixels),
 * so it fits whatever drew the body: a wide or slim player, a zombie, a
 * skeleton's sticks. Everything here is plain Java so the unit tests can drop
 * it on stairs; the client supplies the geometry, the world and the drawing.
 *
 * <h2>Frames</h2>
 * A model point {@code m} (pixels, y down the body, as {@code ModelPart} uses)
 * is in the world at {@code feet + Qm * unit * (m - (0, 24.016, 0))}, where
 * {@code Qm} is vanilla's {@code Ry(180 - bodyYaw) * Rz(180)} and {@code unit}
 * is blocks per pixel (entity scale times renderer scale over sixteen). Each
 * body's frame is its part's frame (so the child's rotation relative to the
 * torso <em>is</em> the part's {@code xRot/yRot/zRot}), moved to the centre of
 * its cube.
 *
 * <h2>Muscles</h2>
 * Every joint carries a drive back to the pose the body died in, as strong as
 * {@link Muscles} says at that moment: firm while there is someone in there,
 * fading to a floor (a joint is not a free hinge) in an order that depends on
 * how it died. See {@link #setMuscles}.
 */
public final class HumanoidRagdoll {
	public static final int HEAD = 0;
	public static final int TORSO = 1;
	public static final int RIGHT_ARM = 2;
	public static final int LEFT_ARM = 3;
	public static final int RIGHT_LEG = 4;
	public static final int LEFT_LEG = 5;
	public static final int PARTS = 6;

	/** Model units per block at scale one with no renderer scale: 16. The offset vanilla applies, in pixels. */
	static final double MODEL_DROP = 1.501 * 16.0;

	/** Share of the body's mass in each part (Winter's segment table, arm and leg each whole). */
	private static final double[] MASS_SHARE = {0.081, 0.497, 0.050, 0.050, 0.161, 0.161};
	private static final double TOTAL_MASS = 70.0;

	/**
	 * Joint limits in radians, per child part: swing about local X (min, max),
	 * swing about local Z (min, max), twist about the limb (min, max). The signs
	 * are the model's: negative X raises a limb forward, positive Z takes the
	 * right side outward, positive head X looks down.
	 */
	private static final double[][] LIMITS = {
			/* head      */ {-1.0, 0.85, -0.6, 0.6, -1.3, 1.3},
			/* torso     */ null,
			// Across the body (adduction) to about 50 degrees: a hand can reach the
			// other side of the chest. The torso, not the limit, stops the arm
			// going through it.
			/* right arm */ {-3.0, 0.9, -0.9, 2.9, -1.3, 1.3},
			/* left arm  */ {-3.0, 0.9, -2.9, 0.9, -1.3, 1.3},
			// Hip flexion with a straight leg stops at about 80 degrees: the
			// hamstrings, not the hip. A Minecraft leg has no knee, so it is always
			// straight, and a body allowed the bent-knee 115 folds into an
			// upside-down V on its head and feet instead of going down.
			/* right leg */ {-1.4, 0.45, -0.3, 0.9, -0.7, 0.7},
			/* left leg  */ {-1.4, 0.45, -0.9, 0.3, -0.7, 0.7},
	};

	/** Self-collision: the points of the first body against the box of the second. */
	private static final int[] SELF_PAIRS = {
			RIGHT_ARM, TORSO, LEFT_ARM, TORSO,
			RIGHT_LEG, LEFT_LEG, LEFT_LEG, RIGHT_LEG,
			RIGHT_ARM, RIGHT_LEG, LEFT_ARM, LEFT_LEG,
			RIGHT_ARM, HEAD, LEFT_ARM, HEAD,
	};

	/** One model part: its pivot and its cube, pixels, in the part's own frame. */
	public record Part(float pivotX, float pivotY, float pivotZ,
	                   float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {}

	/** Vanilla's player model, wide arms; the unit tests' default body. */
	public static Part[] playerParts(boolean slim) {
		float armW = slim ? 3.0F : 4.0F;
		return new Part[] {
				new Part(0, 0, 0, -4, -8, -4, 4, 0, 4),
				new Part(0, 0, 0, -4, 0, -2, 4, 12, 2),
				new Part(-5, slim ? 2.5F : 2.0F, 0, -armW + 1.0F, -2, -2, 1.0F, 10, 2),
				new Part(5, slim ? 2.5F : 2.0F, 0, -1, -2, -2, armW - 1.0F, 10, 2),
				new Part(-1.9F, 12, 0, -2, 0, -2, 2, 12, 2),
				new Part(1.9F, 12, 0, -2, 0, -2, 2, 12, 2),
		};
	}

	public final BodySim sim;
	private final Body[] bodies;
	private final Joint[] joints;
	/** Each cube's centre in its part frame, pixels; the body origin sits there. */
	private final Vector3d[] centre = new Vector3d[PARTS];
	/** Blocks per pixel. */
	public final double unit;
	/** Each body's pose at launch, for mapping points on the standing body onto this one. */
	private final Vector3d[] startX = new Vector3d[PARTS];
	private final Quaterniond[] startQ = new Quaterniond[PARTS];
	/** Each joint's child length, blocks, and the drive's scale: weight times length. */
	private final double[] driveScale;

	/** Each part's pivot in the model, pixels (the torso's frame at rest). */
	private final Vector3d[] pivotPx = new Vector3d[PARTS];
	/** The pose each joint's muscles hold when nothing else asks them to: the pose at death. */
	private final Quaterniond[] restTarget = new Quaterniond[PARTS];
	/** This tick's reflex tone per part (phase 3), on top of the muscles' own; reset every tick. */
	private final double[] reflexTone = new double[PARTS];
	/** What the body still does as it dies (phase 3), or null for none. */
	private @org.jspecify.annotations.Nullable Behaviour behaviour;
	/** What keeps it on its feet (stumbling deaths), or null for none. */
	private @org.jspecify.annotations.Nullable Controller controller;
	/** This tick's exact tone per part, replacing the muscles' own; NaN for none. Reset every tick. */
	private final double[] toneOverride = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};

	/** How much wider than normal each joint's limits have been made so far. */
	private final double[] loosened = {1.0, 1.0, 1.0, 1.0, 1.0, 1.0};
	/** The muscles: how each joint holds and lets go (phase 2); see {@link #setMuscles}. */
	private Muscles muscles = Muscles.standard();
	/** Which part each joint moves, in {@link #joints} order. */
	private final int[] jointPart;

	private double age;
	private int stillTicks;
	private boolean asleep;
	private long sleepDigest;

	private static final double STILL_SPEED = 0.12;
	private static final int STILL_TICKS = 20;
	private static final int WAKE_CHECK_TICKS = 10;
	/** Position-only passes when a body falls asleep, so its joints and the floor agree. */
	private static final int RELAX_PASSES = 12;

	/**
	 * Builds the body standing where the model drew it.
	 *
	 * @param parts     the six parts' geometry, in {@code HEAD..LEFT_LEG} order
	 * @param feet      the entity's position, world
	 * @param bodyYaw   vanilla's body yaw, degrees
	 * @param unit      blocks per pixel
	 * @param rotations each part's {@code xRot, yRot, zRot} at the moment of death, 18 floats
	 */
	public HumanoidRagdoll(Part[] parts, Vector3d feet, float bodyYaw, double unit, float[] rotations) {
		this(parts, feet, bodyYaw, unit, rotations, null);
	}

	/**
	 * As above, with some limbs already gone ({@code present[i]} false): a lost
	 * limb takes no part in the fall. It rides on the torso where it was, so
	 * whatever is drawn at its joint (a stump) stays put.
	 */
	public HumanoidRagdoll(Part[] parts, Vector3d feet, float bodyYaw, double unit, float[] rotations,
			boolean @org.jspecify.annotations.Nullable [] present) {
		this.unit = unit;
		this.startFeet = new Vector3d(feet);
		this.startYaw = bodyYaw;
		this.present = new boolean[PARTS];
		for (int i = 0; i < PARTS; i++) {
			this.present[i] = present == null || present[i] || i == TORSO;
		}
		this.bodies = new Body[PARTS];
		Quaterniond qm = modelRotation(bodyYaw);
		for (int i = 0; i < PARTS; i++) {
			Part p = parts[i];
			double hx = Math.max(0.5, (p.maxX() - p.minX()) * 0.5) * unit;
			double hy = Math.max(0.5, (p.maxY() - p.minY()) * 0.5) * unit;
			double hz = Math.max(0.5, (p.maxZ() - p.minZ()) * 0.5) * unit;
			bodies[i] = new Body(hx, hy, hz, TOTAL_MASS * MASS_SHARE[i]);
			centre[i] = new Vector3d((p.minX() + p.maxX()) * 0.5, (p.minY() + p.maxY()) * 0.5, (p.minZ() + p.maxZ()) * 0.5);
			pivotPx[i] = new Vector3d(p.pivotX(), p.pivotY(), p.pivotZ());
			Quaterniond rot = partRotation(rotations, i);
			Quaterniond q = new Quaterniond(qm).mul(rot);
			// The cube's centre, in the model frame: pivot + R * centre.
			Vector3d m = rot.transform(new Vector3d(centre[i])).add(p.pivotX(), p.pivotY(), p.pivotZ());
			bodies[i].place(modelToWorld(m, feet, qm, unit), q);
			startX[i] = new Vector3d(bodies[i].x);
			startQ[i] = new Quaterniond(bodies[i].q);
		}

		Part torso = parts[TORSO];
		Quaterniond torsoRot = partRotation(rotations, TORSO);
		java.util.List<Joint> list = new java.util.ArrayList<>();
		java.util.List<Integer> partList = new java.util.ArrayList<>();
		driveScale = new double[PARTS - 1];
		Body t = bodies[TORSO];
		for (int i = 0; i < PARTS; i++) {
			// Every part's pose in the torso's frame, for a lost one to ride on.
			riderQ[i] = new Quaterniond(t.q).conjugate().mul(bodies[i].q);
			riderX[i] = new Quaterniond(t.q).conjugate().transform(new Vector3d(bodies[i].x).sub(t.x));
			if (i == TORSO || !this.present[i]) {
				continue;
			}
			Part p = parts[i];
			// The joint sits at the child's pivot. In the torso's frame:
			// R_t^-1 (pivot - torsoPivot) - torsoCentre.
			Vector3d inTorso = new Quaterniond(torsoRot).conjugate().transform(new Vector3d(
					p.pivotX() - torso.pivotX(), p.pivotY() - torso.pivotY(), p.pivotZ() - torso.pivotZ()))
					.sub(centre[TORSO]).mul(unit);
			Vector3d inChild = new Vector3d(centre[i]).negate().mul(unit);
			double[] l = LIMITS[i];
			Joint j = new Joint(bodies[TORSO], bodies[i], inTorso, inChild, l[0], l[1], l[2], l[3], l[4], l[5]);
			j.damping = 2.5;
			// The muscles hold what the body was doing when it died.
			j.relative(j.target);
			restTarget[i] = new Quaterniond(j.target);
			partList.add(i);
			driveScale[list.size()] = bodies[i].mass * 32.0 * bodies[i].hy * 2.0;
			list.add(j);
		}
		this.joints = list.toArray(new Joint[0]);
		this.jointPart = partList.stream().mapToInt(Integer::intValue).toArray();
		java.util.List<Body> simBodies = new java.util.ArrayList<>();
		for (int i = 0; i < PARTS; i++) {
			if (this.present[i]) {
				simBodies.add(bodies[i]);
			}
		}
		java.util.List<Integer> pairList = new java.util.ArrayList<>();
		for (int i = 0; i < SELF_PAIRS.length; i += 2) {
			if (this.present[SELF_PAIRS[i]] && this.present[SELF_PAIRS[i + 1]]) {
				pairList.add(simBodies.indexOf(bodies[SELF_PAIRS[i]]));
				pairList.add(simBodies.indexOf(bodies[SELF_PAIRS[i + 1]]));
			}
		}
		this.sim = new BodySim(simBodies.toArray(new Body[0]), joints,
				pairList.stream().mapToInt(Integer::intValue).toArray());
		applyTone();
	}

	/** Where it stood and faced when it died. */
	private final Vector3d startFeet;
	private final float startYaw;

	/** Which parts are still attached and simulated. */
	private final boolean[] present;
	private final Quaterniond[] riderQ = new Quaterniond[PARTS];
	private final Vector3d[] riderX = new Vector3d[PARTS];

	/** Puts every lost part where it rides on the torso. */
	private void carryLost() {
		Body t = bodies[TORSO];
		for (int i = 0; i < PARTS; i++) {
			if (present[i]) {
				continue;
			}
			Body b = bodies[i];
			b.tickX.set(b.x);
			b.tickQ.set(b.q);
			t.q.transform(riderX[i], b.x).add(t.x);
			b.q.set(t.q).mul(riderQ[i]);
		}
	}

	/**
	 * Takes a limb (or the head) off a body that is already falling (2.0: a dead
	 * body that lands hard enough can still lose one). From here on it is as if
	 * the body had been built without it: the part takes no further part in the
	 * fall and rides on the torso as it hung at this moment, so the stump drawn
	 * at its joint does not jump. The torso cannot be taken; a part already gone
	 * stays gone. Returns whether anything changed.
	 */
	public boolean detach(int part) {
		if (part == TORSO || part < 0 || part >= PARTS || !present[part]) {
			return false;
		}
		present[part] = false;
		Body t = bodies[TORSO];
		Body b = bodies[part];
		riderQ[part] = new Quaterniond(t.q).conjugate().mul(b.q);
		riderX[part] = new Quaterniond(t.q).conjugate().transform(new Vector3d(b.x).sub(t.x));
		b.gone = true;
		b.v.zero();
		b.w.zero();
		b.impact = 0.0;
		// What was resting on it, or asleep with it, has to find its place again.
		wake();
		return true;
	}

	/**
	 * Lets a limb's bone go on a body that is already falling (2.0): nothing
	 * holds it any more and it bends further than it should, exactly as a limb
	 * that was broken before the death ({@link Muscles#forDeath}).
	 */
	public void breakLimb(int part) {
		if (part == TORSO || part == HEAD || part < 0 || part >= PARTS || !present[part]) {
			return;
		}
		setMuscles(muscles.withBroken(part));
		wake();
	}

	/**
	 * How hard a part struck the world in the tick just run: the closing speed
	 * of whichever of its points hit hardest, blocks a second; 0 for a part that
	 * touched nothing, is gone, or belongs to a body asleep.
	 */
	public double impact(int part) {
		return present[part] && !asleep ? bodies[part].impact : 0.0;
	}

	/** The whole body's velocity (its centre of mass) at the start of the tick just run, blocks a second. */
	private final Vector3d arriving = new Vector3d();

	/**
	 * How hard a part struck the world in the tick just run, as the body
	 * arriving and not as a limb flailing: the part's own closing speed, but
	 * never more than the speed the whole body was closing on that surface at.
	 * A tumbling body's arm can whip into the floor at three times the speed the
	 * body is travelling, and which arm does is different on every copy of the
	 * ragdoll; how fast the body as a whole came down is the same on all of
	 * them (measured: {@code CorpseAgreementTest}). Blocks a second.
	 */
	public double strike(int part) {
		double own = impact(part);
		if (own <= 0.0) {
			return 0.0;
		}
		double closing = -arriving.dot(bodies[part].impactNormal);
		return Math.max(0.0, Math.min(own, closing));
	}

	/** The whole body's velocity at the start of the tick just run, blocks a second. */
	public Vector3d arriving() {
		return new Vector3d(arriving);
	}

	/** Vanilla's model rotation for a body yaw: Ry(180 - yaw) * Rz(180). */
	public static Quaterniond modelRotation(float bodyYaw) {
		return new Quaterniond().rotationY(Math.toRadians(180.0 - bodyYaw)).rotateZ(Math.PI);
	}

	/** {@code ModelPart}'s rotation: rotationZYX(z, y, x). */
	static Quaterniond partRotation(float[] rotations, int part) {
		return new Quaterniond().rotationZYX(rotations[part * 3 + 2], rotations[part * 3 + 1], rotations[part * 3]);
	}

	static Vector3d modelToWorld(Vector3d m, Vector3d feet, Quaterniond qm, double unit) {
		Vector3d v = new Vector3d(m.x, m.y - MODEL_DROP, m.z).mul(unit);
		return qm.transform(v).add(feet);
	}

	public Body body(int part) {
		return bodies[part];
	}

	/** Whether a part is still on the body and in the fall (false: a lost limb, carried). */
	public boolean present(int part) {
		return present[part];
	}

	// --- Launch -----------------------------------------------------------------

	/** Adds a velocity to every part, blocks a second. */
	public void push(double vx, double vy, double vz) {
		for (Body b : bodies) {
			b.v.add(vx, vy, vz);
		}
	}

	/** Adds a rigid spin about a world point: every part moves as if the body were one piece. */
	public void spin(Vector3d axisTimesRate, Vector3d about) {
		for (Body b : bodies) {
			Vector3d r = new Vector3d(b.x).sub(about);
			b.v.add(new Vector3d(axisTimesRate).cross(r));
			b.w.add(axisTimesRate);
		}
	}

	/** Adds a velocity to one part only: where the blow landed. */
	public void kick(int part, double vx, double vy, double vz) {
		bodies[part].v.add(vx, vy, vz);
	}

	/**
	 * Gives each part the motion it had in the last tick of its life: from the
	 * pose one tick before to the pose at death (the walk cycle, mostly).
	 *
	 * <p>Only the limbs' own swing and the body's travel are inherited, not a
	 * change of facing: a watching client's copy of someone else's body turns
	 * in jumps, and a jump read as a spin would hurl the body round. The swing
	 * is damped too ({@code limbShare}), because vanilla's walk cycle swings the
	 * legs far faster than legs swing, and a running death would kick them out.
	 *
	 * @param travel   the body's velocity, blocks a second
	 * @param limbShare how much of the walk cycle's swing the limbs keep, 0..1
	 */
	public void inheritMotion(Part[] parts, float[] rotationsBefore, Vector3d travel, double limbShare, double dt) {
		float yaw = startYaw;
		Vector3d feetBefore = new Vector3d(startFeet).sub(new Vector3d(travel).mul(dt));
		HumanoidRagdoll before = new HumanoidRagdoll(parts, feetBefore, yaw, unit, rotationsBefore);
		for (int i = 0; i < PARTS; i++) {
			Body was = before.bodies[i];
			Body b = bodies[i];
			Vector3d moved = new Vector3d(b.x).sub(was.x).div(dt);
			// The travel in full, the swing on top of it in part.
			Vector3d swingPart = moved.sub(travel).mul(limbShare);
			b.v.add(travel).add(swingPart);
			Quaterniond dq = new Quaterniond(b.q).mul(new Quaterniond(was.q).conjugate());
			if (dq.w < 0.0) {
				dq.set(-dq.x, -dq.y, -dq.z, -dq.w);
			}
			b.w.add(new Vector3d(dq.x, dq.y, dq.z).mul(2.0 / dt * limbShare));
		}
	}

	// --- Running ----------------------------------------------------------------

	/** One tick of {@code dt} seconds. */
	public void tick(PhysicsWorld world, double dt) {
		age += dt;
		if (asleep) {
			for (Body b : bodies) {
				b.tickX.set(b.x);
				b.tickQ.set(b.q);
				b.impact = 0.0;
			}
			carryLost();
			if (behaviour != null && behaviour.wantsWake(this, age)) {
				wake();
			} else if (((int) Math.round(age / dt)) % WAKE_CHECK_TICKS == 0 && sim.worldDigest(world) != sleepDigest) {
				asleep = false;
				stillTicks = 0;
			} else {
				return;
			}
		}
		if (behaviour != null || controller != null) {
			for (int i = 0; i < joints.length; i++) {
				joints[i].target.set(restTarget[jointPart[i]]);
			}
			java.util.Arrays.fill(reflexTone, 0.0);
			java.util.Arrays.fill(toneOverride, Double.NaN);
			for (Body b : bodies) {
				b.friction = 1.0;
				b.sink = 0.0;
			}
			sim.uprightStiffness = 0.0;
			sim.uprightAxis.set(0.0, -1.0, 0.0);
			sim.facingStiffness = 0.0;
			sim.supported = null;
			sim.supportAccel = 0.0;
			if (behaviour != null) {
				behaviour.apply(this, world, age, dt);
			}
			if (controller != null) {
				controller.apply(this, world, age, dt);
			}
		}
		applyTone();
		// How the body as a whole is travelling as this tick begins, for strike().
		arriving.zero();
		double mass = 0.0;
		for (int i = 0; i < PARTS; i++) {
			if (present[i]) {
				arriving.fma(bodies[i].mass, bodies[i].v);
				mass += bodies[i].mass;
			}
		}
		arriving.div(mass);
		for (int i = 0; i < PARTS; i++) {
			// A limb its reflexes are moving is not to be smothered as it settles,
			// nor is a leg being stood on or stepped with.
			bodies[i].settles = reflexTone[i] < 0.6 && Double.isNaN(toneOverride[i]);
		}
		sim.step(dt, world);
		carryLost();
		// At rest when nothing is moving, or when nothing has moved: two
		// constraints pressing against each other (a limb at its limit on the
		// floor) can leave a body reporting speed while its pose stays put, and
		// it would never sleep. A body still being held on its feet never is,
		// nor one its controller is not done with.
		boolean held = controller != null && (controller.holding() || controller.restless());
		boolean still = !held && (sim.maxMotion() < STILL_SPEED || poseUnchanged());
		if (still) {
			if (++stillTicks >= STILL_TICKS) {
				asleep = true;
				sim.relax(RELAX_PASSES);
				carryLost();
				sleepDigest = sim.worldDigest(world);
			}
		} else {
			stillTicks = 0;
		}
	}

	/** The pose a while ago, to tell a body that is not going anywhere. */
	private final Vector3d[] anchorX = new Vector3d[PARTS];
	private final Quaterniond[] anchorQ = new Quaterniond[PARTS];
	private int anchorAge = -1;
	/** Over this many ticks, a body that moved less than this has stopped. */
	private static final int ANCHOR_TICKS = 10;
	private static final double ANCHOR_MOVE = 0.03;
	private static final double ANCHOR_TURN = 0.05;

	private boolean poseUnchanged() {
		if (anchorAge < 0 || anchorAge >= ANCHOR_TICKS) {
			boolean unchanged = anchorAge >= ANCHOR_TICKS;
			for (int i = 0; i < PARTS && unchanged; i++) {
				Body b = bodies[i];
				double turn = 2.0 * Math.acos(Math.min(1.0, Math.abs(anchorQ[i].dot(b.q))));
				unchanged = anchorX[i].distance(b.x) < ANCHOR_MOVE && turn < ANCHOR_TURN;
			}
			for (int i = 0; i < PARTS; i++) {
				if (anchorX[i] == null) {
					anchorX[i] = new Vector3d();
					anchorQ[i] = new Quaterniond();
				}
				anchorX[i].set(bodies[i].x);
				anchorQ[i].set(bodies[i].q);
			}
			anchorAge = 0;
			// Still is still for the whole stretch: count it all at once.
			if (unchanged) {
				stillTicks = Math.max(stillTicks, STILL_TICKS - 1);
			}
			return unchanged;
		}
		anchorAge++;
		return false;
	}

	private void applyTone() {
		for (int i = 0; i < joints.length; i++) {
			int part = jointPart[i];
			double tone = Double.isNaN(toneOverride[part])
					? Math.max(muscles.tone(part, age), reflexTone[part]) : toneOverride[part];
			joints[i].stiffness = tone * driveScale[i];
		}
	}

	/**
	 * Gives the body its muscles (phase 2). A broken limb's joint is loosened
	 * here too, so set them before the first tick.
	 */
	public void setMuscles(Muscles m) {
		this.muscles = m;
		for (int i = 0; i < joints.length; i++) {
			// Relative to what was applied before, so muscles given twice (a
			// body let go after being held up) do not loosen a limb twice.
			double f = m.looseness[jointPart[i]] / loosened[jointPart[i]];
			loosened[jointPart[i]] = m.looseness[jointPart[i]];
			if (f != 1.0) {
				Joint j = joints[i];
				j.swingXMin *= f;
				j.swingXMax *= f;
				j.swingZMin *= f;
				j.swingZMax *= f;
				j.twistMin *= f;
				j.twistMax *= f;
			}
		}
		applyTone();
	}

	public Muscles muscles() {
		return muscles;
	}

	/** Which part joint {@code i} moves. */
	public int jointPart(int i) {
		return jointPart[i];
	}

	// --- For behaviours (phase 3) ----------------------------------------------

	public void setBehaviour(@org.jspecify.annotations.Nullable Behaviour behaviour) {
		this.behaviour = behaviour;
	}

	public @org.jspecify.annotations.Nullable Behaviour behaviour() {
		return behaviour;
	}

	/**
	 * This tick: hold the torso upright, as strongly as {@code strength} times
	 * the whole body's weight leaning on it (a stagger; a body without knees
	 * cannot catch its own balance).
	 */
	public void holdUpright(double strength) {
		sim.upright = bodies[TORSO];
		sim.uprightStiffness = Math.max(0.0, strength) * TOTAL_MASS * 32.0;
	}

	/**
	 * This tick: hold the torso's facing (its front) toward a direction along
	 * the ground, as strongly as {@code strength} times the body's weight.
	 */
	public void holdFacing(Vector3d dir, double strength) {
		sim.upright = bodies[TORSO];
		sim.facing.set(dir.x, 0.0, dir.z);
		sim.facingStiffness = Math.max(0.0, strength) * TOTAL_MASS * 32.0;
	}

	/**
	 * This tick: carry {@code share} of the body's weight on the torso, as if
	 * held up under the arms (stumbling deaths). The legs still stand on the
	 * ground; they just have less to hold up and longer to step in.
	 */
	public void support(double share) {
		sim.supported = bodies[TORSO];
		sim.supportAccel = Math.max(0.0, share) * sim.gravity * TOTAL_MASS / bodies[TORSO].mass;
	}

	/** This tick: exactly this tone at the part's joint, in place of its muscles' own (a leg stood on or stepped with). */
	public void setTone(int part, double tone) {
		toneOverride[part] = Math.max(0.0, tone);
	}

	public void setController(@org.jspecify.annotations.Nullable Controller controller) {
		this.controller = controller;
	}

	public @org.jspecify.annotations.Nullable Controller controller() {
		return controller;
	}

	/** A whole body's mass, kilograms: the same for every body, whatever its size. */
	public static double totalMass() {
		return TOTAL_MASS;
	}

	/** A limb's length from its joint to its far end, blocks. */
	public double length(int part) {
		return bodies[part].hy * 2.0;
	}

	/** Where a part's joint is in the world now (a hip, a shoulder). */
	public Vector3d joint(int part, Vector3d out) {
		Body b = bodies[part];
		return b.q.transform(new Vector3d(centre[part]).negate().mul(unit), out).add(b.x);
	}

	/** The middle of a limb's far face in its own frame (a foot's sole, the flat of a hand). */
	public Vector3d tipLocal(int part, Vector3d out) {
		return out.set(0.0, part == HEAD ? -bodies[part].hy : bodies[part].hy, 0.0);
	}

	/** Wakes a sleeping body (a twitch). */
	public void wake() {
		asleep = false;
		stillTicks = 0;
		anchorAge = -1;
	}

	/** The joint that moves this part, or null when the part is the torso or gone. */
	public @org.jspecify.annotations.Nullable Joint jointOf(int part) {
		for (int i = 0; i < joints.length; i++) {
			if (jointPart[i] == part) {
				return present[part] ? joints[i] : null;
			}
		}
		return null;
	}

	/** This tick: the part's muscles pull at least this hard (multiples of its gravity torque). */
	public void reflex(int part, double tone) {
		reflexTone[part] = Math.max(reflexTone[part], tone);
	}

	/** This tick: the joint's target, child relative to torso. */
	public void target(int part, Quaterniond rel) {
		Joint j = jointOf(part);
		if (j != null) {
			j.target.set(rel);
		}
	}

	/** This tick: the target as a swing (about the limb's x and z) and a twist. */
	public void target(int part, double swingX, double swingZ, double twistAngle) {
		Joint j = jointOf(part);
		if (j != null) {
			Joint.compose(swingX, swingZ, twistAngle, j.target);
		}
	}

	/** The pose the part's muscles hold when nothing else asks: the pose at death. */
	public Quaterniond restTarget(int part) {
		return restTarget[part];
	}

	/**
	 * This tick: the limb points from its pivot toward a point given in the
	 * torso's own frame, in model pixels (y down the body, -z the front).
	 */
	public void aimAtModel(int part, double x, double y, double z) {
		Vector3d d = new Vector3d(x, y, z).sub(pivotPx[part]);
		aimLocal(part, d);
	}

	/** This tick: the limb points along a world direction. */
	public void aimWorld(int part, Vector3d dirWorld) {
		Vector3d d = bodies[TORSO].q.conjugate(new Quaterniond()).transform(new Vector3d(dirWorld));
		aimLocal(part, d);
	}

	private void aimLocal(int part, Vector3d d) {
		Joint j = jointOf(part);
		if (j == null || d.lengthSquared() < 1.0e-12) {
			return;
		}
		d.normalize();
		// The shortest turn that takes the limb's own axis (+Y) to d.
		j.target.rotationTo(0.0, 1.0, 0.0, d.x, d.y, d.z);
	}

	/** The far end of a limb (a hand, a foot, the top of the head), world. */
	public Vector3d tip(int part) {
		Body b = bodies[part];
		double end = part == HEAD ? -b.hy : b.hy;
		return b.q.transform(new Vector3d(0.0, end, 0.0)).add(b.x);
	}

	/** The torso's "up" (from the hips to the neck), world, unit. */
	public Vector3d up() {
		return bodies[TORSO].q.transform(new Vector3d(0.0, -1.0, 0.0));
	}

	/** The way the body faces, world, unit. */
	public Vector3d facing() {
		return bodies[TORSO].q.transform(new Vector3d(0.0, 0.0, -1.0));
	}

	/** Whether the part is still on the body and its muscles can move it at all. */
	public boolean usable(int part) {
		return present[part] && muscles.limb[part] > 0.0;
	}

	public boolean asleep() {
		return asleep;
	}

	/** The seconds since death. */
	public double seconds() {
		return age;
	}

	public boolean broken() {
		return sim.broken();
	}

	/** A part's orientation as the body was built (the pose it died in), world. */
	public Quaterniond startOrientation(int part) {
		return new Quaterniond(startQ[part]);
	}

	public double age() {
		return age;
	}

	// --- Reading it back ----------------------------------------------------------

	/** A part's orientation between the last two ticks. */
	public Quaterniond orientation(int part, float partialTicks, Quaterniond out) {
		Body b = bodies[part];
		return out.set(b.tickQ).slerp(b.q, partialTicks);
	}

	/** A part's pivot (its joint, or the neck for the torso) in the world, between the last two ticks. */
	public Vector3d pivot(int part, float partialTicks, Vector3d out) {
		Body b = bodies[part];
		Quaterniond q = orientation(part, partialTicks, new Quaterniond());
		Vector3d x = new Vector3d(b.tickX).lerp(b.x, partialTicks);
		return q.transform(new Vector3d(centre[part]).negate().mul(unit), out).add(x);
	}

	/** The torso's centre now. */
	public Vector3d centre() {
		return new Vector3d(bodies[TORSO].x);
	}

	/** Degrees between the torso's long axis and straight up: 0 standing, 90 lying. */
	public double lean() {
		Vector3d up = bodies[TORSO].q.transform(new Vector3d(0.0, -1.0, 0.0));
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, up.y))));
	}

	/** How far the limbs are from the rest pose, radians summed over the joints' swings and twists. */
	public double spread() {
		double sum = 0.0;
		Vector3d swing = new Vector3d();
		double[] twist = new double[1];
		Quaterniond rel = new Quaterniond();
		for (Joint j : joints) {
			if (j.child.gone) {
				continue;
			}
			Joint.decompose(j.relative(rel), swing, twist);
			sum += Math.abs(swing.x) + Math.abs(swing.z) + Math.abs(twist[0]) * 0.5;
		}
		return sum;
	}

	/** For the tests: the joints, in head, right arm, left arm, right leg, left leg order. */
	public Joint[] joints() {
		return joints;
	}

	/** How far apart the two halves of the worst joint are, blocks. */
	public double worstSeparation() {
		double worst = 0.0;
		for (Joint j : joints) {
			if (j.child.gone) {
				continue;
			}
			Vector3d a = j.parent.q.transform(j.anchorParent, new Vector3d()).add(j.parent.x);
			Vector3d b = j.child.q.transform(j.anchorChild, new Vector3d()).add(j.child.x);
			worst = Math.max(worst, a.distance(b));
		}
		return worst;
	}

	/**
	 * Where a point on the standing body (at the moment of death) is on this one:
	 * carried by whichever part it was on, or nearest to.
	 */
	public Vector3d place(Vector3d upright) {
		int best = TORSO;
		double bestOut = Double.POSITIVE_INFINITY;
		Vector3d local = new Vector3d();
		Vector3d bestLocal = new Vector3d();
		for (int i = 0; i < PARTS; i++) {
			if (!present[i]) {
				// A part that is gone is not in the fall; what it carried goes on
				// what is left (a severed head's wound comes out of the neck).
				continue;
			}
			Body b = bodies[i];
			startQ[i].conjugate(new Quaterniond()).transform(new Vector3d(upright).sub(startX[i]), local);
			double ox = Math.max(0.0, Math.abs(local.x) - b.hx);
			double oy = Math.max(0.0, Math.abs(local.y) - b.hy);
			double oz = Math.max(0.0, Math.abs(local.z) - b.hz);
			double out = ox * ox + oy * oy + oz * oz;
			if (out < bestOut) {
				bestOut = out;
				best = i;
				bestLocal.set(local);
			}
		}
		// Onto the part, so a point that belonged to a missing one lands on the
		// nearest face of what is left rather than in the air where it was.
		Body b = bodies[best];
		bestLocal.set(Math.clamp(bestLocal.x, -b.hx, b.hx), Math.clamp(bestLocal.y, -b.hy, b.hy),
				Math.clamp(bestLocal.z, -b.hz, b.hz));
		return b.q.transform(bestLocal, new Vector3d()).add(b.x);
	}
}
