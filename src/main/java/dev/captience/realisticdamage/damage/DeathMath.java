package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.config.Balance;

/**
 * How violent a death was, and what that does to its ending (1.5).
 *
 * <p>Both death styles used to play the same wind-down whatever the cause: a
 * 50-block drop onto stone got the same slow heart-fade as bleeding out in a
 * corner, and the fade contradicted the event it was supposed to end. Violence
 * is a single 0-to-1 read of the killing blow, scored on the server where the
 * blow is known, and it does exactly two things on the client: it compresses
 * the fade toward a cut, and it pre-ages the dying heart so an overwhelming
 * death has no beats at all — consciousness ended before the heart did.
 *
 * <p>Deliberately continuous rather than an "instant" flag. A cliff edge in the
 * presentation would mean two nearly identical deaths ending in visibly
 * different ways, which is the kind of unearned randomness this mod exists to
 * remove. Identical deaths end identically; worse deaths end faster.
 *
 * <p>Pure and Minecraft-free, in the {@code FallMath}/{@code WoundMath} mould,
 * so the scoring and the compression are unit-testable without a runtime.
 */
public final class DeathMath {
	private DeathMath() {}

	/**
	 * Violence of a lethal fall, from its height beyond safe fall distance. Keyed
	 * to height rather than to the fall's HP damage because 1.4's lethality
	 * compression deliberately shrinks exactly the number a damage-based score
	 * would read — a 50-block drop kills by a modest HP margin on purpose, but
	 * there is nothing modest about the landing.
	 */
	public static double violenceFromFall(double heightOverSafe) {
		if (heightOverSafe <= 0.0) {
			return 0.0;
		}
		return Math.clamp(heightOverSafe / Balance.DEATH_VIOLENCE_FALL_FULL, 0.0, 1.0);
	}

	/**
	 * Violence of any other killing blow: the raw hit measured against the whole
	 * health bar. Raw, not post-armour — armour softens what a mace does to your
	 * body, not how abruptly the world stops — and against max health rather
	 * than remaining health, so a weak poke that finishes a dying player reads
	 * as the quiet end it is instead of as an execution.
	 */
	public static double violenceFromBlow(double rawDamage, double maxHealth) {
		if (rawDamage <= 0.0 || maxHealth <= 0.0) {
			return 0.0;
		}
		return Math.clamp(rawDamage / (maxHealth * Balance.DEATH_VIOLENCE_BLOW_SCALE), 0.0, 1.0);
	}

	/**
	 * The fade duration violence leaves standing: the style's full length at 0,
	 * {@link Balance#DEATH_INSTANT_FACTOR} of it at 1. Never zero — a literal
	 * single-frame cut reads as a rendering glitch, and two or three frames of
	 * collapse is what makes it read as the world going instead.
	 */
	public static double fadeSeconds(double styleSeconds, double violence) {
		double v = Math.clamp(violence, 0.0, 1.0);
		return styleSeconds * (1.0 - (1.0 - Balance.DEATH_INSTANT_FACTOR) * v);
	}
}
