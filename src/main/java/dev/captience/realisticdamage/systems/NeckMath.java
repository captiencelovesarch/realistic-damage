package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DamageCategory;
import org.jspecify.annotations.Nullable;

/**
 * Everything a neck decides, as plain arithmetic (2.0), so the bars and the
 * whiplash curve are unit-tested without a game.
 *
 * <p>Each cause turns into one number, the strain on the neck: at
 * {@link #BREAKS} it snaps, and from {@link Balance#WHIPLASH_MIN_STRAIN} up to
 * that it is wrenched (whiplash). Thresholds, not chances: the same landing
 * breaks the same neck every time.
 */
public final class NeckMath {
	private NeckMath() {}

	/** The strain that snaps a neck. */
	public static final double BREAKS = 1.0;

	/**
	 * A body landing head first (limp, knocked flat or gliding).
	 *
	 * @param effectiveHeight blocks over the safe fall distance, times the surface
	 */
	public static double limpLanding(double effectiveHeight) {
		return Math.max(0.0, effectiveHeight) / Balance.NECK_LIMP_HEIGHT;
	}

	/**
	 * A body landing on its feet: the legs take it, so the neck never snaps, but
	 * a landing hard enough to knock you flat jars it. Zero below the
	 * knockdown's own floor.
	 */
	public static double uprightLanding(double effectiveHeight) {
		if (effectiveHeight <= Balance.KNOCKDOWN_MIN_HEIGHT) {
			return 0.0;
		}
		double t = (effectiveHeight - Balance.KNOCKDOWN_MIN_HEIGHT)
				/ (Balance.NECK_UPRIGHT_FULL_HEIGHT - Balance.KNOCKDOWN_MIN_HEIGHT);
		return Math.min(Balance.NECK_BLUNT_STRAIN_CAP, Balance.NECK_BLUNT_STRAIN_CAP * t);
	}

	/** Flying into a wall, from vanilla's raw damage figure for the crash. */
	public static double wallImpact(double rawDamage) {
		return Math.max(0.0, rawDamage) / Balance.NECK_WALL_DAMAGE;
	}

	/**
	 * A blow to the head. Only blunt blows wrench a neck (an edge cuts, a point
	 * goes in); only a heavy one can snap it.
	 *
	 * @param headTrauma the localized trauma the head was dealt
	 */
	public static double headBlow(@Nullable DamageCategory category, double headTrauma) {
		if (category == null || headTrauma <= 0.0) {
			return 0.0;
		}
		double strain = headTrauma / Balance.NECK_BLUNT_TRAUMA;
		return switch (category) {
			case HEAVY_BLUNT -> strain;
			case BLUNT -> Math.min(Balance.NECK_BLUNT_STRAIN_CAP, strain);
			default -> 0.0;
		};
	}

	/** Thrown off your feet by a blow or a blast: the neck whips. Never snaps it. */
	public static double knockdown(double violence) {
		if (violence < Balance.WHIPLASH_KNOCKDOWN_VIOLENCE) {
			return 0.0;
		}
		double t = Math.min(1.0, (violence - Balance.WHIPLASH_KNOCKDOWN_VIOLENCE)
				/ (1.0 - Balance.WHIPLASH_KNOCKDOWN_VIOLENCE));
		return Balance.WHIPLASH_MIN_STRAIN + t * (Balance.NECK_BLUNT_STRAIN_CAP - Balance.WHIPLASH_MIN_STRAIN);
	}

	public static boolean breaks(double strain) {
		return strain >= BREAKS;
	}

	/** Wrenched but not broken. */
	public static boolean wrenches(double strain) {
		return strain >= Balance.WHIPLASH_MIN_STRAIN && strain < BREAKS;
	}

	/** How bad a whiplash is, 0 (just over the bar) to 1 (all but broken). */
	public static double severity(double strain) {
		double s = (strain - Balance.WHIPLASH_MIN_STRAIN) / (BREAKS - Balance.WHIPLASH_MIN_STRAIN);
		return s < 0.0 ? 0.0 : s > 1.0 ? 1.0 : s;
	}

	/** How long a whiplash of this severity lasts, ticks. */
	public static int whiplashTicks(double severity) {
		double s = Math.clamp(severity, 0.0, 1.0);
		return (int) Math.round(Balance.WHIPLASH_TICKS_MIN
				+ s * (Balance.WHIPLASH_TICKS_MAX - Balance.WHIPLASH_TICKS_MIN));
	}

	/** How stiff a fresh whiplash of this severity starts. */
	public static double startingStiffness(double severity) {
		double s = Math.clamp(severity, 0.0, 1.0);
		return Balance.WHIPLASH_STIFFNESS_MIN + s * (Balance.WHIPLASH_STIFFNESS_MAX - Balance.WHIPLASH_STIFFNESS_MIN);
	}

	/** Stiffness now: it eases off evenly as the time runs out. */
	public static double stiffness(double start, int remaining, int total) {
		if (total <= 0 || remaining <= 0 || start <= 0.0) {
			return 0.0;
		}
		return start * Math.min(1.0, (double) remaining / total);
	}

	/**
	 * The fastest the view may turn at this stiffness, degrees a second; no limit
	 * at all with a sound neck. Eases in quadratically, so a mild whiplash only
	 * catches flicks and a bad one makes a quarter turn take a second.
	 */
	public static double turnCap(double stiffness) {
		if (stiffness <= 0.0) {
			return Double.POSITIVE_INFINITY;
		}
		double free = 1.0 - Math.min(1.0, stiffness);
		return Balance.WHIPLASH_TURN_WORST + (Balance.WHIPLASH_TURN_MILD - Balance.WHIPLASH_TURN_WORST) * free * free;
	}

	/** What a mob's head-turn speed is multiplied by at this stiffness. */
	public static double mobTurnFactor(double stiffness) {
		return stiffness <= 0.0 ? 1.0 : turnCap(stiffness) / Balance.WHIPLASH_TURN_MILD;
	}

	/** What a mob's aim spread is multiplied by at this stiffness. */
	public static double mobAimSpread(double stiffness) {
		return 1.0 + (Balance.MOB_WHIPLASH_AIM_SPREAD - 1.0) * Math.clamp(stiffness, 0.0, 1.0);
	}

	/** Water this deep (blocks) stops nothing: the bottom arrives. */
	public static boolean shallow(int depth) {
		return depth >= 1 && depth <= Balance.SHALLOW_WATER_DEPTH;
	}

	/**
	 * The stiff neck's give: how much of a turn gets through when there is only
	 * {@code budget} degrees of give left. Small turns pass almost untouched,
	 * big ones saturate at the budget, smoothly, so it reads as resistance and
	 * not as a wall. Never more than asked for, never more than the budget.
	 */
	public static double give(double request, double budget) {
		if (request <= 0.0 || budget <= 0.0) {
			return 0.0;
		}
		return budget * Math.tanh(request / budget);
	}
}
