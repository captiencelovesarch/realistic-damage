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
	SCREAM_SHARP("scream_sharp", false),
	/** The long scream of losing a limb (1.7). */
	SCREAM_SEVERE("scream_severe", false);

	private final String id;
	private final boolean styled;

	InjurySound(String id) {
		this(id, true);
	}

	InjurySound(String id, boolean styled) {
		this.id = id;
		this.styled = styled;
	}

	public String id() {
		return id;
	}

	/** Whether this is a voice rather than the body itself. */
	public boolean isScream() {
		return this == SCREAM_SHARP || this == SCREAM_SEVERE;
	}

	/** Sound event path for one of the two styles; the screams have only the one. */
	public String assetPath(boolean visceral) {
		return styled ? id + (visceral ? "_visceral" : "_muted") : id;
	}
}
