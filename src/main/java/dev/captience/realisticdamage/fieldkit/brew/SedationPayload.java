package dev.captience.realisticdamage.fieldkit.brew;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Your own Sedation stage (2.0, phase 3), for your eyelids. Only the stage, an
 * ordinal of {@link SedationMath.Stage}: never the dose or the level, which are
 * as hidden as the meters they feed.
 */
public record SedationPayload(int stage) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SedationPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("sedation"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SedationPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SedationPayload::stage, SedationPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
