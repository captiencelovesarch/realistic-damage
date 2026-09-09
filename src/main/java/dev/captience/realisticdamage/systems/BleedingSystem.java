package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.damage.RDDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Blood drain, thresholds, and bleed-out (spec §16-17). Blood mostly buffers
 * early bleeding; sustained loss starts costing vanilla HP, and Blood 0 is a
 * rapid — but not instant — bleed-out where death still happens through
 * ordinary HP reaching zero.
 */
public final class BleedingSystem {
	private BleedingSystem() {}

	public static void tick(LivingEntity player, RealisticDamageData data) {
		ServerConfig config = ServerConfig.get();
		if (!config.enableBleeding) {
			return;
		}
		if (data.bleeding()) {
			// A hand held over the wound does not close it, it only slows what is
			// coming out. Buying time, not a cure (1.2).
			data.drainBlood(data.effectiveBleedRate() * config.bleedingRateMultiplier
					* data.pressureMultiplier() / 20.0);
		}

		// Tell the player they are heading for a bleed-out before it happens.
		int band = data.bloodBand();
		if (band > data.lastBloodBand() && data.lastBloodBand() >= 0 && band > 0) {
			dev.captience.realisticdamage.util.Notifier.message(player, "realisticdamage.msg.blood." + band);
		}
		data.setLastBloodBand(band);

		double hpPerSecond = bleedOutHpPerSecond(data.blood());
		if (hpPerSecond > 0.0) {
			// Whole-point hurts from an accumulator: no sub-half-heart spam.
			if (data.advanceBleedHpDrain(hpPerSecond / 20.0)) {
				ServerLevel level = (ServerLevel) player.level();
				if (!player.hurtServer(level, RDDamageTypes.bleedOut(level), 1.0F)) {
					// Refused (invulnerable, already dying): keep the point rather
					// than silently destroying accrued bleed damage.
					data.refundBleedHpDrain(1.0);
				}
			}
		}
	}

	/** Vanilla-HP drain rate for the current Blood level (spec §17 bands). */
	public static double bleedOutHpPerSecond(double blood) {
		if (blood <= 0.0) {
			return Balance.BLEEDOUT_HP_PER_SEC_ZERO;
		}
		if (blood < Balance.BLOOD_CRITICAL) {
			return Balance.BLEEDOUT_HP_PER_SEC_CRITICAL;
		}
		if (blood < Balance.BLOOD_SERIOUS) {
			return Balance.BLEEDOUT_HP_PER_SEC_SERIOUS;
		}
		return 0.0;
	}

	/**
	 * The burst thrown when a blow first opens a wound (1.5) — the one piece of
	 * blood the server still emits.
	 *
	 * <p>Continuous bleeding moved to the client in 1.5, driven off the body
	 * snapshot that was already being synced: it costs nothing on the wire, it can
	 * follow the wound's real Blood/sec instead of a fixed timer, and it lets an
	 * arterial wound spurt on the same heartbeat the player is hearing. None of
	 * that works for this one, because opening a wound is an event with a
	 * direction and the snapshot carries neither — so it stays a broadcast, seen
	 * by everyone who can see the victim.
	 */
	public static void spray(LivingEntity player, BodyPart part, WoundSeverity severity, Vec3 direction) {
		spray(player, part, severity, direction, false);
	}

	/**
	 * The ordinary wound spray, or the wider floor-biased mark left when the same
	 * wound is a stump. Kept in one path so the 1.5 cone cannot drift away from the
	 * severance variant and every particle still carries severity in its velocity.
	 */
	public static void spray(LivingEntity player, BodyPart part, WoundSeverity severity, Vec3 direction,
			boolean severance) {
		if (!ServerConfig.get().enableBleeding) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = BodyHitResolver.regionCenter(player, part);
		// Straight up for want of a direction. Unit length, because `away` is a
		// direction and the throw speed is applied to it two lines below: the 0.35
		// this used to be was not shaping the cone, it was quietly throwing the
		// direction-less spray at 35% of SPRAY_SPEED — 2.45 b/s against 7.0, which
		// peaks 0.16 blocks above a torso origin that has 0.72 blocks of hitbox
		// above it, so not one drop of it ever left the body.
		Vec3 away;
		if (direction.lengthSqr() < 1.0e-6) {
			if (severance) {
				double yaw = Math.toRadians(player.getYRot());
				away = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
			} else {
				away = new Vec3(0.0, 1.0, 0.0);
			}
		} else {
			away = direction.normalize().scale(-1.0);
		}
		int count = BloodFlow.burstDrops(severity.ordinal(), severance);
		double spread = severance ? Balance.SEVERANCE_SPRAY_SPREAD : Balance.SPRAY_SPREAD;
		double speed = Balance.SPRAY_SPEED / 20.0;
		for (int i = 0; i < count; i++) {
			// Thrown back along the blow, in a cone. sendParticles' own delta
			// arguments are a gaussian scatter around a single speed, which cannot
			// express a direction, so each drop is sent individually with its own
			// velocity — that velocity is also how the client reads the drop's
			// size, so a critical wound sprays visibly bigger blood.
			double yaw = BloodFlow.burstYaw(i, spread);
			double pitch = BloodFlow.burstPitch(i, spread);
			Vec3 thrown = away.yRot((float) yaw).xRot((float) pitch);
			if (severance) {
				thrown = thrown.add(0.0, -Balance.SEVERANCE_DOWN_BIAS, 0.0).normalize();
			}
			thrown = thrown.scale(speed * BloodFlow.burstSpeed(i));
			level.sendParticles(dev.captience.realisticdamage.RDParticles.BLOOD,
					at.x, at.y, at.z, 0, thrown.x, thrown.y, thrown.z, 1.0);
		}
	}
}
