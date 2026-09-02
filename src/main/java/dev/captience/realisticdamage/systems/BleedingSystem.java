package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.damage.RDDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Blood drain, thresholds, and bleed-out (spec §16-17). Blood mostly buffers
 * early bleeding; sustained loss starts costing vanilla HP, and Blood 0 is a
 * rapid — but not instant — bleed-out where death still happens through
 * ordinary HP reaching zero.
 */
public final class BleedingSystem {
	private BleedingSystem() {}

	public static void tick(ServerPlayer player, RealisticDamageData data) {
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
				ServerLevel level = player.level();
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
	public static void spray(ServerPlayer player, BodyPart part, WoundSeverity severity, Vec3 direction) {
		if (!ServerConfig.get().enableBleeding) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 at = BodyHitResolver.regionCenter(player, part);
		Vec3 away = direction.lengthSqr() < 1.0e-6
				? new Vec3(0.0, 0.35, 0.0)
				: direction.normalize().scale(-1.0);
		int count = Balance.SPRAY_DROPS_PER_SEVERITY * (1 + severity.ordinal());
		double speed = Balance.SPRAY_SPEED / 20.0;
		for (int i = 0; i < count; i++) {
			// Thrown back along the blow, in a cone. sendParticles' own delta
			// arguments are a gaussian scatter around a single speed, which cannot
			// express a direction, so each drop is sent individually with its own
			// velocity — that velocity is also how the client reads the drop's
			// size, so a critical wound sprays visibly bigger blood.
			double yaw = Math.toRadians(BloodFlow.jitterSigned(i, 31) * Balance.SPRAY_SPREAD);
			double pitch = Math.toRadians(BloodFlow.jitterSigned(i, 32) * Balance.SPRAY_SPREAD);
			Vec3 thrown = away.yRot((float) yaw).xRot((float) pitch)
					.scale(speed * (0.55 + 0.45 * BloodFlow.jitter(i, 33)));
			level.sendParticles(dev.captience.realisticdamage.RDParticles.BLOOD,
					at.x, at.y, at.z, 0, thrown.x, thrown.y, thrown.z, 1.0);
		}
	}
}
