package dev.captience.realisticdamage.ragdoll;

/**
 * How a dying body stumbles (stumbling deaths, IK phases 1 and 2): every
 * tuning number of a preset in one table.
 *
 * <p>{@link #grounded()} is the physical one: a hit jolts the part it struck
 * and hardly moves the body, so only a body that was already going somewhere
 * has anything to catch; the hold on its torso is weak and goes with its
 * legs; its steps shorten and slow as it weakens, and its feet slip late on.
 *
 * <p>{@link #arcade()} is the cinematic one: the hit shoves the body back
 * along the shot and turns it toward the shooter; it takes a planned number
 * of long, clean steps (the heavier the blow, the fewer), held up firmly
 * until the last, and goes down with a finisher picked from how it was hit
 * ({@link Stumble.Finisher}). A body whose strings were cut hangs a beat
 * before it drops. Everything Arcade adds is off in Grounded.
 *
 * <p>The fields are public and mutable so the trace tool can sweep them; the
 * game only ever uses a preset as it comes.
 */
public final class StumbleStyle {
	public final String name;

	// --- Who stumbles ---------------------------------------------------------

	/** A body with less of a mind left than this does not stumble: it is not there to try. */
	public double minAwake = 0.5;

	/**
	 * A body the server launched faster than this, blocks a second along the
	 * ground or upward, was thrown, and falls as a thrown body does. (A plain
	 * hit's knockback is 8 each way.)
	 */
	public double thrownAt = 16.0;
	public double thrownUp = 12.0;

	// --- How long ---------------------------------------------------------------

	/**
	 * Seconds on its feet, as a multiple of how long the legs' own muscles
	 * would take to fade to {@link #doneTone}.
	 */
	public double feetScale = 2.0;
	public double feetMin = 0.3;
	public double feetMax = 2.0;
	/** Leg tone below which a leg no longer holds a body up. */
	public double doneTone = 0.5;
	/** Leg tone of a body dying at full strength: what {@link Stepper#strength} 1 stands for. */
	public double fullTone = 2.4;

	// --- The hit ------------------------------------------------------------------

	/** How much of the knockback the server gave the body it keeps, along the ground and up. */
	public double pushShare = 0.05;
	public double liftShare = 0.0;
	/** How much of the blow's kick the struck part keeps: the torso (most of the body's weight), and a limb or the head. */
	public double torsoKick = 0.2;
	public double limbKick = 1.0;
	/** A body that only fades (a bleed-out) sways this fast at the head, blocks a second: the one thing it has to catch. */
	public double sway = 1.1;

	// --- Weakening ------------------------------------------------------------------

	/** Its balance to begin with, when it starts to go and when it has gone (shares of its time on its feet), and what is left. */
	public double balanceStart = 1.0;
	public double balanceFrom = 0.0;
	public double balanceGone = 1.0;
	public double balanceLeft = 0.04;
	/** When its strength starts to go, a share of its time on its feet, and what is left at the end. */
	public double strengthFrom = 0.15;
	public double strengthLeft = 0.3;
	/**
	 * How far forward of upright its torso is held, radians, at first and at
	 * the end of its time on its feet: it hunches over, and folds as it goes.
	 */
	public double hunch = 0.1;
	public double hunchLate = 0.35;
	/** How long its last steps are, a share of its first. */
	public double reachLeft = 0.5;
	/** How much longer its last steps take: 0.5 is half as long again. */
	public double slowBy = 0.5;
	/** When its feet start to lose their grip, a share of its time on its feet, and what grip is left at the end. */
	public double gripFrom = 0.45;
	public double gripLeft = 0.55;

	// --- A wounded leg -----------------------------------------------------------------

	/** A wounded leg's strength, a share of a sound one's. */
	public double woundedLeg = 0.4;
	/** A body with a wounded leg has this share of the time on its feet. */
	public double woundedLegFeet = 0.6;
	/** Seconds a wounded leg holds the body alone before it gives. */
	public double buckleAfter = 0.1;
	/** It goes down toward the wounded leg, this fast, blocks a second. */
	public double buckleShove = 0.9;

	// --- Going down ---------------------------------------------------------------------

	/** The most tone its legs give way from. */
	public double giveTone = 1.2;
	/** How much it has left to break its fall with, at most and at least. */
	public double fallStrength = 0.6;
	public double fallStrengthLeft = 0.25;
	/** A body let go of standing still is tipped the way it faces, this fast, blocks a second. */
	public double nudge = 0.6;
	/**
	 * A body still upright this long after it was let go of is propped on
	 * something (a wall, a step, the side of a hole). For at most this long
	 * its feet lose their grip, to this, and it folds at the hips, this far
	 * (radians) and this hard, until it is down.
	 */
	public double slumpAfter = 0.6;
	public double slumpFor = 4.0;
	public double slumpGrip = 0.05;
	public double slumpFold = 1.2;
	public double slumpTone = 1.5;

	// --- The stepper ----------------------------------------------------------------------

	public double uprightHold = 1.0;
	public double facingHold = 0.5;
	public double support = 0.45;
	public double swingTone = 7.0;
	public double brakeTone = 2.5;
	public double brakeGive = 0.12;
	public double standTone = 3.5;
	public double toeOff = 0.3;
	public double armTone = 0.45;
	public double armToneUp = 1.0;
	public double armRaise = 0.55;
	public double flailAt = 4.5;
	public double swingMin = 0.16;
	public double swingMax = 0.34;
	public double quickAt = 2.5;
	public double reachForward = 0.5;
	public double reachBack = 0.27;
	public double reachOut = 0.34;
	public double circle = 0.0;
	public double overshoot = -0.15;
	public double lead = 0.35;
	public double hipBack = 1.3;
	public double carry = 0.6;
	public double flight = 1.2;
	public double hipDrop = 0.04;
	public double rakedFoot = 1.0;
	/** {@link Stepper#liftClear}: zero scuffs (Grounded). */
	public double liftClear = 0.0;
	/** {@link Stepper#stanceMin}: zero steps again at once (Grounded). */
	public double stanceMin = 0.0;
	/** {@link Stepper#holdAlone}, above zero: a foot left behind holds on while it is the only one down. */
	public double holdAlone = 0.0;
	/** The neck's tone while it is on its feet, held by the stepper; zero leaves the head to its own muscles. */
	public double headHold = 0.0;

	// --- A planned stumble (Arcade; all off in Grounded) ------------------------------

	/**
	 * Steps a planned stumble takes before its finisher, for the lightest
	 * blow and the heaviest (the heavier, the fewer). Zero leaves it all to
	 * the muscles, as Grounded does.
	 */
	public double stepsMost = 0.0;
	public double stepsLeast = 0.0;
	/**
	 * A planned stumble's time on its feet, at most: this, and this much more
	 * for each step. Its finisher comes with the first foot it puts down a
	 * step's worth before that (and no sooner than {@link #feetMin}).
	 */
	public double feetBase = 0.0;
	public double feetPerStep = 0.0;
	/** A planned stumble's strength, 1 a living body's, however its muscles were. */
	public double plannedVigor = 1.0;
	/** The stagger shove along the shot, blocks a second, for the lightest blow and the heaviest. */
	public double shoveMin = 0.0;
	public double shoveMax = 0.0;
	/** A bleed-out's shove, the way it sways, blocks a second. */
	public double fadeShove = 0.0;
	/** How much of the way it was seen going it keeps under the shove. */
	public double keep = 1.0;
	/** How much of the shove's part across the way it faces it keeps: these legs step across badly. */
	public double sideShove = 1.0;
	/** The slowest and the fastest it sets off along its path, blocks a second. */
	public double pathMin = 0.0;
	public double pathMax = 0.0;
	/**
	 * Until its finisher it is kept going along its path at no less than
	 * this share of the speed it set off at, gaining at most
	 * {@link #driveAccel} blocks a second each second. Zero for none.
	 */
	public double drive = 0.0;
	public double driveAccel = 0.0;
	/** The furthest it turns toward where the shot came from, radians, and how long the turn takes, seconds. */
	public double turnMost = 0.0;
	public double turnFor = 0.5;
	/** Steps a wounded leg carries before it can give: zero, it gives the first time it is stood on alone. */
	public double limpSteps = 0.0;
	/**
	 * Within this much (a cosine) of where two finishers meet, either fits
	 * and the body's own seed picks.
	 */
	public double finisherBand = 0.15;

	// --- The finishers (Arcade) -----------------------------------------------------------

	/** Face-plant: it pitches over forward this fast, radians a second, and its arms go out this late, seconds. */
	public double faceTopple = 1.3;
	public double armsLate = 0.2;
	/** Spin-drop: it turns on as it crumples, this fast, radians a second. */
	public double spinRate = 3.0;
	/**
	 * Sit-down: for this long, seconds, its legs go out in front (this far at
	 * the hips, radians, this hard) on feet that slide (this grip), its back
	 * held up this much; then it tips back, this fast, radians a second.
	 */
	public double sitFor = 0.5;
	public double sitFold = 1.3;
	public double sitTone = 2.5;
	public double sitGrip = 0.05;
	public double sitUpright = 0.6;
	public double sitTip = 1.2;
	/** Last look: it holds the look this long, seconds, and drops toward the shooter this fast, blocks a second. */
	public double lookFor = 0.4;
	public double lookNudge = 0.3;

	// --- The headshot hang (Arcade) ---------------------------------------------------------

	/**
	 * A body whose strings were cut (a headshot, a crush, an overkill) hangs
	 * this long, seconds, before it drops (the film beat): its torso held up
	 * this hard, this share of its weight carried, its limbs this stiff. Zero
	 * drops it at once.
	 */
	public double hangFor = 0.0;
	public double hangHold = 2.0;
	public double hangSupport = 0.8;
	public double hangTone = 5.0;

	private StumbleStyle(String name) {
		this.name = name;
	}

	/** Physical, varied, subdued. */
	public static StumbleStyle grounded() {
		return new StumbleStyle("grounded");
	}

	/** Readable, cinematic, consistent. */
	public static StumbleStyle arcade() {
		StumbleStyle s = new StumbleStyle("arcade");
		// Nearly every conscious death by a blow or an arrow, however hard.
		s.minAwake = 0.2;
		s.feetMin = 1.0;
		s.feetMax = 2.4;
		s.stepsMost = 4.0;
		s.stepsLeast = 2.0;
		s.feetBase = 0.5;
		s.feetPerStep = 0.42;
		s.plannedVigor = 1.0;
		// The hit: shoved back along the shot, and turned toward it.
		s.shoveMin = 1.5;
		s.shoveMax = 2.5;
		s.fadeShove = 1.1;
		s.keep = 0.35;
		s.sideShove = 0.3;
		s.pathMin = 1.2;
		s.pathMax = 2.2;
		s.drive = 0.45;
		s.driveAccel = 6.0;
		s.turnMost = Math.toRadians(60.0);
		s.turnFor = 0.35;
		s.torsoKick = 0.35;
		// Held up firmly, until the last step.
		s.balanceFrom = 0.75;
		s.balanceGone = 1.0;
		s.balanceLeft = 0.15;
		s.strengthFrom = 0.7;
		s.strengthLeft = 0.85;
		s.hunch = 0.05;
		s.hunchLate = 0.15;
		s.reachLeft = 0.85;
		s.slowBy = 0.15;
		s.gripFrom = 1.0;
		s.gripLeft = 1.0;
		// A wounded leg limps a couple of steps, then goes.
		s.woundedLeg = 0.6;
		s.woundedLegFeet = 1.0;
		s.limpSteps = 2.0;
		s.buckleShove = 1.0;
		s.giveTone = 1.0;
		s.fallStrength = 0.8;
		s.fallStrengthLeft = 0.5;
		// Long, clean steps that lift clear, slightly slow.
		s.uprightHold = 1.5;
		s.facingHold = 1.0;
		s.support = 0.7;
		s.brakeTone = 2.0;
		s.toeOff = 0.45;
		s.armRaise = 0.7;
		s.flailAt = 4.0;
		s.swingMin = 0.18;
		s.swingMax = 0.48;
		s.quickAt = 3.0;
		s.flight = 0.0;
		s.stanceMin = 0.22;
		s.holdAlone = 1.0;
		s.reachForward = 0.55;
		s.reachBack = 0.45;
		s.circle = 0.02;
		s.overshoot = -0.2;
		s.liftClear = 0.8;
		s.headHold = 1.6;
		s.hangFor = 0.15;
		return s;
	}

	/** A preset by its name, or null for none ("off", or one this version does not have). */
	public static StumbleStyle byName(String name) {
		if (name == null) {
			return null;
		}
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "grounded" -> grounded();
			case "arcade" -> arcade();
			default -> null;
		};
	}

	/** Sets a stepper up the way this style steps, before anything has weakened. */
	void applyTo(Stepper s) {
		s.uprightHold = uprightHold;
		s.facingHold = facingHold;
		s.support = support;
		s.swingTone = swingTone;
		s.brakeTone = brakeTone;
		s.brakeGive = brakeGive;
		s.standTone = standTone;
		s.toeOff = toeOff;
		s.armTone = armTone;
		s.armToneUp = armToneUp;
		s.armRaise = armRaise;
		s.flailAt = flailAt;
		s.swingMin = swingMin;
		s.swingMax = swingMax;
		s.quickAt = quickAt;
		s.reachForward = reachForward;
		s.reachBack = reachBack;
		s.reachOut = reachOut;
		s.circle = circle;
		s.overshoot = overshoot;
		s.lead = lead;
		s.hipBack = hipBack;
		s.carry = carry;
		s.flight = flight;
		s.hipDrop = hipDrop;
		s.rakedFoot = rakedFoot;
		s.liftClear = liftClear;
		s.stanceMin = stanceMin;
		s.holdAlone = holdAlone > 0.0;
		s.holdHead = headHold > 0.0;
		if (headHold > 0.0) {
			s.neckTone = headHold;
		}
	}
}
