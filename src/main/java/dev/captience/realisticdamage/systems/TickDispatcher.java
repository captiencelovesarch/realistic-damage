package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
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
			}
		});
	}

	private static void tickPlayer(ServerPlayer player) {
		// Creative/Spectator ignore Realistic Damage (spec §46).
		if (player.isCreative() || player.isSpectator()) {
			ImpairmentSystem.clear(player);
			return;
		}
		RealisticDamageData data = RDAttachments.get(player);
		for (BodyPartData part : data.allParts()) {
			part.tickStrain();
		}
		data.tickUnconscious();
		ImpairmentSystem.tick(player, data);
		// Milestone 4/5/6 hook in here: bleeding drain, consciousness, healing.
		RDAttachments.pushSnapshot(player);
	}
}
