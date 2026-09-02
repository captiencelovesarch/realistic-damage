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
 * <p>Carries the damage category since 1.5, which is what lets the screen say
 * what happened rather than only that something did. A cut, a puncture, a club
 * and a blast are four different physical events and used to produce one
 * identical white flash; the category is the whole cost of telling them apart,
 * because the client already has the profile table to interpret it with.
 *
 * @param magnitude 0 to 1, how hard it was
 * @param yaw       direction the blow came from, in degrees, for the camera kick
 * @param category  ordinal of the {@code DamageCategory} that produced it
 */
public record ImpactPayload(float magnitude, float yaw, int category) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ImpactPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("impact"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ImpactPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, ImpactPayload::magnitude,
			ByteBufCodecs.FLOAT, ImpactPayload::yaw,
			ByteBufCodecs.VAR_INT, ImpactPayload::category,
			ImpactPayload::new);

	/** The category this cue describes, defensively clamped to a real one. */
	public dev.captience.realisticdamage.damage.DamageCategory resolved() {
		var values = dev.captience.realisticdamage.damage.DamageCategory.values();
		return category < 0 || category >= values.length
				? dev.captience.realisticdamage.damage.DamageCategory.GENERIC
				: values[category];
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
