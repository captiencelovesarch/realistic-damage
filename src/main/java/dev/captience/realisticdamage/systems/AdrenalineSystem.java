package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * The last two hearts (1.7). The state machine is {@link AdrenalineMath}; this
 * feeds it a player's health and sprinting each tick and says the one word the
 * surge deserves. What the phases <em>do</em> lives where the rest of the
 * body's costs do: the speed and the masked pain in {@link ImpairmentSystem},
 * the winded sprint in {@link ImpairmentSystem#sprintBlocked}, and everything
 * you see and hear on the client.
 */
public final class AdrenalineSystem {
	private AdrenalineSystem() {}

	public static void tick(ServerPlayer player, RealisticDamageData data) {
		if (!ServerConfig.get().enableAdrenaline || data.unconscious()) {
			// Out cold is not a fight-or-flight state; nothing surges in the dark.
			// The state is dropped rather than paused, so waking at two hearts is
			// a fresh crossing and gets a fresh surge if the refractory allows.
			if (data.adrenaline().phase != AdrenalineMath.Phase.NONE) {
				int refractory = data.adrenaline().refractory;
				boolean rearmed = data.adrenaline().rearmed;
				data.adrenaline().reset();
				data.adrenaline().refractory = refractory;
				data.adrenaline().rearmed = rearmed;
			}
			return;
		}
		double max = player.getMaxHealth();
		double fraction = max > 0.0 ? player.getHealth() / max : 1.0;
		if (AdrenalineMath.tick(data.adrenaline(), fraction, player.isSprinting())) {
			Notifier.message(player, "realisticdamage.msg.adrenaline");
		}
		if (data.adrenaline().winded && player.isSprinting()) {
			player.setSprinting(false);
		}
	}
}
