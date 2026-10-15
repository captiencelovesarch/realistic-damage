package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.client.compat.Vr;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * The moment a heavy blow lands: a hard flash, a camera kick away from the
 * blow, and a low thud. Everything decays within about half a second, because
 * an impact should punctuate rather than linger, and the lasting consequences
 * are the injuries themselves.
 *
 * <p>Driven by a one-shot server message so it fires on the exact frame of the
 * hit, not when smoothed state catches up.
 */
public final class ImpactEffect {
	private ImpactEffect() {}

	/** 1 at the instant of impact, decaying to 0. */
	private static double shock;
	/** Remaining camera kick, in degrees, and the direction to spend it in. */
	private static double kickRemaining;
	private static double kickYaw;
	private static double kickPitch;

	// The four family-specific cues (1.5). Each decays on its own clock, because
	// what makes them distinguishable is as much how long they last as what they
	// look like: an iris snaps and is gone, a blast whites out and lingers.
	/** How the current cue should look; PLAIN is the pre-1.5 flash. */
	private static ImpactFamily family = ImpactFamily.PLAIN;
	/** Cut: a directional smear across the frame, and the axis to smear along. */
	private static double smear;
	private static double smearYaw;
	/** Pierce: the view pinching shut and springing back. */
	private static double iris;
	/** Blow: a damped oscillation of the camera, and the doubled world that follows. */
	private static double ring;
	private static double ringPhase;
	private static double doubled;
	/** Blast: overpressure whiteout. The hearing loss lives in BlastShock (1.7). */
	private static double whiteout;

	public static void trigger(float magnitude, float yaw, ImpactFamily impactFamily) {
		double m = Mth.clamp(magnitude, 0.0F, 1.0F);
		shock = Math.max(shock, m);
		family = impactFamily;
		VrHaptics.impact(m, impactFamily);

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		double intensity = ClientEffectsConfig.get().impactIntensity;
		switch (impactFamily) {
			case CUT -> {
				smear = Math.max(smear, m);
				smearYaw = yaw - player.getYRot();
				// Something opened you: the first thing you see is your own blood
				// arriving on the lens. Scaled hard by magnitude so a glancing cut
				// does not fog the screen.
				BloodLens.spatter((int) Math.round(1 + m * 4.0));
			}
			case PIERCE -> iris = Math.max(iris, m);
			case BLOW -> {
				ring = Math.max(ring, m);
				ringPhase = 0.0;
				doubled = Math.max(doubled, m);
			}
			case BLAST -> {
				whiteout = Math.max(whiteout, m);
				// Overpressure takes your hearing with it, and takes it for far
				// longer than it takes your sight. Since 1.7 that is BlastShock's
				// job — for every nearby blast, not only the ones that hurt — and
				// a blast that did hurt makes sure it lands at least this hard.
				BlastShock.onBlastHit(m);
			}
			default -> { }
		}
		// The family cues above are scaled by this intensity where they are read;
		// the kick and the thud are produced here, so they are gated here.
		if (intensity <= 0.0) {
			return;
		}
		// Kick away from the blow: the view snaps aside and upward, as if your
		// head has been knocked.
		double relative = Math.toRadians(yaw - player.getYRot());
		kickYaw = -Math.sin(relative);
		kickPitch = -0.55 - 0.45 * Math.cos(relative);
		kickRemaining = Math.max(kickRemaining, 9.0 * m * intensity);
		// And the picture itself lurches (1.6.1): roll, shove and overshoot on
		// the view matrix, where it can be violent without moving the aim.
		CameraMotion.impact(m, relative, impactFamily, intensity);

		// A dull body blow, pitched down for weight.
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
				SoundEvents.PLAYER_ATTACK_STRONG, (float) (0.55 + 0.15 * (1.0 - m)),
				(float) (0.55 + 0.45 * m)));
	}

	/** Bleeds the shock and spends the camera kick; called once per frame. */
	public static void frame(double deltaTicks) {
		if (deltaTicks <= 0.0) {
			return;
		}
		if (shock > 0.0) {
			shock -= deltaTicks * 0.16;
			if (shock < 0.0) {
				shock = 0.0;
			}
		}
		// Each family on its own clock. A cut and a blast decaying at the same
		// rate would look like the same event tinted differently.
		smear = decay(smear, deltaTicks * 0.13);
		iris = decay(iris, deltaTicks * 0.22);
		whiteout = decay(whiteout, deltaTicks * 0.045);
		doubled = decay(doubled, deltaTicks * 0.035);
		if (ring > 0.0) {
			ringPhase += deltaTicks;
			ring = decay(ring, deltaTicks * 0.085);
		}

		if (kickRemaining <= 0.001) {
			spendRing(deltaTicks);
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || Minecraft.getInstance().gui.screen() != null) {
			kickRemaining = 0.0;
			return;
		}
		// Spend most of the kick immediately, then trail off.
		double spend = Math.min(kickRemaining, kickRemaining * Math.min(1.0, deltaTicks * 0.55) + 0.4);
		kickRemaining -= spend;
		// In VR the view is the headset: nothing may turn it but the player's own head (1.7 alpha.10).
		if (!Vr.active()) {
			player.turn(kickYaw * spend / 0.15, kickPitch * spend / 0.15);
		}
		spendRing(deltaTicks);
	}

	private static double decay(double value, double amount) {
		return value <= 0.0 ? 0.0 : Math.max(0.0, value - amount);
	}

	/**
	 * The blunt ring: a damped oscillation of the view after the kick has been
	 * spent. A club does not stop your head where it hit it — the head keeps
	 * going, comes back, and overshoots a little less each time.
	 */
	private static void spendRing(double deltaTicks) {
		if (ring <= 0.001 || deltaTicks <= 0.0) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gui.screen() != null) {
			ring = 0.0;
			return;
		}
		double swing = Math.sin(ringPhase * 0.9) * ring * 1.4 * deltaTicks
				* ClientEffectsConfig.get().impactIntensity;
		if (!Vr.active()) {
			player.turn(swing * 0.6, -swing);
		}
	}

	/** 0 to 1, for the screen flash. */
	public static double shock() {
		return shock * ClientEffectsConfig.get().impactIntensity;
	}

	/** Which family the cue in progress belongs to, for the overlays to branch on. */
	public static ImpactFamily family() {
		return family;
	}

	/** Cut: strength of the directional smear, and the screen-relative axis of it. */
	public static double smear() {
		return smear * ClientEffectsConfig.get().impactIntensity;
	}

	public static double smearYaw() {
		return smearYaw;
	}

	/** Pierce: how far the view is pinched shut, 0 to 1. */
	public static double iris() {
		return iris * ClientEffectsConfig.get().impactIntensity;
	}

	/** Blow: how doubled the world looks, 0 to 1. */
	public static double doubled() {
		return doubled * ClientEffectsConfig.get().impactIntensity;
	}

	/**
	 * The same, before the impact slider. The afterimage reads this one: seeing
	 * double after a club is the afterimage symptom arriving suddenly, so it is
	 * the Afterimages slider that owns it, not Impact Shock.
	 */
	public static double doubledRaw() {
		return doubled;
	}

	/** Blast: overpressure whiteout, 0 to 1. */
	public static double whiteout() {
		return whiteout * ClientEffectsConfig.get().impactIntensity;
	}

	public static void reset() {
		shock = 0.0;
		kickRemaining = 0.0;
		smear = 0.0;
		iris = 0.0;
		ring = 0.0;
		ringPhase = 0.0;
		doubled = 0.0;
		whiteout = 0.0;
		family = ImpactFamily.PLAIN;
	}
}
