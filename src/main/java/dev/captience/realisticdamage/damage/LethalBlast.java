package dev.captience.realisticdamage.damage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.PendingExplosion;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The blast that killed you takes you apart (1.7).
 *
 * <p>{@link ExplosiveDismemberment} judges blasts a body <em>survives</em>,
 * because it hangs off {@code AFTER_DAMAGE}, and Fabric never fires that for a
 * killing blow. So the blasts that should be the most violent in the mod — a
 * creeper at your feet, a bed in the Nether — left the body perfectly whole:
 * everyone watching saw it fly off in one piece and do vanilla's stiff
 * sideways tip. This runs from {@code AFTER_DEATH} instead, which fires inside
 * the same {@code hurtServer} call the blast was recorded around, so the
 * {@link PendingExplosion} is still there to read.
 *
 * <p>Limbs go nearest-first, each against a rising bar on the death's own
 * violence (the raw blast against the health bar, after that limb's armour).
 * A creeper going off three blocks away takes the near leg; one at your feet
 * takes everything. Deterministic like the rest of the mod: the order is by
 * distance to the blast centre, the bars are fixed.
 */
public final class LethalBlast {
	private LethalBlast() {}

	/**
	 * Violence a limb needs, by rank (0 = nearest the blast). The nearest goes on
	 * any blast that is more than a bad scrape — dying to it is the proof it was
	 * enough.
	 */
	static final double[] RANK_BAR = {0.30, 0.55, 0.72, 0.88};

	/** The limbs a lethal blast takes, nearest first; empty when the rule or the body says no. */
	public static List<BodyPart> apply(LivingEntity body, RealisticDamageData data, PendingExplosion blast) {
		ServerConfig config = ServerConfig.get();
		if (!config.enableLimbDestruction || !config.enableExplosiveDismemberment
				|| !MobAnatomy.canLoseParts(body)) {
			return List.of();
		}
		Vec3 centre = new Vec3(blast.x(), blast.y(), blast.z());
		List<BodyPart> limbs = new ArrayList<>(4);
		for (BodyPart part : BodyPart.values()) {
			if (part.isLimb() && MobAnatomy.plan(body).has(part) && !data.part(part).destroyed()) {
				limbs.add(part);
			}
		}
		limbs.sort(Comparator.comparingDouble(
				(BodyPart part) -> BodyHitResolver.regionCenter(body, part).distanceToSqr(centre))
				.thenComparingInt(BodyPart::ordinal));

		double scale = config.bodyDamageMultiplier / MobAnatomy.vitalityScale(body);
		List<BodyPart> taken = new ArrayList<>(limbs.size());
		int rank = 0;
		for (BodyPart part : limbs) {
			double armour = ArmorModifier.reduction(body, part, DamageCategory.EXPLOSION);
			double violence = violence(blast.amount() * (1.0 - armour) * scale, body.getMaxHealth());
			if (rank >= RANK_BAR.length || violence < RANK_BAR[rank]) {
				break;
			}
			Vec3 limbAt = BodyHitResolver.regionCenter(body, part);
			Vec3 away = limbAt.subtract(centre);
			SeveredLimb limb = DestructionLogic.destroy(body, data, part,
					ExplosiveDismemberment.STUMP_WOUND, away, DestructionLogic.Cause.BLAST);
			if (limb != null) {
				// A blast you did not live through throws harder than one you did.
				limb.setDeltaMovement(limb.getDeltaMovement().scale(1.0 + 0.8 * violence));
			}
			taken.add(part);
			rank++;
		}
		return taken;
	}

	/** Same scale as {@link DeathMath#violenceFromBlow}. */
	static double violence(double amount, double maxHealth) {
		return DeathMath.violenceFromBlow(amount, maxHealth);
	}

	/**
	 * How the blast throws the body, blocks a tick: away from the centre with a
	 * lift, harder the more violent. Vanilla's own knockback is applied after
	 * the hurt call returns, so it is not on the body yet when the death fires.
	 */
	public static Vec3 launch(LivingEntity body, PendingExplosion blast) {
		double violence = DeathMath.violenceFromBlow(blast.amount(), body.getMaxHealth());
		Vec3 centre = new Vec3(blast.x(), blast.y(), blast.z());
		Vec3 away = body.position().add(0.0, body.getBbHeight() * 0.5, 0.0).subtract(centre);
		Vec3 flat = new Vec3(away.x, 0.0, away.z);
		Vec3 dir = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		double speed = Balance.CORPSE_BLAST_SPEED_MIN
				+ (Balance.CORPSE_BLAST_SPEED_MAX - Balance.CORPSE_BLAST_SPEED_MIN) * violence;
		double lift = Balance.CORPSE_BLAST_LIFT_MIN
				+ (Balance.CORPSE_BLAST_LIFT_MAX - Balance.CORPSE_BLAST_LIFT_MIN) * violence;
		return dir.scale(speed).add(0.0, lift, 0.0);
	}
}
