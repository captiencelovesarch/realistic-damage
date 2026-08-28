package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * A melty, sloppy camera as consciousness fails.
 *
 * <p>Two coupled effects. Mouse input no longer moves the view directly, it
 * feeds a momentum buffer, so aiming lags behind your hand and overshoots
 * (acceleration). And the friction bleeding that momentum off falls as you
 * fade, so the view keeps gliding after you have stopped moving, like the
 * mouse is on ice.
 *
 * <p>At full consciousness both are inert and input passes through untouched.
 */
public final class MouseInertia {
	private MouseInertia() {}

	/** Pending turn, in the same pre-scaled units Entity.turn takes. */
	private static double velX;
	private static double velY;

	/** Runaway guard: momentum can never exceed a brisk flick. */
	private static final double MAX_VELOCITY = 60.0;

	/** 0 = sharp and vanilla, 1 = fully melted. */
	public static double severity() {
		double intensity = ClientEffectsConfig.get().sensitivityReductionIntensity;
		if (intensity <= 0.0 || !ClientState.active()) {
			return 0.0;
		}
		return ClientState.ramp(Balance.SYMPTOM_SENSITIVITY, 0.0) * Math.min(1.0, intensity);
	}

	/**
	 * Absorbs a raw mouse delta and returns the portion to apply right now, or
	 * null when unimpaired and the input should pass through untouched.
	 */
	public static double[] absorb(double xo, double yo) {
		double severity = severity();
		if (severity <= 0.001) {
			velX = 0.0;
			velY = 0.0;
			return null;
		}
		velX = Mth.clamp(velX + xo, -MAX_VELOCITY, MAX_VELOCITY);
		velY = Mth.clamp(velY + yo, -MAX_VELOCITY, MAX_VELOCITY);
		// Less of your input lands immediately the further gone you are.
		double responsiveness = Mth.lerp(severity, 1.0, 0.20);
		double outX = velX * responsiveness;
		double outY = velY * responsiveness;
		velX -= outX;
		velY -= outY;
		return new double[] {outX, outY};
	}

	/** Bleeds off leftover momentum each frame, which is the glide you feel. */
	public static void frame(double deltaTicks) {
		if (Math.abs(velX) < 1.0e-4 && Math.abs(velY) < 1.0e-4) {
			return;
		}
		double severity = severity();
		if (severity <= 0.001) {
			velX = 0.0;
			velY = 0.0;
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || Minecraft.getInstance().gui.screen() != null) {
			return;
		}
		// Friction falls as you fade: a healthy player sheds momentum at once, a
		// fading one keeps sliding.
		double release = Mth.lerp(severity, 0.60, 0.10);
		double factor = 1.0 - Math.pow(1.0 - release, Math.max(0.0, deltaTicks));
		double outX = velX * factor;
		double outY = velY * factor;
		velX -= outX;
		velY -= outY;
		if (Math.abs(outX) > 1.0e-4 || Math.abs(outY) > 1.0e-4) {
			player.turn(outX, outY);
		}
	}

	public static void reset() {
		velX = 0.0;
		velY = 0.0;
	}
}
