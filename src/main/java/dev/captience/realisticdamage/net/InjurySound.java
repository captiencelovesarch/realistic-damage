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
	COLLAPSE("collapse");

	private final String id;

	InjurySound(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	/** Sound event path for one of the two styles. */
	public String assetPath(boolean visceral) {
		return id + (visceral ? "_visceral" : "_muted");
	}
}
