package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.client.compat.Vr;

/**
 * What the body says through the controllers, in VR (1.7 alpha.10).
 *
 * <p>Flat play feels a blow as the view being knocked aside; in VR nothing may
 * turn the view but the player's own head, so the kick, the landing slam and
 * the low-health tremor stand down there (see {@link Vr}) and the hands take the
 * message instead. The heartbeat is the one cue that is new rather than moved:
 * the thump you hear when the body is in distress also lands in both palms, on
 * the same clock the vignette squeezes to.
 *
 * <p>Everything here scales with the "VR haptics" slider and is silent in flat
 * play and in seated VR (Vivecraft refuses haptics there).
 */
public final class VrHaptics {
	private VrHaptics() {}

	/** Below this distress the heart is not worth feeling, as it is not worth hearing (ConsciousnessAudio). */
	private static final double HEART_THRESHOLD = 0.05;

	private static double strength() {
		return Vr.active() ? ClientEffectsConfig.get().vrHaptics() : 0.0;
	}

	/** Once per client tick, after ClientState has advanced the heart. */
	public static void tick() {
		double strength = strength();
		if (strength <= 0.0 || !ClientState.beatJustFired()) {
			return;
		}
		double level;
		if (ClientState.dying()) {
			// The last beats keep landing while the screen goes dark: the hands
			// are the one sense the death sequence does not take away.
			level = 0.6 * (1.0 - ClientState.deathFade() * 0.5);
		} else if (ClientState.active()) {
			level = ClientState.distress();
			if (level <= HEART_THRESHOLD) {
				return;
			}
		} else {
			return;
		}
		double amplitude = ClientState.beatAmplitude();
		// Low and short, like the sound: a stroke, not a buzz.
		Vr.pulse(Vr.Hand.BOTH, (float) (0.05 + 0.04 * amplitude), 55.0F,
				(float) (Math.min(1.0, level * amplitude) * 0.8 * strength));
	}

	/** A blow landed, 0 to 1. A cut is a sharp buzz, a blunt blow a deep thud. */
	public static void impact(double magnitude, ImpactFamily family) {
		double strength = strength();
		if (strength <= 0.0 || magnitude <= 0.0) {
			return;
		}
		float frequency = switch (family) {
			case CUT, PIERCE -> 180.0F;
			case BLOW -> 70.0F;
			case BLAST -> 40.0F;
			default -> 110.0F;
		};
		Vr.pulse(Vr.Hand.BOTH, (float) (0.08 + 0.22 * magnitude), frequency,
				(float) (Math.min(1.0, 0.25 + 0.75 * magnitude) * strength));
	}

	/** A hard landing, 0 to 1: the whole body stopping at once. */
	public static void landing(double severity) {
		double strength = strength();
		if (strength <= 0.0 || severity <= 0.0) {
			return;
		}
		Vr.pulse(Vr.Hand.BOTH, (float) (0.06 + 0.18 * severity), 45.0F,
				(float) (Math.min(1.0, 0.2 + 0.8 * severity) * strength));
	}

	/** A blast nearby, 0 to 1 by loudness. */
	public static void blast(double loudness) {
		double strength = strength();
		if (strength <= 0.0 || loudness <= 0.0) {
			return;
		}
		Vr.pulse(Vr.Hand.BOTH, (float) (0.15 + 0.35 * loudness), 35.0F,
				(float) (Math.min(1.0, loudness) * strength));
	}
}
