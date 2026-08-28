package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.client.mixin.GameRendererAccessor;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Genuine colour drain and world warping, as opposed to overlays that only
 * imitate them.
 *
 * <p>Desaturation runs through vanilla's post-effect pipeline using its own
 * {@code color_convolve} shader with a luminance matrix, so it really does pull
 * the colour out of the rendered frame. The pipeline bakes uniforms into the
 * chain file and exposes no way to animate them, so intensity is stepped across
 * four chains rather than continuous; the vignette and blur riding on top hide
 * the steps.
 *
 * <p>Distortion feeds vanilla's nausea warp (see WorldDistortionMixin).
 */
public final class VisionEffects {
	private VisionEffects() {}

	private static final Identifier[] GREYOUT = {
			Identifier.fromNamespaceAndPath("realisticdamage", "greyout_1"),
			Identifier.fromNamespaceAndPath("realisticdamage", "greyout_2"),
			Identifier.fromNamespaceAndPath("realisticdamage", "greyout_3"),
			Identifier.fromNamespaceAndPath("realisticdamage", "greyout_4"),
	};

	/** Which chain is currently applied, or -1 for none. */
	private static int applied = -1;

	/** 0 to 1: how far gone the player's vision is. Blood loss greys you out too. */
	private static double severity() {
		if (!ClientState.active()) {
			return 0.0;
		}
		double fromMind = ClientState.ramp(Balance.SYMPTOM_VIGNETTE, 0.0);
		double blood = ClientState.blood();
		double fromBlood = blood >= Balance.BLOOD_MILD ? 0.0
				: Math.clamp((Balance.BLOOD_MILD - blood) / Balance.BLOOD_MILD, 0.0, 1.0);
		return Math.max(fromMind, fromBlood);
	}

	/** Extra nausea-equivalent warp, consumed by the distortion mixin. */
	public static double distortion() {
		if (ClientEffectsConfig.get().distortionIntensity <= 0.0) {
			return 0.0;
		}
		double severity = severity();
		// Held back until things are genuinely bad; the world should not swim
		// while you are merely scratched.
		if (severity < 0.45) {
			return 0.0;
		}
		double ramped = (severity - 0.45) / 0.55;
		return ramped * ramped * ClientEffectsConfig.get().distortionIntensity;
	}

	public static void tick() {
		Minecraft client = Minecraft.getInstance();
		int wanted = wantedLevel();
		if (wanted == applied) {
			return;
		}
		GameRendererAccessor renderer = (GameRendererAccessor) client.gameRenderer;
		if (wanted < 0) {
			client.gameRenderer.clearPostEffect();
		} else {
			renderer.realisticdamage$setPostEffect(GREYOUT[wanted]);
		}
		applied = wanted;
	}

	/** Index into GREYOUT, or -1 for full colour. */
	private static int wantedLevel() {
		if (ClientEffectsConfig.get().desaturationIntensity <= 0.0) {
			return -1;
		}
		// Do not fight another mod's or vanilla's own post effect.
		Identifier current = currentEffect();
		if (current != null && applied < 0) {
			return -1;
		}
		double severity = severity() * ClientEffectsConfig.get().desaturationIntensity;
		if (severity < 0.45) {
			return -1;
		}
		if (severity < 0.62) {
			return 0;
		}
		if (severity < 0.78) {
			return 1;
		}
		return severity < 0.92 ? 2 : 3;
	}

	@Nullable
	private static Identifier currentEffect() {
		return Minecraft.getInstance().gameRenderer.currentPostEffect();
	}

	/** Drop the chain on death/disconnect so nothing lingers. */
	public static void reset() {
		if (applied >= 0) {
			Minecraft.getInstance().gameRenderer.clearPostEffect();
			applied = -1;
		}
	}
}
