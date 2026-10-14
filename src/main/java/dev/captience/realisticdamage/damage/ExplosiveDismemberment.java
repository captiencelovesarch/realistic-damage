package dev.captience.realisticdamage.damage;

import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.PendingExplosion;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.systems.ExplosionMath;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

/**
 * A blast that takes a limb (1.6). Explosions were the one kind of violence the
 * body model read off the health bar, and a survivable blast leaves at most
 * thirteen points of trauma on its nearest region — so a creeper could only ever
 * finish a limb that something else had already ruined, and the better your
 * armour the less a creeper going off against your leg was allowed to mean. This
 * judges the blast vanilla computed for the body instead: the same nearest-region
 * shares and the same localized armour reduction the ordinary explosion trauma
 * gets, against a bar worth 45% of the whole limb.
 *
 * <p>45% and not more, because the real gate on this feature is survival rather
 * than trauma: Fabric's {@code AFTER_DAMAGE} never fires on a killing blow, so a
 * limb can only come off a body that lived through the blast. The derivation of
 * that fraction, and the window it buys a plain-armoured player at ten hearts,
 * are on {@code Balance.EXPLOSIVE_DESTROY_FRACTION}.
 *
 * <p>Limbs only, and only the region the blast was nearest. The head and chest
 * are vanilla's to kill: a blast strong enough to take a skull already killed
 * you, and a mod core-kill goes through a damage type that bypasses
 * invulnerability — so a core rule would fire almost exclusively on totem saves
 * (the pipeline runs after {@code totemStabilize}) and kill the player one call
 * after their totem popped.
 */
public final class ExplosiveDismemberment {
	/**
	 * What the stump bleeds. Fixed rather than derived from what the limb happened
	 * to lose on the way out, because that number is what armour, absorption and a
	 * totem decided the blast was worth — and a totem survivor, whose every part
	 * is floored to 15%, would then bleed <em>less</em> than an armoured survivor
	 * of the identical blast. Identical hits, identical injuries.
	 *
	 * <p>This is also why a blast does not go through
	 * {@code WoundMath.stumpSeverity}, which reads how much condition the killing
	 * blow took off. The appended trauma below carries a condition damage of 0 by
	 * construction — the part is already at zero when it is written — so that rule
	 * would file a limb torn off a standing body as a grind. A blast is never a
	 * grind: it is the definition of taken off in one blow.
	 */
	static final WoundSeverity STUMP_WOUND = WoundSeverity.CRITICAL;

	private ExplosiveDismemberment() {}

	/**
	 * Judges the region the blast was nearest and takes it if it is a limb the
	 * blast was worth. Appends one {@code AppliedTrauma(limb, 0, blastTrauma,
	 * true)} per limb taken: the part is already at zero so nothing downstream can
	 * act on it, the ceiling rule gets the event it needs to lower a rung, and the
	 * impact cue gets the blast's own magnitude even when absorption swallowed
	 * every point of health.
	 *
	 * @param shares one {@code explosionShares} map built from the captured
	 *               centre, shared with the routing below so the region that takes
	 *               the biggest share is the region that can go
	 * @return the limbs taken, in {@link BodyPart} order; empty when none
	 */
	public static List<BodyPart> evaluate(LivingEntity player, RealisticDamageData data, ServerConfig config,
			ClassifiedDamage classified, DamageSource source, PendingExplosion blast,
			Map<BodyPart, Double> shares, List<TraumaPipeline.AppliedTrauma> result) {
		// The same product baseTrauma builds a few lines away in the pipeline, kept
		// as its own local rather than hoisted out of that expression: regrouping
		// the multiplication every damage category in the mod runs through is not
		// bit-identical in general and is not worth one shared variable. If one
		// moves, move both.
		double scale = classified.extraMultiplier() * config.bodyDamageMultiplier;
		if (source.getEntity() instanceof ServerPlayer) {
			scale *= config.pvpTraumaMultiplier;
		}
		// The size rule is the pipeline's, and must be: the same blast cannot be
		// a fifth as bad for the trauma and full strength for the limb (1.7 slice 3).
		scale /= dev.captience.realisticdamage.systems.MobAnatomy.vitalityScale(player);
		double primaryShare = 0.0;
		for (double share : shares.values()) {
			primaryShare = Math.max(primaryShare, share);
		}
		if (primaryShare <= 0.0) {
			return List.of();
		}
		Vec3 direction = blowDirection(player, blast);
		List<BodyPart> taken = new ArrayList<>(1);
		for (BodyPart part : BodyPart.values()) {
			if (!part.isLimb()) {
				continue;
			}
			// Only the region the blast was nearest. explosionShares gives exactly
			// one region the primary share and the other four 0.05 each, so "never
			// two limbs from one blast" is a fact about the map rather than a fact
			// about where the thresholds happen to sit today — and at 0.45 that
			// gate is load-bearing at stock settings, not merely on a hard server.
			// A 0.05 remainder is worth I x 1.35 x 0.05 against an arm bar of 5.85,
			// so it crosses at I = 5.85 / 0.0675 = 86.67: a wither's death blast
			// (14r + 1 = 99 at r = 7) clears it outright, and a charged creeper or
			// an end crystal (85) clears it once bodyDamageMultiplier passes 1.02.
			// Without the gate one crystal would cost four limbs.
			if (shares.getOrDefault(part, 0.0) < primaryShare) {
				continue;
			}
			BodyPartData limb = data.part(part);
			if (limb.destroyed()) {
				continue;
			}
			double trauma = ExplosionMath.limbTrauma(blast.amount(), primaryShare,
					ArmorModifier.reduction(player, part, DamageCategory.EXPLOSION), scale);
			if (!ExplosionMath.dismembers(part, trauma)) {
				continue;
			}
			// Through the choke point, so the blast inherits the stump, the scar
			// and everything a later feature hangs there, and cannot be the one
			// route that forgot.
			DestructionLogic.destroy(player, data, part, STUMP_WOUND, direction,
					DestructionLogic.Cause.BLAST);
			result.add(new TraumaPipeline.AppliedTrauma(part, 0.0, trauma, true));
			taken.add(part);
		}
		return taken.isEmpty() ? List.of() : taken;
	}

	/** From the blast centre toward the victim, so the stump sprays away from the explosion. */
	private static Vec3 blowDirection(LivingEntity player, PendingExplosion blast) {
		Vec3 delta = player.position().subtract(blast.x(), blast.y(), blast.z());
		return delta.lengthSqr() < 1.0e-6 ? Vec3.ZERO : delta;
	}
}
