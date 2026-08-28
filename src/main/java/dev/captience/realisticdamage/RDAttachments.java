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
 * never synced. No {@code copyOnDeath()}, so death-respawn drops it and the
 * initializer hands the fresh player a pristine body — the spec's death reset
 * with zero bookkeeping. Dimension changes and End-return respawns keep it.</li>
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

	public static final AttachmentType<BodySnapshot> SNAPSHOT = AttachmentRegistry.create(
			RealisticDamage.id("snapshot"),
			builder -> builder
					.initializer(BodySnapshot::healthy)
					.syncWith(BodySnapshot.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

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
