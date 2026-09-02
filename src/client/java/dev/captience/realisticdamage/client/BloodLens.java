package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.BloodFlow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Blood on the camera (1.5).
 *
 * <p>The answer to "it's super hard to tell when you're bleeding". Everything
 * else in this rework happens out in the world, where in first person you are
 * mostly not looking: drops fall past your feet, marks land behind you, and
 * your own torso is off-screen by definition. This layer is the one that cannot
 * be missed, and it is driven by the live bleed rate rather than by the blood
 * <em>level</em> the rest of the sensory layer reads — so it says "you are
 * losing blood right now", which nothing in the mod said before.
 *
 * <p>Droplets run, slow as they dry, darken from red to brown and fade. The run
 * decelerates rather than travelling at a constant speed, because a droplet on
 * glass is losing to surface tension the whole way down.
 */
public final class BloodLens {
	private BloodLens() {}

	private static final Identifier LENS =
			Identifier.fromNamespaceAndPath("realisticdamage", "textures/misc/blood_lens.png");
	/** The 2x2 atlas: four 32-pixel cells in a 64-pixel texture. */
	private static final int CELL = 32;
	private static final int ATLAS = 64;

	private static final double[] originX = new double[Balance.LENS_CAPACITY];
	private static final double[] originY = new double[Balance.LENS_CAPACITY];
	private static final double[] size = new double[Balance.LENS_CAPACITY];
	private static final int[] variant = new int[Balance.LENS_CAPACITY];
	private static final double[] age = new double[Balance.LENS_CAPACITY];
	private static final boolean[] alive = new boolean[Balance.LENS_CAPACITY];
	private static int next;
	private static long counter;
	private static double carry;

	/** Total weighted Blood/sec reaching the camera; see {@link BloodFlow#lensWeight}. */
	public static void feed(double weightedBloodPerSecond) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity() * config.bloodLensIntensity;
		if (strength <= 0.0) {
			return;
		}
		double rate = BloodFlow.lensDropsPerSecond(weightedBloodPerSecond) * strength;
		int spawn = BloodFlow.dropsThisTick(carry, rate);
		carry = BloodFlow.carryAfter(carry, rate);
		for (int i = 0; i < spawn; i++) {
			add();
		}
	}

	private static void add() {
		long seed = counter++;
		int slot = next;
		next = (next + 1) % Balance.LENS_CAPACITY;
		originX[slot] = BloodFlow.jitter(seed, 1);
		// Biased to the upper half: blood arrives on the lens from above, and a
		// droplet that starts near the bottom has no room to run before it dries.
		originY[slot] = BloodFlow.jitter(seed, 2) * 0.55;
		size[slot] = 0.05 + BloodFlow.jitter(seed, 3) * 0.075;
		variant[slot] = (int) (BloodFlow.jitter(seed, 4) * 4.0) & 3;
		age[slot] = 0.0;
		alive[slot] = true;
	}

	public static void tick() {
		double lifetime = Balance.LENS_LIFETIME_SECONDS * 20.0;
		for (int i = 0; i < Balance.LENS_CAPACITY; i++) {
			if (!alive[i]) {
				continue;
			}
			age[i] += 1.0;
			if (age[i] >= lifetime) {
				alive[i] = false;
			}
		}
	}

	public static void render(GuiGraphicsExtractor graphics, int width, int height) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity() * config.bloodLensIntensity;
		if (strength <= 0.0) {
			return;
		}
		double lifetime = Balance.LENS_LIFETIME_SECONDS * 20.0;
		for (int i = 0; i < Balance.LENS_CAPACITY; i++) {
			if (!alive[i]) {
				continue;
			}
			double progress = age[i] / lifetime;
			// Runs fast, then slows to a stop as it dries in place.
			double run = Math.min(1.0, progress / Balance.LENS_RUN_FRACTION);
			double eased = 1.0 - (1.0 - run) * (1.0 - run);

			double drawSize = size[i] * height;
			int x = (int) (originX[i] * (width - drawSize));
			int y = (int) ((originY[i] + Balance.LENS_RUN_DISTANCE * eased) * height);

			double wetness = Math.clamp(1.0 - progress * 1.25, 0.0, 1.0);
			int rgb = BloodPalette.packed(wetness);
			float alpha = (float) (fade(progress) * strength);
			if (alpha <= 0.01F) {
				continue;
			}
			int colour = ARGB.color((int) (alpha * 255.0F), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
			graphics.blit(RenderPipelines.GUI_TEXTURED, LENS, x, y,
					(variant[i] % 2) * CELL, (variant[i] / 2) * CELL,
					(int) drawSize, (int) drawSize, CELL, CELL, ATLAS, ATLAS, colour);
		}
	}

	/** In quickly (it landed, it did not arrive), held, then out over the last stretch. */
	private static double fade(double progress) {
		if (progress < 0.05) {
			return progress / 0.05;
		}
		if (progress > 0.7) {
			return Math.max(0.0, (1.0 - progress) / 0.3);
		}
		return 1.0;
	}

	/** How many droplets are on the camera. Exists so tests can see them. */
	public static int count() {
		int alive2 = 0;
		for (boolean living : alive) {
			if (living) {
				alive2++;
			}
		}
		return alive2;
	}

	/** One-shot spatter: a slash landing, or someone else's artery catching you. */
	public static void spatter(int count) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		if (config.bloodIntensity() * config.bloodLensIntensity <= 0.0) {
			return;
		}
		for (int i = 0; i < count; i++) {
			add();
		}
	}

	public static void reset() {
		java.util.Arrays.fill(alive, false);
		next = 0;
		carry = 0.0;
		counter = 0;
	}
}
