package dev.captience.realisticdamage.body;

import dev.captience.realisticdamage.config.Balance;

public enum WoundSeverity {
	MINOR(Balance.BLEED_RATE_MINOR),
	MODERATE(Balance.BLEED_RATE_MODERATE),
	SEVERE(Balance.BLEED_RATE_SEVERE),
	CRITICAL(Balance.BLEED_RATE_CRITICAL);

	private final double bloodPerSecond;

	WoundSeverity(double bloodPerSecond) {
		this.bloodPerSecond = bloodPerSecond;
	}

	public double bloodPerSecond() {
		return bloodPerSecond;
	}

	public WoundSeverity weaker() {
		return ordinal() == 0 ? null : values()[ordinal() - 1];
	}
}
