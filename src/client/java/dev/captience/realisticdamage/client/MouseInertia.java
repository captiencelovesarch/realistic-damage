package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.client.compat.Vr;
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

	/**
	 * Runaway guard on the *leftover* momentum only, applied after the immediate
	 * portion has already been handed to the camera.
	 *
	 * <p>It used to clamp the buffer on the way in, which quietly made it a turn
	 * rate limiter: a fast flick delivers hundreds of units in one frame, so
	 * everything past the clamp was discarded rather than delayed and a 180°
	 * turn became impossible while impaired. Worse, the limit was per frame, so
	 * how fast you could turn depended on your frame rate. Clamping only the
	 * residual keeps total sensitivity intact — the effect delays your input, it
	 * never eats it — while still bounding how much can glide on afterwards.
	 */
	private static final double MAX_RESIDUAL = 45.0;

	/**
	 * Fraction of the buffer that lands on the frame it arrives, at full
	 * severity. Higher means the view tracks your hand more closely; the
	 * remainder is what produces the lag and overshoot.
	 */
	private static final double IMMEDIATE_AT_FULL = 0.50;

	/**
	 * Fraction of leftover momentum shed per tick at full severity. This is what
	 * sets how long the glide lasts once you stop moving: 0.5 settles in about a
	 * fifth of a second, which is enough to feel sloppy without feeling like the
	 * camera has been taken away from you.
	 */
	private static final double RELEASE_AT_FULL = 0.50;

	/** Below this the glide is imperceptible, so stop rather than trailing off. */
	private static final double STOP_BELOW = 0.02;

	/** 0 = sharp and vanilla, 1 = fully melted. */
	public static double severity() {
		double intensity = ClientEffectsConfig.get().lookInertia();
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
		velX += xo;
		velY += yo;
		// A floor of input always lands directly, so that even if the drain were
		// somehow not running the mouse still responds. Never a dead mouse.
		double immediate = Mth.lerp(severity, 1.0, IMMEDIATE_AT_FULL);
		double outX = velX * immediate;
		double outY = velY * immediate;
		velX = Mth.clamp(velX - outX, -MAX_RESIDUAL, MAX_RESIDUAL);
		velY = Mth.clamp(velY - outY, -MAX_RESIDUAL, MAX_RESIDUAL);
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
		if (Math.abs(velX) < STOP_BELOW && Math.abs(velY) < STOP_BELOW) {
			velX = 0.0;
			velY = 0.0;
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
			// Drop it rather than freezing it. Momentum held across an open
			// inventory used to be spent the instant the screen closed, so the view
			// lurched off on its own from a standing start.
			velX = 0.0;
			velY = 0.0;
			return;
		}
		// Friction falls as you fade: a healthy player sheds momentum almost at
		// once, a fading one keeps sliding. Per tick, so it is frame-rate
		// independent.
		double release = Mth.lerp(severity, 0.95, RELEASE_AT_FULL);
		double factor = 1.0 - Math.pow(1.0 - release, Math.max(0.0, deltaTicks));
		double outX = velX * factor;
		double outY = velY * factor;
		velX -= outX;
		velY -= outY;
		if (Math.abs(outX) > 1.0e-4 || Math.abs(outY) > 1.0e-4) {
			// In VR the view is the headset: nothing may turn it but the player's own head (1.7 alpha.10).
			if (!Vr.active()) {
				player.turn(outX, outY);
			}
		}
	}

	public static void reset() {
		velX = 0.0;
		velY = 0.0;
	}
}
