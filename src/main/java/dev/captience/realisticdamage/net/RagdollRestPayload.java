package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "This knocked-down body's ragdoll has come to rest here" (items.8), from a
 * client to the server: where the torso lies, so the server can put the body
 * there. The ragdoll only exists on clients; this is how it becomes the body.
 *
 * @param entityId the mob
 * @param x        the torso, world blocks
 */
public record RagdollRestPayload(int entityId, double x, double y, double z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RagdollRestPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("ragdoll_rest"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RagdollRestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RagdollRestPayload::entityId,
			ByteBufCodecs.DOUBLE, RagdollRestPayload::x,
			ByteBufCodecs.DOUBLE, RagdollRestPayload::y,
			ByteBufCodecs.DOUBLE, RagdollRestPayload::z,
			RagdollRestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
