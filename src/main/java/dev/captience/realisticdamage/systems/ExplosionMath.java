package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;

/**
 * The blast side of explosive dismemberment (1.6). Everything a limb is judged
 * on, with no Minecraft types in it, so the threshold table is pinned by unit
 * tests rather than by playtesting.
 */
public final class ExplosionMath {
	private ExplosionMath() {}

	/**
	 * Vanilla's per-entity blast figure restated
	 * ({@code ExplosionDamageCalculator.getEntityDamageAmount}):
	 * {@code p = (1 - distance/2r) x exposure}, then {@code ((p^2 + p)/2) x 7 x 2r + 1}.
	 *
	 * <p>The live path uses the captured amount verbatim so the mod can never
	 * drift from vanilla's curve; this exists for the unit tests, for the
	 * gametests that need a realistic amount without detonating anything, and for
	 * the distance tables in the docs. The peak is {@code 14r + 1} — 43 for a
	 * creeper, 57 for TNT, 85 for a charged creeper or an end crystal.
	 *
	 * @param distance from the blast centre to the entity's <em>position</em>
	 *                 (feet), which is what {@code Entity.distanceToSqr(Vec3)}
	 *                 measures
	 * @return 0 when radius is not positive or distance is past {@code 2r} —
	 *         vanilla skips those entities entirely
	 */
	public static double intensity(double radius, double distance, double exposure) {
		double doubleRadius = radius * 2.0;
		if (!(doubleRadius > 0.0) || distance > doubleRadius) {
			return 0.0;
		}
		double p = (1.0 - distance / doubleRadius) * exposure;
		if (p <= 0.0) {
			// Vanilla's own expression yields exactly 1.0 at p = 0 (the "+ 1"
			// term). Matching it keeps the restatement honest at the edge of the
			// blast and behind full cover, where the answer is "a bang, and
			// nothing else".
			return 1.0;
		}
		return (p * p + p) / 2.0 * 7.0 * doubleRadius + 1.0;
	}

	/**
	 * What the blast was worth to one region: the {@code applyShares} formula with
	 * the blast in place of the health delta, so the result lives on the same
	 * scale the destruction thresholds already use — and so dismemberment goes
	 * through the same localized armour reduction as every other kind of trauma
	 * rather than inventing its own.
	 *
	 * @param scale server multipliers (extra x body x PvP), the same product
	 *              {@code baseTrauma} builds
	 */
	public static double limbTrauma(double amount, double share, double armourReduction, double scale) {
		double trauma = amount * Balance.TRAUMA_EXPLOSION * share * (1.0 - armourReduction) * scale;
		return trauma > 0.0 ? trauma : 0.0;
	}

	/**
	 * 5.85 for an arm (13.0 x 0.45), 7.2 for a leg (16.0 x 0.45),
	 * {@code +infinity} for the head and torso. Both limb bars are the same
	 * {@link Balance#EXPLOSIVE_DESTROY_FRACTION} of their own maximum, on purpose:
	 * one number moves the feature, and an arm cannot drift away from a leg.
	 * The core is never eligible — a blast strong enough to take a skull already
	 * killed you, and a mod core-kill bypasses invulnerability and so would fire
	 * one call after a totem popped — and a threshold no number can reach says
	 * that more usefully inside a damage pipeline than an exception would.
	 */
	public static double threshold(BodyPart part) {
		if (part.isArm()) {
			return Balance.EXPLOSIVE_DESTROY_ARM;
		}
		return part.isLeg() ? Balance.EXPLOSIVE_DESTROY_LEG : Double.POSITIVE_INFINITY;
	}

	/** Does this blast take the limb? At the line, yes; a hair below it, no. */
	public static boolean dismembers(BodyPart part, double blastTrauma) {
		return blastTrauma >= threshold(part);
	}
}
