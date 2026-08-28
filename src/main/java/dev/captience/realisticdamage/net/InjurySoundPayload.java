package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "Something just broke on somebody." Sent instead of a world sound so that
 * each client can play its own chosen style, and so your own injuries can be
 * loud and close while someone else's are distant.
 *
 * @param sound which event it was
 * @param self  true if this is the receiving player's own body
 * @param x     where it happened; ignored when {@code self}, which plays
 *              non-positionally because it is happening inside your own ears
 */
public record InjurySoundPayload(InjurySound sound, boolean self, double x, double y, double z)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<InjurySoundPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("injury_sound"));

	private static final InjurySound[] VALUES = InjurySound.values();

	public static final StreamCodec<RegistryFriendlyByteBuf, InjurySoundPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> VALUES[Math.clamp(i, 0, VALUES.length - 1)], InjurySound::ordinal),
			InjurySoundPayload::sound,
			ByteBufCodecs.BOOL, InjurySoundPayload::self,
			ByteBufCodecs.DOUBLE, InjurySoundPayload::x,
			ByteBufCodecs.DOUBLE, InjurySoundPayload::y,
			ByteBufCodecs.DOUBLE, InjurySoundPayload::z,
			InjurySoundPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
