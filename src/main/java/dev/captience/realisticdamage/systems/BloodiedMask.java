package dev.captience.realisticdamage.systems;

/**
 * Where one mark of blood sits on an item's picture (2.0).
 *
 * <p>Given only which pixels of a sprite are solid, this decides which of
 * them a mark covers, and how thickly. It works in the sprite's own pixels,
 * so the blood on a sixteen-pixel sword is made of the same size of pixel as
 * the sword, and it works on any item from any mod or resource pack without
 * anybody painting a texture for it.
 *
 * <p>Vanilla draws every tool with its grip at the bottom left and its tip at
 * the top right, so everything here is measured along that diagonal, across
 * the pixels the sprite actually has: "the tip" of a dagger and of a
 * greatsword are both the top-right end of their own pixels.
 *
 * <p>Each {@link BloodKind} has its own shape. All of them grow with the
 * amount without ever losing a pixel they had (so a mark that takes more
 * blood spreads, it does not jump), and none uses a random source: the same
 * sprite, kind, amount and seed give the same pixels on every client.
 */
public final class BloodiedMask {
	private BloodiedMask() {}

	/** Nothing here. */
	public static final byte NONE = 0;
	/** A thin edge of a blot: lighter, and the item shows through it. */
	public static final byte THIN = 1;
	/** The body of a blot. */
	public static final byte BODY = 2;
	/** Where it pooled: the darkest, and the last to dry. */
	public static final byte POOL = 3;

	/** How bright this thickness is drawn, against the blood's colour: thin edges catch the light. */
	public static float brightness(byte thickness) {
		return switch (thickness) {
			case THIN -> 1.10F;
			case BODY -> 1.0F;
			case POOL -> 0.78F;
			default -> 0.0F;
		};
	}

	/** How much of the item this thickness hides. */
	public static float opacity(byte thickness) {
		return switch (thickness) {
			case THIN -> 0.64F;
			case BODY -> 0.86F;
			case POOL -> 0.95F;
			default -> 0.0F;
		};
	}

	/**
	 * @param solid  {@code solid[y][x]}: whether the sprite has a pixel there
	 * @param kind   the mark's shape
	 * @param amount 0 to 1
	 * @param seed   places the mark
	 * @return per pixel, one of {@link #NONE}, {@link #THIN}, {@link #BODY}, {@link #POOL}
	 */
	public static byte[][] paint(boolean[][] solid, BloodKind kind, float amount, long seed) {
		int height = solid.length;
		int width = height == 0 ? 0 : solid[0].length;
		byte[][] out = new byte[height][width];
		if (width == 0 || amount <= 0.0F) {
			return out;
		}
		double much = Math.min(1.0, amount);

		// The diagonal runs from the grip (bottom left) to the tip (top right),
		// stretched over the pixels the sprite really has.
		double low = Double.MAX_VALUE;
		double high = -Double.MAX_VALUE;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (solid[y][x]) {
					double along = along(x, y, width, height);
					low = Math.min(low, along);
					high = Math.max(high, along);
				}
			}
		}
		if (low > high) {
			return out;
		}
		double span = Math.max(1.0e-6, high - low);
		// One unit is a pixel of a sixteen-pixel sprite, so a bigger picture
		// gets the same marks drawn larger, not a finer speckle.
		double unit = Math.max(width, height) / 16.0;
		double cell = 3.0 * unit;

		// The splat's middle: a solid pixel in the tip half, chosen by the seed.
		int splatX = -1;
		int splatY = -1;
		if (kind == BloodKind.SPLAT) {
			double best = Double.MAX_VALUE;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					if (solid[y][x] && (along(x, y, width, height) - low) / span >= 0.45) {
						double pick = hash(x, y, seed ^ 0x6A09E667F3BCC908L);
						if (pick < best) {
							best = pick;
							splatX = x;
							splatY = y;
						}
					}
				}
			}
		}
		double streakAt = 0.48 + 0.42 * hash(3, 7, seed ^ 0x3C6EF372FE94F82BL);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (!solid[y][x]) {
					continue;
				}
				double toTip = (along(x, y, width, height) - low) / span;
				// Along and across the blade, in units, for the shapes that run with it.
				double alongPx = (x - y) / (Math.sqrt(2.0) * unit);
				double acrossPx = (x + y) / (Math.sqrt(2.0) * unit);
				double fine = hash(x, y, seed ^ 0x9E3779B97F4A7C15L);
				double depth;
				switch (kind) {
					case STREAK -> {
						// Long and narrow, lying along the blade, in a band whose
						// middle the seed picks and whose length grows with the blood.
						double noise = 0.8 * smooth(alongPx / 2.8, acrossPx / 0.85, seed) + 0.2 * fine;
						double half = 0.16 + 0.55 * much;
						double window = Math.clamp(1.0 - Math.abs(toTip - streakAt) / half, 0.0, 1.0);
						depth = (0.36 + 0.52 * much) * Math.sqrt(window) - noise;
					}
					case TIP -> {
						// Buried in it: solid at the very end, ragged where it stops.
						double extent = 0.14 + 0.56 * much;
						double in = Math.clamp((toTip - (1.0 - extent)) / extent, 0.0, 1.0);
						double noise = 0.6 * smooth(x / cell, y / cell, seed) + 0.4 * fine;
						depth = in <= 0.0 ? -1.0 : 0.12 + (0.5 + 0.55 * much) * Math.pow(in, 0.7) - noise;
					}
					case SPLAT -> {
						// A round blot with a torn edge, and droplets thrown off it.
						if (splatX < 0) {
							depth = -1.0;
							break;
						}
						double radius = unit * (1.3 + 4.4 * much);
						double distance = Math.hypot(x - splatX, y - splatY);
						double noise = 0.5 * smooth(x / (cell * 0.7), y / (cell * 0.7), seed) + 0.5 * fine;
						depth = 1.2 * (1.0 - distance / radius) - 0.55 * noise;
						// A droplet keeps its own thickness when the blot grows over
						// it, or more blood would thin a pixel that was already full.
						if (distance < radius * 2.3
								&& hash(y, x, seed ^ 0xBB67AE8584CAA73BL) < 0.04 + 0.09 * much) {
							depth = Math.max(depth, 0.05 + 0.2 * fine);
						}
					}
					case SPECKS -> {
						// Single pixels, more of them toward the working end.
						double chance = (0.04 + 0.22 * much) * (0.35 + 0.65 * toTip);
						double pick = hash(y, x, seed ^ 0xA54FF53A5F1D36F1L);
						depth = pick < chance ? 0.04 + 0.2 * fine : -1.0;
					}
					case GRIP -> {
						// Run down from a hand: thickest at the very end of the handle.
						double extent = 0.2 + 0.45 * much;
						double in = Math.clamp(((1.0 - toTip) - (1.0 - extent)) / extent, 0.0, 1.0);
						double noise = 0.7 * smooth(alongPx / 2.2, acrossPx / 0.9, seed) + 0.3 * fine;
						depth = in <= 0.0 ? -1.0 : (0.42 + 0.5 * much) * Math.pow(in, 0.8) - noise;
					}
					default -> {
						// Soak: blots everywhere, far thicker toward the tip.
						double noise = 0.72 * smooth(x / cell, y / cell, seed) + 0.28 * fine;
						double reach = 0.34 + 0.56 * much;
						double grip = reach * 0.45 * much * much;
						depth = grip + (reach - grip) * toTip * toTip - noise;
					}
				}
				if (depth > 0.0) {
					out[y][x] = depth > 0.40 ? POOL : depth > 0.12 ? BODY : THIN;
				}
			}
		}
		if (much >= 0.25 && (kind == BloodKind.SOAK || kind == BloodKind.TIP || kind == BloodKind.SPLAT)) {
			run(out, solid, much, seed);
		}
		return out;
	}

	/** 0 at the bottom-left corner, 1 at the top-right. */
	private static double along(int x, int y, int width, int height) {
		double u = width <= 1 ? 0.5 : x / (double) (width - 1);
		double v = height <= 1 ? 0.5 : y / (double) (height - 1);
		return (u + (1.0 - v)) * 0.5;
	}

	/**
	 * Runs: under some blots a thin line trails a pixel or three toward the
	 * bottom of the sprite, the way a wet mark sags. Only over the sprite's own
	 * pixels and only ever adding. Whether a pixel runs is the pixel's own
	 * choice and how far grows with the blood, so more blood never takes a
	 * run away.
	 */
	private static void run(byte[][] out, boolean[][] solid, double much, long seed) {
		int height = out.length;
		int width = out[0].length;
		int scale = Math.max(1, Math.max(width, height) / 16);
		byte[][] before = new byte[height][];
		for (int y = 0; y < height; y++) {
			before[y] = out[y].clone();
		}
		for (int y = 0; y < height - 1; y++) {
			for (int x = 0; x < width; x++) {
				if (before[y][x] < BODY || hash(x, y, seed ^ 0x51ED270B3C6EF372L) > 0.34) {
					continue;
				}
				int length = scale * (1 + (int) (hash(y, x, seed ^ 0x2545F4914F6CDD1DL) * (1.0 + much * 2.2)));
				for (int i = 1; i <= length && y + i < height; i++) {
					if (!solid[y + i][x]) {
						break;
					}
					if (out[y + i][x] == NONE) {
						out[y + i][x] = THIN;
					}
				}
			}
		}
	}

	/** Smooth value noise in 0..1: hashed corners of a lattice, eased between. */
	private static double smooth(double x, double y, long seed) {
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		double fx = x - x0;
		double fy = y - y0;
		fx = fx * fx * (3.0 - 2.0 * fx);
		fy = fy * fy * (3.0 - 2.0 * fy);
		double a = hash(x0, y0, seed);
		double b = hash(x0 + 1, y0, seed);
		double c = hash(x0, y0 + 1, seed);
		double d = hash(x0 + 1, y0 + 1, seed);
		return (a + (b - a) * fx) * (1.0 - fy) + (c + (d - c) * fx) * fy;
	}

	/** A fixed scramble of two coordinates and a seed into 0..1. */
	static double hash(int x, int y, long seed) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		h *= 0xC4CEB9FE1A85EC53L;
		h ^= h >>> 33;
		return (h >>> 11) / (double) (1L << 53);
	}
}
