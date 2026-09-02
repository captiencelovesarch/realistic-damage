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

	/** Fresh, restrained: dark and desaturated enough to belong in the game's palette. */
	private static final float[] FRESH_MUTED = {0.545F, 0.078F, 0.086F};
	/** Fresh, visceral: brighter and more saturated, closer to arterial. */
	private static final float[] FRESH_VISCERAL = {0.690F, 0.102F, 0.094F};
	/** Dried, both styles: the brown-maroon of something that happened a while ago. */
	private static final float[] DRIED = {0.282F, 0.125F, 0.094F};

	private static float[] fresh() {
		return ClientEffectsConfig.get().visceralBlood ? FRESH_VISCERAL : FRESH_MUTED;
	}

	/**
	 * The colour of blood at a given wetness, 1 (just spilled) to 0 (long dry).
	 * Returns a fresh array; callers are free to keep it.
	 */
	public static float[] colour(double wetness) {
		float w = (float) Math.clamp(wetness, 0.0, 1.0);
		float[] wet = fresh();
		return new float[] {
				DRIED[0] + (wet[0] - DRIED[0]) * w,
				DRIED[1] + (wet[1] - DRIED[1]) * w,
				DRIED[2] + (wet[2] - DRIED[2]) * w,
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
