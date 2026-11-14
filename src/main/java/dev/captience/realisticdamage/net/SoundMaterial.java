package dev.captience.realisticdamage.net;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * What the body that made the sound is made of (2.0). The server knows the
 * body; the client knows what each material should sound like.
 */
public enum SoundMaterial {
	/** Anything that bleeds. */
	FLESH,
	/** Bone and nothing else: the skeleton family. No wet layers. */
	DRY;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static @Nullable SoundMaterial byId(String id) {
		for (SoundMaterial material : values()) {
			if (material.id().equals(id)) {
				return material;
			}
		}
		return null;
	}
}
