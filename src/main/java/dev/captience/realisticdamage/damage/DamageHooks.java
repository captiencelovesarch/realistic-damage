package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Integration with vanilla damage (spec §4-5). ALLOW_DAMAGE captures health so
 * AFTER_DAMAGE (same hurtServer call, fires pre-armor amounts only) can compute
 * the true post-mitigation damage as a health delta — no re-derived armor math,
 * i-frame partial hits and full blocks come out correct for free, and absorbed
 * damage causes no trauma (spec §39).
 */
public final class DamageHooks {
	private DamageHooks() {}

	public static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && participates(player) && !source.is(RDDamageTypes.BLEED_OUT)) {
				RDAttachments.get(player).capturePreHurt(player.getHealth());
			}
			return true;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayer player) || !participates(player) || source.is(RDDamageTypes.BLEED_OUT)) {
				return;
			}
			RealisticDamageData data = RDAttachments.get(player);
			double applied = data.consumePreHurt(player.getHealth());
			if (applied < 0) {
				applied = damageTaken; // no capture (shouldn't happen) — degrade to the pre-armor amount
			}
			TraumaPipeline.handle(player, data, source, baseDamageTaken, damageTaken, applied, blocked);
			RDAttachments.pushSnapshot(player);
		});
	}

	private static boolean participates(ServerPlayer player) {
		return !player.isCreative() && !player.isSpectator();
	}
}
