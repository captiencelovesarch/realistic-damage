package dev.captience.realisticdamage.client;

/** Counter for the mob lay-out pose (1.7), so the client gametest can prove the hook fired. */
public final class DownPose {
	private DownPose() {}

	private static long applications;

	public static void applied() {
		applications++;
	}

	public static long applications() {
		return applications;
	}
}
