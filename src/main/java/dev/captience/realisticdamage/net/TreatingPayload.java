package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Clientbound: who you are currently treating, so their body can be shown next
 * to your own. Sent only when it changes.
 *
 * <p>The body state itself needs no message — snapshots already sync to every
 * client, which 1.0 chose deliberately so remote players could be rendered
 * prone. This just says which one to draw.
 *
 * @param targetId entity id being treated, or -1 for nobody
 */
public record TreatingPayload(int targetId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TreatingPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("treating"));

	public static final int NOBODY = -1;

	public static final StreamCodec<RegistryFriendlyByteBuf, TreatingPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TreatingPayload::targetId,
			TreatingPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
