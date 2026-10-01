package dev.captience.realisticdamage.client;

/**
 * What blood looks like, in one place (1.5).
 *
 * <p>Every blood texture ships as white with a shaped alpha channel, so colour
 * is decided here at draw time rather than baked in. That is not tidiness for
 * its own sake — a stain is drawn wet on the tick it lands and brown four
 * seconds later from the same sprite, and a texture with the red already in it
 * could only ever do the first half.
 *
 * <p>Two styles, chosen client-side like the injury sounds: the muted set sits
 * inside Minecraft's own palette and the visceral set does not.
 */
public final class BloodPalette {
	private BloodPalette() {}

	/**
	 * The colour of a player's blood at a given wetness, 1 (just spilled) to 0
	 * (long dry). Returns a fresh array; callers are free to keep it.
	 */
	public static float[] colour(double wetness) {
		return colour(wetness, BloodTint.RED);
	}

	/** The same, in whatever colour the body bleeds (1.7). */
	public static float[] colour(double wetness, BloodTint tint) {
		float w = (float) Math.clamp(wetness, 0.0, 1.0);
		float[] wet = ClientEffectsConfig.get().visceralBlood ? tint.freshVisceral() : tint.fresh();
		float[] dried = tint.dried();
		return new float[] {
				dried[0] + (wet[0] - dried[0]) * w,
				dried[1] + (wet[1] - dried[1]) * w,
				dried[2] + (wet[2] - dried[2]) * w,
		};
	}

	/**
	 * How much darker blood lit by the world is than blood on the screen (1.7).
	 * The screen palette was tuned for the lens and the HUD, where it is the
	 * brightest thing in the frame by design; the same values on a floor sat at
	 * the brightness of a redstone block, which glows by comparison with every
	 * texture around it — "way too bright compared to everything else" was the
	 * playtest's verdict. Real blood is a dark, saturated red, closer to a
	 * nether wart block than to red wool. Uniform per channel so the purple of
	 * an enderman stays purple; dried blood loses less because it is already
	 * dark.
	 */
	private static final float WORLD_WET = 0.62F;
	private static final float WORLD_DRY = 0.72F;

	/**
	 * The near-white the particle sprites are drawn in (see
	 * {@code tools/generate_blood.py}, PARTICLE_RGB): far enough off grey that a
	 * shader pack cannot mistake a tinted drop for redstone dust. Divided back
	 * out of a particle's tint, so the cast never reaches the screen.
	 */
	private static final float[] SPRITE_RGB = {1.0F, 251.0F / 255.0F, 248.0F / 255.0F};

	/** Blood lying in the world — on a body, a floor, a severed limb — at a given wetness. */
	public static float[] world(double wetness, BloodTint tint) {
		float[] c = colour(wetness, tint);
		float k = WORLD_DRY + (WORLD_WET - WORLD_DRY) * (float) Math.clamp(wetness, 0.0, 1.0);
		c[0] *= k;
		c[1] *= k;
		c[2] *= k;
		return c;
	}

	/** {@link #world}, as the tint for one of the near-white particle sprites. */
	public static float[] particle(double wetness, BloodTint tint) {
		float[] c = world(wetness, tint);
		for (int i = 0; i < 3; i++) {
			c[i] = Math.min(1.0F, c[i] / SPRITE_RGB[i]);
		}
		return c;
	}

	/** Packed 0xRRGGBB at a given wetness, for the overlays that want an int. */
	public static int packed(double wetness) {
		float[] c = colour(wetness);
		return ((int) (c[0] * 255.0F + 0.5F) << 16)
				| ((int) (c[1] * 255.0F + 0.5F) << 8)
				| (int) (c[2] * 255.0F + 0.5F);
	}
}
