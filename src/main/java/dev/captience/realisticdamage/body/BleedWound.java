package dev.captience.realisticdamage.body;

/**
 * One active bleeding wound (spec §16). Severity can decay through healing;
 * {@code decayProgress} accumulates fractional healing toward the next-weaker
 * severity step. Fully decaying a MINOR wound removes it.
 */
public final class BleedWound {
	private final BodyPart part;
	private WoundSeverity severity;
	private double decayProgress;

	public BleedWound(BodyPart part, WoundSeverity severity) {
		this(part, severity, 0.0);
	}

	public BleedWound(BodyPart part, WoundSeverity severity, double decayProgress) {
		this.part = part;
		this.severity = severity;
		this.decayProgress = Math.clamp(decayProgress, 0.0, 1.0);
	}

	public BodyPart part() {
		return part;
	}

	public WoundSeverity severity() {
		return severity;
	}

	public double decayProgress() {
		return decayProgress;
	}

	public double bloodPerSecond() {
		return severity.bloodPerSecond();
	}

	/**
	 * Advances healing decay by the given fraction of a severity step.
	 * Returns true when the wound is fully healed and should be removed.
	 */
	public boolean decay(double fractionOfStep) {
		decayProgress += fractionOfStep;
		while (decayProgress >= 1.0) {
			decayProgress -= 1.0;
			WoundSeverity weaker = severity.weaker();
			if (weaker == null) {
				return true;
			}
			severity = weaker;
		}
		return false;
	}

	/** Escalates this wound to at least the given severity (used when merging new wounds). */
	public void escalateTo(WoundSeverity atLeast) {
		if (atLeast.ordinal() > severity.ordinal()) {
			severity = atLeast;
			decayProgress = 0.0;
		}
	}
}
