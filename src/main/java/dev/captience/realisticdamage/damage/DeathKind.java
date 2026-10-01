package dev.captience.realisticdamage.damage;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;

/**
 * What a death looked like from inside the body (1.7), for the death camera.
 *
 * <p>The first death camera had one collapse for every death: the knees went,
 * the view rolled 32 degrees and thudded, whatever had happened. A lava death
 * and a fifty-block drop got the same half-second slump, and the playtest asked
 * for the camera to "actually move more based on different kinds of deaths".
 * Violence ({@link DeathMath}) already says how hard a death was; this says
 * what kind it was, and the client picks its motion from the pair (see
 * {@code DeathMotion}).
 *
 * <p>Classified from the killing source on the server, where it is known, and
 * sent once with the violence. Deliberately coarser than
 * {@link DamageClassifier}: the body cares whether a blade or a club did it,
 * but the camera only cares whether you were thrown, dropped, burned, drowned
 * or let go.
 */
public enum DeathKind {
	/** Struck: a mob, a weapon, anything that hit you and had a direction. */
	BLOW,
	/** Shot: the head snaps back from the hit, then the body folds forward. */
	PROJECTILE,
	/** The ground: you were already there when it killed you, and very fast. */
	FALL,
	/** Thrown by a blast, tumbling. */
	EXPLOSION,
	/** Something came down on you, or you were pressed into something. */
	CRUSH,
	/** Burning: thrashing, then still. */
	FIRE,
	/** Sinking into lava, face up, thrashing as it goes. */
	LAVA,
	/** Drowned: no thud, a slow weightless drift. */
	DROWNING,
	/** Frozen stiff: goes over like a plank. */
	FREEZE,
	/** Lightning: a convulsion before the fall. */
	SHOCK,
	/** The void: falling, turning slowly, for as long as it takes. */
	VOID,
	/** Let go — bled out, poisoned, withered, starved: the faint, not the fall. */
	FADE;

	private static final DeathKind[] VALUES = values();

	public static DeathKind byOrdinal(int ordinal) {
		return VALUES[Math.clamp(ordinal, 0, VALUES.length - 1)];
	}

	/**
	 * The kind of death a killing source makes. Order matters, specific before
	 * general: lava is fire, a fireball is a projectile and an explosion both,
	 * and the camera should play whichever the body would have felt first.
	 */
	public static DeathKind of(DamageSource source) {
		if (source.is(RDDamageTypes.BLEED_OUT)) {
			return FADE;
		}
		if (source.is(RDDamageTypes.SKULL_CRUSHED) || source.is(RDDamageTypes.CHEST_CRUSHED)) {
			return BLOW;
		}
		if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.OUTSIDE_BORDER)) {
			return VOID;
		}
		if (source.is(DamageTypes.LAVA)) {
			return LAVA;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypes.SONIC_BOOM)) {
			return EXPLOSION;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return FIRE;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)
				|| source.is(DamageTypes.STALAGMITE)) {
			return FALL;
		}
		if (source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_BLOCK)
				|| source.is(DamageTypes.FALLING_STALACTITE) || source.is(DamageTypes.IN_WALL)
				|| source.is(DamageTypes.CRAMMING)) {
			return CRUSH;
		}
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return DROWNING;
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			return FREEZE;
		}
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			return SHOCK;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return PROJECTILE;
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)
				|| source.is(DamageTypes.WITHER) || source.is(DamageTypes.STARVE)
				|| source.is(DamageTypes.DRAGON_BREATH) || source.is(DamageTypes.DRY_OUT)) {
			return FADE;
		}
		return BLOW;
	}
}
