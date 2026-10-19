package dev.captience.realisticdamage.net;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.damage.DeathKind;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * "This body just died, like this" — for everyone watching it (1.7).
 *
 * <p>{@link DeathPayload} goes to the one who died, for their death camera.
 * This goes to every other client tracking the body, so that what they see is
 * a body that falls the way it was hit — thrown by a blast, tumbling, landing
 * flat — rather than vanilla's stiff tip onto its side wherever the server's
 * copy happened to stop. The motion itself is simulated on each watching
 * client from this one launch: a dead player's own client stops sending its
 * position, so the server has no flight to relay.
 *
 * @param entityId the body
 * @param kind     what killed it
 * @param violence 0..1, see {@code DeathMath}
 * @param vx       launch velocity, blocks a tick
 */
public record CorpsePayload(int entityId, DeathKind kind, float violence, float vx, float vy, float vz)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CorpsePayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("corpse"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CorpsePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CorpsePayload::entityId,
			ByteBufCodecs.VAR_INT.map(DeathKind::byOrdinal, DeathKind::ordinal), CorpsePayload::kind,
			ByteBufCodecs.FLOAT, CorpsePayload::violence,
			ByteBufCodecs.FLOAT, CorpsePayload::vx,
			ByteBufCodecs.FLOAT, CorpsePayload::vy,
			ByteBufCodecs.FLOAT, CorpsePayload::vz,
			CorpsePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
