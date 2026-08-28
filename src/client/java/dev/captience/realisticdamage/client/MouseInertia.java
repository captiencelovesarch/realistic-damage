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
		// Everything goes into the buffer and comes back out over time. Holding
		// back a fixed *fraction* per input event did not work: at high frame
		// rates the mouse delivers many tiny deltas, so the buffer never grew and
		// the whole thing just felt like reduced sensitivity. Draining on a time
		// constant instead makes the lag real and preserves total sensitivity, so
		// it reads as slippery rather than sluggish.
		velX = Mth.clamp(velX + xo, -MAX_VELOCITY, MAX_VELOCITY);
		velY = Mth.clamp(velY + yo, -MAX_VELOCITY, MAX_VELOCITY);
		// A floor of input always lands directly, so that even if the drain were
		// somehow not running the mouse still responds. Never a dead mouse.
		double immediate = Mth.lerp(severity, 1.0, 0.25);
		double outX = velX * immediate;
		double outY = velY * immediate;
		velX -= outX;
		velY -= outY;
		return new double[] {outX, outY};
	}

	/** Set each time the render path drains us; the tick fallback watches it. */
	private static boolean drainedThisTick;

	/**
	 * Safety net. The per-frame drain lives in the HUD render path, which does
	 * not run while the HUD is hidden; without this the buffer would fill and
	 * never empty.
	 */
	public static void tickFallback() {
		if (!drainedThisTick) {
			frame(1.0);
		}
		drainedThisTick = false;
	}

	/** Bleeds off leftover momentum each frame, which is the glide you feel. */
	public static void frame(double deltaTicks) {
		drainedThisTick = true;
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
		// Friction falls as you fade: a healthy player sheds momentum almost at
		// once, a fading one keeps sliding. Per tick, so it is frame-rate
		// independent.
		double release = Mth.lerp(severity, 0.95, 0.22);
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
