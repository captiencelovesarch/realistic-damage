package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec2;

/**
 * Your feet stop going exactly where you point them (1.4).
 *
 * <p>The camera has drifted and the aim has been sloppy since 1.0, but walking
 * stayed perfectly obedient right up to the instant of collapse — which meant a
 * player greying out could still cross a ravine on a one-block bridge as
 * precisely as at full health. This rotates the movement vector off the input
 * by a wandering angle, so that at the bottom of the ladder you can still go
 * roughly where you meant to and not exactly there.
 *
 * <p>Deterministic, like everything else that decides an outcome: the angle is
 * two mismatched sines of the world clock, not a random walk. The same descent
 * wanders the same way, and it never jumps — a discontinuity would read as a
 * rubber-band, which is what makes drifting controls feel broken rather than
 * impaired.
 */
public final class StumbleInput {
	private StumbleInput() {}

	/** Smoothed severity, so the wander eases in rather than switching on. */
	private static double severity;

	/** Applied by the input mixin, once per client tick. */
	public static Vec2 apply(Vec2 move) {
		double target = severityTarget();
		severity += (target - severity) * (target > severity ? 0.25 : 0.08);
		if (severity < 0.002 || (move.x == 0.0F && move.y == 0.0F)) {
			return move;
		}
		double angle = Math.toRadians(offsetDegrees());
		double sin = Math.sin(angle);
		double cos = Math.cos(angle);
		return new Vec2(
				(float) (move.x * cos - move.y * sin),
				(float) (move.x * sin + move.y * cos));
	}

	/** How far off true the feet are right now, in degrees. */
	public static double offsetDegrees() {
		Minecraft client = Minecraft.getInstance();
		double time = client.level == null ? 0.0 : client.level.getGameTime();
		double wander = Math.sin(time / Balance.STUMBLE_PERIOD_A * Math.PI * 2.0) * 0.6
				+ Math.sin(time / Balance.STUMBLE_PERIOD_B * Math.PI * 2.0) * 0.4;
		return wander * Balance.STUMBLE_MAX_DEGREES * severity;
	}

	private static double severityTarget() {
		if (!ClientState.active()) {
			return 0.0;
		}
		// Squared so the onset is gentle and the bottom is genuinely bad, rather
		// than a uniform wobble across the whole lower half of the ladder.
		double ramp = ClientState.ramp(Balance.SYMPTOM_STUMBLE, 0.0);
		return ramp * ramp * ClientEffectsConfig.get().stumbleIntensity();
	}

	public static void reset() {
		severity = 0.0;
	}
}
