package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Whose blood the spray about to arrive is (2.0).
 *
 * <p>The spray itself is a plain particle: a place and a velocity and nothing
 * else. The client used to work out whose it was from the place alone, by
 * finding the body standing around it, and a mob that had already been
 * knocked back by the blow was no longer standing there - so the first spray
 * out of a zombie came out red. This is sent just ahead of the drops, on the
 * same connection and so in order, and names the body outright. The place is
 * the exact one the drops are thrown from, which is how the client matches
 * them up.
 */
public record SprayOwnerPayload(int entityId, double x, double y, double z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SprayOwnerPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("spray_owner"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SprayOwnerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SprayOwnerPayload::entityId,
			ByteBufCodecs.DOUBLE, SprayOwnerPayload::x,
			ByteBufCodecs.DOUBLE, SprayOwnerPayload::y,
			ByteBufCodecs.DOUBLE, SprayOwnerPayload::z,
			SprayOwnerPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
