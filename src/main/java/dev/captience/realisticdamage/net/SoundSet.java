package dev.captience.realisticdamage.net;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The two complete sets of injury sounds (2.0). Which one a player hears is
 * their own setting; the server only ever names one when a test command asks
 * for a particular set by name.
 */
public enum SoundSet {
	/** Sounds like Minecraft: the game's own sounds, layered and re-pitched. */
	ARCADE,
	/** Higher quality and gruesome: the mod's own synthesised sounds. */
	GROUNDED;

	/** Folder name under {@code rd_sounds/}, and the word the test command takes. */
	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable SoundSet byId(String id) {
		for (SoundSet set : values()) {
			if (set.id().equals(id)) {
				return set;
			}
		}
		return null;
	}
}
