package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.net.SprayOwnerPayload;

/**
 * Whose blood the server's sprays are (2.0): the last few places a spray was
 * announced from, and the body each came out of. See {@link SprayOwnerPayload}.
 * A drop spawned at exactly one of those places belongs to that body, wherever
 * the body has got to since.
 */
public final class SprayOwners {
	private SprayOwners() {}

	private static final int SLOTS = 16;
	/** An announcement older than this, in client ticks, is for drops that have long since arrived. */
	private static final int KEEP_TICKS = 40;

	private static final double[] xs = new double[SLOTS];
	private static final double[] ys = new double[SLOTS];
	private static final double[] zs = new double[SLOTS];
	private static final int[] owners = new int[SLOTS];
	private static final long[] at = new long[SLOTS];
	private static int next;
	private static long tick;
	private static long matched;

	static {
		reset();
	}

	public static void announce(SprayOwnerPayload payload) {
		xs[next] = payload.x();
		ys[next] = payload.y();
		zs[next] = payload.z();
		owners[next] = payload.entityId();
		at[next] = tick;
		next = (next + 1) % SLOTS;
	}

	/** The body a drop spawned here was announced as coming out of, or -1. Exact: the place is copied, never computed. */
	public static int ownerAt(double x, double y, double z) {
		for (int i = 0; i < SLOTS; i++) {
			if (owners[i] >= 0 && tick - at[i] <= KEEP_TICKS && xs[i] == x && ys[i] == y && zs[i] == z) {
				matched++;
				return owners[i];
			}
		}
		return -1;
	}

	/** Drops matched to an announcement so far; read by the client gametests. */
	public static long matched() {
		return matched;
	}

	public static void tick() {
		tick++;
	}

	public static void reset() {
		java.util.Arrays.fill(owners, -1);
		next = 0;
	}
}
