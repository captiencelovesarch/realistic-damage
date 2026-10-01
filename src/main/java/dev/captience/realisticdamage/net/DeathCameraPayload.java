package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Serverbound (1.7): "I am watching my body; keep me in the world." Sent true
 * when the death camera takes over a death and false once the death screen has
 * faded in over it. See {@code systems.DeathHold} for why the server has to
 * hear this at all, and why a true can never hold a body forever.
 *
 * @param watching whether the client's death camera is still looking at the world
 */
public record DeathCameraPayload(boolean watching) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DeathCameraPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("death_camera"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeathCameraPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, DeathCameraPayload::watching,
			DeathCameraPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
