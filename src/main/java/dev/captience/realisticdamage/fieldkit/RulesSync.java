package dev.captience.realisticdamage.fieldkit;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.command.RealisticDamageCommands;
import dev.captience.realisticdamage.config.ServerConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Keeps every client's {@link RulesView} in step with the server (2.0).
 *
 * <p>Sent on join, and again whenever any setting changes, by whatever route:
 * {@code /rdmg config}, {@code /rdmg preset}, the World rules page in
 * singleplayer writing the config directly, the first-launch screen. Watching
 * the values rather than hooking each route is the point — a route added later
 * cannot forget to tell the clients, which is the same argument the command
 * makes for being generated from the fields. The check is a few dozen field
 * reads twice a second.
 *
 * <p>Also re-sent to one player when their own permission changes ({@code /op},
 * {@code /deop}), so the page unlocks and locks without a rejoin.
 */
public final class RulesSync {
	private RulesSync() {}

	/** Half a second: quick enough to feel immediate on a settings page, cheap enough to be free. */
	private static final int INTERVAL = 10;

	/** The same permission {@code /rdmg} asks for, so the page and the command can never disagree. */
	private static final Predicate<CommandSourceStack> MAY_EDIT =
			PermissionPredicates.require(RealisticDamage.id("command/debug"), PermissionLevel.GAMEMASTERS);

	private static Map<String, String> lastSent = Map.of();
	private static final Map<UUID, Boolean> LAST_CAN_EDIT = new HashMap<>();

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> send(handler.player, values()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				LAST_CAN_EDIT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			lastSent = Map.of();
			LAST_CAN_EDIT.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(RulesSync::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % INTERVAL != 0) {
			return;
		}
		Map<String, String> now = values();
		boolean changed = !now.equals(lastSent);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Boolean before = LAST_CAN_EDIT.get(player.getUUID());
			if (changed || before == null || before != canEdit(player)) {
				send(player, now);
			}
		}
		lastSent = now;
	}

	/** Tells every player now, without waiting for a tick: for a change made while the game is paused. */
	public static void push(MinecraftServer server) {
		Map<String, String> now = values();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			send(player, now);
		}
		lastSent = now;
	}

	public static void send(ServerPlayer player, Map<String, String> values) {
		boolean canEdit = canEdit(player);
		LAST_CAN_EDIT.put(player.getUUID(), canEdit);
		if (ServerPlayNetworking.canSend(player, RulesPayload.TYPE)) {
			ServerPlayNetworking.send(player, new RulesPayload(values, canEdit));
		}
	}

	public static boolean canEdit(ServerPlayer player) {
		return MAY_EDIT.test(player.createCommandSourceStack());
	}

	/** Every setting by name, in the command's order and in the command's words. */
	public static Map<String, String> values() {
		Map<String, String> out = new LinkedHashMap<>();
		ServerConfig config = ServerConfig.get();
		for (Field field : RealisticDamageCommands.settingFields()) {
			out.put(field.getName(), word(field, config));
		}
		return out;
	}

	/** What {@code /rdmg config} prints for a value, and accepts back. */
	public static String word(Field field, ServerConfig config) {
		try {
			Object value = field.get(config);
			if (value instanceof Double number) {
				return String.format(Locale.ROOT, "%.2f", number);
			}
			if (value instanceof Enum<?> constant) {
				return constant.name().toLowerCase(Locale.ROOT);
			}
			return String.valueOf(value);
		} catch (IllegalAccessException e) {
			return "?";
		}
	}

	/** A word back into a value of the field's type: the inverse of {@link #word}. */
	public static Object parse(Field field, String word) {
		Class<?> type = field.getType();
		if (type == boolean.class) {
			if (!word.equals("true") && !word.equals("false")) {
				throw new IllegalArgumentException(word + " is not true or false");
			}
			return Boolean.parseBoolean(word);
		}
		if (type == double.class) {
			return Double.parseDouble(word);
		}
		if (type == int.class) {
			return Integer.parseInt(word);
		}
		if (type.isEnum()) {
			for (Object constant : type.getEnumConstants()) {
				if (((Enum<?>) constant).name().equalsIgnoreCase(word)) {
					return constant;
				}
			}
		}
		throw new IllegalArgumentException(word + " is not a " + type.getSimpleName());
	}
}
