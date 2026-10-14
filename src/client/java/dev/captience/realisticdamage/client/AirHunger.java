package dev.captience.realisticdamage.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Running out of air (1.7). The server drains consciousness once the air is
 * gone (see {@code HypoxiaMath}) and the symptom ladder draws that; this is the
 * part before and after, which only the body knows about.
 *
 * <p>Before: as the last bubbles go, the edges of the view close in, dark and
 * cold, and the chest starts to heave against a closed throat — involuntary
 * contractions, a jerk of the whole view, coming faster as the air runs out.
 * After: the head breaks the surface (or comes out of the wall) and you gasp,
 * then keep heaving for a while, louder the longer you were under.
 *
 * <p>Read straight from vanilla's own synced air supply, so it needs nothing
 * from the server and still works where the server lacks the mod.
 */
public final class AirHunger {
	private AirHunger() {}

	private static final Identifier VIGNETTE =
			Identifier.fromNamespaceAndPath("realisticdamage", "textures/misc/injury_vignette.png");

	/** How badly the body wants air, 0 to 1. */
	private static double hunger;
	/** The phase toward the next contraction. */
	private static double contractionPhase;
	/** The last contraction's pulse, for the vignette to jump with it. */
	private static double pulse;
	/** The worst hunger since the head last went under, for sizing the gasp. */
	private static double worst;
	private static long contractions;

	/** Contractions so far; read by the client gametest. */
	public static long contractions() {
		return contractions;
	}

	public static double hunger() {
		return hunger;
	}

	/** Once per client tick. */
	public static void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || !ClientState.active()) {
			hunger = 0.0;
			worst = 0.0;
			return;
		}
		int max = Math.max(1, player.getMaxAirSupply());
		double air = player.getAirSupply() / (double) max;
		boolean inWall = player.isInWall();
		boolean underwater = player.isUnderWater();
		boolean starved = player.getAirSupply() <= 0 || inWall;
		double target;
		if (starved) {
			target = 1.0;
		} else if (underwater && air < 0.4) {
			target = (0.4 - air) / 0.4 * 0.75;
		} else {
			target = 0.0;
		}
		hunger += (target - hunger) * (target > hunger ? 0.2 : 0.1);
		worst = Math.max(worst, hunger);

		// Contractions start once the air is nearly gone and come faster after:
		// every two seconds or so at first, close to three a second at the end.
		if (hunger > 0.2) {
			double period = 44.0 - 30.0 * hunger;
			contractionPhase += 1.0 / period;
			if (contractionPhase >= 1.0) {
				contractionPhase -= 1.0;
				pulse = 1.0;
				contractions++;
				CameraMotion.contraction(hunger * ClientEffectsConfig.get().vignetteIntensity);
			}
		} else {
			contractionPhase = 0.0;
		}
		pulse = Math.max(0.0, pulse - 0.18);

		// Out of it: gasp, sized by how far gone you got.
		boolean breathing = !underwater && !inWall;
		if (breathing && worst > 0.3) {
			Breathing.gasp(worst);
			worst = 0.0;
		} else if (breathing) {
			worst = Math.max(0.0, worst - 0.02);
		}
	}

	/** Dark, cold edges closing in, jumping with each contraction. */
	public static void render(GuiGraphicsExtractor graphics, int width, int height) {
		double strength = hunger * ClientEffectsConfig.get().vignetteIntensity;
		if (strength <= 0.01) {
			return;
		}
		float s = (float) Math.min(1.0, strength * (0.75 + 0.25 * pulse));
		// The vignette pipeline multiplies by (1 - tint): more red and green taken
		// than blue leaves the edges a deep cold navy rather than black.
		graphics.blit(RenderPipelines.VIGNETTE, VIGNETTE, 0, 0, 0.0F, 0.0F, width, height, width, height,
				ARGB.colorFromFloat(1.0F, s, s * 0.92F, s * 0.62F));
	}

	public static void reset() {
		hunger = 0.0;
		contractionPhase = 0.0;
		pulse = 0.0;
		worst = 0.0;
	}
}
