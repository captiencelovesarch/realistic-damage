package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.systems.BloodKind;
import dev.captience.realisticdamage.systems.BloodLayer;
import dev.captience.realisticdamage.systems.BloodiedMask;
import dev.captience.realisticdamage.systems.BloodiedMath;
import dev.captience.realisticdamage.systems.BloodiedPaint;
import org.junit.jupiter.api.Test;

/** The rules of blood on a held item (2.0): how much, what shape, where it sits, how marks meet. */
class BloodiedMaskTest {
	private static final float[] RED_WET = {0.436F, 0.062F, 0.069F};
	private static final float[] RED_DRY = {0.226F, 0.100F, 0.075F};
	private static final float[] DARK_WET = {0.240F, 0.036F, 0.048F};
	private static final float[] DARK_DRY = {0.128F, 0.056F, 0.048F};
	private static final float[] PURPLE_WET = {0.336F, 0.080F, 0.440F};
	private static final float[] PURPLE_DRY = {0.176F, 0.080F, 0.224F};
	private static final float[] GREEN_WET = {0.200F, 0.416F, 0.128F};
	private static final float[] GREEN_DRY = {0.136F, 0.224F, 0.080F};
	private static final float[] BLUE_WET = {0.096F, 0.208F, 0.440F};
	private static final float[] BLUE_DRY = {0.104F, 0.144F, 0.176F};

	/** A sword-shaped sprite: a diagonal bar from the bottom left to the top right, three pixels thick. */
	private static boolean[][] blade(int size) {
		boolean[][] solid = new boolean[size][size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				solid[y][x] = Math.abs(x - (size - 1 - y)) <= size / 16;
			}
		}
		return solid;
	}

	private static int count(byte[][] mask) {
		int total = 0;
		for (byte[] row : mask) {
			for (byte cell : row) {
				if (cell != BloodiedMask.NONE) {
					total++;
				}
			}
		}
		return total;
	}

	/** Bloody pixels in the tip half and in the grip half of a blade. */
	private static int[] ends(byte[][] mask) {
		int tip = 0;
		int grip = 0;
		for (int y = 0; y < mask.length; y++) {
			for (int x = 0; x < mask[y].length; x++) {
				if (mask[y][x] != BloodiedMask.NONE) {
					if (x > mask.length - 1 - y + 0 && x > y) {
						tip++;
					} else if (x < y) {
						grip++;
					}
				}
			}
		}
		return new int[] {tip, grip};
	}

	@Test
	void bloodOnlyEverSitsOnTheSprite() {
		boolean[][] solid = blade(16);
		for (BloodKind kind : BloodKind.values()) {
			for (int step = 1; step <= 16; step++) {
				byte[][] mask = BloodiedMask.paint(solid, kind, step / 16.0F, 42L);
				for (int y = 0; y < 16; y++) {
					for (int x = 0; x < 16; x++) {
						assertTrue(solid[y][x] || mask[y][x] == BloodiedMask.NONE,
								kind + " at " + step + "/16 painted empty air at " + x + "," + y);
					}
				}
			}
		}
	}

	@Test
	void moreBloodNeverTakesAMarkAway() {
		for (BloodKind kind : BloodKind.values()) {
			for (long seed : new long[] {1L, 42L, 9001L, -77L, 123456789L}) {
				boolean[][] solid = blade(16);
				byte[][] before = BloodiedMask.paint(solid, kind, 1 / 16.0F, seed);
				for (int step = 2; step <= 16; step++) {
					byte[][] now = BloodiedMask.paint(solid, kind, step / 16.0F, seed);
					for (int y = 0; y < 16; y++) {
						for (int x = 0; x < 16; x++) {
							assertTrue(before[y][x] == BloodiedMask.NONE || now[y][x] != BloodiedMask.NONE,
									kind + " seed " + seed + ": " + step + "/16 lost the mark at " + x + "," + y);
						}
					}
					before = now;
				}
				assertTrue(count(before) > 0, kind + " seed " + seed + " never marked anything");
			}
		}
	}

	@Test
	void everyKindHasItsOwnPlace() {
		boolean[][] solid = blade(16);
		int[] tip = ends(BloodiedMask.paint(solid, BloodKind.TIP, 0.4F, 42L));
		assertTrue(tip[0] > 0 && tip[1] == 0, "a thrust bloodies the tip and not the grip: " + tip[0] + "/" + tip[1]);
		int[] grip = ends(BloodiedMask.paint(solid, BloodKind.GRIP, 0.5F, 42L));
		assertTrue(grip[1] > 0 && grip[0] == 0, "an arm bleeds onto the grip and not the tip: " + grip[0] + "/" + grip[1]);
		int[] soak = ends(BloodiedMask.paint(solid, BloodKind.SOAK, 0.5F, 42L));
		assertTrue(soak[0] > soak[1] * 2, "a soak is far thicker at the tip: " + soak[0] + "/" + soak[1]);
		int specks = count(BloodiedMask.paint(solid, BloodKind.SPECKS, 0.2F, 42L));
		assertTrue(specks > 0 && specks < 10, "specks are a few pixels: " + specks);
	}

	@Test
	void twoMarksOfOneKindSitInDifferentPlaces() {
		boolean[][] solid = blade(16);
		int differing = 0;
		for (BloodKind kind : new BloodKind[] {BloodKind.STREAK, BloodKind.SPLAT, BloodKind.SOAK}) {
			byte[][] one = BloodiedMask.paint(solid, kind, 0.4F, 11L);
			byte[][] two = BloodiedMask.paint(solid, kind, 0.4F, 987654321L);
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					if ((one[y][x] == BloodiedMask.NONE) != (two[y][x] == BloodiedMask.NONE)) {
						differing++;
					}
				}
			}
		}
		assertTrue(differing > 12, "two seeds should not draw the same picture: " + differing + " pixels differ");
	}

	@Test
	void theSameMarkIsAlwaysTheSamePixels() {
		boolean[][] solid = blade(16);
		for (BloodKind kind : BloodKind.values()) {
			byte[][] one = BloodiedMask.paint(solid, kind, 0.6F, 7L);
			byte[][] two = BloodiedMask.paint(solid, kind, 0.6F, 7L);
			for (int y = 0; y < 16; y++) {
				assertEquals(java.util.Arrays.toString(one[y]), java.util.Arrays.toString(two[y]));
			}
		}
	}

	@Test
	void aSoakedBladeIsStillItself() {
		boolean[][] solid = blade(16);
		int pixels = count(BloodiedMask.paint(solid, BloodKind.SOAK, 1.0F, 42L));
		int all = 0;
		for (boolean[] row : solid) {
			for (boolean cell : row) {
				all += cell ? 1 : 0;
			}
		}
		assertTrue(pixels > all * 0.45, "soaked should cover about half the item or more: " + pixels + " of " + all);
		assertTrue(pixels < all * 0.92, "soaked should still leave the item recognisable: " + pixels + " of " + all);
	}

	@Test
	void aBiggerPictureGetsBiggerMarksNotMoreOfThem() {
		double small = count(BloodiedMask.paint(blade(16), BloodKind.SOAK, 0.7F, 42L)) / (16.0 * 3);
		double big = count(BloodiedMask.paint(blade(32), BloodKind.SOAK, 0.7F, 42L)) / (32.0 * 5);
		assertTrue(Math.abs(small - big) < 0.35, "coverage drifted with size: " + small + " vs " + big);
	}

	@Test
	void aFlurryIntoOneBodyIsOneGrowingMark() {
		List<BloodLayer> layers = List.of();
		for (int i = 0; i < 5; i++) {
			layers = BloodiedMath.added(layers, BloodKind.STREAK, "dark",
					BloodiedMath.fromWound(WoundSeverity.MINOR), 100 + i, 1000L + i * 12);
		}
		assertEquals(1, layers.size(), "the same blood while it is wet joins the mark that is there");
		assertEquals(0.6F, layers.get(0).amount(), 1.0e-5);
		assertEquals(100, layers.get(0).seed(), "and the mark stays where it started");
	}

	@Test
	void differentBloodAndDriedBloodStartNewMarks() {
		List<BloodLayer> layers = BloodiedMath.added(List.of(), BloodKind.STREAK, "dark", 0.3F, 1, 1000L);
		layers = BloodiedMath.added(layers, BloodKind.STREAK, "purple", 0.3F, 2, 1010L);
		assertEquals(2, layers.size(), "another colour is another mark");
		layers = BloodiedMath.added(layers, BloodKind.TIP, "purple", 0.3F, 3, 1020L);
		assertEquals(3, layers.size(), "another shape is another mark");
		layers = BloodiedMath.added(layers, BloodKind.TIP, "purple", 0.3F, 4, 1020L + BloodiedMath.DRY_TICKS);
		assertEquals(4, layers.size(), "blood on a dried mark is a new mark over it");
		for (int i = 0; i < 10; i++) {
			layers = BloodiedMath.added(layers, BloodKind.SOAK, i % 2 == 0 ? "red" : "green", 0.2F, 10 + i,
					5000L + i * 2L * BloodiedMath.DRY_TICKS);
		}
		assertEquals(BloodiedMath.LAYERS_MAX, layers.size(), "an item keeps only so many marks");
		assertEquals("green", layers.get(layers.size() - 1).tint(), "and it is the oldest that go");
	}

	@Test
	void theGripKeepsItsOwnMark() {
		List<BloodLayer> layers = BloodiedMath.grip(List.of(), "red", 0.1F, 5, 100L);
		layers = BloodiedMath.added(layers, BloodKind.STREAK, "dark", 0.3F, 6, 110L);
		for (int i = 0; i < 40; i++) {
			layers = BloodiedMath.grip(layers, "red", BloodiedMath.gripPerSecond(WoundSeverity.CRITICAL), 7, 120L + i * 20);
		}
		assertEquals(2, layers.size(), "an arm keeps feeding the one grip mark under whatever else landed");
		assertEquals(BloodiedMath.GRIP_MAX, layers.get(0).amount(), 1.0e-5, "and the grip only gets so bloody");
		assertEquals(0.0F, BloodiedMath.gripPerSecond(WoundSeverity.MINOR), "a scratch does not run down the arm");
	}

	@Test
	void waterTakesWetBloodFasterThanDried() {
		long now = 10_000L;
		List<BloodLayer> wet = List.of(new BloodLayer(BloodKind.SOAK, "red", 1.0F, 1, now));
		List<BloodLayer> dry = List.of(new BloodLayer(BloodKind.SOAK, "red", 1.0F, 1, now - BloodiedMath.DRY_TICKS * 2L));
		int wetSeconds = 0;
		while (!wet.isEmpty() && wetSeconds < 100) {
			wet = BloodiedMath.washed(wet, now);
			wetSeconds++;
		}
		int drySeconds = 0;
		while (!dry.isEmpty() && drySeconds < 100) {
			dry = BloodiedMath.washed(dry, now);
			drySeconds++;
		}
		assertEquals(3, wetSeconds, "a soaked, wet item rinses clean in three seconds");
		assertEquals(6, drySeconds, "and a soaked, dried one in six");
	}

	@Test
	void bloodDriesOverHalfAMinuteEdgesFirst() {
		assertEquals(1.0, BloodiedMath.wetness(100L, 100L), 1.0e-9);
		assertEquals(0.5, BloodiedMath.wetness(100L + BloodiedMath.DRY_TICKS / 2, 100L), 1.0e-9);
		assertEquals(0.0, BloodiedMath.wetness(100L + BloodiedMath.DRY_TICKS * 3L, 100L), 1.0e-9);
		// A stamp from a world whose clock has since been set back reads as fresh.
		assertEquals(1.0, BloodiedMath.wetness(50L, 100L), 1.0e-9);
		assertEquals(BloodiedMath.WET_STEPS, BloodiedMath.wetStep(100L, 100L));
		assertEquals(0, BloodiedMath.wetStep(100L + BloodiedMath.DRY_TICKS, 100L));

		// Halfway dried, a mark with thin, full and pooled pixels shows all three
		// states at once: that is what makes it read as drying.
		boolean[][] solid = blade(16);
		BloodiedPaint.Stroke half = new BloodiedPaint.Stroke(BloodKind.SOAK, 1.0F, 42L, 0.5, RED_WET, RED_DRY);
		BloodiedPaint.Stroke fresh = new BloodiedPaint.Stroke(BloodKind.SOAK, 1.0F, 42L, 1.0, RED_WET, RED_DRY);
		BloodiedPaint.Stroke old = new BloodiedPaint.Stroke(BloodKind.SOAK, 1.0F, 42L, 0.0, RED_WET, RED_DRY);
		assertTrue(reddest(BloodiedPaint.compose(solid, List.of(fresh))) > reddest(BloodiedPaint.compose(solid, List.of(old))) + 20,
				"fresh blood is redder than dried");
		// Red against green is what tells wet from dried: fresh blood has almost
		// no green in it and dried blood is brown.
		int[][] drying = BloodiedPaint.compose(solid, List.of(half));
		double wettest = 0.0;
		double driest = Double.MAX_VALUE;
		for (int[] row : drying) {
			for (int pixel : row) {
				if (pixel != 0) {
					double redness = ((pixel >> 16) & 0xFF) / (double) Math.max(1, (pixel >> 8) & 0xFF);
					wettest = Math.max(wettest, redness);
					driest = Math.min(driest, redness);
				}
			}
		}
		assertTrue(wettest > driest * 1.6, "a half-dried mark has wet pools and dried edges at once: "
				+ wettest + " vs " + driest);
	}

	private static int reddest(int[][] picture) {
		int best = 0;
		for (int[] row : picture) {
			for (int pixel : row) {
				if (pixel != 0) {
					best = Math.max(best, ((pixel >> 16) & 0xFF) - (pixel & 0xFF));
				}
			}
		}
		return best;
	}

	@Test
	void wetMarksRunTogetherAndDriedOnesAreCovered() {
		boolean[][] solid = blade(16);
		BloodiedPaint.Stroke red = new BloodiedPaint.Stroke(BloodKind.TIP, 1.0F, 1L, 1.0, RED_WET, RED_DRY);
		BloodiedPaint.Stroke purple = new BloodiedPaint.Stroke(BloodKind.TIP, 1.0F, 1L, 1.0, PURPLE_WET, PURPLE_DRY);
		BloodiedPaint.Stroke redDried = new BloodiedPaint.Stroke(BloodKind.TIP, 1.0F, 1L, 0.0, RED_WET, RED_DRY);
		int[] onlyRed = pooled(BloodiedPaint.compose(solid, List.of(red)));
		int[] onlyPurple = pooled(BloodiedPaint.compose(solid, List.of(purple)));
		int[] mixed = pooled(BloodiedPaint.compose(solid, List.of(red, purple)));
		int[] covered = pooled(BloodiedPaint.compose(solid, List.of(redDried, purple)));
		// Blue is what tells the two apart: red blood has almost none.
		assertTrue(mixed[2] > onlyRed[2] + 15 && mixed[2] < onlyPurple[2] - 15,
				"wet purple over wet red is between the two: " + mixed[2] + " vs " + onlyRed[2] + " and " + onlyPurple[2]);
		assertTrue(covered[2] > mixed[2], "over dried red the purple covers more than it mixes: " + covered[2]);
		assertNotEquals(onlyRed[0], onlyPurple[0]);
	}

	/** Average colour over the picture's bloody pixels. */
	private static int[] pooled(int[][] picture) {
		long r = 0;
		long g = 0;
		long b = 0;
		int n = 0;
		for (int[] row : picture) {
			for (int pixel : row) {
				if (pixel != 0) {
					r += (pixel >> 16) & 0xFF;
					g += (pixel >> 8) & 0xFF;
					b += pixel & 0xFF;
					n++;
				}
			}
		}
		return n == 0 ? new int[3] : new int[] {(int) (r / n), (int) (g / n), (int) (b / n)};
	}

	/**
	 * Not a test: with {@code RD_BLOOD_PREVIEW=<folder of item pngs>} set, writes
	 * {@code <folder>/out/preview.png}: every sprite through a set of cases,
	 * painted with the same code the game uses.
	 */
	@Test
	void previewSheet() throws Exception {
		String folder = System.getenv("RD_BLOOD_PREVIEW");
		if (folder == null) {
			return;
		}
		File[] files = new File(folder).listFiles((dir, name) -> name.endsWith(".png"));
		java.util.Arrays.sort(files);
		List<List<BloodiedPaint.Stroke>> cases = new ArrayList<>();
		cases.add(List.of());
		cases.add(List.of(stroke(BloodKind.STREAK, 0.15F, 1, 1.0, RED_WET, RED_DRY)));
		cases.add(List.of(stroke(BloodKind.STREAK, 0.6F, 1, 1.0, RED_WET, RED_DRY)));
		cases.add(List.of(stroke(BloodKind.TIP, 0.5F, 2, 1.0, RED_WET, RED_DRY)));
		cases.add(List.of(stroke(BloodKind.SPLAT, 0.5F, 3, 1.0, RED_WET, RED_DRY)));
		cases.add(List.of(stroke(BloodKind.SOAK, 0.7F, 4, 1.0, RED_WET, RED_DRY),
				stroke(BloodKind.SPECKS, 0.22F, 5, 1.0, RED_WET, RED_DRY)));
		cases.add(List.of(stroke(BloodKind.STREAK, 0.55F, 6, 1.0, DARK_WET, DARK_DRY),
				stroke(BloodKind.STREAK, 0.45F, 7, 1.0, PURPLE_WET, PURPLE_DRY)));
		cases.add(List.of(stroke(BloodKind.SPLAT, 0.5F, 8, 1.0, GREEN_WET, GREEN_DRY),
				stroke(BloodKind.STREAK, 0.4F, 9, 1.0, RED_WET, RED_DRY),
				stroke(BloodKind.TIP, 0.3F, 10, 1.0, BLUE_WET, BLUE_DRY)));
		cases.add(List.of(stroke(BloodKind.SOAK, 0.7F, 4, 0.45, RED_WET, RED_DRY)));
		cases.add(List.of(stroke(BloodKind.SOAK, 0.6F, 11, 0.0, DARK_WET, DARK_DRY),
				stroke(BloodKind.STREAK, 0.5F, 12, 0.0, RED_WET, RED_DRY)));
		cases.add(List.of(stroke(BloodKind.SOAK, 0.6F, 11, 0.0, DARK_WET, DARK_DRY),
				stroke(BloodKind.TIP, 0.45F, 13, 1.0, RED_WET, RED_DRY)));
		cases.add(List.of(stroke(BloodKind.GRIP, 0.55F, 14, 1.0, RED_WET, RED_DRY),
				stroke(BloodKind.STREAK, 0.3F, 15, 0.6, DARK_WET, DARK_DRY)));
		int zoom = 8;
		int cell = 16 * zoom + 8;
		BufferedImage sheet = new BufferedImage(cases.size() * cell, files.length * cell, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = sheet.createGraphics();
		g.setColor(new java.awt.Color(0x8B, 0x8B, 0x8B));
		g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
		for (int row = 0; row < files.length; row++) {
			BufferedImage sprite = ImageIO.read(files[row]);
			int w = sprite.getWidth();
			int h = Math.min(sprite.getHeight(), w);
			boolean[][] solid = new boolean[h][w];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					solid[y][x] = (sprite.getRGB(x, y) >>> 24) > 25;
				}
			}
			long spriteSeed = files[row].getName().replace(".png", "").hashCode();
			for (int column = 0; column < cases.size(); column++) {
				List<BloodiedPaint.Stroke> strokes = new ArrayList<>();
				for (BloodiedPaint.Stroke stroke : cases.get(column)) {
					strokes.add(new BloodiedPaint.Stroke(stroke.kind(), stroke.amount(), stroke.seed() * 31L + spriteSeed,
							stroke.wetness(), stroke.wet(), stroke.dry()));
				}
				int[][] blood = BloodiedPaint.compose(solid, strokes);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int argb = sprite.getRGB(x, y);
						if ((argb >>> 24) <= 25) {
							continue;
						}
						float r = ((argb >> 16) & 0xFF) / 255.0F;
						float gr = ((argb >> 8) & 0xFF) / 255.0F;
						float b = (argb & 0xFF) / 255.0F;
						int over = blood[y][x];
						if (over != 0) {
							float a = (over >>> 24) / 255.0F;
							r += (((over >> 16) & 0xFF) / 255.0F - r) * a;
							gr += (((over >> 8) & 0xFF) / 255.0F - gr) * a;
							b += ((over & 0xFF) / 255.0F - b) * a;
						}
						g.setColor(new java.awt.Color(Math.min(1.0F, r), Math.min(1.0F, gr), Math.min(1.0F, b)));
						g.fillRect(column * cell + 4 + x * (16 * zoom / w), row * cell + 4 + y * (16 * zoom / h),
								16 * zoom / w, 16 * zoom / h);
					}
				}
			}
		}
		g.dispose();
		File out = new File(folder, "out");
		out.mkdirs();
		ImageIO.write(sheet, "png", new File(out, "preview.png"));
	}

	private static BloodiedPaint.Stroke stroke(BloodKind kind, float amount, long seed, double wetness,
			float[] wet, float[] dry) {
		return new BloodiedPaint.Stroke(kind, amount, seed, wetness, wet, dry);
	}
}
