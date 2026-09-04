package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;

/**
 * How much blood you can see, derived from how much you are actually losing.
 *
 * <p>Every rate here is a function of a wound's real Blood/sec, so the visual is
 * a readout rather than a decoration: a wound that is twice as bad drips twice
 * as hard, and the moment it crosses into arterial it stops dripping and starts
 * throwing. Before 1.5 bleeding was four redstone particles every 25 ticks
 * regardless of severity, which is why it was impossible to tell a scratch from
 * a severed artery — or, in first person, to tell either from nothing at all.
 *
 * <p>Pure and free of Minecraft types on purpose: the client emitter, the server
 * emitter and the tests all have to agree exactly, and the only way to guarantee
 * that is for there to be one copy of the arithmetic.
 */
public final class BloodFlow {
	private BloodFlow() {}

	/**
	 * Visible drops per second for a wound losing {@code bloodPerSecond}.
	 *
	 * <p>Clamped at both ends. The floor exists because a MINOR wound's true rate
	 * rounds to almost nothing and a mod that says nothing about a wound might as
	 * well not have given you one; the ceiling exists because at some point more
	 * particles stop carrying more information and start costing frames.
	 */
	public static double dropsPerSecond(double bloodPerSecond) {
		if (bloodPerSecond <= 0.0) {
			return 0.0;
		}
		return Math.clamp(bloodPerSecond * Balance.DROPS_PER_BLOOD_POINT,
				Balance.DROPS_PER_SECOND_MIN, Balance.DROPS_PER_SECOND_MAX);
	}

	/**
	 * Whether a wound is under enough pressure to spurt rather than seep. Set
	 * between MODERATE and SEVERE so the arterial read arrives exactly when the
	 * wound becomes the thing that is going to kill you, and not before.
	 */
	public static boolean arterial(double bloodPerSecond) {
		return bloodPerSecond >= Balance.ARTERIAL_RATE;
	}

	/**
	 * The share of an arterial wound's output held back for the next beat. The
	 * remainder still seeps between beats — a spurting wound does not politely
	 * stop bleeding in diastole.
	 */
	public static double spurtShare(double bloodPerSecond) {
		return arterial(bloodPerSecond) ? Balance.ARTERIAL_SPURT_SHARE : 0.0;
	}

	/** Launch speed in blocks per second for a drop leaving a wound. */
	public static double dropSpeed(double bloodPerSecond, boolean onBeat) {
		if (!onBeat || !arterial(bloodPerSecond)) {
			return Balance.DROP_SEEP_SPEED;
		}
		// A worse artery throws further, but the relationship flattens quickly:
		// past a point it is all pressure and the difference stops reading.
		double excess = Math.clamp((bloodPerSecond - Balance.ARTERIAL_RATE)
				/ Math.max(1.0e-6, Balance.BLEED_RATE_CRITICAL - Balance.ARTERIAL_RATE), 0.0, 1.0);
		return Balance.ARTERIAL_SPURT_SPEED * (0.72 + 0.28 * Math.sqrt(excess));
	}

	/**
	 * How large a drop reads, 0 (bead) to 1 (gout), from the speed it is travelling
	 * at in blocks per tick. This is the whole of the severity channel: velocity is
	 * already carried by every vanilla particle spawn, so a spurt looks like a
	 * spurt on someone else's screen without a custom payload to describe it.
	 */
	public static double dropScale(double blocksPerTick) {
		double perSecond = Math.abs(blocksPerTick) * 20.0;
		return Math.clamp(perSecond / Balance.DROP_SCALE_FULL_SPEED, 0.0, 1.0);
	}

	/**
	 * Radius of the mark a <em>single</em> drop leaves, in blocks, from its
	 * {@link #dropScale}. Bounded by {@link Balance#STAIN_DROP_SIZE_MAX} and not by
	 * the pool ceiling: what one drop can mark and what many drops can build up to
	 * are different questions, and answering both with one number is what made a
	 * fed pool the same size as one splat.
	 */
	public static double stainSize(double dropScale) {
		return Balance.STAIN_DROP_SIZE_MIN
				+ (Balance.STAIN_DROP_SIZE_MAX - Balance.STAIN_DROP_SIZE_MIN) * Math.clamp(dropScale, 0.0, 1.0);
	}

	/**
	 * Drops in the one-shot spray that opens a wound. Severance uses the same
	 * blood and the same transport, but enough of it to leave a physical record
	 * where the limb was lost rather than letting the model vanish between two
	 * frames with only an action-bar sentence behind it.
	 */
	public static int burstDrops(int severityOrdinal, boolean severance) {
		int ordinary = Balance.SPRAY_DROPS_PER_SEVERITY * (1 + Math.max(0, severityOrdinal));
		return severance ? (int) Math.round(ordinary * Balance.SEVERANCE_SPRAY_SCALE) : ordinary;
	}

	/** Deterministic yaw of one drop in a spray cone, in radians. */
	public static double burstYaw(long index, double spreadDegrees) {
		return Math.toRadians(jitterSigned(index, 31) * spreadDegrees);
	}

	/** Deterministic pitch of one drop in a spray cone, in radians. */
	public static double burstPitch(long index, double spreadDegrees) {
		return Math.toRadians(jitterSigned(index, 32) * spreadDegrees);
	}

	/** Deterministic speed multiplier for one drop in a spray cone. */
	public static double burstSpeed(long index) {
		return 0.55 + 0.45 * jitter(index, 33);
	}

	/**
	 * Marks transferred per second while a bleeding region is pressed against a
	 * wall. This is deliberately a share of the already-bounded visible flow: a
	 * scratch leaves an occasional touch, while an artery paints the surface, and
	 * neither can create an unbounded second emitter.
	 */
	public static double contactTransfersPerSecond(double bloodPerSecond) {
		return dropsPerSecond(bloodPerSecond) * Balance.CONTACT_TRANSFER_SHARE;
	}

	/**
	 * How quickly a sufficiently large wet wall mark sheds drops. A phase rate,
	 * not a changing elapsed-time deadline: a drying mark may slow, but the event
	 * it has already accumulated toward never moves away from it.
	 */
	public static double wallRunDropsPerSecond(double radius, double wetness) {
		if (radius < Balance.STAIN_WALL_RUN_RADIUS || wetness <= 0.0) {
			return 0.0;
		}
		double size = Math.clamp((radius - Balance.STAIN_WALL_RUN_RADIUS)
				/ Math.max(1.0e-9, Balance.STAIN_POOL_SIZE_MAX - Balance.STAIN_WALL_RUN_RADIUS), 0.0, 1.0);
		return Balance.STAIN_WALL_RUN_DROPS_PER_SECOND * Math.clamp(wetness, 0.0, 1.0)
				* (0.35 + 0.65 * size);
	}

	/**
	 * How wet a stain still looks, 1 (fresh) to 0 (dry), over its life. Drives the
	 * colour shift from arterial red to the brown of something that happened a
	 * while ago — which is what turns a trail into information about *when*.
	 */
	public static double stainWetness(double ageFraction) {
		double dryStart = Balance.STAIN_WET_FRACTION;
		double dryEnd = 1.0 - Balance.STAIN_FADE_FRACTION;
		if (ageFraction <= dryStart) {
			return 1.0;
		}
		if (ageFraction >= dryEnd) {
			return 0.0;
		}
		return 1.0 - (ageFraction - dryStart) / (dryEnd - dryStart);
	}

	/** Opacity of a stain over its life: solid until the last stretch, then gone. */
	public static double stainAlpha(double ageFraction) {
		if (ageFraction >= 1.0) {
			return 0.0;
		}
		double fadeStart = 1.0 - Balance.STAIN_FADE_FRACTION;
		if (ageFraction <= fadeStart) {
			return 1.0;
		}
		return 1.0 - (ageFraction - fadeStart) / Balance.STAIN_FADE_FRACTION;
	}

	/**
	 * Droplets landing on the camera per second, given total flow weighted by
	 * where it is coming from (see {@link Balance#LENS_HEAD_WEIGHT}).
	 */
	public static double lensDropsPerSecond(double weightedBloodPerSecond) {
		if (weightedBloodPerSecond <= 0.0) {
			return 0.0;
		}
		return weightedBloodPerSecond * Balance.LENS_DROPS_PER_BLOOD_POINT;
	}

	/** How much a wound on this region contributes to blood reaching your eyes. */
	public static double lensWeight(BodyPart part) {
		return part == BodyPart.HEAD ? Balance.LENS_HEAD_WEIGHT : Balance.LENS_BODY_WEIGHT;
	}

	/**
	 * One step of a fractional-drop accumulator: given the carry from last tick and
	 * a rate, returns how many whole drops to emit now. The leftover is read back
	 * with {@link #carryAfter}.
	 *
	 * <p>Shared rather than reimplemented at each call site because the alternative
	 * — "emit when tickCount % n == 0" — cannot represent a rate below one drop per
	 * tick without rounding it to zero or to one, which is the entire range that
	 * ordinary bleeding lives in.
	 */
	public static int dropsThisTick(double carry, double dropsPerSecond) {
		return (int) (carry + dropsPerSecond / 20.0);
	}

	/** The fractional remainder to carry into the next tick. */
	public static double carryAfter(double carry, double dropsPerSecond) {
		double total = carry + dropsPerSecond / 20.0;
		return total - (int) total;
	}

	/**
	 * A deterministic value in [0, 1) for the given index and channel — the
	 * scatter in a spray cone, the shape of a splat, where a droplet lands on the
	 * lens.
	 *
	 * <p>A hash rather than a generator, so there is no shared mutable state to
	 * desynchronise between two clients and no frame where the same drop is drawn
	 * in two places. Presentation is allowed variety; it is not allowed to be
	 * irreproducible, which is the same reason the injury model has no RNG in it.
	 */
	public static double jitter(long index, int channel) {
		long z = index * 0x9E3779B97F4A7C15L + channel * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		z ^= z >>> 31;
		return (z >>> 11) * 0x1.0p-53;
	}

	/** The same, in [-1, 1) — for offsets that need a direction as well as a size. */
	public static double jitterSigned(long index, int channel) {
		return jitter(index, channel) * 2.0 - 1.0;
	}
}
