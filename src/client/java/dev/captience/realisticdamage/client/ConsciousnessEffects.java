package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * The sensory symptom ladder (spec §23-24). Everything here is smooth,
 * deterministic and individually disableable — the world can get hard to
 * perceive, but the HUD stays readable and the controls never feel broken.
 */
public final class ConsciousnessEffects {
	private ConsciousnessEffects() {}

	private static final Identifier VIGNETTE = Identifier.withDefaultNamespace("textures/misc/vignette.png");
	private static final Identifier NAUSEA = Identifier.withDefaultNamespace("textures/misc/nausea.png");

	/** Slow deterministic pulse (no RNG flicker) driven by game time. */
	private static double pulse(double periodTicks) {
		double time = Minecraft.getInstance().level == null ? 0.0
				: Minecraft.getInstance().level.getGameTime() % (long) periodTicks;
		return 0.5 - 0.5 * Math.cos(time / periodTicks * Math.PI * 2.0);
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!ClientState.active() || ClientState.snapshot() == null) {
			return;
		}
		ClientEffectsConfig config = ClientEffectsConfig.get();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();

		// Vignette (from 75 down): reuse vanilla's texture with a multiplicative blend.
		double vignette = ClientState.ramp(Balance.SYMPTOM_VIGNETTE, Balance.SYMPTOM_SEVERE)
				* config.vignetteIntensity;
		if (vignette > 0.01) {
			float strength = (float) Math.min(1.0, vignette);
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height,
					ARGB.colorFromFloat(1.0F, strength, strength * 0.55F, strength * 0.55F));
		}

		// Exposure / washout (from 35 down): the world looks wrong, not hidden.
		double exposure = ClientState.ramp(Balance.SYMPTOM_EXPOSURE, 0.0) * config.exposureIntensity;
		if (exposure > 0.01) {
			double breathe = 0.55 + 0.45 * pulse(90.0);
			int alpha = (int) (Math.min(1.0, exposure) * breathe * 60.0);
			graphics.fill(0, 0, width, height, ARGB.color(alpha, 235, 232, 220));
		}

		// Afterimages (from 45 down): a ghosted, additively-blended smear that
		// grows with each pulse — reads as trailing vision without touching the
		// frame graph (spec §23, honest v1.0 approach).
		double afterimage = ClientState.ramp(Balance.SYMPTOM_AFTERIMAGE, Balance.SYMPTOM_SEVERE)
				* config.afterimageIntensity;
		if (afterimage > 0.01) {
			double phase = pulse(50.0);
			float strength = (float) (Math.min(1.0, afterimage) * (0.25 + 0.35 * phase));
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2.0F, height / 2.0F);
			graphics.pose().scale((float) (1.02 + 0.06 * phase), (float) (1.02 + 0.06 * phase));
			graphics.pose().translate(-width / 2.0F, -height / 2.0F);
			graphics.blit(RenderPipelines.GUI_NAUSEA_OVERLAY, NAUSEA, 0, 0, 0.0F, 0.0F, width, height,
					width, height, ARGB.colorFromFloat(1.0F, strength * 0.35F, strength * 0.2F, strength * 0.2F));
			graphics.pose().popMatrix();
		}

		// Micro-blackouts below 10, then the full collapse fade.
		double severe = ClientState.ramp(Balance.SYMPTOM_SEVERE, 0.0);
		double blackout = 0.0;
		if (severe > 0.01) {
			blackout = severe * 0.35 * pulse(70.0);
		}
		double fade = ClientState.unconsciousFade();
		if (fade > 0.01) {
			// Never a fully black screen: the player keeps a faint sense of place (spec §25).
			blackout = Math.max(blackout, fade * 0.88);
		}
		if (blackout > 0.01) {
			graphics.fill(0, 0, width, height, ARGB.color((int) (Math.min(1.0, blackout) * 255.0), 0, 0, 0));
		}
	}
}
