package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The screen effects' one way of drawing (1.7 alpha.10): everything the body
 * does to what you see — vignettes, washouts, flashes, the squint, blood on the
 * lens, the blackout — goes through here rather than straight to the HUD.
 *
 * <p>In flat play each call is exactly the HUD draw it replaced. In VR the HUD
 * is a panel hanging in the world, and a blackout drawn on it is a black
 * rectangle you can look around; so while {@link VrLens#capturing()} the calls
 * are recorded instead, in order, and {@link VrLens} replays them onto each eye
 * after the world has drawn. Same numbers, same order, on the eye rather than
 * on the panel.
 */
public final class Lens {
	private Lens() {}

	static final Identifier VIGNETTE = RealisticDamage.id("textures/misc/injury_vignette.png");
	static final Identifier DROPS = RealisticDamage.id("textures/misc/blood_lens.png");
	/** The drops atlas: four 32-pixel cells in a 64-pixel texture. */
	static final int CELL = 32;
	static final int ATLAS = 64;

	/**
	 * The injury vignette over the whole view, with vanilla's VIGNETTE blend:
	 * each channel is multiplied by {@code 1 - texture * tint}, so the tint is
	 * how much of that channel the edges lose.
	 */
	public static void vignette(GuiGraphicsExtractor graphics, float red, float green, float blue) {
		if (VrLens.capturing()) {
			VrLens.vignette(red, green, blue);
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height,
				ARGB.colorFromFloat(1.0F, red, green, blue));
	}

	/** An alpha-blended fill, in HUD pixels. */
	public static void fill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int argb) {
		if (VrLens.capturing()) {
			VrLens.fill(graphics, x0, y0, x1, y1, argb, argb);
			return;
		}
		graphics.fill(x0, y0, x1, y1, argb);
	}

	/** A vertical gradient fill, {@code top} at {@code y0} to {@code bottom} at {@code y1}. */
	public static void gradient(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int top, int bottom) {
		if (VrLens.capturing()) {
			VrLens.fill(graphics, x0, y0, x1, y1, top, bottom);
			return;
		}
		graphics.fillGradient(x0, y0, x1, y1, top, bottom);
	}

	/** One cell of the blood-drop atlas, {@code size} HUD pixels square. */
	public static void drop(GuiGraphicsExtractor graphics, int x, int y, int size, int variant, int argb) {
		if (VrLens.capturing()) {
			VrLens.drop(graphics, x, y, size, variant, argb);
			return;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, DROPS, x, y,
				(variant % 2) * CELL, (variant / 2) * CELL, size, size, CELL, CELL, ATLAS, ATLAS, argb);
	}

	/**
	 * Vanilla's gaussian blur over everything drawn so far. In VR that would
	 * blur only the HUD panel, so the lens blurs the eye's view instead.
	 */
	public static void blur(GuiGraphicsExtractor graphics) {
		if (VrLens.capturing()) {
			VrLens.blur();
			return;
		}
		graphics.blurBeforeThisStratum();
	}
}
