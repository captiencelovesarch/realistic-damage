package dev.captience.realisticdamage.config;

/**
 * Every tuning constant in Realistic Damage lives here (spec: no scattered magic
 * numbers). Server config multipliers scale on top of these base values; the
 * constants themselves are the deterministic baseline the tests pin down.
 */
public final class Balance {
	private Balance() {}

	// --- Body condition maxima (spec §2) ---
	public static final double HEAD_MAX = 20.0;
	public static final double TORSO_MAX = 20.0;
	public static final double ARM_MAX = 13.0;
	public static final double LEG_MAX = 16.0;

	// --- Body state thresholds, as fraction of max condition (spec §3) ---
	public static final double HEALTHY_ABOVE = 0.75;
	public static final double HURT_ABOVE = 0.50;
	public static final double INJURED_ABOVE = 0.25;
	// CRITICAL is (0, INJURED_ABOVE]; DISABLED is exactly 0.

	// --- Trauma multipliers per damage category (spec §5) ---
	public static final double TRAUMA_GENERIC = 0.75;
	public static final double TRAUMA_SLASH = 1.00;
	public static final double TRAUMA_HEAVY_SLASH = 1.15;
	public static final double TRAUMA_PUNCTURE = 1.10;
	public static final double TRAUMA_HEAVY_PUNCTURE = 1.20;
	public static final double TRAUMA_BLUNT = 1.20;
	public static final double TRAUMA_HEAVY_BLUNT = 1.40;
	public static final double TRAUMA_EXPLOSION = 1.35;
	public static final double TRAUMA_FALL = 1.25;
	public static final double TRAUMA_FIRE = 0.60;
	public static final double TRAUMA_LAVA = 1.00;
	public static final double TRAUMA_SYSTEMIC = 0.10;

	/** Head/torso hits leak this fraction of their localized trauma to each limb (spec §8). */
	public static final double CORE_HIT_LIMB_SHOCK = 0.10;

	// --- Deterministic limb breaking (spec §15) ---
	/** Single qualifying heavy impact: condition damage at/above this breaks a limb. */
	public static final double BREAK_IMPACT_ARM = 5.5;
	public static final double BREAK_IMPACT_LEG = 6.5;

	// --- Limb destruction (Arcade preset) ---
	/**
	 * Trauma that lands on an already-Disabled part and meets this threshold
	 * destroys it outright. Deterministic, like every other injury.
	 */
	public static final double DESTROY_TRAUMA_ARM = 5.0;
	public static final double DESTROY_TRAUMA_LEG = 6.0;
	public static final double DESTROY_TRAUMA_CORE = 9.0;
	/** A destroyed leg is a permanent, heavy movement penalty. */
	public static final double LEG_PENALTY_DESTROYED = 0.45;
	/** Both legs gone: you crawl. Slow, but never a zero-speed softlock. */
	public static final double CRAWL_MOVEMENT_PENALTY = 0.72;
	/**
	 * With the main arm gone you work one-handed: mining and swinging still
	 * function, but slowly. Deliberately above the ordinary injury cap — this is
	 * worse than any survivable injury, yet never a hard block, so losing an arm
	 * underground is a crisis rather than an instant run-ender.
	 */
	public static final double ONE_HANDED_PENALTY = 0.80;

	// --- Bleeding (spec §16-17) ---
	public static final double BLOOD_MAX = 100.0;
	/** Blood points per second per wound severity. */
	public static final double BLEED_RATE_MINOR = 0.25;
	public static final double BLEED_RATE_MODERATE = 0.6;
	public static final double BLEED_RATE_SEVERE = 1.2;
	public static final double BLEED_RATE_CRITICAL = 2.0;
	/** Diminishing stacking weights, strongest wound first; 4th+ each get the last weight. */
	public static final double[] WOUND_STACK_WEIGHTS = {1.0, 0.5, 0.25, 0.125};
	/** Hard cap on effective total bleed rate (Blood/sec). */
	public static final double WOUND_STACK_CAP = 3.0;
	/** Max wounds tracked per player; new wounds merge into the weakest same-region wound. */
	public static final int MAX_WOUNDS = 8;

	// Blood bands (spec §17).
	public static final double BLOOD_STABLE = 70.0;
	public static final double BLOOD_MILD = 50.0;
	public static final double BLOOD_SERIOUS = 30.0;
	public static final double BLOOD_CRITICAL = 15.0;

	/** Below BLOOD_SERIOUS: vanilla HP damage per tick-cycle begins. Half-hearts per second. */
	public static final double BLEEDOUT_HP_PER_SEC_SERIOUS = 0.4;
	public static final double BLEEDOUT_HP_PER_SEC_CRITICAL = 1.6;
	/** Empty: a full-health player bleeds out in about four seconds. */
	public static final double BLEEDOUT_HP_PER_SEC_ZERO = 5.0;

	// --- Consciousness (spec §19-23) ---
	public static final double CONSCIOUSNESS_MAX = 100.0;
	/** Baseline stable recovery, points per second (spec §22). */
	public static final double CONSCIOUSNESS_RECOVERY_PER_SEC = 2.5;
	/** Effective consciousness cap by head state (spec §20). */
	public static final double HEAD_CAP_HEALTHY = 100.0;
	public static final double HEAD_CAP_HURT = 95.0;
	public static final double HEAD_CAP_INJURED = 82.0;
	public static final double HEAD_CAP_CRITICAL = 55.0;
	public static final double HEAD_CAP_DISABLED = 25.0;

	/** Collapse at 0; wake only once consciousness reaches this (hysteresis, spec §25). */
	public static final double WAKE_THRESHOLD = 20.0;
	/** Minimum time unconscious, in ticks (~2.5s). */
	public static final int MIN_UNCONSCIOUS_TICKS = 50;

	// Symptom ladder thresholds (spec §23) — shared by server (action locks) and client (fx).
	public static final double SYMPTOM_VIGNETTE = 75.0;
	public static final double SYMPTOM_SENSITIVITY = 65.0;
	public static final double SYMPTOM_ATTACK_RECOVERY = 55.0;
	public static final double SYMPTOM_AFTERIMAGE = 45.0;
	public static final double SYMPTOM_EXPOSURE = 35.0;
	public static final double SYMPTOM_RINGING = 30.0;
	public static final double SYMPTOM_HEARTBEAT = 25.0;
	public static final double SYMPTOM_SPRINT_LOSS = 20.0;
	public static final double SYMPTOM_SEVERE = 10.0;
	/**
	 * Near-death feedback. Vanilla HP answers "am I dying?", and the answer
	 * deserves to be felt: below this fraction of max health the heartbeat rises
	 * and a red vignette closes in, independently of body condition.
	 */
	public static final double LOW_HP_FRACTION = 0.35;

	/** Look-sensitivity reduction is capped (spec §24). */
	public static final double SENSITIVITY_REDUCTION_CAP = 0.35;

	// Continuous consciousness drain from blood loss (points/sec, spec §17, §19).
	public static final double CONSCIOUSNESS_DRAIN_BLOOD_MILD = 0.6;      // blood 30..50
	public static final double CONSCIOUSNESS_DRAIN_BLOOD_SERIOUS = 2.5;   // blood 15..30
	public static final double CONSCIOUSNESS_DRAIN_BLOOD_CRITICAL = 9.0;  // blood 0..15
	/** Empty: consciousness is gone in roughly three seconds. */
	public static final double CONSCIOUSNESS_DRAIN_BLOOD_ZERO = 33.0;     // blood == 0
	/** Drain while out of air (drowning/suffocating), points/sec. */
	public static final double CONSCIOUSNESS_DRAIN_ASPHYXIA = 2.5;
	/** Blood scales recovery down to this floor as it approaches 0 (spec §18-19). */
	public static final double CONSCIOUSNESS_RECOVERY_BLOOD_FLOOR = 0.3;

	// Event-driven consciousness hits (spec §19-20).
	/** Head condition damage converts to consciousness loss at this scale. */
	public static final double CONSCIOUSNESS_HEAD_HIT_SCALE = 4.0;
	/** Non-head trauma converts at this scale (explosions, falls, systemic). */
	public static final double CONSCIOUSNESS_BODY_HIT_SCALE = 1.0;
	/**
	 * Whole-body deceleration: a hard landing or a blast rattles you regardless of
	 * which region absorbed it. Scales off the total damage, which is what makes a
	 * long fall able to knock you out rather than merely wreck your legs.
	 */
	public static final double CONSCIOUSNESS_IMPACT_SCALE = 3.0;
	/** Head trauma that is a knock-on rather than the primary hit still counts, at this weight. */
	public static final double CONSCIOUSNESS_SECONDARY_HEAD_WEIGHT = 0.6;

	// Concussion (spec §21).
	/** Single head trauma at/above this concusses outright. */
	public static final double CONCUSSION_HEAD_TRAUMA_THRESHOLD = 6.0;
	/** Lesser head trauma concusses once the head is INJURED or worse. */
	public static final double CONCUSSION_HEAD_TRAUMA_INJURED_THRESHOLD = 3.0;
	/** Concussion halves consciousness recovery... */
	public static final double CONCUSSION_RECOVERY_FACTOR = 0.5;
	/** ...and amplifies further head-trauma consciousness loss. */
	public static final double CONCUSSION_EXTRA_HEAD_DRAIN = 1.5;
	/**
	 * A concussion needs this much continuous stable time (healthy head, steady
	 * consciousness, not bleeding) before it clears — about 90 seconds. Without a
	 * timer the clear condition was satisfiable the instant it was applied.
	 */
	public static final int CONCUSSION_STABLE_TICKS = 1800;
	/** How close to the cap consciousness must sit to count as "stabilized". */
	public static final double CONCUSSION_STABLE_MARGIN = 2.0;
	/**
	 * Concussion makes every sensory symptom arrive sooner (spec §21): effects are
	 * computed as if consciousness were this much lower than it really is.
	 */
	public static final double CONCUSSION_SYMPTOM_SHIFT = 30.0;
	/** Constant double-vision ghosting while concussed — the signature symptom. */
	public static final double CONCUSSION_GHOST = 0.42;
	/**
	 * Vanilla's gaussian blur has no intensity parameter — it is on or off — so it
	 * is held back to a late, distinct stage while the ghosting carries the smooth
	 * build-up above it. Otherwise it simply pops in mid-ladder.
	 */
	public static final double SYMPTOM_BLUR = 18.0;

	// Low-consciousness action sluggishness (spec §23).
	public static final double SLUGGISH_PENALTY_AT_20 = 0.10;
	public static final double SLUGGISH_PENALTY_AT_0 = 0.30;

	// --- Impairment curve anchors (spec §11-12), piecewise-linear on condition fraction ---
	// Arms: action speed / damage penalty fraction at anchor condition fractions.
	public static final double ARM_PENALTY_AT_50 = 0.05;
	public static final double ARM_PENALTY_AT_25 = 0.15;
	public static final double ARM_PENALTY_NEAR_0 = 0.35;
	public static final double ARM_PENALTY_DISABLED = 0.50;
	/** Broken arm adds a flat penalty on top of the condition curve. */
	public static final double ARM_PENALTY_BROKEN_BONUS = 0.25;
	public static final double ARM_PENALTY_TOTAL_CAP = 0.65;

	// Legs: movement penalty anchors.
	public static final double LEG_PENALTY_AT_50 = 0.07;
	public static final double LEG_PENALTY_AT_25 = 0.18;
	public static final double LEG_PENALTY_NEAR_0 = 0.40;
	public static final double LEG_PENALTY_DISABLED = 0.55;
	public static final double LEG_PENALTY_BROKEN_BONUS = 0.20;
	/** Worse leg dominates the combined movement penalty (spec §12). */
	public static final double LEG_WORSE_WEIGHT = 0.70;
	/** Cap so both-legs-wrecked still limps home at >= ~25% speed (spec §12). */
	public static final double LEG_PENALTY_TOTAL_CAP = 0.75;
	/** Jump penalty scales off the movement penalty with this factor. */
	public static final double JUMP_PENALTY_FACTOR = 0.8;

	/** Injured legs amplify subsequent fall trauma (spec §13): multiplier at worst. */
	public static final double FALL_TRAUMA_INJURED_LEG_MAX_AMP = 2.0;

	// --- Systemic damage (spec §9) ---
	public static final double SYSTEMIC_SPREAD_FRACTION = 0.10;

	// --- Healing (spec §37-38) ---
	/** Body-condition repair budget granted per vanilla natural-regen heal, per HP healed. */
	public static final double HEAL_BUDGET_PER_REGEN_HP = 2.0;
	/** Blood restored per vanilla natural-regen heal, per HP healed. */
	public static final double BLOOD_PER_REGEN_HP = 3.0;
	/** Passive Blood regen per second when stable and not bleeding. */
	public static final double BLOOD_PASSIVE_PER_SEC = 0.05;
	/** Extra Blood per second while well fed (hunger >= 18). */
	public static final double BLOOD_WELLFED_PER_SEC = 0.25;
	/** Immediate Blood restored on eating = nutrition * this. */
	public static final double BLOOD_PER_NUTRITION = 1.0;
	/** Wound severity decay per natural-regen heal event (fraction of a severity step). */
	public static final double WOUND_DECAY_PER_REGEN = 0.34;
	/** Sleep recovery (spec §38). */
	public static final double SLEEP_BLOOD_RESTORE = 60.0;
	public static final double SLEEP_CONDITION_REPAIR_FRACTION = 0.5;

	/** Strained severe arm (recent heavy use) pauses that arm's recovery this long (ticks). */
	public static final int ARM_STRAIN_PAUSE_TICKS = 100;

	/** Burned reduces healing efficiency (spec §27)... */
	public static final double BURNED_HEALING_FACTOR = 0.6;
	/** ...and clears once the body has broadly recovered (average condition fraction) while not on fire. */
	public static final double BURNED_CLEAR_AVG_FRACTION = 0.75;
	/** Sustained burning (fire ticks beyond this) applies Burned (spec §27). */
	public static final int BURNED_APPLY_FIRE_TICKS = 60;
	/** Food exhaustion charged per full-HP body-repair cycle (food powers recovery, spec §37). */
	public static final float HEAL_EXHAUSTION_PER_CYCLE = 4.0F;

	// --- Golden foods (spec §40) ---
	public static final double GAPPLE_BLOOD_RESTORE = 40.0;
	public static final double GAPPLE_CONSCIOUSNESS_RESTORE = 25.0;
	public static final double GAPPLE_CONDITION_REPAIR = 12.0;
	public static final double ENCH_GAPPLE_BLOOD_RESTORE = 100.0;
	public static final double ENCH_GAPPLE_CONSCIOUSNESS_RESTORE = 60.0;
	public static final double ENCH_GAPPLE_CONDITION_REPAIR_FRACTION = 0.8;

	// --- Totem stabilization floors (spec §41) ---
	public static final double TOTEM_CONSCIOUSNESS_FLOOR = 60.0;
	public static final double TOTEM_BLOOD_FLOOR = 45.0;
	/** Disabled parts are raised to this fraction of max (low CRITICAL). */
	public static final double TOTEM_CONDITION_FLOOR_FRACTION = 0.15;

	// --- Hit-location geometry (spec §6) ---
	/** Fraction of bounding-box height where the head band starts (standing 1.8 -> 1.404, real head starts 1.4). */
	public static final double HEAD_BAND_FRACTION = 0.78;
	/** Fraction of bounding-box height where legs end (standing 1.8 -> 0.756, real legs ~0.75). */
	public static final double LEG_BAND_FRACTION = 0.42;
	/** |lateral| beyond this fraction of the half-width inside the torso band is an arm. */
	public static final double ARM_LATERAL_FRACTION = 0.60;

	// --- Environmental routing (spec §7, §29-30) ---
	/** Post-mitigation fall damage at/above this adds secondary torso trauma. */
	public static final double FALL_SECONDARY_TORSO_DAMAGE = 10.0;
	/** ...and at/above this also secondary head trauma (extreme falls, spec §30). */
	public static final double FALL_SECONDARY_HEAD_DAMAGE = 15.0;
	/** Explosion trauma shares: nearest region / core secondary / the rest (spec §29). */
	public static final double EXPLOSION_PRIMARY_SHARE = 0.50;
	public static final double EXPLOSION_SECONDARY_SHARE = 0.30;

	// --- Armor / enchantments (spec §31) ---
	/** Specialized enchantments (Blast/Projectile/Fire Protection) per level on a covering piece. */
	public static final double ENCH_SPECIALIZED_TRAUMA_PER_LEVEL = 0.06;
	/** Overall localized-armor reduction caps. */
	public static final double ARMOR_REDUCTION_CAP = 0.60;
	public static final double ARMOR_FALL_REDUCTION_CAP = 0.85;

	/** Localized armor: covering piece reduces body trauma by up to this fraction (scaled by piece defense). */
	public static final double LOCAL_ARMOR_MAX_REDUCTION = 0.30;
	/** Trauma reduction per Protection level on the covering piece(s). */
	public static final double PROTECTION_TRAUMA_PER_LEVEL = 0.04;
	public static final double PROTECTION_TRAUMA_CAP = 0.20;
	public static final double FEATHER_FALLING_LEG_TRAUMA_PER_LEVEL = 0.18;

	// --- Shields (spec §32) ---
	/** Heavy blunt/explosion damage transfers this fraction of would-be trauma to the shield arm on a full block. */
	public static final double SHIELD_HEAVY_SHOCK_FRACTION = 0.25;
	/** Minimum blocked damage before any shield-arm shock happens. */
	public static final double SHIELD_SHOCK_MIN_DAMAGE = 8.0;
}
