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
 * @param sound    which event it was
 * @param self     true if this is the receiving player's own body
 * @param x        where it happened; ignored when {@code self}, which plays
 *                 non-positionally because it is happening inside your own ears
 * @param strength how bad, 0 to 1 (2.0): the same event at a different weight
 * @param material what the body is made of (2.0)
 * @param forced   0 for the listener's own sound set, or a set's ordinal + 1
 *                 when a test command asked for that set by name
 */
public record InjurySoundPayload(InjurySound sound, boolean self, double x, double y, double z,
		float strength, SoundMaterial material, int forced)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<InjurySoundPayload> TYPE =
			new CustomPacketPayload.Type<>(RealisticDamage.id("injury_sound"));

	private static final InjurySound[] VALUES = InjurySound.values();
	private static final SoundMaterial[] MATERIALS = SoundMaterial.values();
	private static final SoundSet[] SETS = SoundSet.values();

	/** An ordinary event: full strength unless told otherwise, the listener's own set. */
	public InjurySoundPayload(InjurySound sound, boolean self, double x, double y, double z,
			float strength, SoundMaterial material) {
		this(sound, self, x, y, z, strength, material, 0);
	}

	/** The set a test command named, or null for the listener's own. */
	public @org.jspecify.annotations.Nullable SoundSet forcedSet() {
		return forced >= 1 && forced <= SETS.length ? SETS[forced - 1] : null;
	}

	public static int forcing(@org.jspecify.annotations.Nullable SoundSet set) {
		return set == null ? 0 : set.ordinal() + 1;
	}

	public static final StreamCodec<RegistryFriendlyByteBuf, InjurySoundPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(i -> VALUES[Math.clamp(i, 0, VALUES.length - 1)], InjurySound::ordinal),
			InjurySoundPayload::sound,
			ByteBufCodecs.BOOL, InjurySoundPayload::self,
			ByteBufCodecs.DOUBLE, InjurySoundPayload::x,
			ByteBufCodecs.DOUBLE, InjurySoundPayload::y,
			ByteBufCodecs.DOUBLE, InjurySoundPayload::z,
			ByteBufCodecs.FLOAT, InjurySoundPayload::strength,
			ByteBufCodecs.VAR_INT.map(i -> MATERIALS[Math.clamp(i, 0, MATERIALS.length - 1)], SoundMaterial::ordinal),
			InjurySoundPayload::material,
			ByteBufCodecs.VAR_INT, InjurySoundPayload::forced,
			InjurySoundPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
