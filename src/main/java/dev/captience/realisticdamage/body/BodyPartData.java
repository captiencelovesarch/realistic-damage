package dev.captience.realisticdamage.body;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.CeilingMath;

/**
 * Mutable condition/injury state of one body region. Pure logic — persistence
 * codecs and networking live elsewhere.
 */
public final class BodyPartData {
	private final BodyPart part;
	private double condition;
	private boolean broken;
	private boolean destroyed;
	/** While > 0, recovery for this part is paused (severe-arm strain, spec §14). */
	private int strainPauseTicks;
	/**
	 * Long-term injury ceiling (1.6): 0 heals to max; 1..3 pin this part at Hurt,
	 * Injured or Critical. A band and not a figure, so there is no ceiling number
	 * anywhere in the mod to leak and no value between rungs to sanitise. Only
	 * the ceiling logic, the codec, reset() and /rdmg change it — healing never
	 * does, and nothing but restoreCeiling() ever raises it.
	 */
	private int ceilingBand;
	/**
	 * What this region has been through (1.6). Raised inside the setters below so
	 * that no caller — the accumulation path, {@code /rdmg break}, explosive
	 * dismemberment, anything added later — can forget it, and never lowered by
	 * healing: Broken clears itself silently at HEALTHY, so by the time anyone
	 * could ask "was this ever broken" the answer is already gone unless it was
	 * written down here.
	 *
	 * <p>Purely cosmetic. Nothing in the mod's own gameplay reads it — not
	 * impairment, not healing, not the prey score — so a scar can never change
	 * an outcome.
	 */
	private ScarLevel scar = ScarLevel.NONE;

	public BodyPartData(BodyPart part) {
		this.part = part;
		this.condition = part.maxCondition();
	}

	public BodyPart part() {
		return part;
	}

	public double condition() {
		return condition;
	}

	/**
	 * Deliberately against the part's own max, never against the ceiling (1.6).
	 * Measuring a capped part against its ceiling would make it read HEALTHY and
	 * delete every impairment, prey-score and fall penalty the ceiling exists to
	 * impose — the opposite of the feature. A future refactor must not "fix" this.
	 */
	public double fraction() {
		return condition / part.maxCondition();
	}

	public BodyState state() {
		return destroyed ? BodyState.DESTROYED : BodyState.fromFraction(fraction());
	}

	public boolean destroyed() {
		return destroyed;
	}

	/** Irreversible short of death: a destroyed part stays at zero condition forever. */
	public void setDestroyed(boolean destroyed) {
		this.destroyed = destroyed;
		if (destroyed) {
			condition = 0.0;
			// Recorded here rather than at the call sites, and before broken is
			// wiped: whatever the part was, losing it outranks it, and anything
			// downstream trying to derive "was this broken" is already too late.
			broken = false;
			scar = scar.max(ScarLevel.DESTROYED);
		}
	}

	public boolean broken() {
		return broken;
	}

	public void setBroken(boolean broken) {
		this.broken = broken;
		// Inside the setter, because /rdmg break bypasses BreakLogic entirely and
		// a future destruction path would otherwise have to remember to mark.
		if (broken) {
			scar = scar.max(ScarLevel.BROKEN);
		}
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

	public ScarLevel scar() {
		return scar;
	}

	/** Only ever raises: history accumulates, it does not heal. */
	public void markScar(ScarLevel level) {
		scar = scar.max(level);
	}

	/** Raw overwrite for {@code /rdmg scar}; gameplay goes through the setters above. */
	public void setScar(ScarLevel level) {
		scar = level == null ? ScarLevel.NONE : level;
	}

	/** The debug heal is a full restore. Death is not this path. */
	public void clearScar() {
		scar = ScarLevel.NONE;
	}

	public int ceilingBand() {
		return ceilingBand;
	}

	/** The most condition this part can ever recover to. */
	public double ceiling() {
		return CeilingMath.fraction(ceilingBand) * part.maxCondition();
	}

	/** What every heal, setCondition and missing() honours: the ceiling, or zero once destroyed. */
	public double effectiveMax() {
		return destroyed ? 0.0 : ceiling();
	}

	/**
	 * Lowers the ceiling to a band, sanitised and floored per part, and pulls
	 * condition under it. Never raises: a ceiling only ever goes one way.
	 * Deliberately no Broken-clear here — being capped is not mending; that
	 * happens on the next heal that actually reaches the ceiling.
	 *
	 * @return true if the ceiling moved
	 */
	public boolean lowerCeilingTo(int band) {
		int legal = CeilingMath.legalBand(part, band);
		if (legal <= ceilingBand) {
			return false;
		}
		ceilingBand = legal;
		condition = Math.min(condition, effectiveMax());
		return true;
	}

	/** The one way back: /rdmg heal and /rdmg ceiling ... max. */
	public void restoreCeiling() {
		ceilingBand = 0;
	}

	/** Food and time have done all they can: condition sits at the ceiling. */
	public boolean atCeiling() {
		return !destroyed && condition >= effectiveMax() - Balance.CONDITION_EPSILON;
	}

	/** Progress toward the best this part can ever be. Status clears only — never shown, never sent. */
	public double recoveryFraction() {
		return destroyed ? 0.0 : Math.min(1.0, condition / ceiling());
	}

	/** Spec §15: Broken clears at HEALTHY; 1.6: or once healed as far as its ceiling allows. */
	private boolean mended() {
		return state() == BodyState.HEALTHY || atCeiling();
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

	/**
	 * Heals condition, clamped at the effective max — the part's own max until a
	 * 1.6 ceiling lowers it. Returns the amount actually restored, and clears
	 * Broken once the part is mended (spec §15: HEALTHY, or at its ceiling).
	 */
	public double heal(double amount) {
		if (amount <= 0 || destroyed) {
			return 0.0;
		}
		double before = condition;
		condition = Math.min(effectiveMax(), condition + amount);
		if (broken && mended()) {
			broken = false;
		}
		return condition - before;
	}

	public void setCondition(double value) {
		if (destroyed) {
			return;
		}
		condition = Math.clamp(value, 0.0, effectiveMax());
		if (broken && mended()) {
			broken = false;
		}
	}

	/** Destroyed parts are excluded from healing budgets — nothing can repair them. */
	public double missing() {
		return destroyed ? 0.0 : Math.max(0.0, effectiveMax() - condition);
	}
}
