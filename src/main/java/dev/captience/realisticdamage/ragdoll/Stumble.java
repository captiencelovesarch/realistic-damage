package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.physics.BoxList;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import org.joml.Vector3d;

/**
 * A death on its feet (stumbling deaths, IK phases 1 and 2): the body does
 * not know it is dead yet. It keeps its feet under it with the
 * {@link Stepper} for a while, weakening all the while, and then goes down.
 *
 * <p>Which deaths: a blow, an arrow, a bleed-out, of a body that still had a
 * mind and two legs. Never one whose strings were cut (a headshot, a crush,
 * an overkill), and never a blast, a fall, water, lightning or cold: those
 * bodies are thrown, or are not there to try. {@link #plan} decides.
 *
 * <p>Grounded: how long comes from its own leg muscles ({@link Muscles}),
 * which already know how hard it was hit, how awake it was and whether
 * adrenaline had hold of it. The {@link StumbleStyle} turns that into
 * seconds, and says how the body weakens over them: its balance first, its
 * strength after, its steps shortening and slowing, its feet losing their
 * grip. A wounded leg (broken, limp, or the one the killing blow struck) is
 * weak from the start and gives the first time it has to carry the body
 * alone; the body goes down to that side.
 *
 * <p>Arcade plans it instead: the hit shoves the body back along the shot
 * and turns it toward the shooter, it is kept going for a number of steps
 * the blow decides (the heavier, the fewer), held up firmly, and then it goes
 * down with a {@link Finisher} picked from how it was hit. A wounded leg
 * limps a couple of steps before it gives. And a body whose strings were cut
 * hangs a beat before it drops ({@link StumbleStyle#hangFor}).
 *
 * <p>Deterministic: the only thing that varies a death is the body's own
 * seed. Plain Java.
 */
public final class Stumble implements Controller {
	/** How an Arcade stumble ends. */
	public enum Finisher {
		/** Moving forward: the last step gives and it falls on its face, the arms going out late. */
		FACE_PLANT("face-plant"),
		/** Hit from the side: it turns on, about a quarter, and crumples. */
		SPIN_DROP("spin-drop"),
		/** Pushed back: two steps back, it sits down hard, the legs out in front, and tips back. */
		SIT_DOWN("sit-down"),
		/** It turns to where the shot came from, holds that a beat, and drops. */
		LAST_LOOK("last look");

		public final String label;

		Finisher(String label) {
			this.label = label;
		}
	}

	/** Slower than this along the ground, blocks a second, and it is standing still. */
	private static final double STILL_SPEED = 0.15;
	/** A shooter further round than this from the way it faces, radians, is behind it: it turns less, and none from dead behind. */
	private static final double BEHIND = 2.0;
	private static final int[] LEGS = {HumanoidRagdoll.RIGHT_LEG, HumanoidRagdoll.LEFT_LEG};
	private static final int[] ARMS = {HumanoidRagdoll.RIGHT_ARM, HumanoidRagdoll.LEFT_ARM};

	public final Stepper stepper = new Stepper();
	public final StumbleStyle style;
	/** Seconds on its feet before its legs give (at most, for a planned stumble). */
	public final double onFeet;
	/** How strong its legs were when it died, 1 a body at full strength. */
	public final double vigor;
	/** Which leg is wounded, 0 the right and 1 the left, or -1 for neither. */
	public final int weakLeg;
	/** How many times longer than its muscles alone it lasts: what {@link Muscles#stretched} is given. */
	private final double stretch;
	/** Steps before the finisher, or 0 for none planned (Grounded). */
	private int plannedSteps;
	/** A planned stumble's finisher comes at the first step it lands from then on, seconds (or at {@link #onFeet}). */
	private double finishFrom;
	/** ...and at the latest this, seconds: the planned time for its own step count. */
	private double finishBy;
	private int stepsBefore;
	/** Steps it had taken when its finisher (or its last look) began, or -1 before. */
	private int staggered = -1;
	/** Its finisher came early: it lost its footing on open ground before the finisher was due. */
	private boolean early;
	/**
	 * Steps that are not stagger steps: a body that died walking has a foot in
	 * the air, and putting it down is not a step of the stagger.
	 */
	private int notStagger;
	/** Faster than this along the ground, blocks a second, it died going somewhere. */
	private static final double MOVING = 3.0;
	/**
	 * A planned stumble with neither foot down this long, seconds, has lost its
	 * footing whatever the stepper says (it is carried, legs splayed, on the
	 * weight held up under it).
	 */
	private static final double UNPLANTED_FOR = 0.6;
	private double unplantedFor;
	/** Only the beat before a body whose strings were cut drops. */
	private final boolean hang;
	/** What a hanging body does once the beat is over: the drop it would have had at once. */
	private Runnable fall;
	private Finisher planned;
	private Finisher finisher;
	/** The way it died facing, along the ground. */
	private final Vector3d faced = new Vector3d();
	/** The way it is kept going, along the ground, unit, and how fast it set off. */
	private final Vector3d path = new Vector3d();
	private double pathSpeed;
	/** Toward whoever shot it, along the ground, unit; zero for no one. */
	private final Vector3d toShooter = new Vector3d();
	/** How far it turns toward them, radians about +Y from {@link #faced} (signed), and how far they are round. */
	private double turnTo;
	private double shooterAt;
	private boolean looking;
	private double lookFrom;
	private boolean sitting;
	private boolean tipped;
	private double aloneFor;
	private boolean letGo;
	private double sinceLetGo;
	private boolean propped;
	private double letGoAt = -1.0;
	private String why;
	private final Vector3d vel = new Vector3d();

	private Stumble(StumbleStyle style, double onFeet, double vigor, int weakLeg, double stretch, int plannedSteps,
			boolean hang) {
		this.style = style;
		this.onFeet = onFeet;
		this.vigor = vigor;
		this.weakLeg = weakLeg;
		this.stretch = stretch;
		this.plannedSteps = plannedSteps;
		this.hang = hang;
		style.applyTo(stepper);
		if (weakLeg >= 0) {
			stepper.legStrength[weakLeg] = style.woundedLeg;
		}
	}

	/**
	 * The stumble a death gets, or null if the body simply falls.
	 *
	 * @param hitPart  where the killing blow landed (ragdoll index), or -1
	 * @param violence how hard, 0..1 (a planned stumble's step count)
	 * @param muscles  the muscles it died with
	 */
	public static Stumble plan(StumbleStyle style, DeathKind kind, int hitPart, double violence, Muscles muscles,
			HumanoidRagdoll r) {
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
		if (style.stepsMost > 0.0) {
			// Planned: as many steps as the blow leaves it, however its muscles were.
			double v = Math.max(0.0, Math.min(1.0, violence));
			int steps = (int) Math.round(style.stepsMost - (style.stepsMost - style.stepsLeast) * v);
			double onFeet = Math.max(style.feetMin, Math.min(style.feetMax, style.feetBase + style.feetPerStep * steps));
			if (weak >= 0) {
				onFeet = Math.max(style.feetMin, onFeet * style.woundedLegFeet);
			}
			double natural = muscles.delay[Muscles.LEGS]
					+ muscles.fade[Muscles.LEGS] * Math.log(Math.max(1.0, tone / style.doneTone));
			return new Stumble(style, onFeet, style.plannedVigor, weak, onFeet / Math.max(0.05, natural), steps, false);
		}
		if (tone <= style.doneTone) {
			return null;
		}
		double natural = muscles.delay[Muscles.LEGS] + muscles.fade[Muscles.LEGS] * Math.log(tone / style.doneTone);
		double onFeet = Math.max(style.feetMin, Math.min(style.feetMax, style.feetScale * natural));
		if (weak >= 0) {
			onFeet = Math.max(style.feetMin, onFeet * style.woundedLegFeet);
		}
		double vigor = Math.max(0.0, Math.min(1.0, tone / style.fullTone));
		return new Stumble(style, onFeet, vigor, weak, onFeet / Math.max(1.0e-3, natural), 0, false);
	}

	/**
	 * The beat a body whose strings were cut hangs for before it drops
	 * (Arcade), or null: a headshot, a crush or an overkill of a body that was
	 * awake and standing on something.
	 */
	private static Stumble hanging(StumbleStyle style, DeathKind kind, Muscles muscles, HumanoidRagdoll r) {
		if (style.hangFor <= 0.0 || !muscles.stringsCut || !muscles.conscious) {
			return null;
		}
		if (kind != DeathKind.BLOW && kind != DeathKind.PROJECTILE && kind != DeathKind.CRUSH) {
			return null;
		}
		if (!r.present(LEGS[0]) && !r.present(LEGS[1])) {
			return null;
		}
		return new Stumble(style, style.hangFor, 1.0, -1, 1.0, 0, true);
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
	 * @param fall   how the body falls when it simply falls (its muscles, its reactions, its launch): a
	 *               body that {@linkplain StumbleStyle#hangFor hangs} first is given it once the beat is
	 *               over. Null, and no body hangs.
	 */
	public static Stumble begin(StumbleStyle style, HumanoidRagdoll r, Behaviour.Input in, Vector3d sent,
			Vector3d seen, Vector3d hitDir, double kick, Runnable fall) {
		if (style == null || Math.hypot(sent.x, sent.z) > style.thrownAt || sent.y > style.thrownUp) {
			// Thrown: it has no feet to stay on.
			return null;
		}
		Stumble h = fall == null ? null : hanging(style, in.kind(), in.muscles(), r);
		if (h != null) {
			h.fall = fall;
			r.setMuscles(in.muscles());
			r.setController(h);
			return h;
		}
		Stumble s = plan(style, in.kind(), in.hitPart(), in.violence(), in.muscles(), r);
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
		Vector3d f = r.facing();
		faced.set(f.x, 0.0, f.z);
		if (faced.lengthSquared() < 1.0e-6) {
			faced.set(0.0, 0.0, -1.0);
		}
		faced.normalize();
		if (plannedSteps <= 0 && sent.lengthSquared() > seen.lengthSquared()) {
			r.push((sent.x - seen.x) * style.pushShare, (sent.y - seen.y) * style.liftShare,
					(sent.z - seen.z) * style.pushShare);
		}
		int hit = in.hitPart();
		if (hit >= 0 && hit < HumanoidRagdoll.PARTS && r.present(hit) && hitDir != null && kick > 0.0) {
			double k = kick * (hit == HumanoidRagdoll.TORSO ? style.torsoKick : style.limbKick);
			r.kick(hit, hitDir.x * k, hitDir.y * k, hitDir.z * k);
		}
		Vector3d swayDir = null;
		if (in.kind() == DeathKind.FADE && style.sway > 0.0) {
			// Nothing struck it: it sways, some way of its own, about its feet.
			double a = (Math.floorMod(in.seed() * 0x9E3779B9L, 4096L) / 4096.0) * Math.PI * 2.0;
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
			swayDir = dir;
		}
		if (plannedSteps > 0) {
			shove(r, in, seen, hitDir, swayDir);
		}
	}

	/**
	 * A planned stumble's hit: the body is sent off back along the shot (or
	 * the way it sways), keeping a little of the way it was going, and turned
	 * toward whoever shot it. Its finisher is picked here.
	 */
	private void shove(HumanoidRagdoll r, Behaviour.Input in, Vector3d seen, Vector3d hitDir, Vector3d swayDir) {
		Vector3d shot = new Vector3d();
		boolean struck = in.kind() != DeathKind.FADE;
		if (!struck && swayDir != null) {
			shot.set(swayDir.x, 0.0, swayDir.z);
		} else if (hitDir != null) {
			shot.set(hitDir.x, 0.0, hitDir.z);
		} else if (in.push() != null) {
			shot.set(in.push().x, 0.0, in.push().z);
		}
		boolean fromSomewhere = shot.lengthSquared() > 1.0e-6;
		if (!fromSomewhere) {
			// A blow from nowhere in particular: back, the way a blow sends anyone.
			shot.set(faced).negate();
		}
		shot.normalize();
		double v = Math.max(0.0, Math.min(1.0, in.violence()));
		double shove = struck ? style.shoveMin + (style.shoveMax - style.shoveMin) * v : style.fadeShove;
		// Less of it to the side: straight legs step back and forward far better than across.
		Vector3d side = new Vector3d(faced).cross(0.0, 1.0, 0.0);
		double across = shot.dot(side);
		Vector3d push = new Vector3d(shot).fma(across * (style.sideShove - 1.0), side);
		Vector3d was = new Vector3d(seen.x, 0.0, seen.z);
		notStagger = was.length() > MOVING ? 1 : 0;
		Vector3d want = new Vector3d(was).mul(style.keep).fma(shove, push);
		path.set(want.lengthSquared() > 0.09 ? want : shot).normalize();
		pathSpeed = Math.max(style.pathMin, Math.min(style.pathMax, want.length()));
		r.push(path.x * pathSpeed - was.x, 0.0, path.z * pathSpeed - was.z);

		if (struck && fromSomewhere) {
			toShooter.set(shot).negate();
			shooterAt = angle(faced, toShooter);
			double round = Math.abs(shooterAt);
			// Dead behind, which way it would turn is anyone's guess: its seed's.
			double sign = round > Math.PI - 1.0e-3 ? (bit(in.seed(), 1) ? 1.0 : -1.0) : Math.signum(shooterAt);
			double behind = round <= BEHIND ? 1.0 : Math.max(0.0, (Math.PI - round) / (Math.PI - BEHIND));
			turnTo = sign * Math.min(round, style.turnMost) * behind;
		}
		planned = pick(in.seed(), struck && fromSomewhere);
		if (planned == Finisher.FACE_PLANT) {
			// Going over forward, it looks where it is going.
			turnTo = 0.0;
		}
		if (planned == Finisher.SIT_DOWN) {
			plannedSteps = Math.min(plannedSteps, 2);
		} else if (planned == Finisher.LAST_LOOK) {
			plannedSteps = Math.min(plannedSteps, 3);
		}
		// About the same time on its feet each time: a step's worth short of the
		// planned time, and its finisher with the next foot it puts down.
		finishBy = Math.min(onFeet, Math.max(style.feetMin, style.feetBase + style.feetPerStep * plannedSteps));
		finishFrom = Math.min(finishBy, Math.max(style.feetMin, style.feetBase + style.feetPerStep * (plannedSteps - 1)));
	}

	/**
	 * Which finisher, from the way it is sent off against the way it faced:
	 * forward, a face-plant; to a side, a spin-drop; back, a sit-down or (shot
	 * by someone) a last look. Where two meet, either fits and the seed picks,
	 * so every client watching picks the same.
	 */
	private Finisher pick(int seed, boolean shooter) {
		double along = path.dot(faced);
		double band = style.finisherBand;
		boolean heads = bit(seed, 0);
		Finisher back = shooter && bit(seed, 2) ? Finisher.LAST_LOOK : Finisher.SIT_DOWN;
		if (along > 0.5 + band) {
			return Finisher.FACE_PLANT;
		}
		if (along > 0.5 - band) {
			return heads ? Finisher.FACE_PLANT : Finisher.SPIN_DROP;
		}
		if (along > -0.5 + band) {
			return Finisher.SPIN_DROP;
		}
		if (along > -0.5 - band) {
			return heads ? Finisher.SPIN_DROP : back;
		}
		return back;
	}

	/** One bit of a body's seed, well mixed. */
	private static boolean bit(int seed, int which) {
		long h = (seed * 0x2545F4914F6CDD1DL) ^ 0x5BD1E995L;
		h ^= h >>> 29;
		return ((h >>> (17 + which)) & 1L) != 0L;
	}

	/** The turn about +Y, radians, that takes {@code from} to {@code to} along the ground. */
	private static double angle(Vector3d from, Vector3d to) {
		return Math.atan2(from.z * to.x - from.x * to.z, from.x * to.x + from.z * to.z);
	}

	/** {@code dir} turned about +Y, along the ground. */
	private static Vector3d turned(Vector3d dir, double a) {
		double c = Math.cos(a);
		double s = Math.sin(a);
		return new Vector3d(dir.x * c + dir.z * s, 0.0, -dir.x * s + dir.z * c);
	}

	/** How far through its time on its feet it is, 0 to 1 (a planned stumble's own: until its finisher is due). */
	private double spent(double t) {
		double of = plannedSteps > 0 && finishBy > 0.0 ? finishBy : onFeet;
		return Math.max(0.0, Math.min(1.0, t / of));
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
		if (hang) {
			hold(r, t);
			return;
		}
		if (letGo) {
			stepper.settle(r, dt);
			if (sitting) {
				sit(r, t);
			}
			slump(r, world, t);
			return;
		}
		double x = spent(t);
		stepper.strength = strength(t);
		stepper.balance = balance(t);
		if (looking) {
			// Holding the look: as steady as it was at first.
			stepper.strength = vigor;
			stepper.balance = style.balanceStart;
		}
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
		if (plannedSteps > 0) {
			steer(r, t, dt);
		}
		stepper.apply(r, world, t, dt);
		boolean landed = stepper.steps() > stepsBefore;
		stepsBefore = stepper.steps();
		if (stepper.released()) {
			String lost = stepper.lost();
			if (plannedSteps > 0 && planned != null && ("down".equals(lost) || "over".equals(lost))) {
				// Gone a moment early on open ground: down the way it was going to go anyway.
				early = true;
				finish(r, t, planned);
				return;
			}
			// It lost its footing: it is already falling, the way it was going.
			down(r, t, lost, false);
			return;
		}
		if (plannedSteps > 0 && planned != null && !looking) {
			unplantedFor = stepper.planted(0) || stepper.planted(1) ? 0.0 : unplantedFor + dt;
			if (unplantedFor >= UNPLANTED_FOR) {
				early = true;
				finish(r, t, planned);
				return;
			}
		}
		if (weakLeg >= 0 && stepper.steps() >= style.limpSteps) {
			boolean alone = stepper.planted(weakLeg) && !stepper.planted(1 - weakLeg);
			aloneFor = alone ? aloneFor + dt : 0.0;
			if (aloneFor >= style.buckleAfter) {
				down(r, t, "its leg gave", true);
				return;
			}
		}
		if (plannedSteps > 0) {
			if (looking) {
				if (t - lookFrom >= style.lookFor) {
					finish(r, t, Finisher.LAST_LOOK);
				}
			} else if (landed && stagger() >= plannedSteps && t >= finishFrom || t >= finishBy) {
				if (planned == Finisher.LAST_LOOK) {
					looking = true;
					lookFrom = t;
					staggered = stagger();
				} else {
					finish(r, t, planned);
				}
			}
			return;
		}
		if (t >= onFeet) {
			down(r, t, "spent", true);
		}
	}

	/** A planned stumble, each tick: the turn toward the shooter, and the drive along its path. */
	private void steer(HumanoidRagdoll r, double t, double dt) {
		if (looking) {
			// All the way round to them.
			stepper.face(turned(faced, shooterAt));
			return;
		}
		if (turnTo != 0.0) {
			double u = Math.max(0.0, Math.min(1.0, t / Math.max(1.0e-3, style.turnFor)));
			stepper.face(turned(faced, turnTo * u * u * (3.0 - 2.0 * u)));
		}
		if (style.drive > 0.0) {
			// Along the way it faces, back or forward, as much as its path goes that way:
			// across, its feet cannot keep up.
			Vector3d front = stepper.started() ? stepper.facingNow() : new Vector3d(faced);
			double share = path.dot(front);
			Vector3d dir = front.mul(Math.signum(share));
			Balance.velocity(r, vel);
			double along = vel.x * dir.x + vel.z * dir.z;
			double want = pathSpeed * style.drive * Math.abs(share);
			if (along < want) {
				double dv = Math.min(want - along, style.driveAccel * dt);
				r.push(dir.x * dv, 0.0, dir.z * dv);
			}
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

	/** Stagger steps so far. */
	private int stagger() {
		return Math.max(0, stepper.steps() - notStagger);
	}

	/** A planned stumble's end. */
	private void finish(HumanoidRagdoll r, double t, Finisher f) {
		letGo = true;
		letGoAt = t;
		why = f.label;
		finisher = f;
		if (staggered < 0) {
			staggered = stagger();
		}
		Vector3d front = stepper.facingNow();
		stepper.release(r);
		double strength = style.fallStrengthLeft
				+ (style.fallStrength - style.fallStrengthLeft) * (1.0 - spent(t));
		Behaviour b = r.behaviour();
		switch (f) {
			case FACE_PLANT -> {
				// The last step gives, and over it goes; its hands come out late.
				r.setMuscles(r.muscles().legsGiving(t, style.giveTone));
				Vector3d feet = new Vector3d(r.tip(LEGS[0])).add(r.tip(LEGS[1])).mul(0.5);
				r.spin(new Vector3d(front.z, 0.0, -front.x).mul(style.faceTopple), feet);
				if (b != null) {
					b.fallsFrom(t + style.armsLate, strength);
				}
			}
			case SPIN_DROP -> {
				// On round the way it was turning, and down in a heap.
				r.setMuscles(r.muscles().crumpling(t));
				double sign = turnTo != 0.0 ? Math.signum(turnTo) : Math.signum(angle(faced, path));
				Vector3d com = Balance.centreOfMass(r, new Vector3d());
				r.spin(new Vector3d(0.0, (sign == 0.0 ? 1.0 : sign) * style.spinRate, 0.0), com);
				if (b != null) {
					b.fallsFrom(t, strength);
				}
			}
			case SIT_DOWN -> {
				// Down onto its seat ({@link #sit}), and only then back: its arms go as it tips.
				r.setMuscles(r.muscles().legsGiving(t, style.giveTone));
				sitting = true;
				if (b != null) {
					b.fallsFrom(t + style.sitFor, strength);
				}
			}
			case LAST_LOOK -> {
				// Down where it stands, a little toward them.
				r.setMuscles(r.muscles().crumpling(t));
				r.push(toShooter.x * style.lookNudge, 0.0, toShooter.z * style.lookNudge);
				if (b != null) {
					b.fallsFrom(t, strength);
				}
			}
		}
		r.wake();
	}

	/**
	 * Sitting down hard on legs with no knees: they go out in front, the feet
	 * sliding, while the back is held up; then it tips back.
	 */
	private void sit(HumanoidRagdoll r, double t) {
		double since = t - letGoAt;
		if (since < style.sitFor) {
			for (int k = 0; k < 2; k++) {
				r.body(LEGS[k]).friction = style.sitGrip;
				r.target(LEGS[k], -style.sitFold, k == 0 ? 0.05 : -0.05, 0.0);
				r.reflex(LEGS[k], style.sitTone);
			}
			r.holdUpright(style.sitUpright);
			return;
		}
		if (!tipped) {
			tipped = true;
			Vector3d back = stepper.facingNow().negate();
			Vector3d hips = r.joint(LEGS[0], new Vector3d()).add(r.joint(LEGS[1], new Vector3d())).mul(0.5);
			r.spin(new Vector3d(back.z, 0.0, -back.x).mul(style.sitTip), hips);
			r.wake();
		}
	}

	/** The beat: it hangs where it was hit, a moment, before anything gives; then it drops as it would have. */
	private void hold(HumanoidRagdoll r, double t) {
		if (letGo) {
			return;
		}
		if (t < onFeet) {
			r.holdUpright(style.hangHold);
			r.support(style.hangSupport);
			for (int p = 0; p < HumanoidRagdoll.PARTS; p++) {
				if (p != HumanoidRagdoll.HEAD && p != HumanoidRagdoll.TORSO && r.present(p)) {
					r.setTone(p, style.hangTone);
				}
			}
			return;
		}
		letGo = true;
		letGoAt = t;
		why = "hang";
		if (fall != null) {
			fall.run();
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
		if (hang) {
			return false;
		}
		// Until it is known whether it went down, and while it has not.
		return letGo && (sinceLetGo < style.slumpAfter || propped || sitting && !tipped);
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
	 * Why it went down ("spent", "its leg gave", a {@linkplain Finisher#label
	 * finisher's name}, "hang", or how the stepper {@linkplain Stepper#lost()
	 * lost its footing}), or null while it is still on its feet.
	 */
	public String why() {
		return why;
	}

	/** Whether this is only the beat a body whose strings were cut hangs for. */
	public boolean hang() {
		return hang;
	}

	/** Steps a planned stumble takes before its finisher; 0 for none planned. */
	public int plannedSteps() {
		return plannedSteps;
	}

	/** The finisher a planned stumble was given, or null. */
	public Finisher planned() {
		return planned;
	}

	/** The finisher it went down with, or null (not yet, none planned, or it went down some other way). */
	public Finisher finisher() {
		return finisher;
	}

	/** How far it turns toward whoever shot it, radians (signed about +Y); 0 for no turn. */
	public double turnTo() {
		return turnTo;
	}

	/** The way it is kept going, along the ground, unit (zero with none planned). */
	public Vector3d path() {
		return new Vector3d(path);
	}

	/**
	 * The steps it staggered before its finisher (or its last look) began, or
	 * -1 before it has. A body that died walking first puts down the foot it
	 * had in the air: that is not one of them.
	 */
	public int staggered() {
		return staggered;
	}

	/** Whether its finisher came early, because it lost its footing on open ground first. */
	public boolean early() {
		return early;
	}

	/** Whether it is holding its last look. */
	public boolean looking() {
		return looking;
	}
}
