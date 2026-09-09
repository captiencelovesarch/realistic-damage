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

	/** Packed 0xRRGGBB at a given wetness, for the overlays that want an int. */
	public static int packed(double wetness) {
		float[] c = colour(wetness);
		return ((int) (c[0] * 255.0F + 0.5F) << 16)
				| ((int) (c[1] * 255.0F + 0.5F) << 8)
				| (int) (c[2] * 255.0F + 0.5F);
	}
}
