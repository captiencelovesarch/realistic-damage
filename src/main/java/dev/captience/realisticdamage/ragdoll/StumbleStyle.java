package dev.captience.realisticdamage.ragdoll;

/**
 * How a dying body stumbles (stumbling deaths, IK phase 1): every tuning
 * number of a preset in one table.
 *
 * <p>{@link #grounded()} is the physical one: a hit jolts the part it struck
 * and hardly moves the body, so only a body that was already going somewhere
 * has anything to catch; the hold on its torso is weak and goes with its
 * legs; its steps shorten and slow as it weakens, and its feet slip late on.
 * (Arcade arrives in phase 2.)
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

	private StumbleStyle(String name) {
		this.name = name;
	}

	/** Physical, varied, subdued. */
	public static StumbleStyle grounded() {
		return new StumbleStyle("grounded");
	}

	/** A preset by its name, or null for none ("off", or one this version does not have). */
	public static StumbleStyle byName(String name) {
		if (name == null) {
			return null;
		}
		return switch (name.toLowerCase(java.util.Locale.ROOT)) {
			case "grounded" -> grounded();
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
		s.holdHead = false;
	}
}
