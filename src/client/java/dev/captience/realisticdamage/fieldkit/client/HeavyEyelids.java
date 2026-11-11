package dev.captience.realisticdamage.fieldkit.client;

import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath.Stage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Sedation from the inside (2.0, phase 3): your eyelids get heavy.
 *
 * <ul>
 *   <li><b>Drowsy:</b> a slow blink every few seconds, lids closing and
 *   lingering a beat too long.</li>
 *   <li><b>Stumbling:</b> the lids hang a third closed, and the blinks come
 *   oftener and stay shut longer: the microsleeps the server is giving you,
 *   seen from behind your own eyes.</li>
 *   <li><b>Out:</b> shut. The ordinary blackout does the rest.</li>
 * </ul>
 *
 * <p>Drawn as two dark lids closing from the top and the bottom, with a soft
 * edge. The stage comes from the server ({@code SedationPayload}); the dose and
 * level never leave it. Scaled by the Sedation slider (Afflictions), and gone
 * with the panic button.
 */
public final class HeavyEyelids {
	private HeavyEyelids() {}

	private static volatile int stage;
	private static double shown;
	private static int frames;

	public static void setStage(int ordinal) {
		stage = ordinal;
	}

	public static Stage stage() {
		return Stage.byOrdinal(stage);
	}

	public static void reset() {
		stage = 0;
		shown = 0.0;
	}

	/** How closed the lids are drawn now, 0 open to 1 shut, for the game test. */
	public static double shown() {
		return shown;
	}

	/** How many frames have drawn lids, for the game test: a hook that never runs draws nothing. */
	public static int frames() {
		return frames;
	}

	/**
	 * How closed the lids want to be at this moment, before the slider:
	 * a resting droop plus a blink on the stage's own rhythm. Pure, so the
	 * rhythm is the same every time for the same moment.
	 */
	public static double closure(Stage stage, double time) {
		return switch (stage) {
			case NONE -> 0.0;
			case DROWSY -> blink(time, 110.0, 14.0, 10.0, 18.0, 0.92);
			case STUMBLING -> Math.max(0.33, blink(time, 64.0, 10.0, 18.0, 22.0, 1.0));
			case OUT, OVERDOSE -> 1.0;
		};
	}

	/** One slow blink per period: close, hold, open, then wide until the next. */
	private static double blink(double time, double period, double close, double hold, double open, double peak) {
		double t = time % period;
		double shut;
		if (t < close) {
			shut = ease(t / close);
		} else if (t < close + hold) {
			shut = 1.0;
		} else if (t < close + hold + open) {
			shut = 1.0 - ease((t - close - hold) / open);
		} else {
			shut = 0.0;
		}
		return shut * peak;
	}

	private static double ease(double x) {
		return x * x * (3.0 - 2.0 * x);
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		double strength = ClientEffectsConfig.get().sedationEffects();
		Stage now = stage();
		double target = 0.0;
		if (client.level != null && strength > 0.0) {
			double time = client.level.getGameTime() + delta.getGameTimeDeltaPartialTick(false);
			target = closure(now, time) * Math.min(1.0, strength);
		}
		// Follow the rhythm closely, but never snap open when the stage clears.
		double rate = target > shown ? 0.6 : (now == Stage.NONE ? 0.08 : 0.6);
		shown += (target - shown) * Math.min(1.0, delta.getGameTimeDeltaTicks() * rate + 0.05);
		if (shown < 0.004) {
			shown = 0.0;
			return;
		}
		frames++;
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int lid = (int) Math.round(shown * height * 0.5);
		int soft = Math.max(2, height / 18);
		int ink = 0xFF050308;
		// Upper lid, its soft edge below it; lower lid, its soft edge above it.
		graphics.fill(0, 0, width, lid, ink);
		graphics.fillGradient(0, lid, width, lid + soft, ink, ink & 0x00FFFFFF);
		graphics.fill(0, height - lid, width, height, ink);
		graphics.fillGradient(0, height - lid - soft, width, height - lid, ink & 0x00FFFFFF, ink);
	}
}
