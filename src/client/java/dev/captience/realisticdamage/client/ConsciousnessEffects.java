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

	// Our own radial falloff: vanilla's vignette texture is deliberately subtle,
	// far too weak to read as "losing consciousness" even at full tint.
	private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("realisticdamage", "textures/misc/injury_vignette.png");
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
			// The VIGNETTE pipeline blends (ZERO, ONE_MINUS_SRC_COLOR): dst *= (1 - src*tint),
			// so the tint is the darkening strength and the red bias tinges it toward blood.
			float strength = (float) Math.min(1.0, vignette);
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height,
					ARGB.colorFromFloat(1.0F, strength * 0.80F, strength, strength));
		}

		// Near-death: a red vignette that slams shut on every heartbeat. Driven by
		// the worst of low health, blood loss and fading consciousness, so every
		// dangerous state pulses — and the beat is the same clock the sound uses.
		double distress = ClientState.distress() * config.vignetteIntensity;
		if (distress > 0.01) {
			double beat = ClientState.heartbeatEnvelope();
			// A steady squeeze that grows with danger, plus a hard thump on each beat.
			double steady = 0.30 * distress;
			double thump = beat * (0.30 + 0.70 * distress);
			float strength = (float) Math.min(1.0, (steady + thump) * distress);
			if (strength > 0.004F) {
				graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height,
						ARGB.colorFromFloat(1.0F, strength * 0.10F, strength, strength));
			}
		}

		// Exposure / washout (from 35 down): the world looks wrong, not hidden.
		double exposure = ClientState.ramp(Balance.SYMPTOM_EXPOSURE, 0.0) * config.exposureIntensity;
		if (exposure > 0.01) {
			double breathe = 0.55 + 0.45 * pulse(90.0);
			int alpha = (int) (Math.min(1.0, exposure) * breathe * 60.0);
			graphics.fill(0, 0, width, height, ARGB.color(alpha, 235, 232, 220));
		}

		// Vision blur (from 45 down): vanilla's own gaussian blur applied to
		// everything already drawn — i.e. the whole world. This replaces the
		// earlier additive-ghost fake, which was imperceptible. Guarded because
		// the blur may only be requested once per frame and an open Screen
		// already claims it.
		double blur = ClientState.ramp(Balance.SYMPTOM_AFTERIMAGE, 0.0) * config.afterimageIntensity;
		if (blur > 0.15 && Minecraft.getInstance().gui.screen() == null) {
			graphics.blurBeforeThisStratum();
		}
		// Ghosting: doubles as the concussion symptom. A concussion holds a
		// constant low level of it — seeing double is what being dazed looks
		// like — and it deepens as consciousness actually falls.
		double ghost = Math.max(blur, ClientState.concussed()
				? Balance.CONCUSSION_GHOST * config.afterimageIntensity : 0.0);
		if (ghost > 0.01) {
			double phase = pulse(50.0);
			float strength = (float) (Math.min(1.0, ghost) * (0.35 + 0.4 * phase));
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2.0F, height / 2.0F);
			graphics.pose().scale((float) (1.02 + 0.06 * phase), (float) (1.02 + 0.06 * phase));
			graphics.pose().translate(-width / 2.0F, -height / 2.0F);
			graphics.blit(RenderPipelines.GUI_NAUSEA_OVERLAY, NAUSEA, 0, 0, 0.0F, 0.0F, width, height,
					width, height, ARGB.colorFromFloat(1.0F, strength * 0.55F, strength * 0.30F, strength * 0.30F));
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
			// Grounded preset blacks out completely; otherwise the player keeps a
			// faint sense of place.
			blackout = Math.max(blackout, fade * (config.fullBlackUnconscious ? 1.0 : 0.88));
		}
		if (blackout > 0.01) {
			graphics.fill(0, 0, width, height, ARGB.color((int) Math.ceil(Math.min(1.0, blackout) * 255.0), 0, 0, 0));
		}
	}
}
