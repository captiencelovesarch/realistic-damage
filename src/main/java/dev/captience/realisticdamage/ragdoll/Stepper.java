package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.ik.Reach;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import dev.captience.realisticdamage.ragdoll.physics.BoxList;
import dev.captience.realisticdamage.ragdoll.physics.Joint;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import dev.captience.realisticdamage.ragdoll.physics.Pin;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Takes steps (stumbling deaths, IK phase 0): keeps a body that is being
 * held on its feet from falling over by putting a foot down where it is
 * falling, the way a person shoved does.
 *
 * <p>Every tick:
 * <ol>
 * <li>Where is it falling? The {@link Balance capture point}: the spot it
 *     would have to stand on to stop.
 * <li>If that is outside the feet, one foot steps toward where the capture
 *     point will be by the time it lands (it runs away from the foot the body
 *     stands on while the other is in the air). Which foot: the one on the
 *     side it is falling toward when it falls sideways (stepping across the
 *     other leg is a trip), otherwise the one further behind.
 * <li>The step's spot is moved in to what the leg can reach, the ground under
 *     it is found (a step up a stair, down off a slab), and the leg swings
 *     there along an arc, aimed by {@link Reach} so its foot comes down on
 *     the spot. A straight leg passing under the hips would scuff the floor,
 *     so a swinging leg passes through the ground until it is most of the
 *     way there.
 * <li>When the foot lands it is {@linkplain Pin pinned}: it stays exactly
 *     where it came down until it lifts again, and the body vaults over it.
 *     No skating.
 * <li>Both feet down and the capture point between them: it stands, the legs
 *     held as they are.
 * </ol>
 *
 * <p>The torso is held upright and facing the way it faced, and part of the
 * body's weight is held up for it: a six-box body has no ankles or knees to
 * balance with, and in Minecraft's gravity (over three times ours for a body
 * this size) a pendulum this short falls faster than a straight leg can
 * swing. How hard these hold is what makes a stumble strong or weak; they
 * all scale with {@link #strength}.
 *
 * <p>Deterministic: no randomness, a fixed order. Plain Java.
 */
public final class Stepper {
	/** 1 holds the body up as well as a living person would; 0 does nothing. */
	public double strength = 1.0;

	/** The torso held upright, in multiples of the body's weight per radian. */
	public double uprightHold = 3.0;
	/** The torso's facing held, the same units. */
	public double facingHold = 0.6;
	/** Share of the body's weight held up under the torso. */
	public double support = 0.45;
	/** A swinging leg's tone, multiples of its own gravity torque. */
	public double swingTone = 12.0;
	/** The leg being stood on, while the other swings: loose, so the body vaults over it. */
	public double stanceTone = 0.3;
	/** Both legs, standing: held where they are. */
	public double standTone = 9.0;
	/** Both legs, standing while the body still moves over them: they give. */
	public double giveTone = 2.5;
	/** Whether a body that has stopped with its feet anyhow squares up with one more step. */
	public boolean squareUp = true;
	/** The quickest and slowest a step swings, seconds. */
	public double swingMin = 0.16;
	public double swingMax = 0.34;
	/** At this speed along the ground, blocks a second, and faster, steps are as quick as they get. */
	public double quickAt = 2.5;
	/** How far a foot reaches from under its hip, blocks: forward, back, out to the side. */
	public double reachForward = 0.5;
	public double reachBack = 0.27;
	public double reachOut = 0.34;
	/** The narrowest the feet stand side to side, centre to centre, blocks. */
	public double minGap = 0.17;
	/** How far out a swinging foot circles to clear the floor, blocks. */
	public double circle = 0.04;
	/** How far past the capture point a step lands, blocks: past it stops the body, short of it carries on. */
	public double overshoot = 0.0;
	/** How much of the body's travel while the foot is in the air the step leads by. */
	public double lead = 0.35;
	/** How much of a swing brushes through the floor; after this it lands as soon as it touches. */
	private static final double GHOST_UNTIL = 0.6;
	/** From here to {@link #GHOST_UNTIL} the floor comes back up under the swinging foot. */
	private static final double SINK_FROM = 0.35;
	/** How far a swinging foot may brush into the floor, blocks: three centimetres. */
	private static final double SINK = 0.03;
	/** A foot is down when its sole is this close to the ground. */
	private static final double TOUCH = 0.035;
	/** Ground higher than this above the foot stood on is not stepped onto, blocks. */
	private static final double RISER = 0.2;
	/** How high a step can go up, and down, blocks. */
	private static final double STEP_UP = 0.55;
	private static final double STEP_DOWN = 1.1;

	private static final int[] LEGS = {HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG};

	private final Pin[] pins = new Pin[2];
	private boolean started;
	private boolean released;
	/** Which of {@link #LEGS} is in the air, or -1 with both down. */
	private int swing = -1;
	/** Which stepped last, or -1 before the first step (and after squaring up). */
	private int lastSwing = -1;
	/** The step in the air is squaring up, not catching a fall. */
	private boolean settling;
	/** Stepping down off something: the foot stood on has slid off its edge and the body drops. */
	private boolean dropping;
	private double swingStart;
	private double swingTime;
	private final Vector3d from = new Vector3d();
	private final Vector3d to = new Vector3d();
	/** Each leg's pose relative to the torso when it last stood: held while standing. */
	private final Quaterniond[] standPose = {new Quaterniond(), new Quaterniond()};
	private final Vector3d facing = new Vector3d();
	private int steps;
	private double lastLanding = -1.0;
	/** The furthest a pinned foot has slid since it was planted, blocks; for the tests. */
	private double worstDrift;

	// Scratch.
	private final Vector3d com = new Vector3d();
	private final Vector3d vel = new Vector3d();
	private final Vector3d capture = new Vector3d();
	private final Vector3d tmp = new Vector3d();
	private final Vector3d tmp2 = new Vector3d();
	private final BoxList near = new BoxList();
	private final double[] box = new double[6];
	private double ground;
	private double omega;
	private boolean standing;

	/** Once a tick, before the physics step. */
	public void apply(HumanoidRagdoll r, PhysicsWorld world, double t, double dt) {
		if (released) {
			return;
		}
		if (!started) {
			start(r);
		}
		double s = Math.max(0.0, strength);
		Balance.centreOfMass(r, com);
		Balance.velocity(r, vel);
		ground = groundEstimate(r, world);
		double lift = support * s * (dropping ? 0.3 : 1.0);
		omega = Balance.omega(r.sim.gravity * (1.0 - lift), com.y - ground);
		Balance.capturePoint(com, vel, omega, ground, capture);

		r.holdUpright(uprightHold * s);
		r.holdFacing(facing, facingHold * s);
		r.support(lift);
		arms(r, s);
		for (int k = 0; k < 2; k++) {
			if (pins[k].active) {
				worstDrift = Math.max(worstDrift, pins[k].drift());
			}
		}

		standing = false;
		if (swing >= 0) {
			swinging(r, world, t, dt, s);
			return;
		}
		Vector3d a = foot(r, 0, new Vector3d());
		Vector3d b = foot(r, 1, new Vector3d());
		// A body nearly at rest sways a little over its feet and does not step
		// for it: the sway is caught by the legs it stands on (what ankles do
		// for us), not by fidgeting.
		double sway = speed() < SWAY_SPEED ? SWAY : 0.0;
		double outside = Balance.outside(capture, a, b, r.body(LEGS[0]).hx * 0.7 + sway);
		int dangling = -1;
		for (int k = 0; k < 2; k++) {
			if (!pins[k].active && r.present(LEGS[k]) && !touching(r, LEGS[k], world)) {
				dangling = k;
			}
		}
		if (dangling >= 0 && pins[1 - dangling].active) {
			// A foot in the air is no support: it comes down first.
			beginStep(r, world, t, s, dangling, false);
		} else if (outside > 0.0) {
			beginStep(r, world, t, s, choose(r, a, b), false);
		} else if (speed() < SETTLED && t - lastLanding > 0.3 && awkward(r, a, b)) {
			// Caught, but standing anyhow: square up, bringing the foot that
			// is furthest out back in beside the other.
			beginStep(r, world, t, s, furthestOut(a, b), true);
		}
		if (swing >= 0) {
			swinging(r, world, t, dt, s);
			return;
		}
		// Standing: both feet down. While the body is still moving over them
		// the legs give with it (a landing absorbed, not a rigid stop that
		// bounces it back); once it is slow, they hold the pose they have.
		standing = outside <= 0.0;
		boolean moving = speed() > SETTLED;
		if (moving) {
			capturePose(r);
		}
		for (int k = 0; k < 2; k++) {
			// A foot that peeled off and was not needed for a step: down again
			// once the body has slowed, where it is.
			if (!moving && !pins[k].active && r.present(LEGS[k]) && touching(r, LEGS[k], world)) {
				pins[k].plant(r.tipLocal(LEGS[k], new Vector3d()));
			}
		}
		for (int k = 0; k < 2; k++) {
			if (!r.present(LEGS[k])) {
				continue;
			}
			r.target(LEGS[k], standPose[k]);
			r.setTone(LEGS[k], (moving ? giveTone : standTone) * s);
		}
	}

	/** Slower than this, blocks a second, and a capture point this far outside the feet is only a sway. */
	private static final double SWAY_SPEED = 0.6;
	private static final double SWAY = 0.07;
	/** Slower than this along the ground, blocks a second, and the body has stopped. */
	private static final double SETTLED = 0.3;

	/** Feet too far apart to stand like that for long: wider than this side to side, or this front to back. */
	private boolean awkward(HumanoidRagdoll r, Vector3d a, Vector3d b) {
		Vector3d d = new Vector3d(b).sub(a);
		double lateral = Math.abs(d.dot(right(new Vector3d())));
		double along = Math.abs(d.dot(facing));
		return squareUp && (lateral > 0.42 || along > 0.42);
	}

	/** The foot further from under the body. */
	private int furthestOut(Vector3d a, Vector3d b) {
		double da = com.distanceSquared(a.x, com.y, a.z);
		double db = com.distanceSquared(b.x, com.y, b.z);
		return da >= db ? 0 : 1;
	}

	private void start(HumanoidRagdoll r) {
		started = true;
		Vector3d f = r.facing();
		facing.set(f.x, 0.0, f.z);
		if (facing.lengthSquared() < 1.0e-6) {
			facing.set(0.0, 0.0, -1.0);
		}
		facing.normalize();
		for (int k = 0; k < 2; k++) {
			Body leg = r.body(LEGS[k]);
			pins[k] = new Pin(leg);
			pins[k].compliance = 1.0 / (HumanoidRagdoll.totalMass() * 32.0 * 2000.0);
			r.sim.pins.add(pins[k]);
			if (r.present(LEGS[k])) {
				pins[k].plant(r.tipLocal(LEGS[k], new Vector3d()));
			}
		}
		capturePose(r);
	}

	/** Lets go: no more steps, nothing pinned. */
	public void release(HumanoidRagdoll r) {
		released = true;
		for (Pin p : pins) {
			if (p != null) {
				p.active = false;
			}
		}
		for (int leg : LEGS) {
			r.body(leg).sink = 0.0;
		}
	}

	private void capturePose(HumanoidRagdoll r) {
		for (int k = 0; k < 2; k++) {
			Joint j = r.jointOf(LEGS[k]);
			if (j != null) {
				j.relative(standPose[k]);
			}
		}
	}

	/** Where a foot is: its pinned spot when planted, else its sole. */
	private Vector3d foot(HumanoidRagdoll r, int k, Vector3d out) {
		if (pins[k].active) {
			return out.set(pins[k].world);
		}
		return out.set(r.tip(LEGS[k]));
	}

	/** The ground the body stands on: its planted feet, else what is under it. */
	private double groundEstimate(HumanoidRagdoll r, PhysicsWorld world) {
		double sum = 0.0;
		int n = 0;
		for (int k = 0; k < 2; k++) {
			if (pins[k].active) {
				sum += pins[k].world.y;
				n++;
			}
		}
		if (n > 0) {
			return sum / n;
		}
		double y = groundAt(world, com.x, com.z, Math.min(r.tip(LEGS[0]).y, r.tip(LEGS[1]).y) + 0.1, 0.05);
		return Double.isNaN(y) ? com.y - 0.9 : y;
	}

	/**
	 * The top of the highest block under a spot that a foot could stand on,
	 * no higher than {@code fromY + STEP_UP} and no lower than {@code fromY -
	 * STEP_DOWN}; NaN when there is none (a hole).
	 */
	double groundAt(PhysicsWorld world, double x, double z, double fromY, double halfFoot) {
		near.clear();
		world.collect(x - halfFoot, fromY - STEP_DOWN, z - halfFoot, x + halfFoot, fromY + STEP_UP, z + halfFoot, near);
		double best = Double.NaN;
		for (int i = 0; i < near.size(); i++) {
			near.get(i, box);
			double top = box[4];
			if (top > fromY + STEP_UP || top < fromY - STEP_DOWN) {
				continue;
			}
			if (Double.isNaN(best) || top > best) {
				best = top;
			}
		}
		return best;
	}

	/** Whether a foot-sized column at a spot is solid above the ground there (a wall). */
	private boolean blocked(PhysicsWorld world, double x, double z, double groundY, double halfFoot) {
		near.clear();
		world.collect(x - halfFoot, groundY + 0.02, z - halfFoot, x + halfFoot, groundY + 0.6, z + halfFoot, near);
		for (int i = 0; i < near.size(); i++) {
			near.get(i, box);
			if (box[4] > groundY + STEP_UP) {
				return true;
			}
		}
		return false;
	}

	/** The body's right along the ground. */
	private Vector3d right(Vector3d out) {
		return out.set(facing).cross(0.0, 1.0, 0.0).normalize();
	}

	/** +1 for a leg on the body's right, -1 on its left. */
	private double side(HumanoidRagdoll r, int k) {
		Vector3d hip = r.joint(LEGS[k], new Vector3d()).sub(r.body(HumanoidRagdoll.TORSO).x);
		return hip.dot(right(new Vector3d())) >= 0.0 ? 1.0 : -1.0;
	}

	/**
	 * Which leg steps. After the first step, the other one: a stumble
	 * alternates its feet like any walk, and the same foot stepping out again
	 * and again only walks the body sideways. The first: toward a sideways
	 * fall, the leg on that side; otherwise the one further from where it is
	 * going.
	 */
	private int choose(HumanoidRagdoll r, Vector3d a, Vector3d b) {
		if (!r.present(LEGS[0])) {
			return 1;
		}
		if (!r.present(LEGS[1])) {
			return 0;
		}
		if (lastSwing >= 0) {
			return 1 - lastSwing;
		}
		Vector3d mid = new Vector3d(a).add(b).mul(0.5);
		Vector3d toward = new Vector3d(capture).sub(mid);
		toward.y = 0.0;
		double lateral = toward.dot(right(new Vector3d()));
		double along = toward.dot(facing);
		if (Math.abs(lateral) > Math.abs(along) * 0.8) {
			return (lateral > 0.0) == (side(r, 0) > 0.0) ? 0 : 1;
		}
		double da = capture.distanceSquared(a.x, capture.y, a.z);
		double db = capture.distanceSquared(b.x, capture.y, b.z);
		return da >= db ? 0 : 1;
	}

	private void beginStep(HumanoidRagdoll r, PhysicsWorld world, double t, double s, int k, boolean settle) {
		int stance = 1 - k;
		if (!r.present(LEGS[k]) || !pins[stance].active && r.present(LEGS[stance])) {
			// One leg, or nothing to stand on while it swings: no step to take.
			return;
		}
		swing = k;
		pins[k].active = false;
		from.set(r.tip(LEGS[k]));
		// The faster it is going, the quicker the steps: a slow step taken at a
		// run lets the body carry on over the foot it stands on until there is
		// no height left in that leg.
		double hurry = Math.min(1.0, speed() / quickAt);
		swingTime = settle ? swingMax * 1.3 : swingMax - (swingMax - swingMin) * hurry;
		swingStart = t;
		settling = settle;
		plan(r, world, k, swingTime);
	}

	/** Where the swinging foot is to land, {@code remaining} seconds from now. */
	private void plan(HumanoidRagdoll r, PhysicsWorld world, int k, double remaining) {
		int stance = 1 - k;
		Vector3d stanceFoot = foot(r, stance, new Vector3d());
		Vector3d right = right(new Vector3d());
		// Where it is falling, led a little by how fast: the capture point
		// moves on while the foot is in the air. (A free pendulum's would run
		// away exponentially, but this one is held up and braked by the leg it
		// stands on, and steps aimed that far ahead overshoot and throw the
		// body back the other way.) Side to side, where it is now: the foot
		// stood on is a hip's width off the middle, and the other foot landing
		// a hip's width the other way is what catches that.
		Vector3d later = new Vector3d(capture);
		if (settling) {
			// Squaring up: beside the other foot, under the body.
			later.set(com.x, capture.y, com.z);
		} else {
			later.fma(remaining * lead, new Vector3d(vel.x, 0.0, vel.z));
		}
		Vector3d hip = r.joint(LEGS[k], new Vector3d());
		// Where the hip will be: on with the body, roughly.
		hip.fma(remaining, new Vector3d(vel.x, 0.0, vel.z));
		Vector3d target = new Vector3d(later);
		// Feet a hip apart, each under its own side.
		double hipHalf = Math.abs(r.joint(LEGS[k], new Vector3d()).sub(r.body(HumanoidRagdoll.TORSO).x).dot(right));
		target.fma(side(r, k) * hipHalf, right);
		Vector3d going = new Vector3d(later).sub(stanceFoot);
		going.y = 0.0;
		if (going.lengthSquared() > 1.0e-6 && !settling) {
			target.fma(overshoot, going.normalize());
		}
		// Within the leg's reach of where its hip will be, in the body's frame.
		Vector3d rel = new Vector3d(target).sub(hip);
		double fwd = Math.max(-reachBack, Math.min(reachForward, rel.dot(facing)));
		double lat = rel.dot(right) * side(r, k);
		lat = Math.max(-reachOut * 0.4, Math.min(reachOut, lat));
		target.set(hip).fma(fwd, facing).fma(lat * side(r, k), right);
		// Never across the other foot.
		double across = new Vector3d(target).sub(stanceFoot).dot(right) * side(r, k);
		if (across < minGap) {
			target.fma((minGap - across) * side(r, k), right);
		}
		// Short of any wall, and onto whatever ground is there.
		double halfFoot = r.body(LEGS[k]).hx;
		double footY = stanceFoot.y;
		for (int tries = 0; tries < 6; tries++) {
			double y = groundAt(world, target.x, target.z, footY, halfFoot * 0.6);
			// Higher ground than a straight leg can be lifted onto (a stair, a
			// slab) is a wall to a stumbling foot: it would only kick the riser.
			boolean riser = !Double.isNaN(y) && y > footY + RISER;
			if (!Double.isNaN(y) && !riser && !blocked(world, target.x, target.z, y, halfFoot * 0.8)) {
				target.y = y;
				break;
			}
			if (Double.isNaN(y)) {
				// A hole: the foot goes there anyway and finds nothing.
				target.y = footY - STEP_DOWN;
				break;
			}
			// A wall: a shorter step.
			target.lerp(new Vector3d(stanceFoot.x, target.y, stanceFoot.z), 0.3);
			target.y = footY;
		}
		to.set(target);
	}

	private void swinging(HumanoidRagdoll r, PhysicsWorld world, double t, double dt, double s) {
		int k = swing;
		int stance = 1 - k;
		int leg = LEGS[k];
		double done = (t + dt - swingStart) / swingTime;
		if (done < 0.55) {
			// Still early: aim again at where it is falling now.
			plan(r, world, k, Math.max(dt, swingTime * (1.0 - done)));
		}
		double e = Math.min(1.0, done);
		double eased = e * e * (3.0 - 2.0 * e);
		Vector3d spot = new Vector3d(from).lerp(to, eased);
		spot.fma(side(r, k) * circle * Math.sin(Math.PI * e), right(new Vector3d()));
		Vector3d hip = r.joint(leg, new Vector3d());
		double length = r.length(leg);
		Vector3d dir = Reach.legToward(hip, spot, length, length * 0.85, new Vector3d());
		// A leg passing through the floor may only just: a hip that has sunk
		// lower than the leg is long would put the foot deep in the ground, and
		// the ground would throw it out again when the leg stops passing
		// through. Skim it instead.
		double floor = Math.min(from.y, to.y) - 0.04;
		if (hip.y + dir.y * length < floor) {
			Vector3d along = new Vector3d(spot).sub(hip);
			along.y = 0.0;
			if (along.lengthSquared() < 1.0e-8) {
				along.set(to).sub(from);
				along.y = 0.0;
			}
			Reach.skim(hip, along, length, floor, dir);
		}
		r.aimWorld(leg, dir);
		r.setTone(leg, swingTone * s);
		// Brushing through the top of the floor for most of the swing, the
		// floor coming back up under it over the last of it, a centimetre or so
		// a tick, so the foot is pushed out gently rather than thrown.
		Vector3d soleNow = r.tip(leg);
		double under = groundAt(world, soleNow.x, soleNow.z, soleNow.y + 0.1, r.body(leg).hx * 0.6);
		double buried = Double.isNaN(under) ? 0.0 : under - lowestSole(r.body(leg));
		double ease = Math.max(0.0, Math.min(1.0, (done - SINK_FROM) / (GHOST_UNTIL - SINK_FROM)));
		r.body(leg).sink = SINK * (1.0 - ease);
		// A swinging foot has no grip: brushing the floor it slides on, where
		// one that held would brace the body against the foot still planted.
		r.body(leg).friction = 0.05;
		if (r.present(LEGS[stance])) {
			// The leg stood on goes where the body takes it: its muscle only
			// steadies it, pulling toward wherever it already is.
			Joint j = r.jointOf(LEGS[stance]);
			if (j != null) {
				r.target(LEGS[stance], j.relative(new Quaterniond()));
			}
			r.setTone(LEGS[stance], stanceTone * s);
		}
		// A step down off a stair or a slab: a straight leg cannot reach the
		// lower ground while the other stands on the higher, so once the swing
		// is through, the foot stood on slides off the edge and the body drops
		// onto the new one.
		Vector3d stood = foot(r, stance, new Vector3d());
		if (done >= 1.0 && !Double.isNaN(under) && under < stood.y - RISER && pins[stance].active) {
			pins[stance].active = false;
			dropping = true;
		}
		// Down the moment it touches once it has stopped passing through the
		// floor.
		if (done >= GHOST_UNTIL && buried <= SINK && touching(r, leg, world)) {
			land(r, k, t);
		} else if (done > 2.5) {
			// Never came down (stepped into a hole, or the body is in the air):
			// stop stepping with it and choose again.
			swing = -1;
			dropping = false;
			r.body(leg).sink = 0.0;
		}
	}

	/** Whether a leg's sole is on the ground. */
	private boolean touching(HumanoidRagdoll r, int leg, PhysicsWorld world) {
		Body b = r.body(leg);
		Vector3d sole = r.tip(leg);
		double y = groundAt(world, sole.x, sole.z, sole.y + 0.1, b.hx * 0.6);
		return !Double.isNaN(y) && lowestSole(b) <= y + TOUCH;
	}

	/** The lowest corner of a leg's sole, world y. */
	private double lowestSole(Body b) {
		double lowest = Double.POSITIVE_INFINITY;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				b.q.transform(tmp.set(sx * b.hx, b.hy, sz * b.hz)).add(b.x);
				lowest = Math.min(lowest, tmp.y);
			}
		}
		return lowest;
	}

	private void land(HumanoidRagdoll r, int k, double t) {
		int leg = LEGS[k];
		pins[k].plant(r.tipLocal(leg, new Vector3d()));
		r.body(leg).sink = 0.0;
		dropping = false;
		if (speed() > SETTLED) {
			// Still going: the foot behind peels off the ground. Two straight
			// legs with both feet held make a rigid triangle, and a body caught
			// in one stops dead and bounces back; a real trailing foot rolls up
			// onto its toes and lets the body carry on over the new one.
			pins[1 - k].active = false;
		}
		swing = -1;
		lastSwing = settling ? -1 : k;
		settling = false;
		steps++;
		lastLanding = t;
		capturePose(r);
	}

	/** Arms a little out, for balance; the head held steady, as anyone on their feet holds it. */
	private void arms(HumanoidRagdoll r, double s) {
		r.reflex(HumanoidRagdoll.HEAD, 4.0 * s);
		r.target(HumanoidRagdoll.RIGHT_ARM, -0.1, 0.25, 0.0);
		r.target(HumanoidRagdoll.LEFT_ARM, -0.1, -0.25, 0.0);
		r.reflex(HumanoidRagdoll.RIGHT_ARM, 1.6 * s);
		r.reflex(HumanoidRagdoll.LEFT_ARM, 1.6 * s);
	}

	// --- Reading it back -----------------------------------------------------------

	/** Steps taken so far. */
	public int steps() {
		return steps;
	}

	/** Both feet down and the capture point between them, as of the last tick. */
	public boolean standing() {
		return standing && swing < 0;
	}

	/** Whether a leg is in the air. */
	public boolean stepping() {
		return swing >= 0;
	}

	/** The furthest a planted foot has slid from where it landed, blocks. */
	public double worstDrift() {
		return worstDrift;
	}

	/** The capture point as of the last tick. */
	public Vector3d capturePoint() {
		return new Vector3d(capture);
	}

	/** The centre of mass's speed along the ground as of the last tick, blocks a second. */
	public double speed() {
		return Math.hypot(vel.x, vel.z);
	}

	public boolean released() {
		return released;
	}
}
