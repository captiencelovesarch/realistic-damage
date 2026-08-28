package dev.captience.realisticdamage.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Injury icons for the body display: 9x9 pixel art, the same size vanilla draws
 * hearts at, each with a dark outline and three shades so they read against any
 * background instead of looking like flat stencils. Drawn with fills rather
 * than shipped as textures, so they need no atlas and tint cleanly with the
 * HUD's fade.
 *
 * <p>Glyph characters: {@code o} outline, {@code d} shadow, {@code m} base,
 * {@code l} highlight, {@code .} transparent.
 */
public final class StatusIcons {
	private StatusIcons() {}

	public static final int SIZE = 9;

	public record Icon(String[] glyph, int outline, int dark, int mid, int light) {}

	/** A falling drop, lit from the left. */
	public static final Icon BLEED = new Icon(new String[] {
			"....o....",
			"....o....",
			"...ooo...",
			"..olmdo..",
			"..olmdo..",
			".olmmmdo.",
			".olmmmdo.",
			"..odmdo..",
			"...ooo...",
	}, 0xFF3A0000, 0xFF7A0C12, 0xFFC1121F, 0xFFEE4B52);

	/** A bone snapped clean through the middle. */
	public static final Icon BROKEN = new Icon(new String[] {
			"oo.....oo",
			"olo...olo",
			"oomo.omoo",
			".omo.omo.",
			"..o...o..",
			".omo.omo.",
			"oomo.omoo",
			"olo...olo",
			"oo.....oo",
	}, 0xFF2A2A2A, 0xFF9A9A94, 0xFFE8E4D8, 0xFFFFFFFF);

	/** Gone: a heavy cross. */
	public static final Icon DESTROYED = new Icon(new String[] {
			"oo.....oo",
			"olmo..oml",
			".olmoomlo",
			"..olmmlo.",
			"...ommo..",
			"..olmmlo.",
			".olmoomlo",
			"olmo..oml",
			"oo.....oo",
	}, 0xFF1A0000, 0xFF4A0000, 0xFF8B0000, 0xFFC62828);

	/** Seeing stars. */
	public static final Icon CONCUSSION = new Icon(new String[] {
			"....o....",
			"....o....",
			"...olo...",
			"o..olo..o",
			".oolmloo.",
			"o..olo..o",
			"...olo...",
			"....o....",
			"....o....",
	}, 0xFF4A3800, 0xFFB8860B, 0xFFFFD54F, 0xFFFFF176);

	/** A flame with a hot core. */
	public static final Icon BURNED = new Icon(new String[] {
			"....o....",
			"...oo....",
			"..olmo...",
			"..olmlo..",
			".olmmmlo.",
			"olmmmmmlo",
			"olmmmmmlo",
			".olmmmlo.",
			"..ooooo..",
	}, 0xFF3A1200, 0xFFC1440E, 0xFFFF7043, 0xFFFFB74D);

	/**
	 * Draws an icon at (x, y), tinted to the given alpha (top byte). Runs of the
	 * same shade are merged into one fill, so a glyph costs a dozen draws rather
	 * than eighty-one.
	 */
	public static void draw(GuiGraphicsExtractor graphics, Icon icon, int x, int y, int alpha) {
		String[] glyph = icon.glyph();
		for (int row = 0; row < glyph.length; row++) {
			String line = glyph[row];
			int runStart = -1;
			char runChar = '.';
			for (int col = 0; col <= line.length(); col++) {
				char c = col < line.length() ? line.charAt(col) : '.';
				if (c != runChar) {
					if (runStart >= 0 && runChar != '.') {
						int rgb = shade(icon, runChar);
						graphics.fill(x + runStart, y + row, x + col, y + row + 1, (rgb & 0x00FFFFFF) | alpha);
					}
					runStart = c == '.' ? -1 : col;
					runChar = c;
				}
			}
		}
	}

	private static int shade(Icon icon, char c) {
		return switch (c) {
			case 'o' -> icon.outline();
			case 'd' -> icon.dark();
			case 'l' -> icon.light();
			default -> icon.mid();
		};
	}
}
