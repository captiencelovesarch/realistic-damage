package dev.captience.realisticdamage.fieldkit.client;

/**
 * How many regions the numb body diagram has drawn (2.0, phase 3), for the game
 * test: a hook that never runs draws nothing, and only a count can tell.
 */
public final class NumbHud {
	private NumbHud() {}

	private static int regions;

	public static void drew() {
		regions++;
	}

	public static int regions() {
		return regions;
	}
}
