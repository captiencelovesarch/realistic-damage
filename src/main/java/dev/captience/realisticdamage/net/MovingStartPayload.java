package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "The body about to die here was going somewhere" (stumbling deaths, IK
 * phase 1): sent by {@code /rdmg ragdoll <kind> ... walking|sprinting} just
 * before the body's {@link CorpsePayload}, on the same connection, so it
 * always arrives first. The watching client then starts that ragdoll as if
 * it had been walking or running that way, mid-stride, instead of standing
 * where the test body stood.
 *
 * @param entityId the body
 * @param vx       how it was moving, blocks a second, world
 */
public record MovingStartPayload(int entityId, float vx, float vz) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MovingStartPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("moving_start"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MovingStartPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MovingStartPayload::entityId,
			ByteBufCodecs.FLOAT, MovingStartPayload::vx,
			ByteBufCodecs.FLOAT, MovingStartPayload::vz,
			MovingStartPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
