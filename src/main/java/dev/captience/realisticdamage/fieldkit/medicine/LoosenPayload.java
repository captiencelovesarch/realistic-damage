package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RealisticDamage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Take my tourniquet off" (2.0, phase 4): a sneak-use in the air with an empty hand. */
public record LoosenPayload() implements CustomPacketPayload {
	public static final LoosenPayload INSTANCE = new LoosenPayload();
	public static final CustomPacketPayload.Type<LoosenPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("loosen"));
	public static final StreamCodec<ByteBuf, LoosenPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
