package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * The sensory symptom ladder (spec §23-24). Everything here is smooth,
 * deterministic and individually disableable — the world can get hard to
 * perceive, but the HUD stays readable and the controls never feel broken.
 */
public final class ConsciousnessEffects {
	private ConsciousnessEffects() {}


	/**
	 * Blur is cut while the haze is still strong, so the ghosting covers its
	 * exit. The haze itself now lives with the afterimage that hands over to
	 * the blur (see {@link Afterimage#haze()}).
	 */
	private static final double BLUR_CUTOFF = 0.62;

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

	// What the last frame actually asked for. Written here and read by the client
	// gametests, because "the symptom is perceptible" is a claim about magnitude
	// and a screenshot cannot tell 8/255 from 50/255.
	private static boolean blurRequested;
	private static int exposureAlpha;
	private static double ghostStrength;
	/** The consciousness vignette's strength and the total blackout, 0 to 1, on the last frame. */
	private static double vignetteStrength;
	private static double blackoutLevel;
	/**
	 * What {@link #renderBlackout} draws this frame (1.6.2). The black used to
	 * be filled here, in an element that sits just above vanilla's overlays —
	 * and so under the hotbar, the hearts, the crosshair and the body diagram,
	 * every one of which stayed crisp on top of a screen that was supposed to
	 * have gone dark. "You can still see the UI," in the Grounded playtest. It
	 * is now handed to a second element registered last of all, so it covers
	 * the whole HUD; a Screen (the death screen, an inventory) still draws above
	 * it, which is the one thing that must stay reachable.
	 */
	private static double topBlackout;
	private static long blackoutDraws;

	/** Times the top-of-HUD blackout element actually filled the screen; read by the client gametest. */
	public static long blackoutDraws() {
		return blackoutDraws;
	}

	/**
	 * The ladder as death found it (1.6.1). Captured from the last living frame
	 * on the first dying one, then held for the whole of the death sequence, so
	 * that the screen never gets brighter for having died.
	 *
	 * <p>The bug: the dying pass drew only its own look, and its own look starts
	 * from nothing — a vignette at zero, no washout, no ghost — while the living
	 * pass had been drawing a full ladder a frame earlier. A player at
	 * consciousness five saw the screen <em>clear</em> on the frame they died
	 * and then start dimming again from scratch. Only the unconscious blackout
	 * was handed over, and that with a linear crossfade against the death fade,
	 * which for the cinematic ending (whose dim caps at 110/255) still meant a
	 * blacked-out player watched the world come back before it went. Now every
	 * layer the ladder was drawing is the floor the death look builds on.
	 */
	private static boolean deathFloorCaptured;
	private static double floorVignette;
	private static int floorExposureAlpha;
	private static double floorBlackout;

	/** True if the last rendered frame asked vanilla for its gaussian blur. */
	public static boolean blurRequestedLastFrame() {
		return blurRequested;
	}

	/** The washout alpha, 0-255, drawn on the last frame. */
	public static int lastExposureAlpha() {
		return exposureAlpha;
	}

	/** The afterimage strength, 0 to 1, asked for on the last frame. */
	public static double lastGhostStrength() {
		return ghostStrength;
	}

	/** The consciousness vignette's strength, 0 to 1, on the last frame (held while dying). */
	public static double lastVignetteStrength() {
		return vignetteStrength;
	}

	/** The total blackout, 0 to 1, on the last frame — living ladder or death look, whichever drew. */
	public static double lastBlackoutLevel() {
		return blackoutLevel;
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
		// In VR the screen effects are recorded for the eyes, not drawn on the
		// HUD panel (1.7 alpha.10); renderBlackout publishes the recording.
		VrLens.begin(graphics);
		// Dying gets its own pass (1.5). active() excludes dead players — rightly,
		// for gameplay effects — but that exclusion is exactly why death used to
		// snap the screen back to normal on the frame it happened.
		if (ClientState.dying()) {
			if (!deathFloorCaptured) {
				// The last living frame's numbers are still in the readouts; they
				// become the floor before this frame overwrites them.
				floorVignette = vignetteStrength;
				floorExposureAlpha = lastExposureAlphaDrawn;
				floorBlackout = blackoutLevel;
				deathFloorCaptured = true;
			}
			renderDeath(graphics);
			return;
		}
		deathFloorCaptured = false;
		vignetteStrength = 0.0;
		blackoutLevel = 0.0;
		topBlackout = 0.0;
		lastExposureAlphaDrawn = 0;
		if (!ClientState.active() || ClientState.snapshot() == null) {
			return;
		}
		// This callback is the per-frame hook available to us, and camera drift has
		// to run per frame to be smooth (see CameraDrift).
		CameraDrift.frame(deltaTracker);
		ImpactEffect.frame(deltaTracker.getGameTimeDeltaTicks());
		LandingEffect.frame(deltaTracker.getGameTimeDeltaTicks());
		BlastShock.frame(deltaTracker.getGameTimeDeltaTicks());
		ConcussionEffects.frame(deltaTracker.getGameTimeDeltaTicks());
		LowHealthEffects.frame(deltaTracker.getGameTimeDeltaTicks());
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
			vignetteStrength = strength;
			ladderVignette(graphics, width, height, strength);
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
				Lens.vignette(graphics, strength * 0.10F, strength, strength);
			}
		}

		// 1.7: the afflictions that are not a rung of the ladder — the last two
		// hearts, air running out, and the concussed senses. Under the washout
		// and the blur, because they are the body's state rather than the
		// moment's event.
		LowHealthEffects.render(graphics, width, height);
		AirHunger.render(graphics, width, height);
		ConcussionEffects.render(graphics, width, height);

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
			lastExposureAlphaDrawn = exposureAlpha;
			washout(graphics, width, height, exposureAlpha);
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
		// It is deleted here and the afterimage carries 45 properly.
		//
		// Vanilla's blur has no intensity, so it is kept on while the haze is
		// strong and cut once the ghosting is heavy enough to cover its going.
		if (Afterimage.haze() > BLUR_CUTOFF && config.afterimageIntensity > 0.0 && blurAllowed()) {
			Lens.blur(graphics);
			blurRequested = true;
		}
		// Ghosting (1.6.1): no longer drawn here at all. It used to be vanilla's
		// nausea texture, tinted and scaled up a few percent and pulsed — which
		// contains no previous frame and so is not an afterimage, whatever the
		// slider called it. The real one is a post chain over the world render
		// with a persistent history target (see Afterimage); this readout is the
		// strength it was asked for, so the tests can still assert the magnitude.
		ghostStrength = Afterimage.strength();

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
			Lens.vignette(graphics, strength * 0.55F, strength * 0.85F, strength);
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
			Lens.fill(graphics, 0, Math.max(0, centre - band), width, Math.min(height, centre + band),
					ARGB.color((int) (smear * 110.0), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
		}

		// Blast: overpressure whites the world out rather than reddening it, and
		// takes far longer to clear than a blow does.
		// A blast nearby (1.7): dust hanging in the air after it, browner and
		// slower than the flash, and the flash itself whether or not it hurt.
		double dust = BlastShock.dust() * config.impactIntensity;
		if (dust > 0.01) {
			Lens.fill(graphics, 0, 0, width, height, ARGB.color((int) (Math.min(1.0, dust) * 150.0), 126, 112, 92));
		}
		double whiteout = Math.max(ImpactEffect.whiteout(), BlastShock.flash() * config.impactIntensity);
		if (whiteout > 0.01) {
			Lens.fill(graphics, 0, 0, width, height,
					ARGB.color((int) (Math.sqrt(whiteout) * 215.0), 252, 250, 244));
		}

		// Impact flash, last so it sits over everything: a hard white-red bloom
		// that is gone within half a second. Suppressed for the families that
		// have their own opening frame, so a cut does not flash white first.
		double shock = ImpactEffect.shock();
		if (shock > 0.01 && ImpactEffect.family() != ImpactFamily.BLAST
				&& ImpactEffect.family() != ImpactFamily.CUT) {
			double eased = shock * shock;
			Lens.fill(graphics, 0, 0, width, height,
					ARGB.color((int) (eased * 190.0), 255, 205, 195));
		}
		// The landing equivalent: dimmer, and browner than a blow's bloom, because
		// what hit you was the floor.
		double landing = LandingEffect.shock();
		if (landing > 0.01) {
			double eased = landing * landing;
			Lens.fill(graphics, 0, 0, width, height,
					ARGB.color((int) (eased * 150.0), 215, 190, 165));
		}

		// Drawn by renderBlackout, above everything the HUD has to say.
		blackoutLevel = Math.min(1.0, blackout);
		topBlackout = blackoutLevel;
	}

	/**
	 * The last HUD element of all: the blackout, over the hotbar, the hearts,
	 * the crosshair and the mod's own body diagram. See {@link #topBlackout}.
	 */
	public static void renderBlackout(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		double level = Math.min(topBlackout, VrLens.blackCap());
		if (level > 0.004) {
			black(graphics, graphics.guiWidth(), graphics.guiHeight(), level);
			blackoutDraws++;
		}
		VrLens.publish();
	}

	/**
	 * Whether this frame may ask for vanilla's blur. Only one request is allowed
	 * per frame and an ordinary open Screen makes its own; the death screen
	 * does not — it draws a gradient instead — so the blur may be kept up
	 * through the death sequence (1.6.2) without colliding with anything.
	 */
	private static boolean blurAllowed() {
		var screen = Minecraft.getInstance().gui.screen();
		return screen == null || screen instanceof net.minecraft.client.gui.screens.DeathScreen;
	}

	/** Washout alpha actually drawn on the last living frame; the floor reads it. */
	private static int lastExposureAlphaDrawn;

	/** The consciousness vignette: a red-biased darkening of the edges. */
	private static void ladderVignette(GuiGraphicsExtractor graphics, int width, int height, float strength) {
		Lens.vignette(graphics, strength * 0.80F, strength, strength);
	}

	/** The exposure washout: a milky veil, the world looking wrong rather than gone. */
	private static void washout(GuiGraphicsExtractor graphics, int width, int height, int alpha) {
		if (alpha > 0) {
			Lens.fill(graphics, 0, 0, width, height, ARGB.color(alpha, 235, 232, 220));
		}
	}

	private static void black(GuiGraphicsExtractor graphics, int width, int height, double level) {
		Lens.fill(graphics, 0, 0, width, height, ARGB.color((int) Math.ceil(Math.min(1.0, level) * 255.0), 0, 0, 0));
	}

	/**
	 * The seconds after death (1.5). The body finishes its story under the death
	 * screen instead of vanishing from it: the vignette closes in and loses its
	 * red as the last beats squeeze it, the lens blood stays where it landed,
	 * and the darkness settles rather than arriving.
	 *
	 * <p>Since 1.6.1 it starts from where the living ladder left off rather than
	 * from nothing. The consciousness vignette, the washout, the ghost and the
	 * blackout are held at the values of the last living frame for the whole
	 * sequence, and the death look is added on top: its vignette only draws the
	 * part that exceeds the floor, and its darkness is the greater of the two.
	 * Nothing on this screen may ever be brighter than it was the moment before.
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
		// The readouts describe this frame too: the floor, unchanged.
		vignetteStrength = floorVignette;
		exposureAlpha = floorExposureAlpha;
		ghostStrength = Afterimage.strength();

		// What was on the lens when you died stays on the lens, drying.
		BloodLens.render(graphics, width, height);

		// The ladder, exactly as death found it — the blur included (1.6.2).
		// The haze is frozen with everything else while dying, and the death
		// screen draws no blur of its own, so the gaussian a deep-band player
		// was seeing stays up instead of snapping off on the death frame. The
		// afterimage never stopped: it runs on the world render, not the HUD.
		if (Afterimage.haze() > BLUR_CUTOFF && config.afterimageIntensity > 0.0 && blurAllowed()) {
			Lens.blur(graphics);
			blurRequested = true;
		}
		if (floorVignette > 0.01) {
			ladderVignette(graphics, width, height, (float) floorVignette);
		}
		washout(graphics, width, height, floorExposureAlpha);

		double black = floorBlackout;
		if (fade > 0.001) {
			double beat = ClientState.heartbeatEnvelope();
			if (config.realisticDeath) {
				// The realistic ending. No red, no drama — a tunnel that closes.
				// The vignette leads, then a true blackout completes just past the
				// middle of the fade, and from there the only thing left of the
				// world is the heart still beating in the dark. The death screen's
				// own text and buttons draw above this, so the way out never
				// disappears with the world.
				double wanted = Math.min(1.0, fade * 1.6 + beat * 0.15);
				float extra = (float) Math.max(0.0, wanted - floorVignette);
				if (extra > 0.004F) {
					Lens.vignette(graphics, extra, extra, extra);
				}
				black = Math.max(black, Math.clamp((fade - Balance.DEATH_BLACK_FROM)
						/ (Balance.DEATH_BLACK_AT - Balance.DEATH_BLACK_FROM), 0.0, 1.0));
			} else {
				// The cinematic ending: the vignette closes in and drains,
				// red-biased while the heart still runs, settling toward plain
				// darkness once it has stopped.
				double squeeze = Math.min(1.0, fade * (0.55 + 0.45 * Math.sqrt(fade)) + beat * 0.30);
				float extra = (float) Math.max(0.0, squeeze - floorVignette);
				if (extra > 0.004F) {
					float redBias = (float) (0.55 + 0.35 * fade);
					Lens.vignette(graphics, extra * redBias, extra, extra);
				}
				// The settle: a flat dimming that arrives late in the fade, under
				// the death screen's own gradient. Capped well short of black — the
				// screen still has a job to do — unless the body was already
				// further gone than that when it died.
				double dim = Math.max(0.0, fade - 0.45) / 0.55;
				black = Math.max(black, dim * 110.0 / 255.0);
			}
		}
		// Held while the body is still moving (1.7): the fall is what the death
		// camera exists to show. Never below what the living ladder had drawn.
		black = Math.max(floorBlackout, Math.min(black, DeathCamera.blackCap()));
		blackoutLevel = black;
		topBlackout = black;
	}
}
