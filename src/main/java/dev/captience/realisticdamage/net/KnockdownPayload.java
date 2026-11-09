package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "This body was just thrown off its feet, like this, and will be down this
 * long" (active ragdolls phase 4): a blast or a heavy blow that did not kill.
 * The same launch a death carries ({@link CorpsePayload}), to everyone
 * watching and to the one knocked down, whose client runs the ragdoll and
 * carries their position with it.
 *
 * @param body  the launch, as a death would describe it
 * @param ticks how long the server keeps the body down
 */
public record KnockdownPayload(CorpsePayload body, int ticks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<KnockdownPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("knockdown"));

	public static final StreamCodec<RegistryFriendlyByteBuf, KnockdownPayload> CODEC = StreamCodec.composite(
			CorpsePayload.CODEC, KnockdownPayload::body,
			ByteBufCodecs.VAR_INT, KnockdownPayload::ticks,
			KnockdownPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
