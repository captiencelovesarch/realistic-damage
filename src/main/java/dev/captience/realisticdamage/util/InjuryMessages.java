package dev.captience.realisticdamage.util;

/**
 * Which of the mod's action-bar lines a player is allowed to switch off (1.6).
 *
 * <p>Every visible layer of this mod has its own control except the words, and
 * the words are the one output a player cannot refuse — so this is where the
 * refusal is defined. It lives beside {@link Notifier}, which is the single
 * choke point every line goes through, so that a key and its classification are
 * written in the same neighbourhood and are hard to add one without the other.
 *
 * <p>Deliberately free of Minecraft types. The decision is a fact about a
 * string, the client filter that consumes it is not somewhere a unit test can
 * reach, and a rule nobody can test is a rule that quietly stops being true.
 */
public final class InjuryMessages {
	private InjuryMessages() {}

	/** The namespace every line {@link Notifier#message} sends lives under. */
	public static final String PREFIX = "realisticdamage.msg.";

	/**
	 * The lines that answer a keypress rather than report a body: dragging
	 * someone, holding pressure on their wound, feeding them. Never suppressed.
	 *
	 * <p>They are the game acknowledging an action, not your body telling you
	 * something, and nothing else on screen carries them — the triage HUD draws
	 * the patient's condition but has exactly one string of its own, so no
	 * picture anywhere says "they slip from your grip" or "they cannot take any
	 * more". Swallowing these would break a feature rather than quiet an effect.
	 */
	private static final String[] INTERACTION = {
		PREFIX + "drag.", PREFIX + "pressure.", PREFIX + "feed."
	};

	/**
	 * Is this key one of ours, and is it your body reporting on itself?
	 *
	 * <p>The prefix test is what keeps the client filter from swallowing
	 * vanilla's {@code mount.onboard} or {@code record.nowPlaying}, or another
	 * mod's action bar: the namespace has to be ours before anything else is
	 * even considered.
	 */
	public static boolean isBodyReport(String key) {
		if (!key.startsWith(PREFIX)) {
			return false;
		}
		for (String interaction : INTERACTION) {
			if (key.startsWith(interaction)) {
				return false;
			}
		}
		return true;
	}
}
