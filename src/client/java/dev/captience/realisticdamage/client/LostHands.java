package dev.captience.realisticdamage.client;

/** What became of the hands that are gone (1.7 alpha.11); counted for the client gametests. */
public final class LostHands {
	private LostHands() {}

	private static long vrHandsSkipped;

	/** A lost hand's item that Vivecraft would have drawn on a controller, and did not. */
	public static void skippedInVr() {
		vrHandsSkipped++;
	}

	public static long vrHandsSkipped() {
		return vrHandsSkipped;
	}
}
