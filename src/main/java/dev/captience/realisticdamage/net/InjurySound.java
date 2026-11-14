package dev.captience.realisticdamage.net;

/**
 * The injury events that make a noise. Sent as an ordinal so the server never
 * has to know which of the two style variants the receiving client will
 * actually play — style is presentation, and presentation is the client's.
 */
public enum InjurySound {
	/** A bone goes. */
	FRACTURE("fracture"),
	/** A limb is destroyed outright. */
	DESTRUCTION("destruction"),
	/** Consciousness runs out and you go down. */
	COLLAPSE("collapse"),
	/**
	 * A player's yell as a bone goes (1.7). One voice, not two styles: a scream
	 * is a person, not a sound design choice, so "muted" has nothing to soften.
	 */
	SCREAM_SHARP("scream_sharp", Kind.VOICE),
	/** The long scream of losing a limb (1.7). */
	SCREAM_SEVERE("scream_severe", Kind.VOICE),
	/** A neck snaps (2.0): one dry crack, and nothing after it. */
	NECK_CRACK("neck_crack"),
	// From here on (2.0 audio) an event is a recipe on the client and nothing
	// else: no sound event of its own is registered. New ones go at the end,
	// because the wire format is the ordinal.
	/** An edged blow takes the head. */
	DECAPITATION("decapitation", Kind.RECIPE),
	/** Blood landing on the ground. */
	BLOOD_SPLAT("blood_splat", Kind.RECIPE),
	/** A cut or stab opens a wound that bleeds. */
	WOUND_OPEN("wound_open", Kind.RECIPE);

	private enum Kind {
		/** Has the 1.x muted and visceral sound events as well as recipes. */
		STYLED,
		/** A voice: one sound event, played as it is. */
		VOICE,
		/** Recipes only. */
		RECIPE
	}

	private final String id;
	private final Kind kind;

	InjurySound(String id) {
		this(id, Kind.STYLED);
	}

	InjurySound(String id, Kind kind) {
		this.id = id;
		this.kind = kind;
	}

	public String id() {
		return id;
	}

	public static @org.jspecify.annotations.Nullable InjurySound byId(String id) {
		for (InjurySound sound : values()) {
			if (sound.id.equals(id)) {
				return sound;
			}
		}
		return null;
	}

	/**
	 * Whether the common registry holds sound events for this one. Recipes are
	 * played from files by the client, so they register nothing, and a server
	 * without the newest events still agrees with a client that has them.
	 */
	public boolean hasSoundEvents() {
		return kind != Kind.RECIPE;
	}

	/** Whether this is a voice rather than the body itself. */
	public boolean isScream() {
		return this == SCREAM_SHARP || this == SCREAM_SEVERE;
	}

	/** Sound event path for one of the two styles; the screams have only the one. */
	public String assetPath(boolean visceral) {
		return kind == Kind.STYLED ? id + (visceral ? "_visceral" : "_muted") : id;
	}
}
