package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;

/**
 * What death forgets and what it keeps (1.6). Fabric drops DATA on a death
 * respawn because it has no {@code copyOnDeath}, and that stays true — the reset
 * is still "no bookkeeping", and every field added later is still forgotten by
 * default. The permanent marks are copied forward instead: at COPY_FROM, before
 * Fabric's own attachment transfer runs, a fresh body carrying only what is
 * meant to outlive the old one is put on the new player. Forget by default,
 * carry what is listed.
 */
public final class RespawnCarryover {
	private RespawnCarryover() {}

	public static void init() {
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			if (alive) {
				return;   // End return: Fabric transfers DATA wholesale; leave a living body alone
			}
			// getAttached, not RDAttachments.get(): never attach to a removed entity.
			RealisticDamageData old = oldPlayer.getAttached(RDAttachments.DATA);
			if (old == null) {
				return;   // died before anything ever touched the body; pristine is right
			}
			newPlayer.setAttached(RDAttachments.DATA,
					RealisticDamageData.freshCarrying(old, ServerConfig.get().enableLongTermInjuries));
			// No pushSnapshot here: setId and the respawn packet have not run yet
			// (PlayerList.respawn, :392 and after), so a snapshot pushed now would be
			// addressed to an entity id the client has not been told about.
			// TickDispatcher pushes on the next END_SERVER_TICK anyway.
		});

		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (alive) {
				return;
			}
			RealisticDamageData data = newPlayer.getAttached(RDAttachments.DATA);
			if (data == null) {
				return;
			}
			for (BodyPart part : BodyPart.values()) {
				if (data.part(part).ceilingBand() > 0) {
					// The new body is AT its ceilings, so every region reads full and
					// the HUD stays quiet. Without this line nothing whatsoever would
					// tell you the marks came with you.
					Notifier.message(newPlayer, "realisticdamage.msg.ceiling.carried");
					return;
				}
			}
		});
	}
}
