package dev.captience.realisticdamage.body;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.WoundMath;

/**
 * Complete Realistic Damage state for one player (spec §48). Server
 * authoritative; the client only ever sees synced snapshots. Pure logic —
 * codecs/attachment wiring live in the persistence layer.
 */
public final class RealisticDamageData {
	private final Map<BodyPart, BodyPartData> parts = new EnumMap<>(BodyPart.class);
	private final List<BleedWound> wounds = new ArrayList<>();
	private double blood = Balance.BLOOD_MAX;
	private double consciousness = Balance.CONSCIOUSNESS_MAX;
	private boolean unconscious;
	private int unconsciousTicks;
	private boolean concussion;
	private boolean burned;
	/** Transient: health captured at ALLOW_DAMAGE so AFTER_DAMAGE can compute the true post-mitigation damage. */
	private transient double preHurtHealth = Double.NaN;
	/** Transient: fractional progress toward stalling one item-use tick (two-handed impairment, spec §11). */
	private transient double useStallProgress;
	/** Transient: fractional vanilla-HP bleed-out damage accumulated toward the next whole point (spec §17). */
	private transient double bleedHpProgress;

	public RealisticDamageData() {
		for (BodyPart part : BodyPart.values()) {
			parts.put(part, new BodyPartData(part));
		}
	}

	// --- Body parts ---

	public BodyPartData part(BodyPart part) {
		return parts.get(part);
	}

	public Iterable<BodyPartData> allParts() {
		return parts.values();
	}

	/** Both legs gone: the player can only crawl (Arcade preset). */
	public boolean legless() {
		return part(BodyPart.LEFT_LEG).destroyed() && part(BodyPart.RIGHT_LEG).destroyed();
	}

	/** Condition fraction of the worse leg, for fall amplification and sprint checks. */
	public double worseLegFraction() {
		return Math.min(part(BodyPart.LEFT_LEG).fraction(), part(BodyPart.RIGHT_LEG).fraction());
	}

	// --- Blood ---

	public double blood() {
		return blood;
	}

	public void setBlood(double value) {
		blood = Math.clamp(value, 0.0, Balance.BLOOD_MAX);
	}

	public void drainBlood(double amount) {
		setBlood(blood - amount);
	}

	public void restoreBlood(double amount) {
		setBlood(blood + amount);
	}

	// --- Consciousness ---

	public double consciousness() {
		return consciousness;
	}

	/** Effective consciousness ceiling from head condition (spec §20), lowered further by concussion. */
	public double consciousnessCap() {
		double cap = switch (part(BodyPart.HEAD).state()) {
			case HEALTHY -> Balance.HEAD_CAP_HEALTHY;
			case HURT -> Balance.HEAD_CAP_HURT;
			case INJURED -> Balance.HEAD_CAP_INJURED;
			case CRITICAL -> Balance.HEAD_CAP_CRITICAL;
			case DISABLED, DESTROYED -> Balance.HEAD_CAP_DISABLED;
		};
		if (concussion) {
			cap = Math.max(Balance.WAKE_THRESHOLD + 5.0, cap - 10.0);
		}
		return cap;
	}

	public void setConsciousness(double value) {
		consciousness = Math.clamp(value, 0.0, consciousnessCap());
	}

	public void drainConsciousness(double amount) {
		consciousness = Math.max(0.0, consciousness - amount);
	}

	public void restoreConsciousness(double amount) {
		setConsciousness(consciousness + amount);
	}

	public boolean unconscious() {
		return unconscious;
	}

	public void setUnconscious(boolean value) {
		unconscious = value;
		unconsciousTicks = 0;
	}

	public int unconsciousTicks() {
		return unconsciousTicks;
	}

	public void tickUnconscious() {
		if (unconscious) {
			unconsciousTicks++;
		}
	}

	public void setUnconsciousTicks(int ticks) {
		unconsciousTicks = Math.max(0, ticks);
	}

	// --- Injuries ---

	public boolean concussion() {
		return concussion;
	}

	public void setConcussion(boolean value) {
		concussion = value;
	}

	public boolean burned() {
		return burned;
	}

	public void setBurned(boolean value) {
		burned = value;
	}

	// --- Wounds ---

	public List<BleedWound> wounds() {
		return wounds;
	}

	public boolean bleeding() {
		return !wounds.isEmpty();
	}

	/** Worst active wound severity, or null when not bleeding. */
	public WoundSeverity worstWound() {
		WoundSeverity worst = null;
		for (BleedWound wound : wounds) {
			if (worst == null || wound.severity().ordinal() > worst.ordinal()) {
				worst = wound.severity();
			}
		}
		return worst;
	}

	/** Effective total bleed rate in Blood/sec with diminishing stacking (spec §16). */
	public double effectiveBleedRate() {
		if (wounds.isEmpty()) {
			return 0.0;
		}
		double[] rates = new double[wounds.size()];
		for (int i = 0; i < rates.length; i++) {
			rates[i] = wounds.get(i).bloodPerSecond();
		}
		return WoundMath.effectiveBleedRate(rates);
	}

	/**
	 * Adds a wound with the deterministic cap/merge strategy (spec §16): if a
	 * same-region wound exists at or below the new severity, escalate it instead
	 * of stacking objects. Beyond {@link Balance#MAX_WOUNDS} total, escalate the
	 * weakest wound instead of growing the list.
	 */
	public void addWound(BodyPart part, WoundSeverity severity) {
		BleedWound weakestSameRegion = null;
		for (BleedWound wound : wounds) {
			if (wound.part() == part && wound.severity().ordinal() <= severity.ordinal()
					&& (weakestSameRegion == null || wound.severity().ordinal() < weakestSameRegion.severity().ordinal())) {
				weakestSameRegion = wound;
			}
		}
		if (weakestSameRegion != null) {
			// Same spot, equal-or-worse hit: the wound worsens (or refreshes) deterministically.
			if (weakestSameRegion.severity() == severity) {
				WoundSeverity[] all = WoundSeverity.values();
				int next = Math.min(severity.ordinal() + 1, all.length - 1);
				weakestSameRegion.escalateTo(all[next]);
			} else {
				weakestSameRegion.escalateTo(severity);
			}
			return;
		}
		if (wounds.size() >= Balance.MAX_WOUNDS) {
			BleedWound weakest = wounds.getFirst();
			for (BleedWound wound : wounds) {
				if (wound.severity().ordinal() < weakest.severity().ordinal()) {
					weakest = wound;
				}
			}
			weakest.escalateTo(severity);
			return;
		}
		wounds.add(new BleedWound(part, severity));
	}

	public void clearWounds() {
		wounds.clear();
	}

	// --- Damage-hook bookkeeping (spec §5: post-mitigation base) ---

	public void capturePreHurt(double health) {
		preHurtHealth = health;
	}

	/**
	 * Returns the health actually lost since {@link #capturePreHurt} (absorption
	 * excluded per spec §39), or -1 when no capture is pending. Single-use.
	 */
	public double consumePreHurt(double healthNow) {
		if (Double.isNaN(preHurtHealth)) {
			return -1.0;
		}
		double lost = Math.max(0.0, preHurtHealth - healthNow);
		preHurtHealth = Double.NaN;
		return lost;
	}

	/**
	 * Advances the deterministic item-use stall accumulator by {@code penalty}
	 * (0..1); returns true when one use tick should be stalled. A penalty of 0.3
	 * makes bow draws take ~1/0.7x longer, smoothly and reproducibly.
	 */
	public boolean advanceUseStall(double penalty) {
		useStallProgress += penalty;
		if (useStallProgress >= 1.0) {
			useStallProgress -= 1.0;
			return true;
		}
		return false;
	}

	/** Puts an unspent bleed point back when the hurt was refused (i-frames, etc.). */
	public void refundBleedHpDrain(double hp) {
		bleedHpProgress += hp;
	}

	/** Accumulates bleed-out HP drain; returns true when one whole HP point should be dealt. */
	public boolean advanceBleedHpDrain(double hpThisTick) {
		bleedHpProgress += hpThisTick;
		if (bleedHpProgress >= 1.0) {
			bleedHpProgress -= 1.0;
			return true;
		}
		return false;
	}

	// --- Whole-state operations ---

	/** Full reset to a freshly-spawned body (death/respawn, spec §46; debug heal §54). */
	public void reset() {
		for (BodyPart part : BodyPart.values()) {
			BodyPartData data = parts.get(part);
			data.setDestroyed(false);
			data.setCondition(part.maxCondition());
			data.setBroken(false);
			data.strain(0);
		}
		wounds.clear();
		blood = Balance.BLOOD_MAX;
		consciousness = Balance.CONSCIOUSNESS_MAX;
		unconscious = false;
		unconsciousTicks = 0;
		concussion = false;
		burned = false;
	}

	/** Totem stabilization (spec §41): floors, not a full reset. Injury statuses persist. */
	public void totemStabilize() {
		clearWounds();
		if (blood < Balance.TOTEM_BLOOD_FLOOR) {
			blood = Balance.TOTEM_BLOOD_FLOOR;
		}
		for (BodyPartData data : parts.values()) {
			double floor = data.part().maxCondition() * Balance.TOTEM_CONDITION_FLOOR_FRACTION;
			if (data.condition() < floor) {
				data.setCondition(floor);
			}
		}
		// Raise consciousness AFTER conditions so the head-state cap reflects the restored head.
		if (consciousness < Balance.TOTEM_CONSCIOUSNESS_FLOOR) {
			consciousness = Math.min(Balance.TOTEM_CONSCIOUSNESS_FLOOR, consciousnessCap());
		}
		if (unconscious && consciousness >= Balance.WAKE_THRESHOLD) {
			unconscious = false;
			unconsciousTicks = 0;
		}
	}
}
