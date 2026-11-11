package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DamageCategory;
import org.jspecify.annotations.Nullable;

/**
 * When a head comes off (2.0), as pure arithmetic: no Minecraft types, so the
 * whole rule is pinned by {@code HeadMathTest}. {@code Decapitation} applies it.
 */
public final class HeadMath {
	private HeadMath() {}

	/** Whether this kind of blow takes a head off rather than crushing it: an edge. */
	public static boolean edged(@Nullable DamageCategory category) {
		return category == DamageCategory.SLASH || category == DamageCategory.HEAVY_SLASH;
	}

	/**
	 * A head in this state, hit by this, comes off. {@code lethal} is a blow that
	 * killed by itself (dying to it is the proof it was serious enough);
	 * otherwise {@code worth} is what it was worth against the head, judged by
	 * the same bar that destroys a core region.
	 */
	public static boolean takes(@Nullable DamageCategory category, BodyState head, boolean destroyed,
			boolean lethal, double worth) {
		if (!edged(category) || destroyed || head != BodyState.DISABLED) {
			return false;
		}
		return lethal || worth >= Balance.DESTROY_TRAUMA_CORE;
	}

	/**
	 * Whether a wither skeleton's severed skull comes through its burning as a
	 * skull you can take. Vanilla's own odds, so beheading one is the same bet
	 * as killing one and the Wither's gate is where vanilla put it: only a
	 * player's kill counts, and Looting helps. Decided by the skeleton itself
	 * ({@code seed}), not by a roll: the same skeleton gives the same answer.
	 */
	public static boolean skullSurvives(long seed, int looting, boolean byPlayer) {
		if (!byPlayer) {
			return false;
		}
		double chance = Balance.WITHER_SKULL_CHANCE + Balance.WITHER_SKULL_CHANCE_PER_LOOTING * Math.max(0, looting);
		return BloodFlow.jitter(seed, 71) < chance;
	}

	/** How long a headless zombie of this size walks on: three seconds, more for a big one, five at most. */
	public static int walkTicks(double vitality) {
		return (int) Math.min(Balance.HEADLESS_WALK_TICKS_MAX,
				Math.round(Balance.HEADLESS_WALK_TICKS * Math.max(1.0, vitality)));
	}
}
