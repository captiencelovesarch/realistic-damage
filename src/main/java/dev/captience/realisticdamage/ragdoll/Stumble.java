package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.physics.BoxList;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import org.joml.Vector3d;

/**
 * A death on its feet (stumbling deaths, IK phase 1): the body does not know
 * it is dead yet. It keeps its feet under it with the {@link Stepper} for as
 * long as its legs have anything left, weakening all the while, and then
 * goes down.
 *
 * <p>Which deaths: a blow, an arrow, a bleed-out, of a body that still had a
 * mind and two legs. Never one whose strings were cut (a headshot, a crush,
 * an overkill), and never a blast, a fall, water, lightning or cold: those
 * bodies are thrown, or are not there to try. {@link #plan} decides.
 *
 * <p>How long: from its own leg muscles ({@link Muscles}), which already know
 * how hard it was hit, how awake it was and whether adrenaline had hold of
 * it. A {@link StumbleStyle} turns that into seconds, and says how the body
 * weakens over them: its balance first, its strength after, its steps
 * shortening and slowing, its feet losing their grip.
 *
 * <p>A wounded leg (broken, limp, or the one the killing blow struck) is weak
 * from the start and gives the first time it has to carry the body alone;
 * the body goes down to that side.
 *
 * <p>Deterministic: the only thing that varies a death is the body's own
 * seed. Plain Java.
 */
public final class Stumble implements Controller {
	/** Slower than this along the ground, blocks a second, and it is standing still. */
	private static final double STILL_SPEED = 0.15;
	private static final int[] LEGS = {HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG};
	private static final int[] ARMS = {HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.LEFT_ARM};

	public final Stepper stepper = new Stepper();
	public final StumbleStyle style;
	/** Seconds on its feet before its legs give. */
	public final double onFeet;
	/** How strong its legs were when it died, 1 a body at full strength. */
	public final double vigor;
	/** Which leg is wounded, 0 the right and 1 the left, or -1 for neither. */
	public final int weakLeg;
	/** How many times longer than its muscles alone it lasts: what {@link Muscles#stretched} is given. */
	private final double stretch;
	private double aloneFor;
	private boolean letGo;
	private double sinceLetGo;
	private boolean propped;
	private double letGoAt = -1.0;
	private String why;

	private Stumble(StumbleStyle style, double onFeet, double vigor, int weakLeg, double stretch) {
		this.style = style;
		this.onFeet = onFeet;
		this.vigor = vigor;
		this.weakLeg = weakLeg;
		this.stretch = stretch;
		style.applyTo(stepper);
		if (weakLeg >= 0) {
			stepper.legStrength[weakLeg] = style.woundedLeg;
		}
	}

	/**
	 * The stumble a death gets, or null if the body simply falls.
	 *
	 * @param hitPart where the killing blow landed (ragdoll index), or -1
	 * @param muscles the muscles it died with
	 */
	public static Stumble plan(StumbleStyle style, DeathKind kind, int hitPart, Muscles muscles, HumanoidRagdoll r) {
		if (style == null || kind != DeathKind.BLOW && kind != DeathKind.PROJECTILE && kind != DeathKind.FADE) {
			return null;
		}
		if (muscles.stringsCut || muscles.awake < style.minAwake) {
			return null;
		}
		int weak = -1;
		for (int k = 0; k < 2; k++) {
			if (!r.present(LEGS[k])) {
				return null;
			}
			if (muscles.limb[LEGS[k]] <= 0.0 || hitPart == LEGS[k]) {
				if (weak >= 0) {
					// Nothing to stand on.
					return null;
				}
				weak = k;
			}
		}
		double tone = muscles.start[Muscles.LEGS];
		if (tone <= style.doneTone) {
			return null;
		}
		double natural = muscles.delay[Muscles.LEGS] + muscles.fade[Muscles.LEGS] * Math.log(tone / style.doneTone);
		double onFeet = Math.max(style.feetMin, Math.min(style.feetMax, style.feetScale * natural));
		if (weak >= 0) {
			onFeet = Math.max(style.feetMin, onFeet * style.woundedLegFeet);
		}
		double vigor = Math.max(0.0, Math.min(1.0, tone / style.fullTone));
		return new Stumble(style, onFeet, vigor, weak, onFeet / Math.max(1.0e-3, natural));
	}

	/**
	 * Starts a body's death on its feet, if it gets one: its muscles, its
	 * reactions, this controller and what it is launched with. Returns null,
	 * having done nothing, for a death that simply falls.
	 *
	 * @param in     the death, as its reactions are chosen from
	 * @param sent   the velocity the server gave the body (its knockback), blocks a second
	 * @param seen   the velocity the body was seen to have (already given to the ragdoll), blocks a second
	 * @param hitDir the way the killing blow went, or null
	 * @param kick   how hard it struck the part it landed on, blocks a second
	 */
	public static Stumble begin(StumbleStyle style, HumanoidRagdoll r, Behaviour.Input in, Vector3d sent,
			Vector3d seen, Vector3d hitDir, double kick) {
		if (style == null || Math.hypot(sent.x, sent.z) > style.thrownAt || sent.y > style.thrownUp) {
			// Thrown: it has no feet to stay on.
			return null;
		}
		Stumble s = plan(style, in.kind(), in.hitPart(), in.muscles(), r);
		if (s == null) {
			return null;
		}
		r.setMuscles(in.muscles().stretched(s.stretch));
		r.setBehaviour(Behaviour.choose(in, true));
		r.setController(s);
		s.launch(r, in, sent, seen, hitDir, kick);
		return s;
	}

	/**
	 * What the hit does to a body that is going to stay on its feet: far less
	 * than the game's knockback, which is made to throw a living player back a
	 * block and would throw this one off its feet before it took a step.
	 */
	private void launch(HumanoidRagdoll r, Behaviour.Input in, Vector3d sent, Vector3d seen, Vector3d hitDir,
			double kick) {
		if (sent.lengthSquared() > seen.lengthSquared()) {
			r.push((sent.x - seen.x) * style.pushShare, (sent.y - seen.y) * style.liftShare,
					(sent.z - seen.z) * style.pushShare);
		}
		int hit = in.hitPart();
		if (hit >= 0 && hit < HumanoidRagdoll.PARTS && r.present(hit) && hitDir != null && kick > 0.0) {
			double k = kick * (hit == HumanoidRagdoll.TORSO ? style.torsoKick : style.limbKick);
			r.kick(hit, hitDir.x * k, hitDir.y * k, hitDir.z * k);
		}
		if (in.kind() == DeathKind.FADE && style.sway > 0.0) {
			// Nothing struck it: it sways, some way of its own, about its feet.
			double a = (Math.floorMod(in.seed() * 0x9E3779B9L, 4096L) / 4096.0) * Math.PI * 2.0;
			Vector3d f = r.facing();
			Vector3d front = new Vector3d(f.x, 0.0, f.z);
			if (front.lengthSquared() < 1.0e-6) {
				front.set(0.0, 0.0, 1.0);
			}
			front.normalize();
			Vector3d right = new Vector3d(front).cross(0.0, 1.0, 0.0);
			// Mostly forward or to a side: nobody faints backwards onto their heels.
			Vector3d dir = new Vector3d(front).mul(0.35 + 0.65 * Math.abs(Math.cos(a))).fma(Math.sin(a), right).normalize();
			Vector3d feet = new Vector3d(r.tip(LEGS[0])).add(r.tip(LEGS[1])).mul(0.5);
			double height = Math.max(0.5, r.body(HumanoidRagdoll.HEAD).x.y - feet.y);
			r.spin(new Vector3d(dir.z, 0.0, -dir.x).mul(style.sway / height), feet);
		}
	}

	/** How far through its time on its feet it is, 0 to 1. */
	private double spent(double t) {
		return Math.max(0.0, Math.min(1.0, t / onFeet));
	}

	/** Its strength, 1 a living body's, at a time since death. */
	public double strength(double t) {
		return vigor * fade(spent(t), style.strengthFrom, 1.0, style.strengthLeft);
	}

	/** Its balance (the hold on its torso), 1 a living body's, at a time since death. */
	public double balance(double t) {
		return style.balanceStart * fade(spent(t), style.balanceFrom, style.balanceGone,
				style.balanceLeft / Math.max(1.0e-6, style.balanceStart));
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
			slump(r, world, t);
			return;
		}
		double x = spent(t);
		stepper.strength = strength(t);
		stepper.balance = balance(t);
		// Each step shorter than the last, and slower.
		double reach = fade(x, 0.0, 1.0, style.reachLeft);
		stepper.reachForward = style.reachForward * reach;
		stepper.reachBack = style.reachBack * reach;
		double slow = 1.0 + style.slowBy * (1.0 - fade(x, 0.0, 1.0, 0.0));
		stepper.swingMin = style.swingMin * slow;
		stepper.swingMax = style.swingMax * slow;
		stepper.grip = fade(x, style.gripFrom, 1.0, style.gripLeft);
		stepper.hunch = style.hunch + (style.hunchLate - style.hunch) * (1.0 - fade(x, 0.0, 1.0, 0.0));
		// An arm with a hand on a wound, or one that only hangs, is not the stepper's to wave.
		int clutching = r.behaviour() == null ? -1 : r.behaviour().clutchArm(r);
		for (int a = 0; a < 2; a++) {
			stepper.leaveArm[a] = ARMS[a] == clutching || !r.usable(ARMS[a]);
		}
		stepper.apply(r, world, t, dt);
		if (stepper.released()) {
			// It lost its footing: it is already falling, the way it was going.
			down(r, t, stepper.lost(), false);
			return;
		}
		if (weakLeg >= 0) {
			boolean alone = stepper.planted(weakLeg) && !stepper.planted(1 - weakLeg);
			aloneFor = alone ? aloneFor + dt : 0.0;
			if (aloneFor >= style.buckleAfter) {
				down(r, t, "its leg gave", true);
				return;
			}
		}
		if (t >= onFeet) {
			down(r, t, "spent", true);
		}
	}

	private void down(HumanoidRagdoll r, double t, String reason, boolean release) {
		letGo = true;
		letGoAt = t;
		why = reason;
		boolean moving = stepper.speed() > STILL_SPEED;
		Vector3d right = stepper.rightward();
		if (release) {
			stepper.release(r);
		}
		double left = 1.0 - spent(t);
		Muscles m = r.muscles().legsGiving(t, style.giveTone);
		if (weakLeg >= 0) {
			m.limb[LEGS[weakLeg]] = Math.min(m.limb[LEGS[weakLeg]], 0.2);
		}
		r.setMuscles(m);
		if (r.behaviour() != null) {
			r.behaviour().fallsFrom(t, style.fallStrengthLeft + (style.fallStrength - style.fallStrengthLeft) * left);
		}
		if (weakLeg >= 0 && release) {
			// Down over the leg that gave.
			Vector3d hip = r.joint(LEGS[weakLeg], new Vector3d()).sub(r.body(HumanoidRagdoll.TORSO).x);
			double side = hip.dot(right) >= 0.0 ? 1.0 : -1.0;
			r.push(right.x * side * style.buckleShove, 0.0, right.z * side * style.buckleShove);
		} else if (!moving && release) {
			// A nudge the way it faces, so it goes down rather than balancing on its feet.
			Vector3d f = r.facing();
			r.push(f.x * style.nudge, 0.0, f.z * style.nudge);
		}
		r.wake();
	}

	/**
	 * Leaning less than this, degrees, with its head this far above its lower
	 * foot, that foot on something and its chest this far above the ground
	 * under it, a body is still upright. (One lying up a flight of stairs
	 * leans no more than they do, and one hanging in a hole by its arms has
	 * nothing under its feet: neither is standing.)
	 */
	private static final double UPRIGHT = 45.0;
	private static final double HEAD_UP = 1.2;
	private static final double CHEST_UP = 0.7;
	private static final double FOOT_ON = 0.3;

	private final BoxList near = new BoxList();
	private final double[] box = new double[6];

	/**
	 * Legs that have given hold nothing up. But a body let go of against a
	 * wall, a step or the side of a hole can be left propped there, on legs that
	 * are two stiff sticks (it has no knees to fold): it folds at the hips
	 * instead, its feet slide out from under it, and it sits down.
	 */
	private void slump(HumanoidRagdoll r, PhysicsWorld world, double t) {
		sinceLetGo = t - letGoAt;
		propped = false;
		if (sinceLetGo < style.slumpAfter || sinceLetGo > style.slumpAfter + style.slumpFor || r.lean() >= UPRIGHT) {
			return;
		}
		Vector3d foot = r.tip(LEGS[r.tip(LEGS[0]).y <= r.tip(LEGS[1]).y ? 0 : 1]);
		if (r.body(HumanoidRagdoll.HEAD).x.y - foot.y < HEAD_UP) {
			return;
		}
		if (Double.isNaN(topUnder(world, foot.x, foot.z, foot.y + 0.1, FOOT_ON + 0.1))) {
			return;
		}
		Vector3d chest = r.body(HumanoidRagdoll.TORSO).x;
		double under = topUnder(world, chest.x, chest.z, chest.y, CHEST_UP);
		if (!Double.isNaN(under)) {
			return;
		}
		propped = true;
		for (int k = 0; k < 2; k++) {
			r.body(LEGS[k]).friction = style.slumpGrip;
			r.target(LEGS[k], -style.slumpFold, k == 0 ? 0.05 : -0.05, 0.0);
			r.reflex(LEGS[k], style.slumpTone);
		}
	}

	/** The top of the highest block under a spot, from a height down to {@code reach} below it; NaN for none. */
	private double topUnder(PhysicsWorld world, double x, double z, double fromY, double reach) {
		near.clear();
		world.collect(x - 0.05, fromY - reach, z - 0.05, x + 0.05, fromY, z + 0.05, near);
		double best = Double.NaN;
		for (int i = 0; i < near.size(); i++) {
			near.get(i, box);
			if (box[4] <= fromY && box[4] >= fromY - reach && (Double.isNaN(best) || box[4] > best)) {
				best = box[4];
			}
		}
		return best;
	}

	@Override
	public boolean holding() {
		return !letGo;
	}

	@Override
	public boolean restless() {
		// Until it is known whether it went down, and while it has not.
		return letGo && (sinceLetGo < style.slumpAfter || propped);
	}

	/** Whether it was let go of and is still upright, propped on something, its feet sliding out. */
	public boolean propped() {
		return propped;
	}

	/** Seconds since death at which it went down, or -1 while it is still on its feet. */
	public double letGoAt() {
		return letGoAt;
	}

	/**
	 * Why it went down ("spent", "its leg gave", or how the stepper
	 * {@linkplain Stepper#lost() lost its footing}), or null while it is still
	 * on its feet.
	 */
	public String why() {
		return why;
	}
}
