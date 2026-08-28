package dev.captience.realisticdamage.util;

import dev.captience.realisticdamage.body.BodyPart;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Action-bar feedback on meaningful transitions only — never per tick
 * (spec §43). All text is translatable; en_us ships in resources.
 */
public final class Notifier {
	private Notifier() {}

	public static void limbBreaks(ServerPlayer player, BodyPart part) {
		message(player, "realisticdamage.msg.break." + part.id());
		// A dull crack, heard at the player.
		player.level().playSound(null, player.blockPosition(),
				SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.PLAYERS, 0.6F, 0.7F);
	}

	public static void destroyed(ServerPlayer player, BodyPart part) {
		message(player, "realisticdamage.msg.destroyed." + part.id());
		player.level().playSound(null, player.blockPosition(),
				SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	public static void message(ServerPlayer player, String key) {
		// Mock players (gametests) have no connection; skip the cosmetic packet.
		if (player.connection != null) {
			player.sendOverlayMessage(Component.translatable(key));
		}
	}
}
