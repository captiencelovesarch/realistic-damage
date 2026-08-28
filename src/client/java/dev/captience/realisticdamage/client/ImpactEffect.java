package dev.captience.realisticdamage.client;

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

	public static void trigger(float magnitude, float yaw) {
		double m = Mth.clamp(magnitude, 0.0F, 1.0F);
		shock = Math.max(shock, m);

		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		// Kick away from the blow: the view snaps aside and upward, as if your
		// head has been knocked.
		double relative = Math.toRadians(yaw - player.getYRot());
		kickYaw = -Math.sin(relative);
		kickPitch = -0.55 - 0.45 * Math.cos(relative);
		kickRemaining = Math.max(kickRemaining, 9.0 * m * ClientEffectsConfig.get().impactIntensity);

		if (ClientEffectsConfig.get().impactIntensity > 0.0) {
			// A dull body blow, pitched down for weight.
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forLocalAmbience(
					SoundEvents.PLAYER_ATTACK_STRONG, (float) (0.55 + 0.15 * (1.0 - m)),
					(float) (0.55 + 0.45 * m)));
		}
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
		if (kickRemaining <= 0.001) {
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
		player.turn(kickYaw * spend / 0.15, kickPitch * spend / 0.15);
	}

	/** 0 to 1, for the screen flash. */
	public static double shock() {
		return shock * ClientEffectsConfig.get().impactIntensity;
	}

	public static void reset() {
		shock = 0.0;
		kickRemaining = 0.0;
	}
}
