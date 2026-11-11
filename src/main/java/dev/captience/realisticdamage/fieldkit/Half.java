package dev.captience.realisticdamage.fieldkit;

import com.mojang.serialization.Codec;
import dev.captience.realisticdamage.config.ServerConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;

/**
 * The field kit's two halves (2.0), each behind its own server switch.
 *
 * <p><b>Weapons</b> is anything that hurts someone or protects you from being
 * hurt; <b>Medicine</b> is anything that treats, supports or reads an injury that
 * already happened. Every field kit item, effect and potion belongs to exactly
 * one, and everything that makes, shows or uses one asks its half first.
 *
 * <p>Two places hold the answer. The server's is {@link ServerConfig} and is the
 * only one that decides anything. A client connected to a server cannot read the
 * server's file, so it keeps a copy the server sends ({@link RulesView}), used
 * only to draw: the creative tab, a tooltip, the World rules page. Server-side
 * code asks {@link #on()}; code that may run on either side asks
 * {@link #on(Level)}.
 */
public enum Half implements StringRepresentable {
	WEAPONS("weapons", "enableWeapons"),
	MEDICINE("medicine", "enableMedicine");

	public static final Codec<Half> CODEC = StringRepresentable.fromEnum(Half::values);
	public static final StreamCodec<ByteBuf, Half> STREAM_CODEC =
			ByteBufCodecs.VAR_INT.map(i -> values()[i], Half::ordinal);

	private final String id;
	private final String field;

	Half(String id, String field) {
		this.id = id;
		this.field = field;
	}

	/** The server's answer: is this half switched on? */
	public boolean on() {
		return on(ServerConfig.get());
	}

	public boolean on(ServerConfig config) {
		return this == WEAPONS ? config.enableWeapons : config.enableMedicine;
	}

	/** The server's answer on a server level, the synced copy on a client one. */
	public boolean on(Level level) {
		return level.isClientSide() ? RulesView.on(this) : on();
	}

	public void set(ServerConfig config, boolean value) {
		if (this == WEAPONS) {
			config.enableWeapons = value;
		} else {
			config.enableMedicine = value;
		}
	}

	/** The {@link ServerConfig} field, which is also the {@code /rdmg config} word. */
	public String field() {
		return field;
	}

	public String id() {
		return id;
	}

	/** "Weapons" or "Medicine", for messages and tooltips. */
	public Component title() {
		return Component.translatable("realisticdamage.fieldkit.half." + id);
	}

	@Override
	public String getSerializedName() {
		return id;
	}
}
