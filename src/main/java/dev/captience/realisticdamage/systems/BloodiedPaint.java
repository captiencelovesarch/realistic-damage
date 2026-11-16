package dev.captience.realisticdamage.systems;

import java.util.List;

/**
 * Every mark on an item painted into one picture (2.0).
 *
 * <p>This is what makes a bloodied item more than one stain. Marks go down
 * oldest first, each in its own colour and at its own age, and three things
 * happen as they meet. Dried blood is simply covered by what lands on it.
 * Two marks that are both still wet run together where they overlap, so
 * enderman purple over fresh zombie blood is neither where they touch. And a
 * mark does not dry all at once: its thin edges brown first and its pools
 * last, so a drying blot is brown-rimmed and red-hearted for a while. Wet
 * blood catches the light on its upper edge; dried blood is matt, and its
 * thinnest edges flake away.
 *
 * <p>Pure, and with no random source: the picture is a function of the
 * sprite and the marks.
 */
public final class BloodiedPaint {
	private BloodiedPaint() {}

	/**
	 * A mark ready to paint.
	 *
	 * @param wetness 1 fresh to 0 dry, for the mark as a whole
	 * @param wet     its colour fresh, red green blue 0..1
	 * @param dry     its colour dried
	 */
	public record Stroke(BloodKind kind, float amount, long seed, double wetness, float[] wet, float[] dry) {}

	/**
	 * @return {@code [y][x]} colours as 0xAARRGGBB, straight alpha; 0 where the item is clean
	 */
	public static int[][] compose(boolean[][] solid, List<Stroke> strokes) {
		int height = solid.length;
		int width = height == 0 ? 0 : solid[0].length;
		int[][] out = new int[height][width];
		float[][] red = new float[height][width];
		float[][] green = new float[height][width];
		float[][] blue = new float[height][width];
		float[][] alpha = new float[height][width];
		float[][] wetHere = new float[height][width];

		for (Stroke stroke : strokes) {
			byte[][] mask = BloodiedMask.paint(solid, stroke.kind(), stroke.amount(), stroke.seed());
			double dried = 1.0 - Math.clamp(stroke.wetness(), 0.0, 1.0);
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					byte thickness = mask[y][x];
					if (thickness == BloodiedMask.NONE) {
						continue;
					}
					float wet = (float) pixelWetness(thickness, dried);
					double grain = BloodiedMask.hash(x, y, stroke.seed() ^ 0x1F83D9ABFB41BD6BL);
					// Dried to nothing at its thinnest, some of it has flaked off.
					if (wet <= 0.0F && thickness == BloodiedMask.THIN && grain < 0.3) {
						continue;
					}
					// A little unevenness, the same pixel always the same way.
					float shade = BloodiedMask.brightness(thickness) * (0.93F + 0.14F * (float) grain);
					float r = (stroke.dry()[0] + (stroke.wet()[0] - stroke.dry()[0]) * wet) * shade;
					float g = (stroke.dry()[1] + (stroke.wet()[1] - stroke.dry()[1]) * wet) * shade;
					float b = (stroke.dry()[2] + (stroke.wet()[2] - stroke.dry()[2]) * wet) * shade;
					// Wet blood shines where its upper-left edge faces the light.
					if (wet > 0.7F && thickness >= BloodiedMask.BODY && grain > 0.7
							&& (x == 0 || y == 0 || mask[y - 1][x - 1] <= BloodiedMask.THIN)) {
						r += (1.0F - r) * 0.20F;
						g += (1.0F - g) * 0.13F;
						b += (1.0F - b) * 0.13F;
					}
					float a = BloodiedMask.opacity(thickness) * (0.84F + 0.16F * wet);

					if (alpha[y][x] <= 0.0F) {
						red[y][x] = r;
						green[y][x] = g;
						blue[y][x] = b;
						alpha[y][x] = a;
						wetHere[y][x] = wet;
					} else if (wet > 0.3F && wetHere[y][x] > 0.3F) {
						// Wet on wet: the two run together.
						red[y][x] += (r - red[y][x]) * 0.55F;
						green[y][x] += (g - green[y][x]) * 0.55F;
						blue[y][x] += (b - blue[y][x]) * 0.55F;
						alpha[y][x] = Math.min(0.97F, Math.max(alpha[y][x], a) + 0.04F);
						wetHere[y][x] = Math.max(wetHere[y][x], wet);
					} else {
						// Onto something dry, or dry itself: it covers.
						red[y][x] += (r - red[y][x]) * a;
						green[y][x] += (g - green[y][x]) * a;
						blue[y][x] += (b - blue[y][x]) * a;
						alpha[y][x] = Math.min(0.97F, alpha[y][x] + a * (1.0F - alpha[y][x]));
						wetHere[y][x] = wet;
					}
				}
			}
		}
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (alpha[y][x] > 0.0F) {
					out[y][x] = (channel(alpha[y][x]) << 24) | (channel(red[y][x]) << 16)
							| (channel(green[y][x]) << 8) | channel(blue[y][x]);
				}
			}
		}
		return out;
	}

	/**
	 * How wet one pixel of a mark is when the mark as a whole is this far
	 * dried: a thin edge is dry halfway through, the body at four fifths, and
	 * a pool stays fully wet for the first third and is the last to go.
	 */
	static double pixelWetness(byte thickness, double dried) {
		return switch (thickness) {
			case BloodiedMask.THIN -> Math.clamp(1.0 - dried * 2.0, 0.0, 1.0);
			case BloodiedMask.BODY -> Math.clamp(1.0 - dried * 1.25, 0.0, 1.0);
			default -> Math.clamp(1.0 - Math.max(0.0, dried * 1.6 - 0.6), 0.0, 1.0);
		};
	}

	private static int channel(float value) {
		return Math.clamp(Math.round(value * 255.0F), 0, 255);
	}
}
