package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.config.Balance;

/**
 * The food and drink's rules as numbers (2.0, phase 4, round 3), apart from the
 * items so a unit test can load them. No dice: the same drink on the same body
 * always lands the same way.
 */
public final class ProvisionsMath {
	private ProvisionsMath() {}

	/**
	 * How big a body counts as for Sedation: a caffeinated one carries a dose
	 * as a bigger body would, so the same dose takes it a stage less far.
	 */
	public static double sedationScale(double vitalityScale, boolean caffeinated) {
		return Math.max(1.0, vitalityScale) * (caffeinated ? Balance.COFFEE_SEDATION_RESIST : 1.0);
	}

	/** The open wounds' bleed, raised by a heart on coffee and by spirits. */
	public static double bleedFactor(boolean caffeinated, boolean tipsy) {
		return (caffeinated ? Balance.COFFEE_BLEED_FACTOR : 1.0) * (tipsy ? Balance.SPIRITS_BLEED_FACTOR : 1.0);
	}

	/** A limp or a sore arm's slowing, tipsy: a weaker numbing, the pain with its edge off. */
	public static double painFactor(boolean tipsy) {
		return tipsy ? Balance.SPIRITS_PAIN_FACTOR : 1.0;
	}
}
