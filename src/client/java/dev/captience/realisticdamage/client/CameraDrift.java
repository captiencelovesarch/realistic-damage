package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The head sagging as consciousness fades. Deterministic (two out-of-phase
 * sines, no RNG), slow, and small: it should feel like your character can't
 * hold their head up, never like the mouse is broken. Off unless enabled.
 */
public final class CameraDrift {
	private CameraDrift() {}

	/** Max degrees per tick of yaw sway at zero consciousness. */
	private static final double MAX_YAW_PER_TICK = 0.42;
	/** Max degrees per tick of downward pitch sag at zero consciousness. */
	private static final double MAX_PITCH_PER_TICK = 0.30;

	public static void tick() {
		double intensity = ClientEffectsConfig.get().cameraDriftIntensity;
		if (intensity <= 0.0 || !ClientState.active()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || Minecraft.getInstance().gui.screen() != null) {
			return;
		}
		// Begins where the other symptoms do, so drift and vignette arrive together.
		double severity = ClientState.ramp(Balance.SYMPTOM_SENSITIVITY, 0.0) * Math.min(1.0, intensity);
		if (severity <= 0.001) {
			return;
		}

		double time = player.level().getGameTime();
		// Two slow, mutually prime periods so the sway never looks like a loop.
		double yawWave = Math.sin(time / 47.0) * 0.7 + Math.sin(time / 113.0) * 0.3;
		double pitchWave = Math.sin(time / 61.0) * 0.6;
		// The head also sags downward as you go under, not just sways.
		double sag = 0.35;

		float yaw = (float) (yawWave * MAX_YAW_PER_TICK * severity);
		float pitch = (float) ((pitchWave + sag) * MAX_PITCH_PER_TICK * severity);
		player.setYRot(player.getYRot() + yaw);
		player.setXRot(Mth.clamp(player.getXRot() + pitch, -90.0F, 90.0F));
	}
}
