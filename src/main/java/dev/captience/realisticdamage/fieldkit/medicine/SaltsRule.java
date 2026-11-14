package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath;
import org.jspecify.annotations.Nullable;

/**
 * Whether smelling salts bring a body round (2.0, phase 4 round 2), as a rule
 * apart from the world. The body doesn't record why it went out, so this
 * reads it from the body: with too little blood the drain never stops and
 * they'd only drop again; with no air there's nothing to breathe the salts
 * in; under Sedation (out cold or overdosed) the drug holds them down.
 * Anything else that leaves someone out (a blow, a concussion, a faint that
 * has passed) salts can answer.
 */
public final class SaltsRule {
	private SaltsRule() {}

	public enum Why {
		AWAKE("awake"), BLOOD("blood"), AIR("air"), SEDATED("sedated");

		public final String key;

		Why(String key) {
			this.key = key;
		}
	}

	/** Why salts won't work, or null when they will. */
	public static @Nullable Why why(boolean unconscious, double blood, boolean starved, SedationMath.Stage stage) {
		if (!unconscious) {
			return Why.AWAKE;
		}
		if (blood < Balance.BLOOD_MILD) {
			return Why.BLOOD;
		}
		if (starved) {
			return Why.AIR;
		}
		if (stage == SedationMath.Stage.OUT || stage == SedationMath.Stage.OVERDOSE) {
			return Why.SEDATED;
		}
		return null;
	}
}
