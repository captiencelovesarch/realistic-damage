package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.body.Vitals;
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

	/**
	 * Smoothed haze level. Rises instantly so a blow lands at once, then falls
	 * linearly, because vanilla's blur is all-or-nothing and without a decay to
	 * hide behind it simply vanishes mid-frame when you recover.
	 */
	private static double haze;
	/** Blur is cut while the haze is still strong, so the ghosting covers its exit. */
	private static final double BLUR_CUTOFF = 0.62;
	/** Haze lost per tick on the way down: a touch under a second and a half. */
	private static final double HAZE_FALLOFF = 0.035;

	/**
	 * Peak washout alpha, 120/255 = 47% of the way to near-white. Presentation
	 * magnitude rather than a ladder threshold, so it lives with the renderer and
	 * not in {@link Balance}.
	 *
	 * <p>Deliberately assertive, because the value it replaces (60, and reachable
	 * only at zero consciousness) has been proven invisible in play: over the whole
	 * band a conscious player occupies it never got past 25/255, and it was
	 * lerping toward a near-white that daylight is already most of the way to. A
	 * lerp 47% of the way to (235, 232, 220) is an unmistakable milky veil and is
	 * still well short of hiding the world, which is the distinction this rung is
	 * for: the world looks wrong, not gone. It is also the one number here that a
	 * test cannot settle — if the author's eye says 47% is now too much at 20,
	 * this is the single constant to lower.
	 */
	private static final double EXPOSURE_MAX_ALPHA = 120.0;
	/**
	 * How far the ghosted copy slides out of register at the top of its pulse.
	 * The old 0.06 was a 6% breath, which reads as a static tint rather than as a
	 * doubled image — and an afterimage that never separates is not one. Vanilla
	 * sweeps this same texture across 2.0 → 1.0, so 1.03 → 1.17 is still far more
	 * restrained than vanilla nausea.
	 */
	private static final double GHOST_SEPARATION = 0.14;
	/** One ghost pulse: 40 / 20 = two seconds, slow enough to read as dazed. */
	private static final double GHOST_PERIOD = 40.0;

	// What the last frame actually asked for. Written here and read by the client
	// gametests, because "the symptom is perceptible" is a claim about magnitude
	// and a screenshot cannot tell 8/255 from 50/255.
	private static boolean blurRequested;
	private static int exposureAlpha;
	private static double ghostStrength;

	/** True if the last rendered frame asked vanilla for its gaussian blur. */
	public static boolean blurRequestedLastFrame() {
		return blurRequested;
	}

	/** The washout alpha, 0-255, drawn on the last frame. */
	public static int lastExposureAlpha() {
		return exposureAlpha;
	}

	/** The ghost blit's strength, 0 to 1, on the last frame. */
	public static double lastGhostStrength() {
		return ghostStrength;
	}

	/** Slow deterministic pulse (no RNG flicker) driven by game time. */
	private static double pulse(double periodTicks) {
		double time = Minecraft.getInstance().level == null ? 0.0
				: Minecraft.getInstance().level.getGameTime() % (long) periodTicks;
		return 0.5 - 0.5 * Math.cos(time / periodTicks * Math.PI * 2.0);
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		// Cleared first thing, so the three readouts always describe this frame
		// rather than the last frame that got past the early returns below.
		blurRequested = false;
		exposureAlpha = 0;
		ghostStrength = 0.0;
		// Dying gets its own pass (1.5). active() excludes dead players — rightly,
		// for gameplay effects — but that exclusion is exactly why death used to
		// snap the screen back to normal on the frame it happened.
		if (ClientState.dying()) {
			renderDeath(graphics);
			return;
		}
		if (!ClientState.active() || ClientState.snapshot() == null) {
			return;
		}
		// This callback is the per-frame hook available to us, and camera drift has
		// to run per frame to be smooth (see CameraDrift).
		CameraDrift.frame(deltaTracker);
		ImpactEffect.frame(deltaTracker.getGameTimeDeltaTicks());
		LandingEffect.frame(deltaTracker.getGameTimeDeltaTicks());
		MouseInertia.frame(deltaTracker.getGameTimeDeltaTicks());
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
		// The curve is Vitals.exposureStrength — full at 10 rather than at 0, and
		// square-rooted — because the linear 35 → 0 ramp this replaces spent its
		// whole useful range underneath the blackout.
		double exposure = Vitals.exposureStrength(ClientState.effectiveConsciousness())
				* config.exposureIntensity;
		if (exposure > 0.01) {
			// The same breathing clock the heart's sinus arrhythmia rides, so the
			// screen and the pulse breathe as one body rather than drifting apart.
			// Narrowed from 0.55-1.00: the coupling is worth keeping, halving the
			// effect twice every 4.5 seconds was not. A wash that keeps ducking
			// back to nothing is one the eye renormalises away instead of reading.
			double breathe = 0.80 + 0.20 * ClientState.breath();
			exposureAlpha = (int) (Math.min(1.0, exposure) * breathe * EXPOSURE_MAX_ALPHA);
			graphics.fill(0, 0, width, height, ARGB.color(exposureAlpha, 235, 232, 220));
		}

		// Vision blur (from 18 down): vanilla's own gaussian blur applied to
		// everything already drawn — i.e. the whole world. Guarded because the
		// blur may only be requested once per frame and an open Screen already
		// claims it.
		//
		// 18, not 45, and now it says so. The 45 here was never real: the ramp was
		// squared before the 0.62 cutoff, so (45-c)/45 > sqrt(0.62) only past
		// c = 9.6, by which point the deep clamp below had already forced 0.95.
		// The quadratic term was dead code for every consciousness it could ever
		// see, and pretending it carried the 45 rung is what left that rung empty.
		// It is deleted here and the ghost carries 45 properly, below.
		boolean deep = ClientState.effectiveConsciousness() < Balance.SYMPTOM_BLUR;
		double hazeTarget = deep ? 0.95 * config.afterimageIntensity : 0.0;
		if (hazeTarget >= haze) {
			haze = hazeTarget;   // getting worse lands immediately
		} else {
			haze = Math.max(hazeTarget, haze - HAZE_FALLOFF * deltaTracker.getGameTimeDeltaTicks());
		}

		// Vanilla's blur has no intensity, so it is kept on while the haze is
		// strong and cut once the ghosting is heavy enough to cover its going.
		if (haze > BLUR_CUTOFF && config.afterimageIntensity > 0.0
				&& Minecraft.getInstance().gui.screen() == null) {
			graphics.blurBeforeThisStratum();
			blurRequested = true;
		}
		// Ghosting: doubles as the concussion symptom, and since 1.6 it is what
		// actually carries the 45 rung. A concussion holds a constant low level of
		// it — seeing double is what being dazed looks like — and it deepens as
		// consciousness falls, over 45 → 18 rather than 45 → 0, so the whole band
		// is rungs instead of twenty-seven points of nothing in front of a cliff.
		// A club to the head adds a burst of it on top (1.5), which is the same
		// symptom arriving suddenly rather than settling in.
		double ghost = Math.max(Math.max(haze,
						Vitals.afterimageStrength(ClientState.effectiveConsciousness(),
								ClientState.concussed()) * config.afterimageIntensity),
				ImpactEffect.doubled() * 0.8);
		if (ghost > 0.01) {
			double phase = pulse(GHOST_PERIOD);
			// Floor raised from 0.35 to 0.55 for the same reason the breath was
			// narrowed: a pulse that drops to a third of itself twice a cycle is
			// read as flicker, not as a second image coming and going.
			float strength = (float) (Math.min(1.0, ghost) * (0.55 + 0.45 * phase));
			ghostStrength = strength;
			graphics.pose().pushMatrix();
			graphics.pose().translate(width / 2.0F, height / 2.0F);
			float scale = (float) (1.03 + GHOST_SEPARATION * phase);
			graphics.pose().scale(scale, scale);
			graphics.pose().translate(-width / 2.0F, -height / 2.0F);
			graphics.blit(RenderPipelines.GUI_NAUSEA_OVERLAY, NAUSEA, 0, 0, 0.0F, 0.0F, width, height,
					width, height, ARGB.colorFromFloat(1.0F, strength * 0.85F, strength * 0.55F, strength * 0.55F));
			graphics.pose().popMatrix();
		}

		// A dimming that swells below 10 — the sense of nearly going, underneath
		// the real blackouts.
		double severe = ClientState.ramp(Balance.SYMPTOM_SEVERE, 0.0);
		double blackout = 0.0;
		if (severe > 0.01) {
			blackout = severe * 0.35 * pulse(70.0);
		}
		// Microsleeps (1.4): the server has actually taken the controls away for
		// these, so unlike the swell above they go properly black. Held just short
		// of total unless the player asked for total, so that coming back is
		// oriented rather than disorienting.
		double micro = ClientState.microsleepFade();
		if (micro > 0.01) {
			blackout = Math.max(blackout, micro * (config.fullBlackUnconscious ? 1.0 : 0.94));
		}
		double fade = ClientState.unconsciousFade();
		if (fade > 0.01) {
			// Grounded preset blacks out completely; otherwise the player keeps a
			// faint sense of place.
			blackout = Math.max(blackout, fade * (config.fullBlackUnconscious ? 1.0 : 0.88));
		}
		// Blood on the camera (1.5). Above the world effects because it is on the
		// lens rather than in the scene, and below the blackout because losing
		// consciousness covers it like everything else.
		BloodLens.render(graphics, width, height);

		// The four damage-type cues (1.5). Each one is the same information — you
		// were hit, this hard, from there — said in the shape of the thing that
		// hit you. Before this they were all the one warm flash below, which told
		// you that something had happened and nothing about what.

		// Pierce: the frame pinches shut and springs back. Focal, not a wash.
		double iris = ImpactEffect.iris();
		if (iris > 0.01) {
			float strength = (float) Math.min(1.0, iris * 1.6);
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height,
					ARGB.colorFromFloat(1.0F, strength * 0.55F, strength * 0.85F, strength));
		}

		// Cut: a streak of blood-dark drawn across the axis of the stroke. Thin,
		// off-centre and gone quickly — a wipe rather than a fog, since the fog is
		// the lens droplets' job.
		double smear = ImpactEffect.smear();
		if (smear > 0.01) {
			double radians = Math.toRadians(ImpactEffect.smearYaw());
			int band = (int) (height * 0.16 * smear);
			int centre = (int) (height * 0.5 + Math.sin(radians) * height * 0.18);
			int rgb = BloodPalette.packed(1.0);
			graphics.fill(0, Math.max(0, centre - band), width, Math.min(height, centre + band),
					ARGB.color((int) (smear * 110.0), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
		}

		// Blast: overpressure whites the world out rather than reddening it, and
		// takes far longer to clear than a blow does.
		double whiteout = ImpactEffect.whiteout();
		if (whiteout > 0.01) {
			graphics.fill(0, 0, width, height,
					ARGB.color((int) (Math.sqrt(whiteout) * 215.0), 252, 250, 244));
		}

		// Impact flash, last so it sits over everything: a hard white-red bloom
		// that is gone within half a second. Suppressed for the families that
		// have their own opening frame, so a cut does not flash white first.
		double shock = ImpactEffect.shock();
		if (shock > 0.01 && ImpactEffect.family() != ImpactFamily.BLAST
				&& ImpactEffect.family() != ImpactFamily.CUT) {
			double eased = shock * shock;
			graphics.fill(0, 0, width, height,
					ARGB.color((int) (eased * 190.0), 255, 205, 195));
		}
		// The landing equivalent: dimmer, and browner than a blow's bloom, because
		// what hit you was the floor.
		double landing = LandingEffect.shock();
		if (landing > 0.01) {
			double eased = landing * landing;
			graphics.fill(0, 0, width, height,
					ARGB.color((int) (eased * 150.0), 215, 190, 165));
		}

		if (blackout > 0.01) {
			graphics.fill(0, 0, width, height, ARGB.color((int) Math.ceil(Math.min(1.0, blackout) * 255.0), 0, 0, 0));
		}
	}

	/**
	 * The seconds after death (1.5). The body finishes its story under the death
	 * screen instead of vanishing from it: the vignette closes in and loses its
	 * red as the last beats squeeze it, the lens blood stays where it landed,
	 * and the darkness settles rather than arriving.
	 *
	 * <p>Deliberately restrained at the top end — the death screen's own text and
	 * buttons sit above this and must stay readable, so the fade closes most of
	 * the way and no further.
	 */
	private static void renderDeath(GuiGraphicsExtractor graphics) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		double fade = ClientState.deathFade() * config.deathIntensity();

		// What was on the lens when you died stays on the lens, drying.
		BloodLens.render(graphics, width, height);

		// Dying while already blacked out must not brighten the screen on the
		// death frame. The unconscious fade froze where death interrupted it;
		// hand it over to the death look across the fade rather than dropping it.
		double frozen = Math.max(ClientState.unconsciousFade(),
				ClientState.microsleepFade() * 0.94)
				* (config.fullBlackUnconscious ? 1.0 : 0.88)
				* (1.0 - ClientState.deathFade());
		if (frozen > 0.01) {
			graphics.fill(0, 0, width, height,
					ARGB.color((int) Math.ceil(Math.min(1.0, frozen) * 255.0), 0, 0, 0));
		}
		if (fade <= 0.001) {
			return;
		}

		double beat = ClientState.heartbeatEnvelope();
		if (ClientEffectsConfig.get().realisticDeath) {
			// The realistic ending. No red, no drama — a tunnel that closes. The
			// vignette leads, then a true blackout completes just past the middle
			// of the fade, and from there the only thing left of the world is the
			// heart still beating in the dark. The death screen's own text and
			// buttons draw above this, so the way out never disappears with the
			// world.
			float strength = (float) Math.min(1.0, fade * 1.6 + beat * 0.15);
			graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height,
					ARGB.colorFromFloat(1.0F, strength, strength, strength));
			double black = Math.clamp((fade - Balance.DEATH_BLACK_FROM)
					/ (Balance.DEATH_BLACK_AT - Balance.DEATH_BLACK_FROM), 0.0, 1.0);
			if (black > 0.004) {
				graphics.fill(0, 0, width, height,
						ARGB.color((int) Math.ceil(black * 255.0), 0, 0, 0));
			}
			return;
		}

		// The cinematic ending: the vignette closes in and drains, red-biased
		// while the heart still runs, settling toward plain darkness once it has
		// stopped.
		double squeeze = Math.min(1.0, fade * (0.55 + 0.45 * Math.sqrt(fade)) + beat * 0.30);
		float strength = (float) squeeze;
		float redBias = (float) (0.55 + 0.35 * fade);
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height,
				ARGB.colorFromFloat(1.0F, strength * redBias, strength, strength));

		// The settle: a flat dimming that arrives late in the fade, under the
		// death screen's own gradient. Capped well short of black — the screen
		// still has a job to do.
		double dim = Math.max(0.0, fade - 0.45) / 0.55;
		if (dim > 0.01) {
			graphics.fill(0, 0, width, height, ARGB.color((int) (dim * 110.0), 0, 0, 0));
		}
	}
}
