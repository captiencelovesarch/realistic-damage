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
 * <p>None of it is rigid. The hold on the torso is soft, so it leans the way
 * it is falling; the leg stood on brakes the body going over it and gives as
 * it does (which is what slows a stumble down, a step at a time, rather than
 * one step stopping it dead); a foot left behind lifts instead of holding the
 * body back; the arms are loose and come up as it loses its balance; the head
 * lags. And it knows when it has {@linkplain #lost() lost its footing}: a
 * body that is over, down on its hips, in the air or stepping into a hole is
 * let go of, to fall as any body does.
 *
 * <p>Deterministic: no randomness, a fixed order. Plain Java.
 */
public final class Stepper {
	/** 1 holds the body up as well as a living person would; 0 does nothing. */
	public double strength = 1.0;
	/**
	 * How much of its balance is left, 1 all of it: the share of the hold on
	 * its torso that is applied. This is what goes first in a body that is
	 * dying on its feet. With it the torso stays up whatever the legs do, and
	 * every shove is caught; without it the torso pitches the way the body is
	 * going, the legs chase it, and after a few steps it is over.
	 */
	public double balance = 1.0;

	/** The torso held upright, in multiples of the body's weight per radian. */
	public double uprightHold = 1.0;
	/**
	 * How far forward of upright the torso is held, radians: a dying body
	 * hunches over, and one that has caught itself does not stand up straight
	 * again. Zero holds it upright.
	 */
	public double hunch = 0.0;
	/** The torso's facing held, the same units. */
	public double facingHold = 0.5;
	/** Share of the body's weight held up under the torso. */
	public double support = 0.45;
	/** A swinging leg's tone, multiples of its own gravity torque. */
	public double swingTone = 7.0;
	/**
	 * The leg being stood on while the body still moves: it resists the body
	 * going over it, which is what brakes a stumble.
	 */
	public double brakeTone = 2.5;
	/**
	 * Seconds over which a braking leg gives to where it has been pushed. The
	 * give is what makes it a brake and not a spring: nothing is stored to throw
	 * the body back.
	 */
	public double brakeGive = 0.12;
	/** Both legs, standing still: held where they are, not locked. */
	public double standTone = 3.5;
	/**
	 * A planted foot left this far behind its hip, along the way the body is
	 * going, lifts off: a leg can hold a body up but not hold it back.
	 */
	public double toeOff = 0.3;
	/** The arms' tone hanging, and the most it rises to as they come up. */
	public double armTone = 0.45;
	public double armToneUp = 1.4;
	/** How far the arms come up, radians from hanging, with the body at {@link #flailAt} blocks a second. */
	public double armRaise = 1.15;
	public double flailAt = 3.0;
	/** The neck: loose enough that the head lags behind the body. */
	public double neckTone = 1.4;
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
	/**
	 * Each leg's own strength, {@link HumanoidRagdoll#RIGHT_LEG} then the left,
	 * 1 sound: a wounded leg swings and holds with less.
	 */
	public final double[] legStrength = {1.0, 1.0};
	/**
	 * Arms the stepper leaves alone, the right then the left: one that hangs
	 * limp, or is busy with something else (a hand on a wound).
	 */
	public final boolean[] leaveArm = new boolean[2];
	/** Whether the stepper holds the head up; if not, the neck is as strong as the body's own muscles are. */
	public boolean holdHead = true;
	/**
	 * How well a planted foot holds, 1 fast: below it the foot slides the way
	 * the body pushes on it, the faster the less it holds.
	 */
	public double grip = 1.0;
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
	/** A swinging foot this near its spot along the ground may land before its time is up, blocks. */
	private static final double NEAR = 0.1;
	/** A swinging foot this far ahead of the other, blocks, has come through: a stride, landed or not. */
	private static final double THROUGH = 0.25;
	/** The torso further over than this, degrees, for {@link #LOST_FOR} seconds: it is falling, not stumbling. */
	private static final double OVER = 55.0;
	/** The hips lower than this share of a leg above the ground the feet are on: it is down. */
	private static final double HIPS_DOWN = 0.5;
	private static final double LOST_FOR = 0.1;
	/** Seconds with no foot on anything before it is simply falling. */
	private static final double AIR_FOR = 0.35;
	/**
	 * Leaning further than this, degrees, and hardly moving, for
	 * {@link #PROPPED_FOR} seconds: something other than its legs is holding
	 * it up (it has fallen against stairs, a wall, a fence), and it is down.
	 */
	private static final double PROPPED = 35.0;
	private static final double PROPPED_SPEED = 0.35;
	private static final double PROPPED_FOR = 0.3;
	/** A step into a hole is given this much longer than its swing to find ground, as a share of it. */
	private static final double HOLE_PATIENCE = 0.3;

	/**
	 * How far back a leg may go at the hip while the body is on its feet,
	 * radians. A dead hip stops at about 26 degrees, as a living one does, but a
	 * living leg left behind bends its knee and comes up on its toes; a straight
	 * one that stopped there would lever the torso over forwards with every
	 * stride (and a body that died walking has a leg further back than that
	 * already). It goes back to what the joint had once the body is let go of,
	 * over {@link #HIP_BACK_FOR} seconds. Zero leaves the joint as it is.
	 */
	public double hipBack = 0.0;
	/**
	 * A body caught mid-stride with both feet in the air (the walk it died in
	 * swings its legs far wider than they are long, so neither foot of a
	 * walking body is on the ground for most of its stride) is carried, its
	 * whole weight, for this share of the swing that brings its leading foot
	 * down. Left to drop it lands low on a leg stuck out in front, which stops
	 * it dead and sits it down. Zero carries nothing.
	 */
	public double carry = 0.0;
	/**
	 * With no foot down and one on its way, this much of the body's weight is
	 * carried for each unit of its strength (never more than all of it). A
	 * body going faster than its feet can be put down under it is in the air
	 * for a moment of every stride, as anything running is, and at twenty
	 * ticks a second it would otherwise have dropped onto its hips by the time
	 * the foot arrived. It goes with the body's strength, so later strides sag.
	 * Zero carries nothing.
	 */
	public double flight = 0.0;
	/**
	 * How far the hips may come down for a step, blocks. These legs are
	 * straight: one reaches only so far ahead of its hip before the hip has to
	 * come down to it, and a body going fast that lands on a leg stuck out
	 * further than that is stopped dead by it, hips on the floor. Negative for
	 * no limit: a body catching a shove takes as long a step as it needs.
	 */
	public double hipDrop = -1.0;
	/**
	 * A foot is put down only once it is out of the floor it swung through,
	 * and that is judged by the lowest corner of its sole. A leg raked far
	 * forward (the leading foot of a body going down at a run, its hips sunk)
	 * has a corner under the ground for as long as it is raked, however its
	 * sole lies, and judged like that it is never put down at all: the body
	 * rides over a foot it is not standing on. This much of the tilt is
	 * forgiven: 0 none, 1 all of it (the middle of the sole is what counts).
	 */
	public double rakedFoot = 0.0;
	/**
	 * A swinging foot that lifts clear (Arcade): it ignores the floor for this
	 * share of its swing, skimming along the top of it rather than through it,
	 * and comes down only after. These legs are straight, so a foot passing
	 * under its hip is at the floor whatever it does; a scuffing one (zero,
	 * Grounded) brushes and drags there, one that lifts clear passes over.
	 */
	public double liftClear = 0.0;
	/**
	 * Seconds a landed foot is stood on before the next step may begin, for a
	 * body that is only going over (not one with a foot in the air): the beat
	 * on each foot of a deliberate stagger. It shortens the faster the body
	 * goes, and is gone at {@link #quickAt}. Zero steps again at once.
	 */
	public double stanceMin = 0.0;
	/**
	 * Whether a foot left behind still holds on while it is the only one down
	 * (the other in the air). Without it, a slow step lets the body down onto
	 * its hips with nothing under it; with it, the body is held up by the leg
	 * behind it until the other foot lands. Off: it lifts, as {@link #toeOff} says.
	 */
	public boolean holdAlone;
	/** How far a foot that lifts clear may pass into the floor while it ignores it, blocks. */
	private static final double LIFT_SINK = 0.07;
	private static final double HIP_BACK_FOR = 0.35;

	/** How stiffly a foot that holds fast is pinned, blocks per unit of force. */
	private static final double PIN = 1.0 / (HumanoidRagdoll.totalMass() * 32.0 * 2000.0);
	/** A foot with no grip at all is pinned this many times softer, and its spot follows it this fast, a second. */
	private static final double SLIP_SOFT = 400.0;
	private static final double SLIP_RATE = 9.0;

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
	private double swingStart;
	private double swingTime;
	private final Vector3d from = new Vector3d();
	private final Vector3d to = new Vector3d();
	/** Each leg's pose relative to the torso when it last stood: held while standing. */
	private final Quaterniond[] standPose = {new Quaterniond(), new Quaterniond()};
	private final Vector3d facing = new Vector3d();
	/** The way it is to face instead of the way it died facing, once {@link #face} has been told. */
	private final Vector3d faceAs = new Vector3d();
	private boolean faceSet;
	private int steps;
	/** Let go of with a foot swung through ahead of the other and not yet down. */
	private boolean strideCut;
	/** Which of {@link #LEGS} landed last, or -1 before any has. */
	private int landed = -1;
	private double lastLanding = -1.0;
	/** The furthest any planted foot has slipped for want of grip, blocks. */
	private double slipped;
	/** The furthest a pinned foot has slid since it was planted, blocks; for the tests. */
	private double worstDrift;
	/** The step in the air is aimed at a hole. */
	/** How fast the pose its joints rest in goes back to the one it died in, once let go: a time constant, seconds. */
	private static final double SETTLE = 0.8;
	/** The pose it died in, joint by joint. */
	private final Quaterniond[] diedIn = new Quaterniond[HumanoidRagdoll.PARTS];

	/** How many times further back than its joint allows each leg may go at the hip, for now. */
	private final double[] hipWider = {1.0, 1.0};

	private boolean carried;
	private boolean hole;
	/** The height of the ground the foot stood on had when the step in the air began. */
	private double stoodY;
	/** Why the footing was lost, or null while it has not been. */
	private String lost;
	private double overFor;
	private double downFor;
	private double airFor;
	private double proppedFor;
	/** How far up the arms are, 0..1, eased. */
	private double armsUp;
	private double flailPhase;
	private final Quaterniond scratchQ = new Quaterniond();

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
			start(r, world);
		}
		if (faceSet) {
			facing.set(faceAs);
		}
		double s = Math.max(0.0, strength);
		Balance.centreOfMass(r, com);
		Balance.velocity(r, vel);
		toeOff(r);
		ground = groundEstimate(r, world);
		String why = footing(r, world, dt);
		if (why != null) {
			lost = why;
			release(r);
			return;
		}
		double lift = support * s;
		if (carried && (swing < 0 || (t - swingStart) / swingTime >= carry)) {
			carried = false;
		}
		omega = Balance.omega(r.sim.gravity * (1.0 - lift), com.y - ground);
		Balance.capturePoint(com, vel, omega, ground, capture);

		// Carried, its torso is held as well: the leg coming down under it would
		// otherwise throw it back the other way.
		r.holdUpright(uprightHold * s * (carried ? 1.0 : Math.max(0.0, Math.min(1.0, balance))));
		if (hunch != 0.0) {
			// The torso's own axis that is brought to upright leans back by as
			// much (its +Z is its back), which leaves the torso leaning forward.
			r.sim.uprightAxis.set(0.0, -Math.cos(hunch), Math.sin(hunch));
		}
		r.holdFacing(facing, facingHold * s);
		boolean flying = flight > 0.0 && swing >= 0 && !pins[0].active && !pins[1].active;
		r.support(Math.max(lift, carried ? 1.0 : flying ? Math.min(1.0, flight * s) : 0.0));
		arms(r, s, dt);
		double loose = Math.max(0.0, Math.min(1.0, 1.0 - grip));
		for (int k = 0; k < 2; k++) {
			pins[k].compliance = PIN * (1.0 + SLIP_SOFT * loose);
			if (pins[k].active) {
				if (loose > 0.0) {
					// A foot losing its grip: the spot it holds goes after it.
					Vector3d sole = r.tip(LEGS[k]);
					double a = Math.min(1.0, loose * SLIP_RATE * dt);
					double dx = (sole.x - pins[k].world.x) * a;
					double dz = (sole.z - pins[k].world.z) * a;
					pins[k].world.x += dx;
					pins[k].world.z += dz;
					slipped += Math.hypot(dx, dz);
				} else {
					worstDrift = Math.max(worstDrift, pins[k].drift());
				}
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
		boolean[] inAir = new boolean[2];
		for (int k = 0; k < 2; k++) {
			if (!pins[k].active && r.present(LEGS[k]) && !down(r, k, world)) {
				dangling = k;
				inAir[k] = true;
			}
		}
		// Nothing planted, going somewhere, and the foot out in front is in the
		// air: caught mid-stride (a body that died walking has its feet where
		// the stride left them).
		int leading = behind(r, 0) <= behind(r, 1) ? 0 : 1;
		boolean midStride = !pins[0].active && !pins[1].active && r.present(LEGS[0]) && r.present(LEGS[1])
				&& inAir[leading] && speed() > SETTLED;
		if (dangling >= 0 && pins[1 - dangling].active) {
			// A foot in the air is no support: it comes down first.
			beginStep(r, world, t, s, dangling, false, false);
		} else if (midStride) {
			// The foot in front comes down first, under the body and not
			// wherever the stride had it.
			beginStep(r, world, t, s, leading, false, true);
		} else if (outside > 0.0 && t - lastLanding >= stanceMin * Math.max(0.0, 1.0 - speed() / quickAt)) {
			beginStep(r, world, t, s, choose(r, a, b), false, false);
		} else if (speed() < SETTLED && t - lastLanding > 0.3 && awkward(r, a, b)) {
			// Caught, but standing anyhow: square up, bringing the foot that
			// is furthest out back in beside the other.
			beginStep(r, world, t, s, furthestOut(a, b), true, false);
		}
		if (swing >= 0) {
			swinging(r, world, t, dt, s);
			return;
		}
		// Standing: both feet down. While the body is still moving over them
		// the legs brake it and give as they do (a landing absorbed, not a
		// rigid stop that bounces it back); once it is slow, they hold the pose
		// they have.
		standing = outside <= 0.0;
		boolean moving = speed() > SETTLED;
		for (int k = 0; k < 2; k++) {
			// A foot that peeled off and was not needed for a step: down again
			// where it is, once the body has slowed or if it is not being left
			// behind.
			if (!pins[k].active && r.present(LEGS[k]) && down(r, k, world)
					&& (!moving || behind(r, k) < toeOff * 0.5)) {
				pins[k].plant(r.tipLocal(LEGS[k], new Vector3d()));
				hold(r, k);
			}
		}
		for (int k = 0; k < 2; k++) {
			if (!r.present(LEGS[k])) {
				continue;
			}
			if (moving) {
				give(r, k, dt);
			}
			r.target(LEGS[k], standPose[k]);
			r.setTone(LEGS[k], (moving ? brakeTone : standTone) * s * legStrength[k]);
		}
	}

	/**
	 * How far a foot is behind its hip along the way the body is going, blocks;
	 * negative for a foot out ahead.
	 */
	private double behind(HumanoidRagdoll r, int k) {
		double v = speed();
		if (v < 1.0e-6) {
			return 0.0;
		}
		Vector3d hip = r.joint(LEGS[k], tmp2);
		Vector3d at = foot(r, k, tmp);
		return ((hip.x - at.x) * vel.x + (hip.z - at.z) * vel.z) / v;
	}

	/** A planted foot the body has left behind lifts. */
	private void toeOff(HumanoidRagdoll r) {
		if (!started || speed() < SETTLED) {
			return;
		}
		for (int k = 0; k < 2; k++) {
			if (pins[k].active && behind(r, k) > toeOff && !(holdAlone && !pins[1 - k].active)) {
				pins[k].active = false;
			}
		}
	}

	/** A leg's pose as it is now is the pose it holds. */
	private void hold(HumanoidRagdoll r, int k) {
		Joint j = r.jointOf(LEGS[k]);
		if (j != null) {
			j.relative(standPose[k]);
		}
	}

	/** A braking leg gives: the pose it holds creeps toward where the body has pushed it. */
	private void give(HumanoidRagdoll r, int k, double dt) {
		Joint j = r.jointOf(LEGS[k]);
		if (j != null) {
			standPose[k].slerp(j.relative(scratchQ), 1.0 - Math.exp(-dt / Math.max(1.0e-3, brakeGive)));
		}
	}

	/**
	 * Whether the body still has its feet under it; if not, why not. A body
	 * that has lost its footing is not held: nothing looks worse than one kept
	 * upright with nothing to stand on.
	 */
	private String footing(HumanoidRagdoll r, PhysicsWorld world, double dt) {
		overFor = r.lean() > OVER ? overFor + dt : 0.0;
		if (overFor >= LOST_FOR) {
			return "over";
		}
		boolean planted = false;
		boolean onGround = false;
		double hips = Double.POSITIVE_INFINITY;
		double leg = 0.75;
		for (int k = 0; k < 2; k++) {
			if (!r.present(LEGS[k])) {
				continue;
			}
			planted |= pins[k].active;
			onGround |= pins[k].active || touching(r, LEGS[k], world);
			hips = Math.min(hips, r.joint(LEGS[k], tmp).y);
			leg = r.length(LEGS[k]);
		}
		// The hips' height over what the feet are on. With nothing planted there
		// is nothing to measure it from: that is the next rule's business.
		downFor = planted && hips - ground < HIPS_DOWN * leg ? downFor + dt : 0.0;
		if (downFor >= LOST_FOR) {
			return "down";
		}
		airFor = onGround ? 0.0 : airFor + dt;
		if (airFor >= AIR_FOR) {
			return "in the air";
		}
		// A lean like this cannot be stood in: a body in it is either going
		// over, and moving, or lying against something.
		proppedFor = r.lean() > PROPPED && vel.length() < PROPPED_SPEED ? proppedFor + dt : 0.0;
		if (proppedFor >= PROPPED_FOR) {
			return "against something";
		}
		return null;
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

	private void start(HumanoidRagdoll r, PhysicsWorld world) {
		started = true;
		for (int part = 0; part < HumanoidRagdoll.PARTS; part++) {
			if (r.jointOf(part) != null) {
				diedIn[part] = new Quaterniond(r.restTarget(part));
			}
		}
		Vector3d f = r.facing();
		facing.set(f.x, 0.0, f.z);
		if (facing.lengthSquared() < 1.0e-6) {
			facing.set(0.0, 0.0, -1.0);
		}
		facing.normalize();
		for (int k = 0; k < 2; k++) {
			Body leg = r.body(LEGS[k]);
			pins[k] = new Pin(leg);
			pins[k].compliance = PIN;
			pins[k].active = false;
			r.sim.pins.add(pins[k]);
			// Only a foot that is down: a body that died mid-stride has one in
			// the air, or both, and a foot pinned in the air holds it there.
			if (r.present(LEGS[k]) && down(r, k, world)) {
				pins[k].plant(r.tipLocal(LEGS[k], new Vector3d()));
			}
			Joint hip = r.jointOf(LEGS[k]);
			if (hip != null && hip.swingXMax > 0.0 && hip.swingXMax < hipBack) {
				hipWider[k] = hipBack / hip.swingXMax;
				hip.swingXMax *= hipWider[k];
			}
		}
		capturePose(r);
	}

	/** Lets go: no more steps, nothing pinned. */
	public void release(HumanoidRagdoll r) {
		if (released) {
			return;
		}
		released = true;
		if (started && swing >= 0 && speed() > SETTLED) {
			// The foot in the air, against the other, along the way it is going.
			Vector3d ahead = foot(r, swing, tmp);
			Vector3d other = foot(r, 1 - swing, tmp2);
			strideCut = ((ahead.x - other.x) * vel.x + (ahead.z - other.z) * vel.z) / speed() > THROUGH;
		}
		for (Pin p : pins) {
			if (p != null) {
				p.active = false;
			}
		}
		for (int leg : LEGS) {
			r.body(leg).sink = 0.0;
		}
		// What its muscles let go from is the pose it was caught in, not the
		// one it died in: a body dropped mid-stride does not first bring its
		// feet together.
		for (int part = 0; part < HumanoidRagdoll.PARTS; part++) {
			Joint j = r.jointOf(part);
			if (j != null) {
				j.relative(r.restTarget(part));
			}
		}
	}

	/**
	 * Once a tick after {@link #release}. The pose it was caught in is what
	 * its muscles let go from, but not what its dead joints rest in: left at
	 * that, a body that went over forwards, bent at the hips with its arms up,
	 * stays propped on its hands and toes like a table. So what its joints
	 * rest in goes back to the pose it died in, slower than its muscles go,
	 * and nothing pulls it straight while there is still strength to do it with.
	 */
	public void settle(HumanoidRagdoll r, double dt) {
		if (!released) {
			return;
		}
		double a = 1.0 - Math.exp(-dt / SETTLE);
		for (int part = 0; part < HumanoidRagdoll.PARTS; part++) {
			if (diedIn[part] != null) {
				r.restTarget(part).slerp(diedIn[part], a);
			}
		}
		// Its hips stop where a body's do again. As a factor, so whatever else
		// has widened or narrowed the joint since (its muscles letting go) stays.
		for (int k = 0; k < 2; k++) {
			Joint hip = r.jointOf(LEGS[k]);
			if (hip != null && hipWider[k] > 1.0) {
				double next = Math.max(1.0, hipWider[k] - 3.0 * dt / HIP_BACK_FOR);
				hip.swingXMax *= next / hipWider[k];
				hipWider[k] = next;
			}
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

	/**
	 * From now on the body faces this way along the ground (a turn toward
	 * where the shot came from), in place of the way it faced when it died:
	 * its torso is turned to it, and its steps are taken in its frame.
	 */
	public void face(Vector3d dir) {
		faceAs.set(dir.x, 0.0, dir.z);
		faceSet = faceAs.lengthSquared() > 1.0e-9;
		if (faceSet) {
			faceAs.normalize();
		}
	}

	/** The way along the ground its steps are taken in, world, unit (valid once it has {@linkplain #started}). */
	public Vector3d facingNow() {
		return new Vector3d(facing);
	}

	/** Whether it has had its first tick. */
	public boolean started() {
		return started;
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

	private void beginStep(HumanoidRagdoll r, PhysicsWorld world, double t, double s, int k, boolean settle, boolean fromAir) {
		int stance = 1 - k;
		if (!r.present(LEGS[k]) || !fromAir && !pins[stance].active && r.present(LEGS[stance])) {
			// One leg, or nothing to stand on while it swings: no step to take.
			return;
		}
		swing = k;
		pins[k].active = false;
		from.set(r.tip(LEGS[k]));
		// Where the standing foot was pinned, not where its sole is: one that
		// has peeled off since is up on its toes. With neither down, the ground
		// under the body.
		stoodY = fromAir ? ground : r.present(LEGS[stance]) ? pins[stance].world.y : from.y;
		// The faster it is going, the quicker the steps: a slow step taken at a
		// run lets the body carry on over the foot it stands on until there is
		// no height left in that leg.
		double hurry = Math.min(1.0, speed() / quickAt);
		swingTime = settle ? swingMax * 1.3 : swingMax - (swingMax - swingMin) * hurry;
		swingStart = t;
		settling = settle;
		carried = fromAir && carry > 0.0 && steps == 0;
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
		double forward = reachForward;
		if (carried || hipDrop >= 0.0) {
			// No further ahead than the leg reaches the ground from where the hip is.
			double h = Math.max(0.0, r.joint(LEGS[k], tmp).y - stoodY - Math.max(0.0, hipDrop));
			double length = r.length(LEGS[k]);
			forward = Math.min(forward, Math.sqrt(Math.max(0.0, length * length - h * h)));
		}
		double fwd = Math.max(-reachBack, Math.min(forward, rel.dot(facing)));
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
		double footY = stoodY;
		hole = false;
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
				hole = true;
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
		double floor = Math.min(from.y, to.y) - (liftClear > 0.0 ? 0.0 : 0.04);
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
		r.setTone(leg, swingTone * s * legStrength[k]);
		// Brushing through the top of the floor for most of the swing, the
		// floor coming back up under it over the last of it, a centimetre or so
		// a tick, so the foot is pushed out gently rather than thrown.
		Vector3d soleNow = r.tip(leg);
		double under = groundAt(world, soleNow.x, soleNow.z, soleNow.y + 0.1, r.body(leg).hx * 0.6);
		double buried = Double.isNaN(under) ? 0.0 : under - lowestSole(r.body(leg));
		// Lifting clear, it passes over the floor for longer, and deeper.
		double ghostUntil = liftClear > 0.0 ? Math.max(GHOST_UNTIL, liftClear) : GHOST_UNTIL;
		double sinkFrom = liftClear > 0.0 ? ghostUntil - (GHOST_UNTIL - SINK_FROM) : SINK_FROM;
		double ease = Math.max(0.0, Math.min(1.0, (done - sinkFrom) / (ghostUntil - sinkFrom)));
		r.body(leg).sink = (liftClear > 0.0 ? LIFT_SINK : SINK) * (1.0 - ease);
		// A swinging foot has no grip: brushing the floor it slides on, where
		// one that held would brace the body against the foot still planted.
		r.body(leg).friction = 0.05;
		if (r.present(LEGS[stance])) {
			// The leg stood on brakes the body going over it, giving as it does.
			give(r, stance, dt);
			r.target(LEGS[stance], standPose[stance]);
			r.setTone(LEGS[stance], brakeTone * s * legStrength[stance]);
		}
		// Ground lower than a straight leg can be put down on while the other
		// still stands on the higher (a stair, a slab, a block's edge): an edge.
		boolean edge = !Double.isNaN(under) && under < stoodY - RISER;
		// Down the moment it touches, once it has stopped passing through the
		// floor and is about where it was going: a foot put down halfway there
		// is a foot put down behind the body.
		boolean there = done >= 1.0 || Math.hypot(soleNow.x - to.x, soleNow.z - to.z) < NEAR;
		// How far into the floor it still is. Raked, its lowest corner is under
		// the ground for as long as it is raked, with its sole on it.
		double deep = buried - rakedFoot * Math.max(0.0, soleNow.y - lowestSole(r.body(leg)));
		if (done >= ghostUntil && there && deep <= SINK && touching(r, leg, world)) {
			land(r, k, t);
		} else if ((hole || edge || Double.isNaN(under)) && done > 1.0 + HOLE_PATIENCE) {
			// Stepped into a hole or off an edge, or came down over one, and
			// found nothing under the foot: it goes after its foot. (Stepping
			// down half a block takes a knee, and these legs have none; what a
			// stumbling body does at the top of a stair is fall down it.)
			lost = edge ? "a step off an edge" : "a step into nothing";
			release(r);
		} else if (done > 2.5) {
			// Never came down (the body is in the air): stop stepping with it
			// and choose again.
			swing = -1;
			r.body(leg).sink = 0.0;
		}
	}

	/**
	 * Whether a foot is down on something it can stand on. One nearly level
	 * with its own hip is not: a leg stuck out that far (the stride of a body
	 * that died walking, its foot on the stair ahead or against its riser)
	 * holds nothing up, and pinned there it would stop the body dead.
	 */
	private boolean down(HumanoidRagdoll r, int k, PhysicsWorld world) {
		return touching(r, LEGS[k], world)
				&& r.joint(LEGS[k], tmp2).y - r.tip(LEGS[k]).y >= HIPS_DOWN * r.length(LEGS[k]);
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
		if (speed() > SETTLED) {
			// Still going: the foot behind peels off the ground. Two straight
			// legs with both feet held make a rigid triangle, and a body caught
			// in one stops dead and bounces back; a real trailing foot rolls up
			// onto its toes and lets the body carry on over the new one.
			pins[1 - k].active = false;
		}
		swing = -1;
		carried = false;
		lastSwing = settling ? -1 : k;
		settling = false;
		hole = false;
		steps++;
		landed = k;
		lastLanding = t;
		hold(r, k);
	}

	/**
	 * Loose arms and a loose neck. Hanging, the arms swing with whatever the
	 * body does; as it loses its balance they come up and out, away from the
	 * way it is falling, one after the other (what anyone's arms do), and drop
	 * again once it has caught itself.
	 */
	private void arms(HumanoidRagdoll r, double s, double dt) {
		if (holdHead) {
			r.setTone(HumanoidRagdoll.HEAD, neckTone * s);
		}
		double want = Math.max(0.0, Math.min(1.0, (speed() - SETTLED) / Math.max(0.1, flailAt - SETTLED)));
		// Up fast, down slowly.
		armsUp += (want - armsUp) * Math.min(1.0, dt * (want > armsUp ? 14.0 : 3.0));
		flailPhase += dt * 13.0 * armsUp;
		Vector3d right = right(new Vector3d());
		double v = speed();
		for (int a = 0; a < 2; a++) {
			int arm = a == 0 ? HumanoidRagdoll.RIGHT_ARM : HumanoidRagdoll.LEFT_ARM;
			if (!r.present(arm) || leaveArm[a]) {
				continue;
			}
			double side = a == 0 ? 1.0 : -1.0;
			double raise = 0.12 + armsUp * (armRaise + 0.4 * Math.sin(flailPhase + a * Math.PI));
			// Out to its own side, and against the fall.
			Vector3d out = tmp.set(right).mul(side * 0.8);
			if (v > 1.0e-6) {
				out.fma(-0.6 * armsUp / v, tmp2.set(vel.x, 0.0, vel.z));
			}
			out.normalize();
			Vector3d dir = new Vector3d(0.0, -Math.cos(raise), 0.0).fma(Math.sin(raise), out);
			r.aimWorld(arm, dir);
			r.setTone(arm, (armTone + (armToneUp - armTone) * armsUp) * s);
		}
	}

	// --- Reading it back -----------------------------------------------------------

	/** Steps taken so far: feet put down. */
	public int steps() {
		return steps;
	}

	/**
	 * Strides taken: the steps that landed, and the one it was let go of in
	 * the middle of, if that foot had already come through ahead of the other.
	 * A body that goes over as its second foot comes down has taken two
	 * strides and landed one step.
	 */
	public int strides() {
		return steps + (strideCut ? 1 : 0);
	}

	/** Which leg is in the air, 0 the right and 1 the left, or -1 with neither. */
	public int swingLeg() {
		return swing;
	}

	/** Which leg landed last, 0 the right and 1 the left, or -1 before any step. */
	public int landedLeg() {
		return landed;
	}

	/** Whether a leg's foot is planted, 0 the right and 1 the left. */
	public boolean planted(int k) {
		return started && pins[k].active;
	}

	/** Where a leg's foot is planted (valid while {@link #planted}), 0 the right and 1 the left. */
	public Vector3d plantedAt(int k) {
		return new Vector3d(pins[k].world);
	}

	/** How far its planted feet have slipped for want of grip, blocks, all told. */
	public double slipped() {
		return slipped;
	}

	/** The body's right along the ground, as it faced when it died (or as it was {@linkplain #face turned}). */
	public Vector3d rightward() {
		return right(new Vector3d());
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

	/**
	 * Why the body lost its footing and was let go of ("over", "down", "in the
	 * air", "against something", "a step into nothing", "a step off an edge"),
	 * or null if it has not.
	 */
	public String lost() {
		return lost;
	}

	/** How far up the arms are, 0 hanging to 1 right up. */
	public double armsUp() {
		return armsUp;
	}
}
