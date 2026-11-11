package dev.captience.realisticdamage.fieldkit.client;

import java.lang.reflect.Field;
import java.util.Locale;

import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.fieldkit.RulesSync;
import dev.captience.realisticdamage.fieldkit.RulesView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;

/**
 * Reading and changing the server's settings from a screen (2.0), whichever
 * kind of game this is.
 *
 * <ul>
 *   <li><b>Local</b> — the title screen, or a singleplayer world (opened to LAN
 *   or not): the settings are this install's own file, and the world's server
 *   runs in this process. A change is written straight to the config, on the
 *   server's thread when there is one, and saved.</li>
 *   <li><b>Remote</b> — a server elsewhere: the values are the copy the server
 *   sent, and an operator's change goes to the server as {@code /rdmg config},
 *   so the server's own permission check and its own broadcast to operators
 *   apply exactly as if it had been typed. Anyone else reads only.</li>
 * </ul>
 */
public final class LocalRules {
	private LocalRules() {}

	public static boolean remote() {
		Minecraft client = Minecraft.getInstance();
		return client.getSingleplayerServer() == null && client.getConnection() != null;
	}

	public static boolean canEdit() {
		return !remote() || RulesView.canEdit();
	}

	/** A setting in the command's words: {@code true}, {@code 1.25}, {@code nearby}. */
	public static String read(Field field) {
		if (remote()) {
			return RulesView.values().getOrDefault(field.getName(), "?");
		}
		return RulesSync.word(field, ServerConfig.get());
	}

	/** Changes one setting. Refused quietly when this player may not. */
	public static void write(Field field, String word) {
		if (remote()) {
			ClientPacketListener connection = Minecraft.getInstance().getConnection();
			if (connection != null && RulesView.canEdit()) {
				connection.sendCommand("rdmg config " + field.getName() + " " + word);
			}
			return;
		}
		IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
		Runnable set = () -> {
			try {
				field.set(ServerConfig.get(), parse(field, word));
				ServerConfig.save();
				// A settings screen pauses singleplayer, and a paused server runs
				// no ticks to notice the change in, so say it now.
				if (server != null) {
					RulesSync.push(server);
				}
			} catch (IllegalAccessException | IllegalArgumentException e) {
				RealisticDamage.LOGGER.error("Could not set {} to {}", field.getName(), word, e);
			}
		};
		if (server != null) {
			server.execute(set);
		} else {
			set.run();
		}
	}

	static Object parse(Field field, String word) {
		return RulesSync.parse(field, word);
	}

	/** The command's word for a value. */
	public static String word(Object value) {
		if (value instanceof Double number) {
			return String.format(Locale.ROOT, "%.2f", number);
		}
		if (value instanceof Enum<?> constant) {
			return constant.name().toLowerCase(Locale.ROOT);
		}
		return String.valueOf(value);
	}
}
