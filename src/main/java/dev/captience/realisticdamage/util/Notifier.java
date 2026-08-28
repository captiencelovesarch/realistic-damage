package dev.captience.realisticdamage.util;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.InjurySound;
import dev.captience.realisticdamage.net.InjurySoundPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Action-bar feedback on meaningful transitions only — never per tick
 * (spec §43). All text is translatable; en_us ships in resources.
 */
public final class Notifier {
	private Notifier() {}

	public static void limbBreaks(ServerPlayer player, BodyPart part) {
		message(player, "realisticdamage.msg.break." + part.id());
		injurySound(player, InjurySound.FRACTURE);
	}

	public static void destroyed(ServerPlayer player, BodyPart part) {
		message(player, "realisticdamage.msg.destroyed." + part.id());
		injurySound(player, InjurySound.DESTRUCTION);
	}

	public static void message(ServerPlayer player, String key) {
		// Mock players (gametests) have no connection; skip the cosmetic packet.
		if (player.connection != null) {
			player.sendOverlayMessage(Component.translatable(key));
		}
	}

	/**
	 * Can this player actually receive our sound cue? Asking the channel rather
	 * than just null-checking the connection matters on a server: a client
	 * without the mod has no receiver registered, and the practice dummy's
	 * connection goes nowhere at all.
	 */
	private static boolean canHear(ServerPlayer player) {
		return player.connection != null
				&& ServerPlayNetworking.canSend(player, InjurySoundPayload.TYPE);
	}

	/**
	 * Your own body is the loudest thing in the room; someone else's is a cue
	 * from across it. Sent as a message rather than played as a world sound so
	 * that each client picks its own style variant, and so the injured player
	 * hears it non-positionally instead of as a noise coming from their feet.
	 */
	public static void injurySound(ServerPlayer player, InjurySound sound) {
		if (canHear(player)) {
			ServerPlayNetworking.send(player, new InjurySoundPayload(
					sound, true, player.getX(), player.getY(), player.getZ()));
		}
		for (ServerPlayer other : player.level().getEntitiesOfClass(ServerPlayer.class,
				player.getBoundingBox().inflate(Balance.INJURY_SOUND_RADIUS),
				candidate -> candidate != player && canHear(candidate))) {
			ServerPlayNetworking.send(other, new InjurySoundPayload(
					sound, false, player.getX(), player.getY(), player.getZ()));
		}
	}
}
