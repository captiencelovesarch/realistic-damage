package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.damage.RDDamageTypes;
import net.minecraft.core.particles.DustParticleOptions;
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

	private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(0x8A0303, 0.8F);
	/** One particle burst per wound every this many ticks (spec §56: no particle spam). */
	private static final int PARTICLE_INTERVAL = 25;

	public static void tick(ServerPlayer player, RealisticDamageData data) {
		ServerConfig config = ServerConfig.get();
		if (!config.enableBleeding) {
			return;
		}
		if (data.bleeding()) {
			data.drainBlood(data.effectiveBleedRate() * config.bleedingRateMultiplier / 20.0);
			if (player.tickCount % PARTICLE_INTERVAL == 0) {
				spawnWoundParticles(player, data);
			}
		}

		double hpPerSecond = bleedOutHpPerSecond(data.blood());
		if (hpPerSecond > 0.0) {
			// Whole-point hurts from an accumulator: no sub-half-heart spam.
			if (data.advanceBleedHpDrain(hpPerSecond / 20.0)) {
				ServerLevel level = player.level();
				player.hurtServer(level, RDDamageTypes.bleedOut(level), 1.0F);
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

	private static void spawnWoundParticles(ServerPlayer player, RealisticDamageData data) {
		ServerLevel level = player.level();
		for (BleedWound wound : data.wounds()) {
			Vec3 at = BodyHitResolver.regionCenter(player, wound.part());
			int count = 1 + wound.severity().ordinal();
			level.sendParticles(BLOOD_DUST, at.x, at.y, at.z, count, 0.08, 0.1, 0.08, 0.0);
		}
	}
}
