package dev.captience.realisticdamage;

import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.persist.RDCodecs;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;

/**
 * Two attachments (spec §46-48):
 *
 * <ul>
 * <li>{@link #DATA} — the mutable server-authoritative master state. Persistent,
 * never synced. No {@code copyOnDeath()}, so death-respawn still drops it and
 * every field is still forgotten by default — the spec's death reset with zero
 * bookkeeping, and the rule that keeps each new field from silently outliving a
 * death. Since 1.6 there is one documented exception: {@code RespawnCarryover}
 * puts a fresh body carrying the listed permanent marks on the new player
 * first. Dimension changes and End-return respawns keep the old data.</li>
 * <li>{@link #SNAPSHOT} — immutable quantized presentation state, auto-synced to
 * the owning client on join/respawn/dimension-change and on every effective
 * change ({@code Objects.equals} dedup). Not persistent.</li>
 * </ul>
 */
public final class RDAttachments {
	public static final AttachmentType<RealisticDamageData> DATA = AttachmentRegistry.create(
			RealisticDamage.id("data"),
			builder -> builder
					.initializer(RealisticDamageData::new)
					.persistent(RDCodecs.DATA));

	// Synced to ALL clients (not just the owner): remote clients need the
	// unconscious flag to render prone poses, and it keeps future teammate
	// body-HUDs possible. The snapshot is ~60 bytes and equality-deduped.
	/**
	 * The id suffix is the wire version: bump it whenever {@link BodySnapshot}'s
	 * wire shape changes. There is no version field inside the payload and a 1.5
	 * decoder could not branch on one anyway, so a shared id would let Fabric sync
	 * a 1.6 snapshot to a 1.5 client, whose shorter composite stops early, leaves
	 * bytes in the buffer and trips the decoder's length check — a disconnect one
	 * tick after every join. A different id is never offered in the handshake at
	 * all, the client's snapshot stays null, and every reader here already
	 * tolerates null. A blank HUD beats a kick loop.
	 */
	public static final AttachmentType<BodySnapshot> SNAPSHOT = AttachmentRegistry.create(
			RealisticDamage.id("snapshot_v2"),
			builder -> builder
					.initializer(BodySnapshot::healthy)
					.syncWith(BodySnapshot.STREAM_CODEC, AttachmentSyncPredicate.all()));

	public static RealisticDamageData get(ServerPlayer player) {
		return player.getAttachedOrCreate(DATA);
	}

	/** Recomputes and publishes the client snapshot; equal snapshots are not re-sent. */
	public static void pushSnapshot(ServerPlayer player) {
		player.setAttached(SNAPSHOT, BodySnapshot.of(get(player)));
	}

	/** Called from mod init to force classloading/registration. */
	public static void init() {}

	private RDAttachments() {}
}
