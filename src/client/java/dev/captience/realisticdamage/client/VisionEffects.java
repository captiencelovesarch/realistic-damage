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

	/**
	 * How far the colour has drained, 0 to 1 — and this one is <em>blood loss</em>
	 * (1.5).
	 *
	 * <p>Until 1.5 there was a single severity taking the worst of blood loss and
	 * fading consciousness, which both the colour drain and the world warping
	 * read. The consequence was that the screen could say how bad things were but
	 * never what was wrong: bleeding out and being concussed looked identical, so
	 * neither told you whether to stop the bleeding or stop taking hits to the
	 * head. Split, greyout means blood — which is also the medically right
	 * pairing, since losing colour from the edges inward is what actually precedes
	 * a blackout from hypovolemia.
	 */
	private static double greyoutSeverity() {
		if (!ClientState.active()) {
			return 0.0;
		}
		double blood = ClientState.blood();
		return blood >= Balance.BLOOD_MILD ? 0.0
				: Math.clamp((Balance.BLOOD_MILD - blood) / Balance.BLOOD_MILD, 0.0, 1.0);
	}

	/** ...and this one is the head: the warping, the swimming, the doubled world. */
	private static double warpSeverity() {
		return ClientState.active() ? ClientState.ramp(Balance.SYMPTOM_VIGNETTE, 0.0) : 0.0;
	}

	/** Extra nausea-equivalent warp, consumed by the distortion mixin. */
	public static double distortion() {
		if (ClientEffectsConfig.get().distortionIntensity <= 0.0) {
			return 0.0;
		}
		// A little blood loss still swims — enough that a heavy bleed is not
		// completely stable underfoot — but the warp belongs to the head, and it
		// is the head that can drive it all the way.
		double severity = Math.max(warpSeverity(), greyoutSeverity() * 0.45);
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
		// Dying finishes the drain (1.5): whatever colour was left leaves over
		// the death fade, so the world behind the death screen ends grey rather
		// than snapping back to vivid on the frame you die. The realistic style
		// front-loads it — the colour has to be gone before the blackout closes
		// over it at 55%, or the drain happens where nobody can see it.
		if (ClientState.dying()) {
			double pace = ClientEffectsConfig.get().realisticDeath ? 2.0 : 1.0;
			double gone = ClientState.deathFade() * pace
					* ClientEffectsConfig.get().deathIntensity()
					* ClientEffectsConfig.get().desaturationIntensity;
			if (gone < 0.15) {
				return applied;   // hold whatever dying interrupted; no brightening
			}
			if (gone < 0.35) {
				return Math.max(applied, 0);
			}
			if (gone < 0.6) {
				return Math.max(applied, 1);
			}
			return gone < 0.85 ? Math.max(applied, 2) : 3;
		}
		// Blood drives this outright; a fading mind contributes, but only enough
		// that going under still dims the world rather than leaving it vivid.
		double severity = Math.max(greyoutSeverity(), warpSeverity() * 0.6)
				* ClientEffectsConfig.get().desaturationIntensity;
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
