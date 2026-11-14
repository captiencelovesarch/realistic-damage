package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.net.RagdollRestPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The ragdoll is the body (items.8). A knocked-down mob's ragdoll flies and
 * tumbles on the clients, free, and the nearest watcher's client reports where
 * it is, every tick while it is thrown and again where it comes to rest; the
 * server puts the mob there across the ground (its fall stays the server's,
 * landing and all), so its hitbox, what arrows hit and what a hand finds to
 * drag are where the body is seen, in the air and lying.
 *
 * <p>Until now the ragdoll was made to follow the entity instead: softly, so it
 * could come to rest blocks from its hitbox (in a pit the entity never fell
 * into), and then hard, which made a flung body jerk every tick it outran its
 * entity. A player's own client already carries its position with its ragdoll;
 * this is the same thing for a mob, through the one client the server listens
 * to for it: the nearest player's, so two watchers never pull it two ways.
 *
 * <p>Checked: a mob that is down, a report within {@link #REACH} of where the
 * server has it, and a place its lying box fits. Once it has come to rest a
 * client reports again only if it has lain still well away from its entity,
 * so a body being dragged (the hand leads then) is not fought over.
 */
public final class RagdollRest {
	private RagdollRest() {}

	/** The farthest a report may move a body, blocks, across and up or down. */
	static final double REACH = 6.0;
	static final double RISE = 4.0;

	public static void receive(ServerPlayer reporter, RagdollRestPayload payload) {
		ServerLevel level = reporter.level();
		Entity found = level.getEntity(payload.entityId());
		if (!(found instanceof Mob mob)) {
			return;
		}
		place(level, mob, reporter, new Vec3(payload.x(), payload.y(), payload.z()));
	}

	/** Puts the mob where its ragdoll lies, if this report is the one to listen to. True when it moved. */
	public static boolean place(ServerLevel level, Mob mob, ServerPlayer reporter, Vec3 torso) {
		RealisticDamageData data = mob.getAttached(RDAttachments.DATA);
		if (!mob.isAlive() || data == null || !(data.unconscious() || data.knockedDown())) {
			return false;
		}
		Player nearest = level.getNearestPlayer(mob, 64.0);
		if (nearest != reporter) {
			return false;
		}
		Vec3 at = mob.position();
		if (Math.hypot(torso.x - at.x, torso.z - at.z) > REACH || Math.abs(torso.y - at.y) > RISE
				|| !Double.isFinite(torso.x + torso.y + torso.z)) {
			return false;
		}
		// Across the ground only. The height is the server's own: its fall
		// builds the fall distance, and its landing is what breaks a neck or a
		// leg; a body moved over a hole drops into it the server's way. Lifted
		// only to clear a step the ragdoll came to rest on.
		for (double lift : new double[] {0.0, 0.6, 1.1}) {
			Vec3 to = new Vec3(torso.x, at.y + lift, torso.z);
			AABB box = mob.getDimensions(mob.getPose()).makeBoundingBox(to);
			if (level.noCollision(mob, box)) {
				// An ordinary move, not a teleport: every watcher's client
				// interpolates it, as it does a walk.
				mob.setPos(to.x, to.y, to.z);
				mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
				return true;
			}
		}
		return false;
	}
}
