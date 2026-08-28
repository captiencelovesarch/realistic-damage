package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.net.PressurePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Turns "holding use with empty hands" into a pressure request, once per tick
 * for as long as it is held.
 *
 * <p>Sent as a repeated claim rather than a start/stop pair: there is no stop
 * message to be lost, so a dropped packet or a crash simply lets the hold lapse
 * on the server instead of pinning someone in place indefinitely.
 *
 * <p>Nothing here decides anything. The client asks only when asking could
 * plausibly be granted — which keeps it quiet rather than making it
 * authoritative — and the server re-checks range, consciousness and bleeding.
 */
public final class RescueInput {
	private RescueInput() {}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.gui.screen() != null
				|| player.isSpectator() || !player.isAlive()) {
			return;
		}
		if (!client.options.keyUse.isDown() || !player.getMainHandItem().isEmpty()) {
			return;
		}
		Integer target = target(client, player);
		if (target != null) {
			ClientPlayNetworking.send(new PressurePayload(target));
		}
	}

	/**
	 * Who the hold would apply to, or null when there is nothing worth asking
	 * about. Both cases are gated on someone actually bleeding, so idly holding
	 * right-click with an empty hand sends nothing at all.
	 */
	private static Integer target(Minecraft client, LocalPlayer player) {
		Entity looked = client.crosshairPickEntity;
		if (looked instanceof Player other && other != player
				&& other.distanceTo(player) <= Balance.RESCUE_REACH) {
			BodySnapshot snapshot = other.getAttached(RDAttachments.SNAPSHOT);
			if (snapshot != null && snapshot.unconscious() && bleeding(snapshot)) {
				return other.getId();
			}
			return null;
		}
		BodySnapshot own = ClientState.snapshot();
		return own != null && bleeding(own) ? PressurePayload.SELF : null;
	}

	private static boolean bleeding(BodySnapshot snapshot) {
		return snapshot.woundMask() != 0;
	}
}
