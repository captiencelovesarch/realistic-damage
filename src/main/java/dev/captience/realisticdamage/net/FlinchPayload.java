package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "This body was just hit here, from there, this hard" (active ragdolls phase
 * 5): a living body's flinch, played on every watching client on top of its
 * animation. Not for killing blows (those are {@link CorpsePayload}).
 *
 * @param entityId the body
 * @param part     the region struck ({@code BodyPart} ordinal)
 * @param hx       the way the blow travelled, unit (zero for none)
 * @param strength 0..1
 */
public record FlinchPayload(int entityId, int part, float hx, float hy, float hz, float strength)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FlinchPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("flinch"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FlinchPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FlinchPayload::entityId,
			ByteBufCodecs.VAR_INT, FlinchPayload::part,
			ByteBufCodecs.FLOAT, FlinchPayload::hx,
			ByteBufCodecs.FLOAT, FlinchPayload::hy,
			ByteBufCodecs.FLOAT, FlinchPayload::hz,
			ByteBufCodecs.FLOAT, FlinchPayload::strength,
			FlinchPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
