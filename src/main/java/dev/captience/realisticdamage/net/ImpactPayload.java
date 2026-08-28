package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A one-shot "that hurt" cue. The synced snapshot carries state, not events, so
 * a heavy blow needs its own message for the client to react on the exact frame
 * it lands.
 *
 * @param magnitude 0 to 1, how hard it was
 * @param yaw       direction the blow came from, in degrees, for the camera kick
 */
public record ImpactPayload(float magnitude, float yaw) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ImpactPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("impact"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ImpactPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, ImpactPayload::magnitude,
			ByteBufCodecs.FLOAT, ImpactPayload::yaw,
			ImpactPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
