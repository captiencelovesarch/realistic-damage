package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "You are dead, and this is how hard it happened" (1.5).
 *
 * <p>Sent once, from AFTER_DEATH, and only when the death had any violence to
 * report — the client's fade defaults to the slow ending, so a bleed-out or a
 * void death needs no packet at all. Its own message rather than a snapshot
 * field for the usual reason (death is an event, the snapshot carries state),
 * and sent after death is <em>confirmed</em> rather than when the lethal blow
 * is seen, because the gap between those two is where a totem fires.
 *
 * @param violence 0 (slipped away) to 1 (obliterated); see {@code DeathMath}
 */
public record DeathPayload(float violence) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<DeathPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("death"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeathPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, DeathPayload::violence,
			DeathPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
