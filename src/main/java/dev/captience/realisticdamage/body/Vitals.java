package dev.captience.realisticdamage.body;

import dev.captience.realisticdamage.config.Balance;

/**
 * Band math for the two hidden meters. Pure and Minecraft-free so it unit-tests
 * without a runtime, and shared by the server (which decides when to warn you)
 * and the client (which names the band on the inventory panel) so the two can
 * never end up telling you different things.
 *
 * <p>Bands, never figures: naming a band is the most the presentation layer is
 * allowed to say about Blood and Consciousness (spec §42).
 */
public final class Vitals {
	private Vitals() {}

	/** 0 stable, 1 mild, 2 significant, 3 critical, 4 catastrophic. */
	public static int bloodBand(double blood) {
		if (blood < Balance.BLOOD_CRITICAL) {
			return 4;
		}
		if (blood < Balance.BLOOD_SERIOUS) {
			return 3;
		}
		if (blood < Balance.BLOOD_MILD) {
			return 2;
		}
		return blood < Balance.BLOOD_STABLE ? 1 : 0;
	}

	/**
	 * 0 clear, 1 woozy, 2 fading, 3 barely there, 4 out cold. Cut at the points
	 * on the symptom ladder where the player can actually feel a change, so the
	 * word matches what the screen is doing.
	 */
	public static int consciousnessBand(double consciousness, boolean unconscious) {
		if (unconscious || consciousness <= 0.0) {
			return 4;
		}
		if (consciousness < Balance.SYMPTOM_SPRINT_LOSS) {
			return 3;
		}
		if (consciousness < Balance.SYMPTOM_EXPOSURE) {
			return 2;
		}
		return consciousness < Balance.SYMPTOM_VIGNETTE ? 1 : 0;
	}
}
