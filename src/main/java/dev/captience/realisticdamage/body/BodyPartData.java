package dev.captience.realisticdamage.body;

/**
 * Mutable condition/injury state of one body region. Pure logic — persistence
 * codecs and networking live elsewhere.
 */
public final class BodyPartData {
	private final BodyPart part;
	private double condition;
	private boolean broken;
	/** While > 0, recovery for this part is paused (severe-arm strain, spec §14). */
	private int strainPauseTicks;

	public BodyPartData(BodyPart part) {
		this.part = part;
		this.condition = part.maxCondition();
	}

	public BodyPartData(BodyPart part, double condition, boolean broken, int strainPauseTicks) {
		this.part = part;
		this.condition = Math.clamp(condition, 0.0, part.maxCondition());
		this.broken = broken;
		this.strainPauseTicks = Math.max(0, strainPauseTicks);
	}

	public BodyPart part() {
		return part;
	}

	public double condition() {
		return condition;
	}

	public double fraction() {
		return condition / part.maxCondition();
	}

	public BodyState state() {
		return BodyState.fromFraction(fraction());
	}

	public boolean broken() {
		return broken;
	}

	public void setBroken(boolean broken) {
		this.broken = broken;
	}

	public int strainPauseTicks() {
		return strainPauseTicks;
	}

	public void strain(int ticks) {
		strainPauseTicks = Math.max(strainPauseTicks, ticks);
	}

	public void tickStrain() {
		if (strainPauseTicks > 0) {
			strainPauseTicks--;
		}
	}

	/** Applies condition damage, clamped at 0. Returns the amount actually dealt. */
	public double damage(double amount) {
		if (amount <= 0) {
			return 0.0;
		}
		double before = condition;
		condition = Math.max(0.0, condition - amount);
		return before - condition;
	}

	/** Heals condition, clamped at max. Returns the amount actually restored. Clears Broken on reaching HEALTHY (spec §15). */
	public double heal(double amount) {
		if (amount <= 0) {
			return 0.0;
		}
		double before = condition;
		condition = Math.min(part.maxCondition(), condition + amount);
		if (broken && state() == BodyState.HEALTHY) {
			broken = false;
		}
		return condition - before;
	}

	public void setCondition(double value) {
		condition = Math.clamp(value, 0.0, part.maxCondition());
		if (broken && state() == BodyState.HEALTHY) {
			broken = false;
		}
	}

	public double missing() {
		return part.maxCondition() - condition;
	}
}
