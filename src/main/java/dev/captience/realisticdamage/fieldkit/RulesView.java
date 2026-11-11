package dev.captience.realisticdamage.fieldkit;

import java.util.Map;

/**
 * The client's copy of the server's settings (2.0): what the server last sent
 * in a {@link RulesPayload}. Never consulted to decide anything — the server
 * decides — only to draw: which half the creative tab shows, whether a tooltip
 * says an item is switched off, and what the World rules page reads out.
 *
 * <p>Before anything has arrived (the title screen, Mod Menu with no world
 * open) it answers from this install's own config file, which is what a
 * singleplayer world will use when one is opened.
 *
 * <p>Lives in the common source set because the creative tab's generator is
 * registered there; only the client ever fills it.
 */
public final class RulesView {
	private RulesView() {}

	private static volatile Map<String, String> values = Map.of();
	private static volatile boolean canEdit;
	private static volatile boolean received;
	private static volatile int version;

	/** A new payload from the server. */
	public static void accept(RulesPayload payload) {
		values = Map.copyOf(payload.values());
		canEdit = payload.canEdit();
		received = true;
		version++;
	}

	/** Back to "nothing received", on leaving a world or server. */
	public static void clear() {
		values = Map.of();
		canEdit = false;
		received = false;
		version++;
	}

	public static boolean received() {
		return received;
	}

	/** May this player change the server's settings? Answered by the server, from {@code /rdmg}'s own permission. */
	public static boolean canEdit() {
		return canEdit;
	}

	/** Every setting by field name, in the server's own words ({@code true}, {@code 1.00}, {@code nearby}). */
	public static Map<String, String> values() {
		return values;
	}

	/** Bumped on every change, so a screen can tell it needs to redraw. */
	public static int version() {
		return version;
	}

	public static boolean on(Half half) {
		if (!received) {
			return half.on();
		}
		return Boolean.parseBoolean(values.get(half.field()));
	}
}
