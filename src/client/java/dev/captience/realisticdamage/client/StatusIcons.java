package dev.captience.realisticdamage.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Tiny pixel glyphs for the body display, drawn with fills rather than shipped
 * as textures so they scale with the HUD and need no atlas. Colour carries most
 * of the meaning at this size; shape disambiguates.
 */
public final class StatusIcons {
	private StatusIcons() {}

	public static final int COLOR_BLEED = 0xFFC1121F;
	public static final int COLOR_BROKEN = 0xFFEFEFEF;
	public static final int COLOR_DESTROYED = 0xFF8B0000;
	public static final int COLOR_CONCUSSION = 0xFFFFD54F;
	public static final int COLOR_BURNED = 0xFFFF7043;

	/** A falling drop. */
	public static final String[] BLEED = {
			"...#...",
			"...#...",
			"..###..",
			".#####.",
			"#######",
			"#######",
			".#####.",
	};

	/** A fracture line. */
	public static final String[] BROKEN = {
			"..#....",
			"..#....",
			".##....",
			"..####.",
			"....#..",
			"....#..",
			"....##.",
	};

	/** Gone. */
	public static final String[] DESTROYED = {
			"#.....#",
			".#...#.",
			"..#.#..",
			"...#...",
			"..#.#..",
			".#...#.",
			"#.....#",
	};

	/** Seeing stars. */
	public static final String[] CONCUSSION = {
			"..###..",
			".#...#.",
			"#..#..#",
			"#.#.#.#",
			"#..#..#",
			".#...#.",
			"..###..",
	};

	/** A flame. */
	public static final String[] BURNED = {
			"...#...",
			"..##...",
			".#.#...",
			".#..#..",
			"#...#..",
			"#..##..",
			".####..",
	};

	public static final int SIZE = 7;

	/**
	 * Draws a glyph at (x, y). Consecutive set pixels are merged into one fill,
	 * so a glyph costs a handful of draws rather than 49.
	 */
	public static void draw(GuiGraphicsExtractor graphics, String[] glyph, int x, int y, int argb) {
		for (int row = 0; row < glyph.length; row++) {
			String line = glyph[row];
			int runStart = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean set = col < line.length() && line.charAt(col) == '#';
				if (set && runStart < 0) {
					runStart = col;
				} else if (!set && runStart >= 0) {
					graphics.fill(x + runStart, y + row, x + col, y + row + 1, argb);
					runStart = -1;
				}
			}
		}
	}
}
