package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The head sagging as consciousness fades. Deterministic (two out-of-phase
 * sines, no RNG), and applied exactly the way vanilla applies mouse look:
 * once per frame, through {@link net.minecraft.world.entity.Entity#turn}, which
 * advances the previous rotation alongside the current one.
 *
 * <p>Doing this per tick instead is what made it choppy — a 20 Hz step on a
 * high-refresh display, with the camera trying to interpolate the delta while
 * the per-frame mouse path kept rewriting the baseline underneath it.
 */
public final class CameraDrift {
	private CameraDrift() {}

	/** Peak degrees per tick of yaw sway at zero consciousness. */
	private static final double MAX_YAW_PER_TICK = 1.7;
	/** Peak degrees per tick of downward pitch sag at zero consciousness. */
	private static final double MAX_PITCH_PER_TICK = 1.15;
	/** Entity.turn multiplies its arguments by this before applying them. */
	private static final double TURN_SCALE = 0.15;

	/** Continuous tick clock, so the waveform is smooth between ticks too. */
	private static double time;

	public static void frame(DeltaTracker deltaTracker) {
		double deltaTicks = deltaTracker.getGameTimeDeltaTicks();
		if (deltaTicks <= 0.0) {
			return;
		}
		double intensity = ClientEffectsConfig.get().cameraDriftIntensity;
		if (intensity <= 0.0 || !ClientState.active()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || client.gui.screen() != null) {
			return;
		}
		// Squared so it stays subtle while you are merely woozy and grows sharply
		// as you approach collapse, which is where it should be felt.
		double ramp = ClientState.ramp(Balance.SYMPTOM_SENSITIVITY, 0.0);
		double severity = ramp * ramp * Math.min(1.0, intensity);
		if (severity <= 0.001) {
			return;
		}

		time += deltaTicks;
		// Two slow, mutually prime periods so the sway never looks like a loop.
		double yawWave = Math.sin(time / 47.0) * 0.7 + Math.sin(time / 113.0) * 0.3;
		double pitchWave = Math.sin(time / 61.0) * 0.6;
		// The head also sags downward as you go under, not just sways.
		double sag = 0.35;

		double yaw = yawWave * MAX_YAW_PER_TICK * severity * deltaTicks;
		double pitch = (pitchWave + sag) * MAX_PITCH_PER_TICK * severity * deltaTicks;
		// turn() takes pre-scaled input and keeps the previous rotation in step,
		// so this composes with mouse look instead of fighting it.
		player.turn(yaw / TURN_SCALE, pitch / TURN_SCALE);
	}
}
