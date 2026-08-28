package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serverbound: "I am still holding a wound shut." Sent every tick the key is
 * down and simply stops when it is released, so there is no matching stop
 * message to lose — a dropped packet lapses the hold rather than sticking it on
 * forever.
 *
 * <p>The client only ever asks. The server checks range, consciousness and
 * whether the target is bleeding at all before agreeing.
 *
 * @param targetId entity id being treated, or -1 for the sender's own wounds
 */
public record PressurePayload(int targetId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PressurePayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("pressure"));

	/** Sentinel for "myself"; the sender's own id is not trusted from the client. */
	public static final int SELF = -1;

	public static final StreamCodec<RegistryFriendlyByteBuf, PressurePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PressurePayload::targetId,
			PressurePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
