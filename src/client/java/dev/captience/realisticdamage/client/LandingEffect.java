package dev.captience.realisticdamage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The ground arriving (1.4). Deliberately not the generic impact cue: a blow
 * knocks your head aside, a landing drives it down and then bounces it, and the
 * two feeling identical was most of why falling from a great height used to
 * register as "some damage happened" rather than as an event.
 *
 * <p>Three parts, all decaying within about a second: a hard downward camera
 * slam that recoils, a settling shake while you are on the floor, and a thud
 * pitched by how much give the ground had.
 */
public final class LandingEffect {
	private LandingEffect() {}

	/** 1 at the instant of landing, decaying to 0 — drives the screen flash. */
	private static double shock;
	/** Remaining vertical camera travel, in degrees; negative phases look back up. */
	private static double slamRemaining;
	/** Ticks of residual shake, for the settling wobble while down. */
	private static double shakeRemaining;
	private static double shakeStrength;
	/** Advances the shake waveform; kept off wall-clock so it stays reproducible. */
	private static double shakePhase;

	public static void trigger(float severity, float hardness) {
		double s = Mth.clamp(severity, 0.0F, 1.0F);
		double intensity = ClientEffectsConfig.get().impactIntensity;
		if (intensity <= 0.0) {
			return;
		}
		shock = Math.max(shock, s * 0.75);
		// Straight down, and harder than a body blow: this is your whole mass
		// stopping at once. Half of it is the aim being driven down; the other
		// half (1.6.1) is the eye itself dropping and springing back, which is
		// the part that reads as knees buckling.
		slamRemaining = Math.max(slamRemaining, 10.0 * s * intensity);
		CameraMotion.landing(s, intensity);
		shakeStrength = Math.max(shakeStrength, s * intensity);
		shakeRemaining = Math.max(shakeRemaining, 4.0 + 16.0 * s);

		Minecraft client = Minecraft.getInstance();
		// Pitch tracks the surface: stone cracks, hay thumps.
		float pitch = (float) (0.62 + 0.30 * (1.0 - hardness) - 0.12 * s);
		client.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
				SoundEvents.PLAYER_ATTACK_STRONG, pitch, (float) (0.5 + 0.5 * s)));
		if (s > 0.35) {
			// A second, lower layer only on landings worth wincing at.
			client.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
					SoundEvents.PLAYER_BIG_FALL, (float) (0.75 + 0.2 * (1.0 - hardness)), (float) (0.6 + 0.4 * s)));
		}
	}

	/** Spends the slam and the shake; called once per frame. */
	public static void frame(double deltaTicks) {
		if (deltaTicks <= 0.0) {
			return;
		}
		if (shock > 0.0) {
			shock = Math.max(0.0, shock - deltaTicks * 0.14);
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || Minecraft.getInstance().gui.screen() != null) {
			reset();
			return;
		}
		if (slamRemaining > 0.001) {
			// Most of it lands on the first frame, then it eases out — the same
			// shape as the impact kick, pointed down instead of aside.
			double spend = Math.min(slamRemaining,
					slamRemaining * Math.min(1.0, deltaTicks * 0.5) + 0.5);
			slamRemaining -= spend;
			player.turn(0.0, spend / 0.15);
		}
		if (shakeRemaining > 0.0) {
			shakeRemaining -= deltaTicks;
			shakePhase += deltaTicks;
			// Two fast, mismatched sines: a settling wobble rather than a rattle.
			double decay = Math.max(0.0, shakeRemaining / 20.0) * shakeStrength;
			double yaw = Math.sin(shakePhase * 1.9) * 0.55 * decay;
			double pitch = Math.cos(shakePhase * 2.7) * 0.40 * decay;
			player.turn(yaw / 0.15 * deltaTicks, pitch / 0.15 * deltaTicks);
			if (shakeRemaining <= 0.0) {
				shakeStrength = 0.0;
			}
		}
	}

	/** 0 to 1, for the screen flash — dimmer and browner than a blow's. */
	public static double shock() {
		return shock * ClientEffectsConfig.get().impactIntensity;
	}

	public static void reset() {
		shock = 0.0;
		slamRemaining = 0.0;
		shakeRemaining = 0.0;
		shakeStrength = 0.0;
	}
}
