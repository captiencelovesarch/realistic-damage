package dev.captience.realisticdamage.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

/**
 * One right-click on a body is one action (items.8). When the main hand's
 * click on an entity does nothing on the clicking side, the game tries the
 * off hand as well, so the server hears the same click twice in one tick, once
 * per hand. A toggle (take hold of a body, let go of it) acted on both and
 * undid itself: "You let go." the moment you took hold. Whatever acts on a
 * click marks it; a second hand's copy in the same tick is then consumed and
 * does nothing.
 */
public final class OneClick {
	private OneClick() {}

	private static final Map<UUID, Long> HANDLED = new HashMap<>();

	/** Whether a click of this player's was already acted on this tick. */
	public static boolean handled(ServerPlayer player) {
		Long at = HANDLED.get(player.getUUID());
		return at != null && at == player.level().getGameTime();
	}

	/** This player's click this tick has been acted on. */
	public static void mark(ServerPlayer player) {
		HANDLED.put(player.getUUID(), player.level().getGameTime());
	}

	public static void forget(ServerPlayer player) {
		HANDLED.remove(player.getUUID());
	}
}
