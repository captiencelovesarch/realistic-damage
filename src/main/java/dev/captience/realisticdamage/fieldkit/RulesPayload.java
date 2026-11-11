package dev.captience.realisticdamage.fieldkit;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The server's settings, for the client to read (2.0).
 *
 * <p>Every {@code ServerConfig} field rather than only the two switches: the
 * World rules page shows all of them, read-only for a player who is not an
 * operator, and a client cannot read a server's config file. Values travel as
 * the same words {@code /rdmg config} prints and accepts, so the page can show
 * them as they are and send an operator's change back as a command.
 *
 * @param values  every setting by field name, in name order
 * @param canEdit whether this player passes {@code /rdmg}'s own permission check
 */
public record RulesPayload(Map<String, String> values, boolean canEdit) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RulesPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("rules"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RulesPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.<RegistryFriendlyByteBuf, String, String, Map<String, String>>map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8, 256),
			RulesPayload::values,
			ByteBufCodecs.BOOL, RulesPayload::canEdit,
			RulesPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
