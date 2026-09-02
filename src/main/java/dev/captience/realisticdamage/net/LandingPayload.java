package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The ground arriving (1.4). Distinct from {@link ImpactPayload} because a
 * landing is not a blow: it comes from directly below, it throws the view down
 * and forward rather than aside, and how much give the ground had changes what
 * it should sound like. Sharing the generic impact cue made every fall feel
 * like being punched by the floor.
 *
 * @param severity 0 to 1, scaled off drop height
 * @param hardness 0 soft (hay, leaves) to 1 hard (stone, iron), for the sound
 */
public record LandingPayload(float severity, float hardness) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<LandingPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("landing"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LandingPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, LandingPayload::severity,
			ByteBufCodecs.FLOAT, LandingPayload::hardness,
			LandingPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
