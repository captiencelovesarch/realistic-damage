package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.client.compat.Vr;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;

/**
 * The last two hearts, as they feel (1.7). The server runs the surge and the
 * crash ({@code AdrenalineMath}); this is what they look and sound like.
 *
 * <p><b>Surge</b>: tunnel vision — the edges go dark and the field of view
 * narrows, even though you are moving faster — and auditory exclusion: the
 * world goes dull and distant while your own heart and breath get loud. Real
 * adrenaline does both, and together they read as focus rather than damage.
 *
 * <p><b>Crash</b>: the colour leaves the edges, the hands shake — the held item
 * trembles in first person and the aim wanders by a hair — and you can hear
 * yourself breathing, hard when you are winded.
 */
public final class LowHealthEffects {
	private LowHealthEffects() {}

	private static double surge;
	private static double crash;
	private static double time;
	/** The aim tremor's last offset, so only its change is applied. */
	private static double lastTremorYaw;
	private static double lastTremorPitch;

	private static double intensity() {
		return ClientEffectsConfig.get().lowHealthEffects();
	}

	/** Advances the envelopes and the tremor; once per rendered frame. */
	public static void frame(double deltaTicks) {
		if (deltaTicks <= 0.0) {
			return;
		}
		time += deltaTicks;
		AdrenalineMath.Phase phase = ClientState.active() ? ClientState.adrenaline() : AdrenalineMath.Phase.NONE;
		double surgeTarget = phase == AdrenalineMath.Phase.SURGE ? 1.0 : 0.0;
		double crashTarget = phase == AdrenalineMath.Phase.CRASH ? (ClientState.winded() ? 1.0 : 0.7) : 0.0;
		surge += (surgeTarget - surge) * Math.min(1.0, deltaTicks * (surgeTarget > surge ? 0.25 : 0.03));
		crash += (crashTarget - crash) * Math.min(1.0, deltaTicks * 0.04);
		aimTremor();
	}

	/**
	 * The aim wandering by a hair while the hands shake. Applied as the change
	 * in a smooth offset, so it never accumulates into a drift — the crosshair
	 * shivers around where you put it. Rides Camera Drift, the control for
	 * anything that moves the aim.
	 */
	private static void aimTremor() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		double amplitude = crash * 0.09 * intensity() * ClientEffectsConfig.get().cameraDriftIntensity;
		double yaw = amplitude * (Math.sin(time * 0.71) * 0.6 + Math.sin(time * 1.37 + 1.1) * 0.4);
		double pitch = amplitude * (Math.sin(time * 0.83 + 2.0) * 0.6 + Math.sin(time * 1.91) * 0.4);
		if (player != null && client.gui.screen() == null && ClientState.active()) {
			double dy = yaw - lastTremorYaw;
			double dp = pitch - lastTremorPitch;
			if (Math.abs(dy) > 1.0e-5 || Math.abs(dp) > 1.0e-5) {
				// In VR the view is the headset: nothing may turn it but the player's own head (1.7 alpha.10).
				if (!Vr.active()) {
					StiffNeck.turnInvoluntarily(player, dy / 0.15, dp / 0.15);
				}
			}
		}
		lastTremorYaw = yaw;
		lastTremorPitch = pitch;
	}

	/** Field-of-view multiplier for the local player: narrows in the surge. */
	public static float fovFactor() {
		return (float) (1.0 - 0.15 * surge * intensity());
	}

	/** How much the surge and crash dull the world's sound, 0 to 1, for the muffle. */
	public static double muffle() {
		return Math.max(surge * 0.55, crash * 0.25) * intensity();
	}

	/** How grey the edges go, 0 to 1, for the colour drain. */
	public static double greyout() {
		// 0.7 resting in the crash lands on the first grey rung (0.45), winded on
		// the second; below that the drain would never start at all.
		return crash * 0.75 * intensity();
	}

	/**
	 * First-person hand shake, as offsets in model units: {x, y, roll degrees}.
	 * Small, fast and uneven, worse when winded.
	 */
	public static double[] handShake() {
		double a = crash * intensity();
		if (a <= 0.001) {
			return null;
		}
		double fast = time * 2.3;
		return new double[] {
				a * 0.006 * (Math.sin(fast) + 0.5 * Math.sin(fast * 2.7 + 0.4)),
				a * 0.006 * (Math.cos(fast * 1.3) + 0.5 * Math.sin(fast * 3.1)),
				a * 1.2 * Math.sin(fast * 1.7 + 1.0)};
	}

	/** Surge: a dark tunnel. Crash: a grey edge breathing in and out. */
	public static void render(GuiGraphicsExtractor graphics, int width, int height) {
		double strength = intensity() * ClientEffectsConfig.get().vignetteIntensity;
		if (strength <= 0.0) {
			return;
		}
		double tunnel = surge * 0.9 + crash * 0.4 * (0.75 + 0.25 * ClientState.breath());
		float s = (float) Math.min(1.0, tunnel * strength);
		if (s > 0.004F) {
			Lens.vignette(graphics, s, s, s);
		}
	}

	public static double surge() {
		return surge;
	}

	public static double crash() {
		return crash;
	}

	public static void reset() {
		surge = 0.0;
		crash = 0.0;
		lastTremorYaw = 0.0;
		lastTremorPitch = 0.0;
	}
}
