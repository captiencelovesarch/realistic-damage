package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.damage.DeathKind;

/**
 * How the dead body's view moves, by kind of death (1.7).
 *
 * <p>The first death camera had one collapse for everything: the eye sank over
 * three-quarters of a second, the view rolled 32 degrees and sagged ten, and
 * then nothing moved again. The playtest asked for two things that turn out to
 * be one: more motion, and motion that depends on how you died. A fifty-block
 * drop is on the floor before you know it; lava is a slow sink with your face
 * turned up at the light while you thrash; a blast throws you end over end; a
 * bleed-out is a faint, a sway and a slow fold. And whatever the death, the
 * body keeps settling for a while after it lands — a head rolls the last few
 * degrees onto its side, a nerve fires once — so the frame never goes still
 * the moment the fall ends, which is what read as the game freezing.
 *
 * <p>Pure and Minecraft-free like {@link Cardio} and {@link BloodFlow}: the
 * profiles and curves are unit-tested here, and the client's {@code
 * DeathCamera} only integrates them. Deterministic throughout — no random
 * source anywhere; which side you fall to is the knockback's choice, and every
 * oscillation has a fixed phase.
 */
public final class DeathMotion {
	private DeathMotion() {}

	/**
	 * One kind of death's motion.
	 *
	 * @param collapseTicks  ticks for the eye to reach the floor
	 * @param easePower      the collapse curve's exponent: above 1 holds and then
	 *                       goes (knees buckle, a faint), below 1 slams down at once
	 * @param roll           final roll onto the side, degrees
	 * @param pitch          final pitch, degrees; positive looks down at the floor,
	 *                       negative tips the head back
	 * @param eye            final eye height above the feet, blocks
	 * @param thud           strength of the landing jolt, 1 = a normal fall
	 * @param tumble         spin while airborne, degrees a tick
	 * @param writhe         amplitude of the thrash or convulsion, degrees
	 * @param writheHz       its frequency, cycles a second
	 * @param writheTicks    how long it takes to die away, ticks
	 * @param jerk           head snap at the moment of death, degrees of pitch
	 *                       (negative: thrown back)
	 * @param loll           roll still to come after the body lands, degrees
	 * @param lollTicks      how long that last settle takes, ticks
	 * @param twitches       agonal twitches after landing, 0 to 2
	 * @param impactAtDeath  the landing already happened: thud on the first tick
	 */
	public record Profile(double collapseTicks, double easePower, double roll, double pitch, double eye,
			double thud, double tumble, double writhe, double writheHz, double writheTicks, double jerk,
			double loll, double lollTicks, int twitches, boolean impactAtDeath) {}

	/** Eye height of a body lying on the ground, blocks; the old constant. */
	public static final double LYING_EYE = 0.3;

	/**
	 * The motion for one death. Violence scales what a harder death would make
	 * bigger — how far you are thrown over, how fast you spin, how hard you land
	 * — and leaves alone what it would not: a drowning is not more violent for
	 * having been quick.
	 */
	public static Profile profile(DeathKind kind, double violence) {
		double v = Math.clamp(violence, 0.0, 1.0);
		double hard = 0.75 + 0.5 * v;
		int twitches = v >= 0.7 ? 2 : v >= 0.35 ? 1 : 0;
		return switch (kind) {
			// The old collapse, kept as the baseline: knees, then everything.
			case BLOW -> new Profile(15, 2.0, 32 * hard, 10, LYING_EYE,
					1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 9, 60, twitches, false);
			// Snapped back by the hit, then folded forward onto the face.
			case PROJECTILE -> new Profile(17, 2.2, 14 * hard, 42, LYING_EYE,
					1.0, 0.0, 0.0, 0.0, 0.0, -14 * hard, 6, 60, twitches, false);
			// Already on the ground, very fast: the slam is the first tick.
			case FALL -> new Profile(4, 0.6, 30 + 28 * v, 18, LYING_EYE,
					0.6 + 0.9 * v, 0.0, 2.5 * v, 6.0, 10, 0.0, 10, 70, twitches, true);
			// Thrown and spinning; lands hard and well over.
			case EXPLOSION -> new Profile(10, 1.2, 58 * hard, 22, LYING_EYE,
					1.3, 7.0 + 11.0 * v, 0.0, 0.0, 0.0, -10 * hard, 8, 60, Math.max(1, twitches), false);
			// Pressed down onto your face.
			case CRUSH -> new Profile(5, 0.6, 16, 38, 0.22,
					1.4, 0.0, 1.5, 7.0, 8, 0.0, 4, 50, twitches, true);
			// Thrashing while you go down, and for a moment after.
			case FIRE -> new Profile(22, 1.6, 40, 14, LYING_EYE,
					0.8, 0.0, 9.0, 2.2, 45, 0.0, 8, 70, 0, false);
			// Sinking face-up below the surface, thrashing less and less.
			case LAVA -> new Profile(26, 1.4, 22, -32, 0.08,
					0.0, 0.0, 8.0, 1.8, 50, 0.0, 6, 80, 0, false);
			// Weightless: a slow roll and a drift that never quite stops.
			case DROWNING -> new Profile(34, 1.5, 20, -18, LYING_EYE,
					0.0, 0.0, 3.5, 0.25, 600, 0.0, 10, 120, 0, false);
			// Stiff: holds, then goes over all at once like a plank.
			case FREEZE -> new Profile(28, 3.2, 74, 2, LYING_EYE,
					1.3, 0.0, 1.2, 9.0, 60, 0.0, 2, 40, 0, false);
			// The convulsion first, then the fall.
			case SHOCK -> new Profile(16, 1.8, 40, 12, LYING_EYE,
					1.0, 0.0, 7.0, 11.0, 22, -8, 8, 60, 2, false);
			// Falling into the dark, turning slowly.
			case VOID -> new Profile(30, 1.5, 24, 8, LYING_EYE,
					0.0, 3.5, 0.0, 0.0, 0.0, 0.0, 0, 1, 0, false);
			// A faint: a sway on the spot, then a slow fold, then a soft landing.
			case FADE -> new Profile(36, 2.6, 26, 14, LYING_EYE,
					0.4, 0.0, 4.0, 0.45, 40, 0.0, 12, 90, 0, false);
		};
	}

	/** How far down the collapse is at {@code t} ticks since death, 0 to 1. */
	public static double collapse(Profile p, double t) {
		double x = Math.clamp(t / p.collapseTicks(), 0.0, 1.0);
		return Math.pow(x, p.easePower());
	}

	/** Smoothstep on top of the collapse, so a roll lands rather than stops. */
	public static double settle(double collapse) {
		double c = Math.clamp(collapse, 0.0, 1.0);
		return c * c * (3.0 - 2.0 * c);
	}

	/**
	 * The thrash or convulsion at {@code t} ticks, degrees, as a roll and a pitch.
	 * Two incommensurate sines so it never visibly repeats, fading out over
	 * {@code writheTicks}. Fixed phases: identical deaths writhe identically.
	 */
	public static double writheRoll(Profile p, double t) {
		if (p.writhe() <= 0.0 || t >= p.writheTicks()) {
			return 0.0;
		}
		double w = 2.0 * Math.PI * p.writheHz() / 20.0;
		return p.writhe() * envelope(p, t) * (0.7 * Math.sin(w * t) + 0.3 * Math.sin(w * 1.618 * t + 1.1));
	}

	public static double writhePitch(Profile p, double t) {
		if (p.writhe() <= 0.0 || t >= p.writheTicks()) {
			return 0.0;
		}
		double w = 2.0 * Math.PI * p.writheHz() / 20.0;
		return 0.6 * p.writhe() * envelope(p, t) * Math.sin(w * 0.77 * t + 2.3);
	}

	private static double envelope(Profile p, double t) {
		double x = Math.clamp(t / p.writheTicks(), 0.0, 1.0);
		return (1.0 - x) * (1.0 - x);
	}

	/** The head snap at death, degrees of pitch: there at once, gone in under half a second. */
	public static double jerk(Profile p, double t) {
		if (p.jerk() == 0.0 || t < 0.0) {
			return 0.0;
		}
		return p.jerk() * Math.exp(-t / 3.0) * Math.min(1.0, t / 1.0 + 0.35);
	}

	/**
	 * How much of the after-landing settle has happened, {@code t} ticks after the
	 * body came to rest: ease-out, so the head goes most of the way at once and
	 * creeps the rest.
	 */
	public static double loll(Profile p, double t) {
		if (t <= 0.0) {
			return 0.0;
		}
		double x = Math.clamp(t / p.lollTicks(), 0.0, 1.0);
		return 1.0 - (1.0 - x) * (1.0 - x) * (1.0 - x);
	}

	/** Ticks after landing at which an agonal twitch fires; fixed, not drawn. */
	public static final int[] TWITCH_TICKS = {9, 26};

	/**
	 * Where an airborne tumble comes to rest once the body lands: the nearest
	 * whole turn, so a spin that ended upside down rolls on round to upright
	 * rather than unwinding back through everything it just did.
	 */
	public static double restingTumble(double angle) {
		return Math.rint(angle / 360.0) * 360.0;
	}
}
