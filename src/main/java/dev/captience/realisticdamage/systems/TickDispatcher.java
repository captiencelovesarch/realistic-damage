package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Central per-player tick (spec §49): one dispatcher, isolated systems. Keeps
 * allocations near zero — the only per-tick object is the quantized snapshot,
 * and only when state actually drifted enough to change it.
 */
public final class TickDispatcher {
	private TickDispatcher() {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tickPlayer(player);
				// Rain rinses a creative player's sword too; the rest of it gates itself.
				dev.captience.realisticdamage.damage.BloodiedItem.tickHolder(player);
			}
			// Mobs with bodies (1.7): only the injured set, nothing else is touched.
			MobAnatomy.tick();
			// Dead bodies still falling (2.0): at most a handful, each until it lies still.
			dev.captience.realisticdamage.damage.CorpseImpacts.tick(server);
		});
	}

	private static void tickPlayer(ServerPlayer player) {
		// Creative/Spectator ignore Realistic Damage (spec §46).
		if (player.isCreative() || player.isSpectator()) {
			ImpairmentSystem.clear(player);
			// Otherwise a mode switch mid-landing leaves you face-down on the
			// floor the moment you switch back.
			RDAttachments.get(player).clearKnockdown();
			RDAttachments.get(player).clearMicrosleep();
			RDAttachments.get(player).adrenaline().reset();
			return;
		}
		RealisticDamageData data = RDAttachments.get(player);
		// A corpse does not heal (1.7 fix). Dead players stay in the player list
		// until they respawn — and since 1.7 in the world too, while the death
		// camera watches — so the body systems kept running on them: the healing
		// clock mended a dead man's burns and said so on the action bar. Death
		// freezes the body where it fell; the client freezes its vitals the same
		// way. Rescue still ticks, because a pressure hold on the dead has to be
		// let go.
		if (player.isDeadOrDying()) {
			RescueSystem.tick(player, data);
			return;
		}
		for (BodyPartData part : data.allParts()) {
			part.tickStrain();
		}
		data.tickUnconscious();
		data.tickKnockdown(Balance.KNOCKDOWN_RECOVERY_TICKS);
		data.tickWhiplash();
		// Ahead of bleeding, so a pressure hold claimed this tick counts this tick.
		RescueSystem.tick(player, data);
		BleedingSystem.tick(player, data);
		ConsciousnessSystem.tick(player, data);
		ConcussionSystem.tick(player, data);
		AdrenalineSystem.tick(player, data);
		HealingSystem.tick(player, data);
		ImpairmentSystem.tick(player, data);
		RDAttachments.pushSnapshot(player);
	}
}
