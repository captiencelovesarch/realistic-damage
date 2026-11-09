package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import dev.captience.realisticdamage.ragdoll.physics.BoxList;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import java.util.EnumSet;
import org.joml.Vector3d;

/**
 * What a dying body still does (active ragdolls, phase 3): the reflexes that
 * make a fall look like someone falling. An arm goes out to meet the ground, a
 * hand goes to the wound, the arms come up in front of a blast, a struck body
 * staggers a step before it goes, a burning one writhes.
 *
 * <p>Two halves. {@link #choose} picks the reactions from how the body died
 * and what was left of it (a table, unit-tested). {@link #apply} runs every
 * tick before the physics step and works only through the muscles: it sets
 * where each joint's muscles pull and how hard ({@code reflex} tone, on top of
 * what {@link Muscles} still has), so a reflex is as strong as the body that
 * makes it, and gravity, the ground and the joint limits still decide what
 * actually happens. The one exception is the stagger, which holds the torso up
 * for a moment, because a body without knees cannot catch its own balance.
 *
 * <p>The body is six boxes (phase 1 was dropped by choice), so every reach is
 * an arm aimed from its shoulder; there is no elbow to bend. Deterministic:
 * the rhythms of writhing and convulsing are sines seeded by the entity.
 */
public final class Behaviour {
	public enum Reaction {
		/** Falling forward or sideways: the arms go out to meet the ground, and give when they hit it. */
		BRACE,
		/** A blast: the arms come up in front of the face, the chin tucks. */
		PROTECT_HEAD,
		/** A hand goes to the wound. */
		CLUTCH,
		/** Struck in the gut: folds over it. */
		DOUBLE_OVER,
		/** A blow to someone on two good legs: a shuffle in the push direction before the fall. */
		STAGGER,
		/** Burning: limbs thrash, slowing as the mind goes. */
		WRITHE,
		/** Electrocuted: a rigid, jerking second. */
		CONVULSE,
		/** Drowning: the arms reach up for the surface. */
		REACH_UP,
		/** Falling for ever: slow, drifting limbs. */
		FLAIL_SLOW,
		/** A violent end twitches once or twice after it has settled. */
		TWITCH
	}

	/**
	 * What the reflexes need to know.
	 *
	 * @param kind      what killed it
	 * @param violence  0..1
	 * @param hitPart   where the killing blow landed (ragdoll index), or -1
	 * @param woundPart the body's worst open wound (ragdoll index), or -1
	 * @param muscles   the muscles it died with (how awake, what is broken)
	 * @param push      the blow's direction, world (zero for none)
	 * @param seed      the entity, for the rhythms
	 */
	public record Input(DeathKind kind, double violence, int hitPart, int woundPart, Muscles muscles, Vector3d push,
			int seed) {}

	private final EnumSet<Reaction> reactions;
	/** The region a hand goes to, or -1. */
	private final int clutchPart;
	private final Vector3d push;
	private final double awake;
	private final int seed;
	/** How long the stagger holds the body up, seconds. */
	private final double staggerFor;

	/** Per arm: it has met the ground while bracing and given way. */
	private final boolean[] gave = new boolean[HumanoidRagdoll.PARTS];
	private double settledAt = -1.0;
	private int twitches;
	private final BoxList near = new BoxList();

	private Behaviour(EnumSet<Reaction> reactions, int clutchPart, Vector3d push, double awake, int seed,
			double staggerFor) {
		this.reactions = reactions;
		this.clutchPart = clutchPart;
		this.push = push;
		this.awake = awake;
		this.seed = seed;
		this.staggerFor = staggerFor;
	}

	/** The same reactions for a body that lives through it (a knockdown): no death twitch. */
	public Behaviour withoutTwitch() {
		EnumSet<Reaction> set = reactions.clone();
		set.remove(Reaction.TWITCH);
		return new Behaviour(set, clutchPart, push, awake, seed, staggerFor);
	}

	public boolean has(Reaction r) {
		return reactions.contains(r);
	}

	public EnumSet<Reaction> reactions() {
		return reactions.clone();
	}

	public int clutchPart() {
		return clutchPart;
	}

	// --- Choosing ---------------------------------------------------------------------

	/** The reactions a death leaves the body with. */
	public static Behaviour choose(Input in) {
		Muscles m = in.muscles();
		EnumSet<Reaction> set = EnumSet.noneOf(Reaction.class);
		double v = Math.max(0.0, Math.min(1.0, in.violence()));
		boolean headshot = in.hitPart() == HumanoidRagdoll.HEAD
				&& (in.kind() == DeathKind.BLOW || in.kind() == DeathKind.PROJECTILE);
		if (v >= 0.6 || headshot || in.kind() == DeathKind.CRUSH) {
			set.add(Reaction.TWITCH);
		}
		if (in.kind() == DeathKind.FREEZE) {
			// Frozen solid: nothing moves, not even afterwards.
			set.clear();
		}
		int clutch = -1;
		double stagger = 0.0;
		Vector3d push = new Vector3d(in.push().x, 0.0, in.push().z);
		if (push.lengthSquared() > 1.0e-6) {
			push.normalize();
		} else {
			push.zero();
		}
		if (m.stringsCut || m.awake < 0.15) {
			return new Behaviour(set, -1, push, m.awake, in.seed(), 0.0);
		}
		switch (in.kind()) {
			case EXPLOSION -> {
				if (m.awake >= 0.3) {
					set.add(Reaction.PROTECT_HEAD);
				}
			}
			case BLOW, PROJECTILE -> {
				if (in.hitPart() >= 0 && in.hitPart() != HumanoidRagdoll.HEAD) {
					clutch = in.hitPart();
				} else if (in.woundPart() >= 0 && in.woundPart() != HumanoidRagdoll.HEAD) {
					clutch = in.woundPart();
				}
				boolean legsSound = m.limb[HumanoidRagdoll.RIGHT_LEG] > 0.0 && m.limb[HumanoidRagdoll.LEFT_LEG] > 0.0;
				if (m.awake >= 0.5 && v < 0.8 && legsSound && push.lengthSquared() > 0.0) {
					set.add(Reaction.STAGGER);
					stagger = (0.35 + 0.35 * (1.0 - v)) * m.awake;
				}
				set.add(Reaction.BRACE);
			}
			case FADE -> {
				// Let go, not struck: no brace (a faint does not catch itself), but a
				// hand goes to whatever is bleeding.
				clutch = in.woundPart();
			}
			case FIRE, LAVA -> set.add(Reaction.WRITHE);
			case SHOCK -> set.add(Reaction.CONVULSE);
			case DROWNING -> set.add(Reaction.REACH_UP);
			case VOID -> set.add(Reaction.FLAIL_SLOW);
			default -> {}
		}
		if (clutch >= 0) {
			set.add(Reaction.CLUTCH);
			if (clutch == HumanoidRagdoll.TORSO) {
				set.add(Reaction.DOUBLE_OVER);
			}
		}
		return new Behaviour(set, clutch, push, m.awake, in.seed(), stagger);
	}

	// --- Doing ------------------------------------------------------------------------

	/** Once a tick, before the physics step: where the muscles pull, and how hard. */
	public void apply(HumanoidRagdoll r, PhysicsWorld world, double t, double dt) {
		twitchIfDue(r, t);
		twitching(r, t);
		if (awake <= 0.0) {
			return;
		}
		boolean[] busy = new boolean[HumanoidRagdoll.PARTS];
		if (has(Reaction.STAGGER) && t < staggerFor) {
			stagger(r, t);
		}
		if (has(Reaction.PROTECT_HEAD) && t < 1.4) {
			protectHead(r, t, busy);
		}
		if (has(Reaction.CLUTCH)) {
			clutch(r, t, busy);
		}
		if (has(Reaction.BRACE) && t < 1.6) {
			brace(r, world, t, busy);
		}
		if (has(Reaction.WRITHE) && t < 4.5) {
			writhe(r, t);
		}
		if (has(Reaction.CONVULSE) && t < 1.1) {
			convulse(r, t);
		}
		if (has(Reaction.REACH_UP) && t < 2.2) {
			reachUp(r, t);
		}
		if (has(Reaction.FLAIL_SLOW) && t < 8.0) {
			flailSlow(r, t);
		}
	}

	/**
	 * Holds the torso up and lets the feet shuffle, while the legs step, for a
	 * moment that fades: a struck body does not fall the instant it is hit.
	 */
	private void stagger(HumanoidRagdoll r, double t) {
		double s = 1.0 - t / staggerFor;
		r.holdUpright(2.5 * s);
		// Feet that slide instead of stick: the shuffle.
		double feet = 0.3 + 0.7 * (1.0 - s);
		r.body(HumanoidRagdoll.RIGHT_LEG).friction = feet;
		r.body(HumanoidRagdoll.LEFT_LEG).friction = feet;
		// Steps: the legs scissor along the push, as seen from the body.
		Vector3d facing = r.facing();
		Vector3d side = new Vector3d(facing).cross(0.0, 1.0, 0.0);
		boolean sideways = Math.abs(push.dot(side)) > Math.abs(push.dot(facing));
		double phase = t * Math.PI * 2.0 * 2.4;
		double step = 0.25 * Math.sin(phase) * s;
		// Legs that carry the weight: without knees, a leg that gives splays, and
		// the body sits down instead of staggering.
		double legTone = 7.0 * awake * s;
		if (sideways) {
			r.target(HumanoidRagdoll.RIGHT_LEG, 0.0, Math.max(0.0, step), 0.0);
			r.target(HumanoidRagdoll.LEFT_LEG, 0.0, Math.min(0.0, step), 0.0);
		} else {
			r.target(HumanoidRagdoll.RIGHT_LEG, step, 0.05, 0.0);
			r.target(HumanoidRagdoll.LEFT_LEG, -step, -0.05, 0.0);
		}
		r.reflex(HumanoidRagdoll.RIGHT_LEG, legTone);
		r.reflex(HumanoidRagdoll.LEFT_LEG, legTone);
		// Arms out for balance.
		r.target(HumanoidRagdoll.RIGHT_ARM, -0.4, 0.7, 0.0);
		r.target(HumanoidRagdoll.LEFT_ARM, -0.4, -0.7, 0.0);
		r.reflex(HumanoidRagdoll.RIGHT_ARM, 1.4 * awake * s);
		r.reflex(HumanoidRagdoll.LEFT_ARM, 1.4 * awake * s);
	}

	/** Both arms up in front of the face, the chin tucked, fading through the tumble. */
	private void protectHead(HumanoidRagdoll r, double t, boolean[] busy) {
		double f = awake * Math.exp(-t / 0.8);
		if (r.usable(HumanoidRagdoll.RIGHT_ARM)) {
			r.aimAtModel(HumanoidRagdoll.RIGHT_ARM, -1.5, -6.0, -6.5);
			r.reflex(HumanoidRagdoll.RIGHT_ARM, 1.8 * f);
			busy[HumanoidRagdoll.RIGHT_ARM] = true;
		}
		if (r.usable(HumanoidRagdoll.LEFT_ARM)) {
			r.aimAtModel(HumanoidRagdoll.LEFT_ARM, 1.5, -6.0, -6.5);
			r.reflex(HumanoidRagdoll.LEFT_ARM, 1.8 * f);
			busy[HumanoidRagdoll.LEFT_ARM] = true;
		}
		r.target(HumanoidRagdoll.HEAD, 0.55, 0.0, 0.0);
		r.reflex(HumanoidRagdoll.HEAD, 1.4 * f);
	}

	/**
	 * A hand to the wound: points on the body in model pixels (y down, -z the
	 * front, the body's right is -x), reached by the arm that would reach it.
	 */
	private void clutch(HumanoidRagdoll r, double t, boolean[] busy) {
		double f = awake * Math.exp(-t / 2.2);
		int arm;
		double x;
		double y;
		double z;
		switch (clutchPart) {
			case HumanoidRagdoll.TORSO -> {
				// Well in front of the belly: a straight arm swung in low runs into
				// the torso's side; aimed out front it comes across it.
				arm = HumanoidRagdoll.RIGHT_ARM;
				x = 1.0;
				y = 9.0;
				z = -6.5;
			}
			case HumanoidRagdoll.HEAD -> {
				arm = HumanoidRagdoll.RIGHT_ARM;
				x = -2.5;
				y = -4.0;
				z = -4.5;
			}
			case HumanoidRagdoll.RIGHT_ARM -> {
				arm = HumanoidRagdoll.LEFT_ARM;
				x = -5.0;
				y = 5.0;
				z = -2.8;
			}
			case HumanoidRagdoll.LEFT_ARM -> {
				arm = HumanoidRagdoll.RIGHT_ARM;
				x = 5.0;
				y = 5.0;
				z = -2.8;
			}
			case HumanoidRagdoll.RIGHT_LEG -> {
				arm = HumanoidRagdoll.RIGHT_ARM;
				x = -2.5;
				y = 15.0;
				z = -3.0;
			}
			case HumanoidRagdoll.LEFT_LEG -> {
				arm = HumanoidRagdoll.LEFT_ARM;
				x = 2.5;
				y = 15.0;
				z = -3.0;
			}
			default -> {
				return;
			}
		}
		if (!r.usable(arm) || busy[arm]) {
			// The other hand, if the wound is somewhere both can reach.
			int other = arm == HumanoidRagdoll.RIGHT_ARM ? HumanoidRagdoll.LEFT_ARM : HumanoidRagdoll.RIGHT_ARM;
			boolean central = clutchPart == HumanoidRagdoll.TORSO || clutchPart == HumanoidRagdoll.HEAD;
			if (!central || !r.usable(other) || busy[other]) {
				return;
			}
			arm = other;
			x = -x;
		}
		r.aimAtModel(arm, x, y, z);
		r.reflex(arm, 3.5 * f);
		busy[arm] = true;
		// Doubling over waits for a stagger to end: folding at the hips while
		// the stagger holds the torso up would swing the legs out and sit the
		// body down.
		double fold = t - staggerFor;
		if (has(Reaction.DOUBLE_OVER) && fold >= 0.0 && fold < 1.0) {
			double d = awake * (1.0 - fold);
			r.target(HumanoidRagdoll.RIGHT_LEG, -0.55, 0.05, 0.0);
			r.target(HumanoidRagdoll.LEFT_LEG, -0.55, -0.05, 0.0);
			r.reflex(HumanoidRagdoll.RIGHT_LEG, 1.6 * d);
			r.reflex(HumanoidRagdoll.LEFT_LEG, 1.6 * d);
			r.target(HumanoidRagdoll.HEAD, 0.5, 0.0, 0.0);
			r.reflex(HumanoidRagdoll.HEAD, 1.2 * d);
		}
	}

	/**
	 * Going over forward or sideways: the free arms reach for where the body
	 * will land, and give when they meet the ground. Going over backward: they
	 * fling forward and out.
	 */
	private void brace(HumanoidRagdoll r, PhysicsWorld world, double t, boolean[] busy) {
		double lean = r.lean();
		if (lean < 6.0 || lean > 95.0) {
			return;
		}
		Vector3d up = r.up();
		Vector3d toward = new Vector3d(up.x, 0.0, up.z);
		if (toward.lengthSquared() < 1.0e-6) {
			return;
		}
		toward.normalize();
		Vector3d facing = r.facing();
		Vector3d flatFacing = new Vector3d(facing.x, 0.0, facing.z);
		double forward = flatFacing.lengthSquared() > 1.0e-6 ? toward.dot(flatFacing.normalize()) : 0.0;
		double f = awake * Math.exp(-t / 1.4);
		for (int arm : new int[] {HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.LEFT_ARM}) {
			if (!r.usable(arm) || busy[arm]) {
				continue;
			}
			if (forward > -0.35) {
				// Down, a little ahead: hands under the chest before the chest lands.
				r.aimWorld(arm, new Vector3d(toward).mul(0.3).add(0.0, -1.0, 0.0));
				if (!gave[arm] && touching(r.tip(arm), world)) {
					gave[arm] = true;
				}
				r.reflex(arm, (gave[arm] ? 0.6 : 2.6) * f);
			} else {
				boolean right = arm == HumanoidRagdoll.RIGHT_ARM;
				r.target(arm, -1.7, right ? 0.9 : -0.9, 0.0);
				r.reflex(arm, 1.3 * f);
			}
		}
	}

	/** Whether a point is in or just above something solid. */
	private boolean touching(Vector3d p, PhysicsWorld world) {
		near.clear();
		world.collect(p.x - 0.03, p.y - 0.08, p.z - 0.03, p.x + 0.03, p.y + 0.03, p.z + 0.03, near);
		return near.size() > 0;
	}

	/** Thrashing: every limb on its own slow rhythm, fading as the mind goes. */
	private void writhe(HumanoidRagdoll r, double t) {
		double a = Math.exp(-t / 2.4);
		double f = awake * a;
		int[] parts = {HumanoidRagdoll.HEAD, HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.LEFT_ARM,
				HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG};
		double[] amp = {0.5, 1.3, 1.3, 0.7, 0.7};
		for (int k = 0; k < parts.length; k++) {
			int p = parts[k];
			double phi = phase(k * 3);
			double sx = amp[k] * a * (Math.sin(2.7 * t + phi) + 0.5 * Math.sin(4.3 * t + phase(k * 3 + 1)));
			double sz = amp[k] * a * 0.6 * Math.sin(3.4 * t + phase(k * 3 + 2));
			if (p == HumanoidRagdoll.RIGHT_ARM) {
				sz += 0.5 * a;
			} else if (p == HumanoidRagdoll.LEFT_ARM) {
				sz -= 0.5 * a;
			}
			r.target(p, sx, sz, 0.0);
			r.reflex(p, 3.0 * f);
		}
	}

	/** Locked and jerking: every joint snapping back and forth, stiff, for about a second. */
	private void convulse(HumanoidRagdoll r, double t) {
		int[] parts = {HumanoidRagdoll.HEAD, HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.LEFT_ARM,
				HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG};
		for (int k = 0; k < parts.length; k++) {
			double jerk = Math.sin(38.0 * t + phase(k)) >= 0.0 ? 0.3 : -0.3;
			double side = 0.2 * Math.sin(29.0 * t + phase(k + 7));
			r.target(parts[k], jerk, side, 0.0);
			r.reflex(parts[k], 3.5 * awake);
		}
	}

	/** Reaching for the surface, slowing, then still. */
	private void reachUp(HumanoidRagdoll r, double t) {
		double f = awake * Math.exp(-t / 1.4);
		double wobble = 1.5 * Math.sin(2.2 * t + phase(1));
		if (r.usable(HumanoidRagdoll.RIGHT_ARM)) {
			r.aimAtModel(HumanoidRagdoll.RIGHT_ARM, -4.0 + wobble, -18.0, -3.0);
			r.reflex(HumanoidRagdoll.RIGHT_ARM, 1.0 * f);
		}
		if (r.usable(HumanoidRagdoll.LEFT_ARM)) {
			r.aimAtModel(HumanoidRagdoll.LEFT_ARM, 4.0 - wobble, -18.0, -3.0);
			r.reflex(HumanoidRagdoll.LEFT_ARM, 1.0 * f);
		}
		r.target(HumanoidRagdoll.HEAD, -0.5, 0.0, 0.0);
		r.reflex(HumanoidRagdoll.HEAD, 0.8 * f);
	}

	/** The long fall: limbs drifting, as if through water. */
	private void flailSlow(HumanoidRagdoll r, double t) {
		double f = awake * Math.max(0.0, 1.0 - t / 8.0);
		r.target(HumanoidRagdoll.RIGHT_ARM, 1.2 * Math.sin(1.3 * t + phase(2)), 0.8 + 0.4 * Math.sin(0.9 * t), 0.0);
		r.target(HumanoidRagdoll.LEFT_ARM, 1.2 * Math.sin(1.3 * t + phase(3)), -0.8 - 0.4 * Math.sin(0.9 * t + 1.0), 0.0);
		r.target(HumanoidRagdoll.RIGHT_LEG, 0.5 * Math.sin(1.1 * t + phase(4)), 0.1, 0.0);
		r.target(HumanoidRagdoll.LEFT_LEG, 0.5 * Math.sin(1.1 * t + phase(5)), -0.1, 0.0);
		for (int p : new int[] {HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.LEFT_ARM, HumanoidRagdoll.RIGHT_LEG,
				HumanoidRagdoll.LEFT_LEG}) {
			r.reflex(p, 0.9 * f);
		}
	}

	// --- The twitch -------------------------------------------------------------------

	/** Asleep: whether a twitch is due, which wakes the body. */
	public boolean wantsWake(HumanoidRagdoll r, double t) {
		return twitchIfDue(r, t);
	}

	private static final int[] TWITCH_LIMBS = {HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.LEFT_ARM,
			HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_ARM};
	/** The limb twitching now, and until when. */
	private int twitchLimb = -1;
	private double twitchUntil;

	/**
	 * Once the body has come to rest (asleep, or barely moving a second in), a
	 * violent death twitches: a limb flicks, half a second later, and once more.
	 */
	private boolean twitchIfDue(HumanoidRagdoll r, double t) {
		if (!has(Reaction.TWITCH) || twitches >= 2) {
			return false;
		}
		if (settledAt < 0.0) {
			if (r.asleep() || (t > 1.2 && r.sim.maxMotion() < 0.25)) {
				settledAt = t;
			}
			return false;
		}
		double due = settledAt + 0.35 + Math.floorMod(seed, 5) * 0.08 + twitches * 0.75;
		if (t < due) {
			return false;
		}
		int limb = TWITCH_LIMBS[Math.floorMod(seed + twitches, TWITCH_LIMBS.length)];
		twitches++;
		if (!r.present(limb)) {
			return false;
		}
		twitchLimb = limb;
		twitchUntil = t + 0.2;
		// Aimed once, from where the limb lies now. Re-aiming every tick from
		// wherever the limb had got to would chase it, and a thin limb (a
		// skeleton's) spun up to the speed cap that way.
		var joint = r.jointOf(limb);
		if (joint != null) {
			Vector3d swing = new Vector3d();
			double[] twist = new double[1];
			dev.captience.realisticdamage.ragdoll.physics.Joint.decompose(
					joint.relative(new org.joml.Quaterniond()), swing, twist);
			double flick = twitches == 1 ? -0.9 : 0.6;
			double sx = Math.max(joint.swingXMin, Math.min(joint.swingXMax, swing.x + flick));
			dev.captience.realisticdamage.ragdoll.physics.Joint.compose(sx, swing.z, twist[0], twitchTarget);
		}
		return true;
	}

	private final org.joml.Quaterniond twitchTarget = new org.joml.Quaterniond();

	/**
	 * The twitch itself: a short, hard pull of one limb's muscles toward a pose
	 * a little forward or back of where it lay (through the muscles, so the
	 * joint's limits and the ground still have their say).
	 */
	private void twitching(HumanoidRagdoll r, double t) {
		if (twitchLimb < 0 || t >= twitchUntil) {
			twitchLimb = -1;
			return;
		}
		r.target(twitchLimb, twitchTarget);
		r.reflex(twitchLimb, 3.0);
	}

	/** A fixed phase from the seed, so two bodies do not writhe in step. */
	private double phase(int k) {
		long h = (seed * 0x9E3779B97F4A7C15L) ^ (k * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 29;
		return (Math.floorMod(h, 6283L)) / 1000.0;
	}
}
