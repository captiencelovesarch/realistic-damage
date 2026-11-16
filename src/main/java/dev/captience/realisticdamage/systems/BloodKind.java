package dev.captience.realisticdamage.systems;

import java.util.Locale;

/**
 * The shape a mark of blood takes on an item (2.0), which is decided by how
 * it got there. A slash drags blood along the blade; a thrust buries the tip
 * in it; a blunt blow leaves a splat where it landed; a heavy bleeder throws
 * specks; a kill or a severed limb soaks the working end; and a wielder
 * bleeding from their own arm has it run onto the grip.
 */
public enum BloodKind {
	SOAK,
	STREAK,
	TIP,
	SPLAT,
	SPECKS,
	GRIP;

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** The kind with this id; an id from a newer or older version reads as a plain soak. */
	public static BloodKind byId(String id) {
		for (BloodKind kind : values()) {
			if (kind.id().equals(id)) {
				return kind;
			}
		}
		return SOAK;
	}
}
