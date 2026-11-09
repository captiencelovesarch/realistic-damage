package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "The body about to die here is the stepper's test body" (stumbling deaths,
 * IK phase 0): sent by {@code /rdmg ragdoll shove} just before the body's
 * {@link CorpsePayload}, on the same connection, so it always arrives first.
 * The watching client then holds that ragdoll on its feet and shoves it,
 * instead of letting it fall.
 *
 * @param entityId the body
 * @param vx       the shove, blocks a second, world
 */
public record StandTestPayload(int entityId, float vx, float vz) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<StandTestPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("stand_test"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StandTestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, StandTestPayload::entityId,
			ByteBufCodecs.FLOAT, StandTestPayload::vx,
			ByteBufCodecs.FLOAT, StandTestPayload::vz,
			StandTestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
