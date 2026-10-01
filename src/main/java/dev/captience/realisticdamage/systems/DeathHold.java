package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.config.Balance;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps a dead player in the world while their death camera is watching it (1.7).
 *
 * <p>The playtest bug: the collapse "moves a bunch and then just FREEZES".
 * Vanilla removes a dead player from the server's level twenty ticks after
 * death ({@code LivingEntity.tickDeath}), and removing a player from the level
 * takes them out of entity tracking and chunk broadcasting too: the server
 * sends a remove packet for every mob they could see and never another block
 * update. Vanilla gets away with that because its death screen is already up.
 * The death camera keeps showing the world — so one second in, the iron golem
 * that threw you vanished, nothing moved again, and the body landed in a world
 * that had stopped. Measured in a client gametest: every client entity but the
 * body gone on tick 19, the frame static from landing to the death screen.
 *
 * <p>So while the client says it is watching, the server holds back exactly
 * that removal and nothing else: the body is still dead, still untargetable and
 * unhittable, its position still the server's own. Released when the client
 * says the death screen has faded in, at the respawn, on disconnect — and by a
 * hard cap, because a dead player in the level still counts against everyone
 * else's night skip and a client that never sent its release must not be able
 * to hold that forever. A release lets vanilla's removal run on the next tick,
 * poof and all.
 */
public final class DeathHold {
	private DeathHold() {}

	/** Whose bodies are held, and the game time each hold began. Server thread only. */
	private static final Map<UUID, Long> HOLDS = new HashMap<>();

	public static void init() {
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				HOLDS.remove(oldPlayer.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				HOLDS.remove(handler.player.getUUID()));
	}

	/** The client's word. Only a dead player can be held; a living one asking is ignored. */
	public static void request(ServerPlayer player, boolean watching) {
		if (watching && player.isDeadOrDying()) {
			HOLDS.putIfAbsent(player.getUUID(), player.level().getGameTime());
		} else {
			HOLDS.remove(player.getUUID());
		}
	}

	/** Whether this player's removal is being held back right now. Expires the hold past the cap. */
	public static boolean holding(ServerPlayer player) {
		return holdingAt(player, player.level().getGameTime());
	}

	/** {@link #holding} at a given game time, so the cap is testable inside one tick. */
	public static boolean holdingAt(ServerPlayer player, long gameTime) {
		Long since = HOLDS.get(player.getUUID());
		if (since == null) {
			return false;
		}
		if (!player.isDeadOrDying() || gameTime - since > Balance.DEATH_HOLD_MAX_TICKS) {
			HOLDS.remove(player.getUUID());
			return false;
		}
		return true;
	}

	/** For the gametests, which drive the hold without a client. */
	public static void clear() {
		HOLDS.clear();
	}
}
