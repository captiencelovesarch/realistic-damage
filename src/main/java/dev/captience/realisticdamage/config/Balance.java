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
	/**
	 * Firearms (1.3). Above heavy puncture because a bullet dumps its energy
	 * inside the region it enters, but the real difference is in the bleed
	 * thresholds rather than here: guns are dangerous because of what they open,
	 * not because of raw condition damage.
	 */
	public static final double TRAUMA_BALLISTIC = 1.30;
	public static final double TRAUMA_HEAVY_BALLISTIC = 1.55;
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

	// --- Explosive dismemberment (1.6) ---
	/**
	 * Blast trauma — vanilla's pre-armour blast figure x {@link #TRAUMA_EXPLOSION}
	 * x the region's share x (1 - localized armour) x the server multipliers — at
	 * or above this fraction of a limb's maximum condition takes the limb
	 * outright.
	 *
	 * <p><b>The gate is survival, not trauma.</b> Fabric's {@code AFTER_DAMAGE}
	 * never fires on the blow that kills, so to lose a limb you have to live
	 * through the blast that took it: every figure below is therefore a
	 * <em>window</em>, the stretch of distance between the closest you can stand
	 * and survive and the furthest you can stand and still be maimed. Survival is
	 * vanilla's own chain — {@code Player.hurtServer}'s difficulty scaling, then
	 * {@code CombatRules.getDamageAfterAbsorb}, then
	 * {@code getDamageAfterMagicAbsorb} — and it is what a threshold picked off
	 * the band table alone cannot see.
	 *
	 * <p>0.75 was that threshold: {@code 1 - INJURED_ABOVE}, a blast that would by
	 * itself drive a full limb into the CRITICAL band. Derived, tidy, and against
	 * a creeper on Normal it gave a plain-diamond player at 20 HP a window of
	 * 0.37 blocks and a plain-diamond player at 10 HP a window of exactly
	 * <em>zero</em> — closest survivable stand 2.14 blocks, furthest maiming
	 * stand 1.08. On Hard every plain kit died first. The feature it actually
	 * shipped was enchanted-armour-only, which is the outcome the design panel had
	 * already rejected.
	 *
	 * <p>0.45 is the largest fraction — the smallest move off 0.75 — that gives
	 * the case this is for a window with room in it rather than a knife edge.
	 * Against a creeper on Normal, window = (leg comes off out to) - (you survive
	 * from):
	 * <pre>
	 *   plain diamond, 20 HP   0.37 -> 1.92 blocks   (2.63 - 0.71)
	 *   plain diamond, 10 HP   0.00 -> 0.49 blocks   (2.63 - 2.14)   &lt;- the case
	 *   plain diamond, Hard 20 0.00 -> 0.58 blocks   (2.63 - 2.05)
	 *   unarmoured,    10 HP   0.00 -> 0.00 blocks   (3.94 - 4.06)
	 * </pre>
	 * The last row is the rule working, not failing: at half health with nothing
	 * on, a creeper close enough to take your leg is a creeper that kills you, and
	 * it should be. 0.50 was the alternative and gives the 10 HP case 0.21 blocks,
	 * which is one stride — a knife edge, not a window.
	 *
	 * <p>The cost is Blast Protection IV, and it was accepted knowingly. At 0.75
	 * the 0.60 armour cap needed an intensity of 44.44 and a creeper peaks at 43,
	 * so Blast IV was flat immunity to creeper dismemberment. At 0.45 the cap
	 * needs 26.67 and loses the leg inside 1.72 blocks. That is still the strongest
	 * protection in the game — a plain-diamond leg goes out to 2.63 — but it is
	 * protection, a shorter reach, rather than a line the game cannot cross.
	 *
	 * <p>The bar an intensity has to clear is
	 * {@code threshold / (1.35 x 0.50 x (1 - reduction))}: 7.2 / 0.675 = 10.67 on
	 * a bare leg, 7.2 / 0.37125 = 19.39 behind a plain diamond set, 7.2 / 0.27 =
	 * 26.67 at the cap. Keyed to the blast, not to the difficulty: Easy makes
	 * creepers less lethal, not less violent — difficulty moves only the survival
	 * half of the window, which is why the table above names one.
	 */
	public static final double EXPLOSIVE_DESTROY_FRACTION = 0.45;
	/** 13.0 x 0.45 = 5.85. Arms are only 60% covered by a chestplate, so they are the exposed limb. */
	public static final double EXPLOSIVE_DESTROY_ARM = ARM_MAX * EXPLOSIVE_DESTROY_FRACTION;
	/** 16.0 x 0.45 = 7.2. Legs are the best-covered region (leggings full, boots half) and the one a grounded creeper reaches first. */
	public static final double EXPLOSIVE_DESTROY_LEG = LEG_MAX * EXPLOSIVE_DESTROY_FRACTION;

	// --- Long-term injuries (1.6, hardcore) ---
	/**
	 * A ceiling is a band, never a figure: 1 pins the part at Hurt, 2 at Injured,
	 * 3 at Critical. The rungs are the state boundaries themselves
	 * ({@link #HEALTHY_ABOVE}, {@link #HURT_ABOVE}, {@link #INJURED_ABOVE}), so a
	 * part healed to its ceiling sits exactly on a band line and its colour is
	 * the ceiling made visible. No new doubles here for that reason.
	 */
	public static final int CEILING_BANDS = 3;
	/** Arms and legs bottom out at Critical: still a limb, permanently a poor one (leg penalty 0.18, falls x1.75). */
	public static final int CEILING_FLOOR_BAND_LIMB = 3;
	/**
	 * Head and chest stop at Hurt, one rung. Hurt caps consciousness at 95, and
	 * 95 - CONCUSSION_CAP_DROP - CONCUSSION_SYMPTOM_SHIFT = 67 still clears
	 * SYMPTOM_SENSITIVITY (65) and SYMPTOM_ATTACK_RECOVERY (55). An Injured
	 * ceiling gives 54 and installs a permanent action penalty; Critical (55) is
	 * the symptom ladder itself. A ceiling may cost the buffer above the ladder,
	 * never a rung of it. The torso follows the head because nothing reads torso
	 * condition: a permanently red chest would be a screen saying "dying" over a
	 * body that is fine.
	 */
	public static final int CEILING_FLOOR_BAND_CORE = 1;
	/** "At the ceiling" and "nothing left to heal" must agree to the same epsilon or they disagree for a tick. */
	public static final double CONDITION_EPSILON = 1.0e-9;

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
	/**
	 * Empty. Eased from 5.0 to 3.5 in 1.2: at four seconds from full health this
	 * was not a last stand so much as an announcement, and now that a companion
	 * can reach you and put food in you it needs to be a window rather than a
	 * formality. Roughly six seconds from full, and far less from the health you
	 * will actually have — still the worst place in the mod to be.
	 */
	public static final double BLEEDOUT_HP_PER_SEC_ZERO = 3.5;

	// --- Blood, as seen (1.5) ---
	// Everything here is presentation, but it is presentation of a real number:
	// every rate below is derived from the wound's actual Blood/sec, so what you
	// see is what you are losing. Before 1.5 bleeding was a fixed burst of four
	// redstone particles every 25 ticks, which encoded neither rate nor severity
	// and — spawning at the body's own regions — was invisible in first person.

	/** Drops per second at the point a wound stops being a scratch. */
	public static final double DROPS_PER_BLOOD_POINT = 4.0;
	/** However torn up you are, the emitter never exceeds this. Frame budget, not realism. */
	public static final double DROPS_PER_SECOND_MAX = 22.0;
	/**
	 * Below this Blood/sec a wound seeps rather than drips: drops still come, but
	 * as occasional single beads. Without a floor a MINOR wound rounds to nothing
	 * and the mod goes back to saying nothing at all.
	 */
	public static final double DROPS_PER_SECOND_MIN = 0.8;

	/**
	 * At or above this Blood/sec a wound is under pressure and throws blood on the
	 * beat instead of dripping steadily. Set to sit between MODERATE and SEVERE, so
	 * the arterial read arrives exactly when the wound becomes the thing that will
	 * kill you.
	 */
	public static final double ARTERIAL_RATE = 0.9;
	/** Fraction of a spurting wound's output saved up and thrown on the beat. */
	public static final double ARTERIAL_SPURT_SHARE = 0.72;
	/** Blocks per second a spurt leaves the wound at, before gravity takes it. */
	public static final double ARTERIAL_SPURT_SPEED = 5.5;
	/** How wide the spurt cone is, in degrees — narrow, because it is pressure, not spray. */
	public static final double ARTERIAL_SPURT_SPREAD = 14.0;
	/**
	 * However bad an artery is, one beat may not throw more than this many drops.
	 *
	 * <p>Lived in {@code BloodEmitter} as a private until the pool tier needed it.
	 * It is a balance number, and it is the exact bound
	 * {@link #STAIN_POOL_SPRITE_FEEDS} has to beat: every drop in a burst leaves
	 * one point on one tick, so a burst is the largest number of drops that can
	 * land in a single mark with no time passing at all. A pure test cannot check
	 * that gate against a number it cannot see.
	 */
	public static final int ARTERIAL_SPURT_DROPS_MAX = 7;

	/** Blocks per second a drop simply falling out of a wound starts with. */
	public static final double DROP_SEEP_SPEED = 0.6;
	/** Fraction of velocity kept per tick in air. */
	public static final double DROP_AIR_DRAG = 0.94;
	public static final double DROP_GRAVITY = 0.055;
	/** Ticks a drop may live before it gives up and fades, if it never lands. */
	public static final int DROP_MAX_AGE_TICKS = 120;
	/**
	 * Speed (blocks/sec) at which a drop reads as a full-size gout rather than a
	 * bead. Drops carry their severity in their velocity rather than in a custom
	 * particle payload: speed is already on the wire for every particle, so a
	 * spurt arrives at another client looking like a spurt for free.
	 */
	public static final double DROP_SCALE_FULL_SPEED = 6.0;

	/** Burst thrown when a blow first opens a wound, per severity step. */
	public static final int SPRAY_DROPS_PER_SEVERITY = 5;
	public static final double SPRAY_SPEED = 7.0;
	public static final double SPRAY_SPREAD = 38.0;
	/** A lost limb leaves more than the ordinary wound-opening spray behind. */
	public static final double SEVERANCE_SPRAY_SCALE = 1.8;
	/** Wide enough to put the loss on the floor as well as along the blow. */
	public static final double SEVERANCE_SPRAY_SPREAD = 70.0;
	/** Downward vector bias applied after the severance cone is rotated. */
	public static final double SEVERANCE_DOWN_BIAS = 0.45;
	/** Share of visible wound flow transferred to a wall the body is touching. */
	public static final double CONTACT_TRANSFER_SHARE = 0.35;

	// Blood meeting water (1.6.1): it disperses instead of sinking to the floor.
	/** Fraction of a drop's speed that survives the instant it enters water. */
	public static final double DROP_WATER_ENTRY_KEEP = 0.18;
	/** Velocity kept per tick while dispersing; water is thick. */
	public static final double DROP_WATER_DRAG = 0.82;
	/** Blocks per tick squared the cloud sinks at; blood is a shade denser than water. */
	public static final double DROP_WATER_SINK = 0.0012;
	/** How long a bead takes to disperse, scaled from a seep to a gout. */
	public static final int DROP_DISSOLVE_TICKS_MIN = 18;
	public static final int DROP_DISSOLVE_TICKS_MAX = 44;
	/** How many times its starting size the cloud reaches before it is gone. */
	public static final double DROP_DISSOLVE_GROWTH = 5.5;
	/** ...and the ceiling on that, so a gout does not fog a whole pool. */
	public static final double DROP_DISSOLVE_SIZE_MAX = 0.34;

	// Decapitation (2.0).
	/** A lethal blast at least this violent takes the head too: the limbs' top bar. */
	public static final double DECAP_BLAST_VIOLENCE = 0.88;
	/** How long a headless zombie of ordinary size walks on, and the most any does. */
	public static final int HEADLESS_WALK_TICKS = 60;
	public static final int HEADLESS_WALK_TICKS_MAX = 100;
	/** How hard it lurches along, blocks a tick, and how far its heading wanders, degrees a tick. */
	public static final double HEADLESS_WALK_SPEED = 0.055;
	public static final double HEADLESS_WANDER = 9.0;
	/** A head you can take lies as long as a dropped item; it can be picked up after this. */
	public static final int HEAD_LIFETIME_TICKS = 6000;
	public static final int HEAD_PICKUP_DELAY_TICKS = 20;
	/**
	 * A wither skeleton's skull hisses and flashes for this long before it
	 * either burns away or drops, and it drops as often as vanilla drops one:
	 * 2.5% of player kills, one point more for each level of Looting.
	 */
	public static final int WITHER_SKULL_HISS_TICKS = 30;
	public static final double WITHER_SKULL_CHANCE = 0.025;
	public static final double WITHER_SKULL_CHANCE_PER_LOOTING = 0.01;
	/** The death fade when your head came off: Grounded is brief, Arcade lets you watch. */
	public static final double DECAP_FADE_SECONDS_REALISTIC = 2.0;
	public static final double DECAP_FADE_SECONDS = 4.0;
	/** ...and in VR, or with the view set to cut to black: gone before the head lands. */
	public static final double DECAP_FADE_SECONDS_CUT = 0.15;

	// The severed limb (1.6.2): a thrown, bleeding, burnable object.
	/** Ticks the limb lies there before it is gone: a minute. */
	public static final int LIMB_LIFETIME_TICKS = 1200;
	/** Ticks over which its trickle runs dry; shorter than its life, so it lies still and dry at the end. */
	public static final int LIMB_BLEED_TICKS = 900;
	/** Ticks in fire or lava before it has burnt away. */
	public static final int LIMB_BURN_TICKS = 24;
	/** Blocks per tick along a blow (6 b/s), and along a blast (17 b/s). */
	public static final double LIMB_THROW_SPEED = 0.30;
	public static final double LIMB_BLAST_SPEED = 0.85;
	/** Upward component so a throw arcs and a drop clears the body. */
	public static final double LIMB_THROW_LIFT = 0.22;
	public static final double LIMB_BLAST_LIFT = 0.30;
	public static final double LIMB_DROP_LIFT = 0.12;
	/** Sideways speed of a limb that merely drops, so it clears the body it fell off. */
	public static final double LIMB_DROP_SIDE_SPEED = 0.10;
	/** Deterministic sideways scatter, blocks per tick, so two limbs do not land in one spot. */
	public static final double LIMB_THROW_SCATTER = 0.05;
	public static final double LIMB_GRAVITY = 0.045;
	public static final double LIMB_AIR_DRAG = 0.98;
	/** Meat does not slide: most of the horizontal speed goes on contact. */
	public static final double LIMB_GROUND_DRAG = 0.55;
	/** One low bounce, and none below this landing speed. */
	public static final double LIMB_BOUNCE = 0.35;
	public static final double LIMB_BOUNCE_MIN_SPEED = 0.12;
	public static final double LIMB_WATER_DRAG = 0.85;
	public static final double LIMB_WATER_SINK = 0.004;

	// Mob bodies (1.7).
	/** Ticks since a mob was last hurt before it starts mending on its own. */
	public static final int MOB_MEND_CALM_TICKS = 100;
	/** Regen-HP-equivalents of recovery a calm mob is granted per healing cycle. */
	public static final double MOB_MEND_PER_CYCLE = 0.5;
	/** A concussed mob loses its target this often, in ticks. */
	public static final int MOB_DAZE_FORGET_TICKS = 40;
	/** Multipliers on a ranged mob's shot inaccuracy: a dazed head, a broken drawing arm. */
	public static final double MOB_DAZE_AIM_SPREAD = 3.0;
	public static final double MOB_BROKEN_ARM_AIM_SPREAD = 2.0;
	/**
	 * A body's size against a person's (1.7 slice 3): max health over this.
	 * Trauma and blood loss are divided by the ratio (never below one).
	 */
	public static final double VITALITY_BASELINE_HP = 20.0;
	/** What a shell takes off torso trauma (turtle, armadillo, nautilus). */
	public static final double SHELL_TORSO_REDUCTION = 0.5;
	/** Worn body armour on a mob (horse armour, wolf armour): head and torso fully, limbs by half. */
	public static final double MOB_BODY_ARMOR_LIMB_COVERAGE = 0.5;
	/** Worst-first weights for four walking legs; sums to 1. One destroyed leg of four (0.45) costs ~0.2. */
	public static final double[] FOUR_LEG_WEIGHTS = {0.45, 0.30, 0.15, 0.10};
	/** Downward speed a flyer with a lost wing is held to while airborne, blocks/tick. */
	public static final double GROUNDED_FALL_SPEED = 0.18;

	// Stains: what a drop leaves on the surface it hits.
	/** Hard cap on tracked stains. Oldest are evicted first (spec §56: no unbounded state). */
	public static final int STAIN_CAPACITY = 768;
	/** Seconds a stain lives before it has fully dried away. */
	public static final double STAIN_LIFETIME_SECONDS = 45.0;
	/** Fraction of its life a stain spends wet and bright before it starts to darken. */
	public static final double STAIN_WET_FRACTION = 0.18;
	/** ...and the fraction at the end spent fading out, having already gone brown. */
	public static final double STAIN_FADE_FRACTION = 0.30;
	/** The smallest mark a single drop leaves: a bead of seep landing on stone. */
	public static final double STAIN_DROP_SIZE_MIN = 0.10;
	/**
	 * ...and the largest, which is a full arterial gout arriving all at once.
	 *
	 * <p>Was {@code STAIN_SIZE_MAX}, one constant doing two incompatible jobs: it
	 * bounded what a single drop could mark <em>and</em> what a mark fed by many
	 * drops could grow to. Being the same number meant a fully-fed pool was exactly
	 * the size of one splat, so pooling had no size it could express — see
	 * {@link #STAIN_POOL_SIZE_MAX}.
	 */
	public static final double STAIN_DROP_SIZE_MAX = 0.34;
	/**
	 * The largest a single mark may grow to once drops keep feeding it.
	 *
	 * <p>Deliberately larger than {@link #STAIN_DROP_SIZE_MAX}: the difference is
	 * the entire vocabulary pooling has. Kept under 0.5 because a stain is drawn as
	 * a flat quad of half-extent {@code radius}, so a mark this size laid against a
	 * wall pokes {@code radius} blocks through it — 0.46 keeps the poke-through
	 * inside the block the mark sits on. Width beyond one quad is not bought by
	 * enlarging the quad; it is bought by accretion, see {@link #STAIN_POOL_MARKS_MAX}.
	 */
	public static final double STAIN_POOL_SIZE_MAX = 0.46;
	/**
	 * How many marks one pool may consist of, which is what actually sets its width.
	 *
	 * <p>Ten marks of up to 0.92 blocks across, laid inside a stationary player's
	 * 0.30-block landing footprint, reads as roughly 1.2 blocks of contiguous
	 * ground — which is "a pool you would walk around" rather than "a splat".
	 * Beyond this the pool stops widening and further drops only keep it wet, so a
	 * player who stands still forever does not consume the whole field. The global
	 * {@link #STAIN_CAPACITY} still bounds everything above this.
	 */
	public static final int STAIN_POOL_MARKS_MAX = 10;
	/** Marks this close together on the same face are counted as one pool. */
	public static final double STAIN_POOL_RADIUS = 0.55;
	/**
	 * Past this radius a mark stops using a splat sprite and takes a pool one.
	 *
	 * <p>A magnified splat sprite keeps its flung satellite droplets and its
	 * off-centre mass, so scaling one up gives a magnified splatter — word for word
	 * what the author reported. Past this size the sprite is swapped for a round,
	 * dense one instead.
	 *
	 * <p>Set above {@link #STAIN_DROP_SIZE_MAX}, so that no size one drop is
	 * capable of marking is already at the tier. It was 0.30, below it, defended
	 * on the grounds that the swap only happens inside a feed and never at
	 * construction — true, and not enough. A gout marks at
	 * 0.10 + (0.34 - 0.10) x (5.5 / 6.0) = 0.32, which is 94% of what any drop may
	 * mark, so it crossed 0.30 on its first feed of any size whatever. The old
	 * javadoc called that correct because "a spurt that has since been bled into
	 * twice over is not a spurt any more"; the word doing the work there is
	 * "since", and there is no since. Both drops leave one point on one tick.
	 *
	 * <p>Raising it is belt-and-braces, not the fix — {@link #STAIN_POOL_SPRITE_FEEDS}
	 * is the fix — but it makes "no single drop lands already drawn as a puddle"
	 * checkable as one comparison of two constants, which is the guard that stops
	 * this coming back. It also costs a moderate seep pool four extra feeds
	 * (ceil((0.36 - 0.124) / 0.0149) = 16 rather than 12, about 1.7 s more), which
	 * is a real change to the standing case and is named here rather than
	 * discovered.
	 */
	public static final double STAIN_POOL_SPRITE_RADIUS = 0.36;
	/** A wet wall mark must be this large before gravity starts pulling a run from it. */
	public static final double STAIN_WALL_RUN_RADIUS = STAIN_POOL_SPRITE_RADIUS;
	/** Maximum drops per second shed by one saturated wet wall mark. */
	public static final double STAIN_WALL_RUN_DROPS_PER_SECOND = 1.0;
	/** Radius paid by a wall mark each time it sheds a drop. */
	public static final double STAIN_WALL_RUN_DRAIN = 0.025;
	/**
	 * ...and how many drops must have gone into a mark before the swap may happen
	 * at all. A pool is an accretion, and only a count can say so.
	 *
	 * <p>Size alone provably cannot. Feeding a gout mark adds
	 * 0.32 x {@link #STAIN_MERGE_GROWTH} = 0.0384, so it goes
	 * 0.32 -> 0.3584 -> 0.3968 -> 0.4352 -> {@link #STAIN_POOL_SIZE_MAX}: four
	 * feeds, five drops, and it is at the ceiling. One heartbeat throws up to
	 * {@link #ARTERIAL_SPURT_DROPS_MAX} drops from one point on one tick, of which
	 * roughly two in five land inside {@link #STAIN_MERGE_DISTANCE} of each other.
	 * There is therefore no radius at or below the ceiling that a single beat
	 * cannot cross, and at severe and critical rates every beat was minting one: a
	 * player sprinting with an open artery left a pool every three blocks, one per
	 * heartbeat, evenly spaced, all the same size. The even spacing was his pulse.
	 * That is a trail lying about being a pool, and it erases the only distinction
	 * the ground was carrying — standing still grows a pool, walking lays a trail.
	 *
	 * <p>A burst of N drops puts N-1 feeds into one mark, so a gate of
	 * {@link #ARTERIAL_SPURT_DROPS_MAX} clears the largest burst there is with one
	 * feed to spare. It costs nothing in latency: measured, the first pool sprite
	 * appears at the same moment for gates of 6, 7 and 8, because what binds is
	 * the mark's growth and not the count — a mark being fed continuously passes
	 * seven feeds within a second of passing four.
	 *
	 * <p>Falsifiable, if anyone wants to check the diagnosis by hand on a build
	 * from before this: the artifact should disappear on its own at a high enough
	 * heart rate, because a one-drop burst has nothing to merge with. A severe
	 * wound's burst is round(4.8 x 0.72 x 60/bpm), which drops to 1 once
	 * 3.456 x 60/bpm &lt; 1.5, i.e. above 138 bpm. Sprint with a severe wound while
	 * panicked and the trail goes back to discrete splats. A critical wound never
	 * gets there — it would need 231 bpm against a {@link #HEART_MAX_BPM} of 190 —
	 * which is why the artifact was worst exactly where the bleeding was worst.
	 */
	public static final int STAIN_POOL_SPRITE_FEEDS = ARTERIAL_SPURT_DROPS_MAX;
	/**
	 * Stains closer together than this merge and grow instead of stacking. Without
	 * it, standing still over a critical wound spends the entire capacity on one
	 * square of floor and erases the trail that led there.
	 */
	public static final double STAIN_MERGE_DISTANCE = 0.22;
	/**
	 * A merged stain grows by this fraction of the incoming one, and never past
	 * {@link #STAIN_POOL_SIZE_MAX}.
	 *
	 * <p>Was 0.35. A seep drop marks at radius 0.124 and so added 0.0434 a feed:
	 * five feeds — two and a half seconds — took it to the old ceiling, after which
	 * every further drop was discarded. Ninety-two per cent of a minute's drops
	 * changed nothing, and a minute of bleeding looked identical to three seconds
	 * of it.
	 *
	 * <p>At 0.12 the same drop adds 0.0149, so 0.124 -> 0.46 takes
	 * ceil(0.336 / 0.0149) = 23 feeds, about ten seconds of a moderate wound; an
	 * arterial spurt lands at 0.32 and takes four. Ten seconds is roughly how long
	 * a player stands still before they look down, so the growth happens while
	 * someone is watching it rather than before they arrive.
	 */
	public static final double STAIN_MERGE_GROWTH = 0.12;
	/** Beyond this many blocks stains are not drawn. */
	public static final double STAIN_RENDER_DISTANCE = 40.0;
	/** Distance off the surface a stain is drawn, to lose the z-fight without floating. */
	public static final double STAIN_SURFACE_OFFSET = 0.011;

	// Staining the body itself.
	/**
	 * Blood units of staining a region accrues per second per Blood/sec bled from
	 * it. A critical wound therefore takes roughly fifteen seconds to soak a limb
	 * and a minor one a couple of minutes.
	 *
	 * <p>Was 0.55, which saturated a limb in under a second flat: everything from
	 * a nick to a severed artery looked identical within one tick of being
	 * inflicted, so the gradient this value exists to produce was never visible.
	 * The client gametest caught it by asserting the stain was still growing.
	 */
	public static final double BODY_STAIN_GAIN = 0.035;
	/** ...and loses per second once the wound is closed. Slower: blood dries on, it does not rinse. */
	public static final double BODY_STAIN_DECAY = 0.02;
	/**
	 * ...until water does it (2.0). What a second under water takes off a region
	 * that is not still bleeding: a soaked limb is clean in about four seconds,
	 * the same few seconds a weapon takes to rinse.
	 */
	public static final double BODY_STAIN_WASH_WATER = 0.25;
	/** The same for a second out in the rain, which gets there in the end: about twelve seconds. */
	public static final double BODY_STAIN_WASH_RAIN = 0.08;
	public static final double BODY_STAIN_MAX = 1.0;
	/** A drop landing on someone stains them by this much. */
	public static final double BODY_STAIN_PER_DROP = 0.16;

	// Blood on the camera.
	/** Lens droplets per second at one Blood/sec of total flow. */
	public static final double LENS_DROPS_PER_BLOOD_POINT = 0.9;
	public static final int LENS_CAPACITY = 24;
	/** Seconds a lens droplet takes to run, dry and vanish. */
	public static final double LENS_LIFETIME_SECONDS = 6.5;
	/** Fraction of its life a droplet spends running before it dries in place. */
	public static final double LENS_RUN_FRACTION = 0.42;
	/** Screen heights a droplet travels over its whole run, at full size. */
	public static final double LENS_RUN_DISTANCE = 0.30;
	/**
	 * How much a wound contributes to blood on the camera, by where it is. A head
	 * wound runs into your eyes; a leg wound reaches the lens only by way of your
	 * hands, so it counts for much less. Without this split, a cut shin fogged the
	 * screen exactly as hard as a cut scalp.
	 */
	public static final double LENS_HEAD_WEIGHT = 1.0;
	public static final double LENS_BODY_WEIGHT = 0.35;

	// --- The heart (1.5) ---
	// Before 1.5 the beat was bpm = 55 + 110 * distress with a fixed envelope
	// measured in *fractions of a beat*, which had two consequences. The thump
	// got shorter in real time as the rate rose — 60ms at rest, 20ms at full
	// distress — so dying turned the heartbeat into a click track. And because
	// distress is a single smooth scalar, the beat was perfectly periodic
	// forever: it raced, but it never faltered.

	/** Resting rate, beats per minute. */
	public static final double HEART_REST_BPM = 55.0;
	/** Added at full distress when the cause is ordinary injury: a pounding heart. */
	public static final double HEART_POUNDING_GAIN = 110.0;
	/** ...when the cause is blood loss: faster still, and weaker with it. */
	public static final double HEART_THREADY_GAIN = 123.0;
	/**
	 * ...and when the cause is the head. Negative on purpose: a failing brainstem
	 * gives a slow, heavy, spaced-out beat, not a fast one. It is the more
	 * frightening of the two and the old single-curve model could not reach it.
	 */
	public static final double HEART_AGONAL_GAIN = -17.0;
	/** Never let the rate fall so far that the beat stops reading as alive. */
	public static final double HEART_MIN_BPM = 32.0;
	public static final double HEART_MAX_BPM = 190.0;

	/** Seconds of decay on the first sound, and on the second. Absolute time, not beat fractions. */
	public static final double HEART_LUB_DECAY_SECONDS = 0.055;
	public static final double HEART_DUB_DECAY_SECONDS = 0.065;
	/** Seconds after the first sound that the second arrives. */
	public static final double HEART_DUB_DELAY_SECONDS = 0.19;
	public static final double HEART_DUB_LEVEL = 0.72;
	/** The agonal character stretches both decays by up to this factor: slower, heavier. */
	public static final double HEART_AGONAL_STRETCH = 2.4;
	/** The thready character scales amplitude down to this: fast, faint, insistent. */
	public static final double HEART_THREADY_AMPLITUDE = 0.55;

	/** Respiratory sinus arrhythmia: fraction the interval swings across a breath. */
	public static final double HEART_BREATH_SWING = 0.08;

	// Dying (1.5). Death used to snap every effect off between one frame and the
	// next — active() excluded dead players so the heartbeat could not run on
	// the death screen, which was correct right up until the death screen was
	// asked to feel like anything. Now the body finishes the story instead:
	// the heart slows, heavies and stops; the colour finishes draining; the
	// world goes quiet under a swell of ringing; and only then is it over.
	// Two death styles, selected client-side like the muted/visceral sound split.
	// The cinematic one keeps the screen readable and finishes in five seconds —
	// a dramatised ending. The realistic one reorders the senses the way dying
	// actually reorders them: vision tunnels out to true black just past the
	// middle of a longer fade, the heart keeps beating in the dark for a while
	// after — hearing is the last sense to go — and the final stretch before the
	// respawn is genuinely nothing: black, and silent.
	/** Seconds from death to the end of the visual fade, cinematic style. */
	public static final double DEATH_FADE_SECONDS = 5.0;
	/** ...and realistic style. Longer, because the ordering needs room to read. */
	public static final double DEATH_FADE_SECONDS_REALISTIC = 8.0;
	/** Realistic style: fraction of the fade at which vision has fully gone. */
	public static final double DEATH_BLACK_AT = 0.55;
	/** ...and the moment it starts going, so the blow itself is still seen. */
	public static final double DEATH_BLACK_FROM = 0.08;
	/** Realistic style: the heart falls silent here — well after the dark. */
	public static final double DEATH_HEART_SILENT_REALISTIC = 0.75;
	/** Realistic style: the ringing is gone by here, leaving true silence. */
	public static final double DEATH_RING_END_REALISTIC = 0.85;
	/** Realistic style: the world's audio is fully gone by this point of the fade. */
	public static final double DEATH_DUCK_FULL_AT = 0.7;

	// How violent the death was, 0 (slipped away) to 1 (obliterated). Both death
	// styles used to play the same wind-down whatever killed you, so a 50-block
	// drop onto stone got the same slow heart-fade as bleeding out in a corner.
	// Violence compresses the fade and pre-ages the heart: a marginal death runs
	// a touch quicker, an overwhelming one is a cut — black inside a second,
	// with no beats at all, because you were gone before your heart was.
	/** Blocks beyond safe fall distance at which a lethal fall is fully instant. */
	public static final double DEATH_VIOLENCE_FALL_FULL = 40.0;
	/**
	 * A killing blow of this many times your max health is fully instant.
	 * Measured on the raw blow, not the post-armour wound: armour softens what a
	 * mace does to your body, not how abruptly the world stops.
	 */
	public static final double DEATH_VIOLENCE_BLOW_SCALE = 1.5;
	/** Fraction of the style's fade duration left at full violence. */
	public static final double DEATH_INSTANT_FACTOR = 0.15;
	/**
	 * The dying heart's rate runs from the first of these down to the second
	 * across the fade. It starts fast — the body has just taken whatever killed
	 * it — and collapses through and below the living minimum, which no living
	 * state is allowed to do: five or six beats with visibly widening gaps,
	 * rather than the two an agonal-slow start left room for.
	 */
	public static final double DEATH_BPM_START = 140.0;
	public static final double DEATH_BPM_END = 16.0;
	/**
	 * Fraction of the fade at which the heart falls silent. Deliberately just
	 * past the middle: the heart stopping while the screen is still going is the
	 * sequence people recognise, and it leaves the last stretch of the fade in
	 * silence.
	 */
	public static final double DEATH_HEART_SILENT = 0.6;
	/** Where in the fade the dying ring of tinnitus peaks before it too fades. */
	public static final double DEATH_RING_PEAK = 0.4;
	/** How much of the world's audio is gone by the end of the fade. */
	public static final double DEATH_DUCK = 0.85;

	/** Below this distress the beat is regular; ectopic beats begin above it. */
	public static final double ECTOPY_THRESHOLD = 0.35;
	/** Share of beats that misfire at full distress — about one in four. */
	public static final double ECTOPY_CHANCE_MAX = 0.26;
	/** An ectopic beat arrives early, at this fraction of the normal interval... */
	public static final double ECTOPY_EARLY_SCALE = 0.62;
	/** ...and this weak, or silent entirely if it is low enough to vanish. */
	public static final double ECTOPY_WEAK_AMPLITUDE = 0.28;
	/** The compensatory pause that follows: longer than a normal interval... */
	public static final double ECTOPY_PAUSE_SCALE = 1.58;
	/** ...and then the beat that ends it lands harder than normal. */
	public static final double ECTOPY_THUMP_AMPLITUDE = 1.65;

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
	/**
	 * A concussion lowers whichever head cap applies by this much — the fog on
	 * top of the injury, not a separate injury of its own.
	 */
	public static final double CONCUSSION_CAP_DROP = 10.0;
	/**
	 * ...but never below {@link #WAKE_THRESHOLD} plus this, which is exactly
	 * {@link #HEAD_CAP_DISABLED}: a concussion may keep you groggy, it may not on
	 * its own hold you under the waking line.
	 */
	public static final double CONCUSSION_CAP_FLOOR_MARGIN = 5.0;

	/** Collapse at 0; wake only once consciousness reaches this (hysteresis, spec §25). */
	public static final double WAKE_THRESHOLD = 20.0;
	/** How far an unconscious player keeps drawing hostile attention, in blocks. */
	public static final double UNCONSCIOUS_AGGRO_RADIUS = 12.0;
	/**
	 * Close enough that a hostile would plainly notice a body at its feet. The
	 * NEARBY aggro mode uses this rather than the full radius, so collapsing in a
	 * fight stays lethal without distant mobs crossing a cave to finish you off.
	 */
	public static final double UNCONSCIOUS_AGGRO_NEARBY_RADIUS = 6.0;

	/** Minimum time unconscious, in ticks (~2.5s). */
	public static final int MIN_UNCONSCIOUS_TICKS = 50;

	// --- Injury-aware aggro (1.6) ---
	// A ladder, not a set of weights. Each rung is sized so that nothing below
	// it — stacked with everything else below it, plus the grudge, plus the
	// switch margin — can reach it, which is what makes "a body on the floor
	// always outranks a limp" a property rather than a hope. PreyMathTest
	// enumerates the seven inequalities so a retune cannot silently reorder
	// them.

	/** A body on the floor: unconscious, knocked down, or crawling on two lost legs. */
	public static final int PREY_TIER_DOWN = 600;
	/** A leg that is gone. The limp is permanent and the model draws the absence. */
	public static final int PREY_TIER_LEG_DESTROYED = 300;
	/** A broken leg: an event, with a message and a sound everyone nearby hears. */
	public static final int PREY_TIER_LEG_BROKEN = 200;
	/**
	 * Per visible limp rung of the worse leg: Critical 1, Disabled 2. Healthy,
	 * Hurt and Injured are all rung 0 — the leg curve is flat above 0.75, 7% at
	 * 0.50 and 18% at 0.25, and the worse leg is weighted 0.70, so a Hurt leg
	 * costs under 5% of your speed and an Injured one under 13%. A limp nobody
	 * can see is not a limp anyone can hunt. The rung itself is
	 * {@code LimpMath}'s, shared with the pose the model draws.
	 */
	public static final int PREY_LIMP_BAND = 60;
	/**
	 * Per wound severity above MINOR: MODERATE 10, SEVERE 20, CRITICAL 30. A
	 * MINOR wound scores nothing — at 0.25 Blood/s it is below the rate that
	 * even produces a falling drop.
	 */
	public static final int PREY_WOUND_STEP = 10;
	/**
	 * How much better a candidate must be before a mob abandons the player it is
	 * already fighting. Above two wound steps and below three, so blood alone
	 * never flips a fight until it turns arterial; below one limp rung, so a leg
	 * giving out always does.
	 */
	public static final int PREY_SWITCH_MARGIN = 25;
	/**
	 * The player who last hit this mob holds it. Replaces the switch margin
	 * rather than stacking with it, so the rule reads the same on a fresh grudge
	 * and a held one: hit it and it stays on you against any limp and any blood
	 * (150 at worst), and loses you to a broken leg (200), a lost leg (300) or a
	 * body on the floor (600). Vanilla forgets an attacker after 100 ticks; so
	 * does this.
	 */
	public static final int PREY_REVENGE_BONUS = 175;

	// --- Rescue (1.2) ---

	/** How close you must be to grab, press or feed someone. */
	public static final double RESCUE_REACH = 3.0;
	/** Movement penalty while hauling a body: hands full, half speed. */
	public static final double DRAG_MOVEMENT_PENALTY = 0.50;
	/** Where the body trails, in blocks behind the dragger. */
	public static final double DRAG_FOLLOW_DISTANCE = 1.3;
	/** Past this the grip is lost — a wall, a ledge, or someone sprinting off. */
	public static final double DRAG_BREAK_DISTANCE = 5.0;
	/** Cap on how fast the body is pulled, in blocks per tick. */
	public static final double DRAG_PULL_SPEED = 0.34;
	/**
	 * What pressure does to a bleed rate. Strong enough to be worth kneeling
	 * down for, short of a cure: pressure buys time, food is still the only
	 * thing that puts blood back.
	 *
	 * <p>A companion is better at it than you are, and deliberately so. They
	 * have two free hands, they can see the wound, and they are not the one
	 * going into shock. At the stacking cap of 3.0 Blood/sec this is the
	 * difference between roughly 45 seconds of blood left and 133 — which is the
	 * gap that makes fetching someone worth the trip.
	 */
	public static final double PRESSURE_BLEED_MULTIPLIER_SELF = 0.40;
	public static final double PRESSURE_BLEED_MULTIPLIER_COMPANION = 0.25;
	/**
	 * Grace period on a pressure claim, in ticks. The presser refreshes this
	 * every tick; two ticks of slack keeps the effect steady if one is missed
	 * without letting it linger after they let go.
	 */
	public static final int PRESSURE_GRACE_TICKS = 2;

	/** How far away someone else's fracture or collapse can still be heard, in blocks. */
	public static final double INJURY_SOUND_RADIUS = 24.0;
	/** How loud someone else's injury is, against hearing your own. */
	public static final float INJURY_SOUND_OTHERS_VOLUME = 0.4F;
	/**
	 * A scream carries where a crack does not (1.7): someone else's is heard at
	 * twice the other injury cues' share, and your own a little under your own
	 * bones (see InjuryAudio).
	 */
	public static final float SCREAM_OTHERS_VOLUME = 0.8F;
	public static final float SCREAM_SELF_VOLUME = 0.85F;

	/**
	 * Longest the server keeps a dead player in the world for their death camera,
	 * in ticks (1.7, see DeathHold). The client releases the hold itself once its
	 * death screen is up — a few seconds at most — so this is only the backstop
	 * for a client that never says so.
	 */
	public static final long DEATH_HOLD_MAX_TICKS = 400;

	// Symptom ladder thresholds (spec §23) — shared by server (action locks) and client (fx).
	public static final double SYMPTOM_VIGNETTE = 75.0;
	public static final double SYMPTOM_SENSITIVITY = 65.0;
	public static final double SYMPTOM_ATTACK_RECOVERY = 55.0;
	public static final double SYMPTOM_AFTERIMAGE = 45.0;
	public static final double SYMPTOM_EXPOSURE = 35.0;
	/** Your feet stop going exactly where you point them (1.4). */
	public static final double SYMPTOM_STUMBLE = 32.0;
	public static final double SYMPTOM_RINGING = 30.0;
	public static final double SYMPTOM_HEARTBEAT = 25.0;
	/** Consciousness starts cutting out entirely, in brief episodes (1.4). */
	public static final double SYMPTOM_MICROSLEEP = 22.0;
	public static final double SYMPTOM_SPRINT_LOSS = 20.0;
	public static final double SYMPTOM_SEVERE = 10.0;
	/**
	 * Below this you cannot muster a swing or a pickaxe stroke at all (1.4).
	 * Deliberately beneath every other rung: the slowdown above it is the warning,
	 * and this is the point where the warning stops being one.
	 */
	public static final double SYMPTOM_FEEBLE = 8.0;
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
	 *
	 * <p>Eased from 3.0 to 2.0 in 1.4. Falls now derive their trauma from height
	 * rather than from the health bar, which raised every other term in the sum at
	 * once; left at 3.0 the impact term alone knocked you out from any drop worth
	 * surviving, and an unconsciousness that arrives every single time stops being
	 * a consequence and becomes a cutscene.
	 */
	public static final double CONSCIOUSNESS_IMPACT_SCALE = 2.0;
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
	 * consciousness, not bleeding) before it clears: about 25 seconds. Without a
	 * timer at all, the clear condition was satisfiable the instant it applied.
	 */
	public static final int CONCUSSION_STABLE_TICKS = 500;
	/** How close to the cap consciousness must sit to count as "stabilized". */
	public static final double CONCUSSION_STABLE_MARGIN = 2.0;
	/**
	 * Concussion makes every sensory symptom arrive sooner (spec §21): effects are
	 * computed as if consciousness were this much lower than it really is.
	 */
	public static final double CONCUSSION_SYMPTOM_SHIFT = 18.0;
	/** Constant double-vision ghosting while concussed — the signature symptom. */
	public static final double CONCUSSION_GHOST = 0.25;
	/**
	 * How much of the previous frame the afterimage keeps, per tick, at the
	 * bottom and top of its strength (1.6.1). Raised to the power of the real
	 * frame delta, so a trail lasts the same fraction of a second at any frame
	 * rate. 0.72/tick is a smear you can see your own hand through; 0.10 is a
	 * faint doubling that is gone within a couple of frames.
	 */
	public static final double AFTERIMAGE_KEEP_MIN = 0.10;
	public static final double AFTERIMAGE_KEEP_MAX = 0.72;
	/**
	 * Vanilla's gaussian blur has no intensity parameter — it is on or off — so it
	 * is held back to a late, distinct stage while the ghosting carries the smooth
	 * build-up above it. Otherwise it simply pops in mid-ladder.
	 *
	 * <p>A backstop, then, and not a rung. That distinction is why it is absent
	 * from the published ladder and why it belongs below {@link
	 * #SYMPTOM_MICROSLEEP}: every rung above it is something the player is meant
	 * to notice arriving and act on, and a switch that can only be thrown cannot
	 * be one of those. It marks the floor the ghost ramps down to rather than an
	 * event of its own, and 1.6 made that honest — {@link
	 * dev.captience.realisticdamage.body.Vitals#afterimageStrength} ends its band
	 * here, so the ghost hands over at full strength instead of the blur arriving
	 * out of nowhere.
	 */
	public static final double SYMPTOM_BLUR = 18.0;

	// Low-consciousness action sluggishness (spec §23).
	/**
	 * Steepened hard in 1.4 (was 0.10/0.30). At a tenth of a point of
	 * consciousness you could still mine at 70% speed, which meant the entire
	 * approach to collapse was cosmetic: the screen said you were dying and the
	 * gameplay said you were fine. The curve now caves in over the last twenty
	 * points, so the bottom of the ladder is somewhere you cannot work.
	 */
	public static final double SLUGGISH_PENALTY_AT_20 = 0.22;
	public static final double SLUGGISH_PENALTY_AT_0 = 0.85;

	/**
	 * Eating slows with consciousness (1.4), in stalled use-ticks per tick: 1.0
	 * doubles the meal, 4.0 makes it five times as long. A 32-tick loaf becomes
	 * eight seconds at the bottom — still always possible, because food is the
	 * only cure, but no longer free.
	 */
	public static final double EAT_STALL_AT_20 = 1.0;
	public static final double EAT_STALL_AT_0 = 4.0;
	/**
	 * The longest any meal may take, stalled, in ticks: five seconds (1.6.2).
	 * The curve above is a factor on the food's own duration — 1.0 extra is
	 * twice as long, 4.0 is five times — and this caps the product, so bread at
	 * zero consciousness is five seconds and not eight. Before 1.6.2 the stall
	 * was applied as whole extra ticks per tick, which at a factor of 1.0 or more
	 * cancelled the meal's own countdown entirely: below consciousness 20 food
	 * could not be finished until natural recovery lifted you back over the line,
	 * which the playtest measured as "about a minute".
	 */
	public static final int EAT_MAX_TICKS = 100;

	// --- Microsleeps (1.4) ---
	/**
	 * Consciousness does not fade out evenly; it drops out. Below
	 * {@link #SYMPTOM_MICROSLEEP} the player loses the thread entirely for a
	 * moment at a time, more often and for longer the further down they are.
	 *
	 * <p>Driven by a phase accumulator rather than a die roll, so two identical
	 * descents produce identical blackouts — the no-RNG rule applies to losing
	 * consciousness exactly as it applies to breaking a leg.
	 *
	 * <p>The floor values are set by a rule the tests enforce: even at the very
	 * bottom you must be present more of the time than you are absent. At 45/26
	 * you were blacked out 58% of the time, which is not a stutter before the
	 * stop — it is unconsciousness with the lights flickering, and it takes the
	 * game away rather than making it frightening. 60/22 leaves nearly two
	 * seconds of control between one-second blackouts.
	 */
	public static final int MICROSLEEP_PERIOD_TICKS_MAX = 160;
	public static final int MICROSLEEP_PERIOD_TICKS_MIN = 60;
	public static final int MICROSLEEP_DURATION_TICKS_MIN = 4;
	public static final int MICROSLEEP_DURATION_TICKS_MAX = 22;

	// --- Stumbling (1.4) ---
	/** How far off your input your feet can wander at worst, in degrees. */
	public static final double STUMBLE_MAX_DEGREES = 42.0;
	/**
	 * Two coprime-ish periods in ticks, summed. One period alone reads as a
	 * machine; two make it read as a body that cannot hold a line.
	 */
	public static final double STUMBLE_PERIOD_A = 37.0;
	public static final double STUMBLE_PERIOD_B = 83.0;

	// --- Knockdown on a hard landing (1.4) ---
	// Every height below is measured in blocks *beyond the player's safe fall
	// distance* (three, ordinarily), which is the same unit the trauma figure
	// uses. Add three to read them as drops: knockdown starts at a ten-block
	// fall, the torso joins in at twelve, the head at nineteen.
	/**
	 * Below this nothing knocks you over — routine jumping and three-block hops
	 * have to stay unremarkable or the mod becomes a nuisance rather than a
	 * hazard.
	 */
	public static final double KNOCKDOWN_MIN_HEIGHT = 7.0;
	/** At or above this height the knockdown runs its full length. */
	public static final double KNOCKDOWN_FULL_HEIGHT = 22.0;
	public static final int KNOCKDOWN_TICKS_MIN = 12;
	public static final int KNOCKDOWN_TICKS_MAX = 50;
	/** After you get up you are still unsteady: this long, at this movement penalty. */
	public static final int KNOCKDOWN_RECOVERY_TICKS = 20;
	public static final double KNOCKDOWN_RECOVERY_MOVEMENT_PENALTY = 0.45;

	// --- Fall severity, keyed to height rather than to the health bar (1.4) ---
	/**
	 * Condition-damage equivalent per block of fall beyond the safe distance,
	 * before the FALL profile multiplier. Falls used to derive their trauma from
	 * post-armour HP, which meant a good pair of boots did not merely protect you
	 * — it deleted the event. Diamond armour and Feather Falling IV took a
	 * forty-block drop down to a scratch, so the legs it should have shattered
	 * were never touched and the head impact never happened at all. Height is
	 * what your skeleton actually experiences; armour still helps, through the
	 * localized reduction every other category goes through.
	 */
	public static final double FALL_TRAUMA_PER_BLOCK = 0.76;
	/** Drop height at which the torso starts taking a share of a landing. */
	public static final double FALL_SECONDARY_TORSO_HEIGHT = 9.0;
	/** ...and where that share reaches its full weight. */
	public static final double FALL_SECONDARY_TORSO_FULL_HEIGHT = 16.0;
	/** Drop height at which your head starts to be part of the impact. */
	public static final double FALL_SECONDARY_HEAD_HEIGHT = 16.0;
	public static final double FALL_SECONDARY_HEAD_FULL_HEIGHT = 30.0;
	public static final double FALL_TORSO_SHARE = 0.30;
	public static final double FALL_HEAD_SHARE = 0.22;
	/**
	 * Nobody lands on both feet equally. The leading leg takes this fraction of
	 * the leg share on a committed landing, the trailing leg the rest — chosen
	 * from the walk cycle, so it is the foot that was actually forward rather
	 * than a coin flip, and so replaying the same fall breaks the same ankle.
	 */
	public static final double FALL_LEADING_LEG_SHARE = 0.68;
	/** Height at which the asymmetry is fully expressed; gentler drops trend back to even. */
	public static final double FALL_ASYMMETRY_FULL_HEIGHT = 12.0;

	// --- What you landed on (1.4) ---
	/** Hay, slime, honey, beds, powder snow, leaves: your landing has some give. */
	public static final double LANDING_SURFACE_SOFT = 0.55;
	/** Dirt, sand, gravel, wood, wool, moss: better than rock. */
	public static final double LANDING_SURFACE_YIELDING = 0.78;
	public static final double LANDING_SURFACE_NEUTRAL = 1.0;
	/** Stone, deepslate, metal, obsidian: no give whatsoever. */
	public static final double LANDING_SURFACE_HARD = 1.18;

	/**
	 * Vanilla fall damage is linear and therefore lethal at a cliff edge: 23
	 * blocks is exactly twenty points and exactly death. 1.4 compresses the tail
	 * so that height buys consequence instead of buying an instant respawn —
	 * damage up to the soft cap is untouched, and every point past it counts for
	 * {@link #FALL_HP_COMPRESSION}. A 35-block drop becomes survivable from full
	 * health, at around two hearts, with a shattered leg and a concussion to show
	 * for it; past roughly fifty blocks the ground still wins.
	 */
	public static final double FALL_HP_SOFT_CAP = 10.0;
	public static final double FALL_HP_COMPRESSION = 0.40;

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
	/**
	 * The most a limb injury may take off your jump. Chosen against vanilla
	 * arithmetic rather than by feel, because jump height is not linear in launch
	 * velocity and a penalty that reads as a smooth dial turns into a wall without
	 * anyone deciding that it should.
	 *
	 * <p>A jumping player rises {@code y += v; v = (v - 0.08) * 0.98} every tick,
	 * so JUMP_STRENGTH 0.42 peaks at 1.2522 blocks and is at or above y=1.0 for six
	 * consecutive ticks — that six-tick window is why a one-block step-up feels free
	 * in vanilla. Step-assist cannot help: it only fires while on the ground. Peak
	 * height falls to exactly 1.0 at JUMP_STRENGTH 0.36813, i.e. at a penalty of
	 * 1 - 0.36813/0.42 = 0.1235. Every penalty this system produced past the middle
	 * of INJURED was above that line, so an injury stopped being a limp and started
	 * deleting the way out of the ravine you fell into — while both legs still read
	 * amber on the HUD, with no warning anywhere.
	 *
	 * <p>0.10 leaves JUMP_STRENGTH 0.42 * 0.90 = 0.378, a 1.047-block peak and three
	 * ticks of overlap with the ledge against vanilla's six. That is deliberately the
	 * minimum and not the normal height. Overlap is a step function of the cap: three
	 * ticks survives up to a cap of 0.1019, two up to 0.1052, and one — a peak of
	 * exactly 1.0, which is an apex you have to hit rather than a ledge you walk onto
	 * — up to 0.1235. 0.10 is the round number just inside the three-tick band, 0.0019
	 * below where it would drop to two and 0.0235 below the cliff where the climb
	 * stops existing at all. The nearness of the two-tick line is the point and not an
	 * oversight: this is meant to sit at the bottom of what still works. There is no
	 * runtime spread to absorb — the arc is fully determined by launch velocity, and
	 * this is the mod's only JUMP_STRENGTH modifier — so the margin that matters is
	 * the 0.0235 to the cliff, not the 0.0019 to a slightly tighter window. The limp
	 * keeps everything else it had:
	 * no sprint, up to 75% off walk speed, shorter jump distance, doubled fall
	 * trauma. Same principle as {@link #LEG_PENALTY_TOTAL_CAP} one screen up — an
	 * injury may cost you the journey, never the map — and the same reason: getting
	 * to food is how you heal, so an injury that strands you blocks its own cure.
	 *
	 * <p>Deliberately NOT applied to the hard vetoes in ImpairmentSystem.tick.
	 * Crawling on two lost legs, dragging a body, kneeling on a wound, knockdown and
	 * its unsteady tail, microsleep and unconsciousness all assign the penalty 1.0
	 * outright after the derived value is computed, and must keep doing so. This caps
	 * what an injury costs, not what a state forbids.
	 */
	public static final double JUMP_PENALTY_INJURY_CAP = 0.10;

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
	// Fall secondary shares moved to the height-keyed constants above in 1.4.
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

	// --- Hypoxia (1.7): drowning and suffocation take the mind first ---
	/**
	 * Consciousness drain the moment air runs out, points/sec. The drain then
	 * climbs with how long the brain has gone without — see {@link
	 * #HYPOXIA_DRAIN_PER_SECOND_OF_DEBT} — so from full consciousness the lights
	 * go out about seven seconds after the last bubble, with drowning damage
	 * still four or five seconds from killing you. That gap is the point: you
	 * black out before you die, sink, and a friend has a few seconds to pull you
	 * out. Before 1.7 the drain was a flat 2.5/s, which took forty seconds to
	 * matter and so never did.
	 */
	public static final double HYPOXIA_DRAIN_BASE = 4.0;
	/** Extra drain per second of accumulated oxygen debt. */
	public static final double HYPOXIA_DRAIN_PER_SECOND_OF_DEBT = 3.0;
	/** Ceiling on the hypoxia drain, points/sec. */
	public static final double HYPOXIA_DRAIN_MAX = 30.0;
	/** Oxygen debt repaid per second of breathing again. */
	public static final double HYPOXIA_REPAY_PER_SEC = 0.6;
	/** Debt is capped: past this the brain is as starved as it gets. */
	public static final double HYPOXIA_DEBT_MAX = 20.0;

	// --- Concussion grades (1.7 rework) ---
	/** Severity, 0-100, at which a concussion reads as moderate rather than mild. */
	public static final double CONCUSSION_GRADE_MODERATE = 35.0;
	/** ...and as severe. */
	public static final double CONCUSSION_GRADE_SEVERE = 70.0;
	public static final double CONCUSSION_SEVERITY_MAX = 100.0;
	/** What a first concussion is worth: base plus this much per point of head trauma. */
	public static final double CONCUSSION_FRESH_BASE = 18.0;
	public static final double CONCUSSION_FRESH_PER_TRAUMA = 5.0;
	/**
	 * Second impact: while concussed, a concussive head hit of at least this much
	 * trauma deepens the concussion (no INJURED-head precondition, no 6.0 bar —
	 * an already-concussed brain does not need a big hit).
	 */
	public static final double SECOND_IMPACT_MIN_TRAUMA = 1.5;
	public static final double SECOND_IMPACT_BASE = 15.0;
	public static final double SECOND_IMPACT_PER_TRAUMA = 6.0;
	/** Head trauma that knocks a concussed player straight out, by grade (mild, moderate, severe). */
	public static final double SECOND_IMPACT_KO_MILD = 5.0;
	public static final double SECOND_IMPACT_KO_MODERATE = 3.5;
	public static final double SECOND_IMPACT_KO_SEVERE = 2.0;
	/** The flare a second impact adds on the spot. */
	public static final double SECOND_IMPACT_FLARE = 35.0;
	/** Severity a bare {@code setConcussion(true)} or an old save without a severity means. */
	public static final double CONCUSSION_DEFAULT_SEVERITY = 40.0;
	/** Symptom-ladder shift per grade (mild, moderate, severe); moderate is the old flat 18. */
	public static final double CONCUSSION_SHIFT_MILD = 10.0;
	public static final double CONCUSSION_SHIFT_MODERATE = 18.0;
	public static final double CONCUSSION_SHIFT_SEVERE = 26.0;
	/** A full flare pushes the ladder down by this much on top of the grade. */
	public static final double CONCUSSION_FLARE_SHIFT = 14.0;
	/** Consciousness-cap drop per grade (mild, moderate, severe); moderate is the old flat 10. */
	public static final double CONCUSSION_CAP_DROP_MILD = 5.0;
	public static final double CONCUSSION_CAP_DROP_SEVERE = 15.0;
	/**
	 * Severity healed per second, by activity. Resting means standing still (or
	 * crouching, or riding); walking about is light; sprinting, fighting, or a
	 * flare that is still climbing heals nothing. A mild knock clears in under
	 * two minutes of rest; a severe one is five minutes of rest or a night's sleep.
	 */
	public static final double CONCUSSION_HEAL_RESTING = 0.30;
	public static final double CONCUSSION_HEAL_LIGHT = 0.12;
	/**
	 * A mob's concussion heals on time alone, at this rate: a moderate one is
	 * about forty seconds of daze. The player rates would leave a zombie
	 * forgetting its target every two seconds for five minutes.
	 */
	public static final double CONCUSSION_HEAL_MOB = 1.0;
	/** Healing multiplier while the head itself is still INJURED or worse. */
	public static final double CONCUSSION_HEAL_BAD_HEAD = 0.5;
	/** Dark rooms help: healing multiplier at block brightness at or below {@link #CONCUSSION_DARK_LIGHT}. */
	public static final double CONCUSSION_HEAL_DARK_BONUS = 1.25;

	// Flare: how much the concussed brain is being pushed right now, 0-100.
	public static final double FLARE_MAX = 100.0;
	public static final double FLARE_SPRINT_PER_SEC = 7.0;
	public static final double FLARE_PER_JUMP = 3.5;
	public static final double FLARE_PER_ATTACK = 4.0;
	public static final double FLARE_PER_BLOCK_BROKEN = 1.0;
	public static final double FLARE_PER_HIT_TAKEN = 10.0;
	/** Bright light: flare per second per brightness level above {@link #CONCUSSION_BRIGHT_LIGHT}. */
	public static final double FLARE_LIGHT_PER_SEC_PER_LEVEL = 0.9;
	/**
	 * Light can push the flare this far and no further. It hurts, it takes your
	 * sprint away if you stay in it, but it never empties your stomach on its own
	 * — that takes pushing on: fighting, jumping, getting hit. A severe
	 * concussion walking about in daylight would otherwise vomit every twenty
	 * seconds, which is a punishment for being outdoors, not for being reckless.
	 */
	public static final double FLARE_LIGHT_CEILING = 62.0;
	/** Brightness (0-15, sky darkening included) at which light starts to hurt. */
	public static final int CONCUSSION_BRIGHT_LIGHT = 12;
	/** Brightness at or below which a dark place soothes. */
	public static final int CONCUSSION_DARK_LIGHT = 6;
	/** Flare shed per second when still, when moving gently, and extra in the dark. */
	public static final double FLARE_DECAY_RESTING = 9.0;
	public static final double FLARE_DECAY_LIGHT = 3.0;
	public static final double FLARE_DECAY_DARK_EXTRA = 3.0;
	/** Gain multipliers by grade (mild, moderate, severe). */
	public static final double FLARE_GAIN_MILD = 0.6;
	public static final double FLARE_GAIN_SEVERE = 1.5;
	/** Flare at which sprinting is refused. */
	public static final double FLARE_NO_SPRINT = 60.0;
	/** Flare at which you vomit, and what it drops back to afterwards. */
	public static final double FLARE_RETCH = 90.0;
	public static final double FLARE_AFTER_RETCH = 50.0;
	/** Ticks spent doubled over. */
	public static final int RETCH_TICKS = 50;
	/** Food points and all saturation lost to one retch. */
	public static final int RETCH_FOOD_LOSS = 3;

	// Vertigo spells: a phase accumulator, like the microsleeps.
	/** Ticks between spells by grade at zero flare (mild, moderate, severe). */
	public static final double VERTIGO_PERIOD_MILD = 1500.0;
	public static final double VERTIGO_PERIOD_MODERATE = 900.0;
	public static final double VERTIGO_PERIOD_SEVERE = 500.0;
	/** A full flare makes spells come this many times as often. */
	public static final double VERTIGO_FLARE_RATE = 2.0;
	/** Spell length in ticks by grade, plus a little more the worse the flare. */
	public static final int VERTIGO_TICKS_MILD = 50;
	public static final int VERTIGO_TICKS_MODERATE = 70;
	public static final int VERTIGO_TICKS_SEVERE = 100;
	public static final double VERTIGO_TICKS_PER_FLARE = 0.4;
	/** Movement penalty while the room is spinning. */
	public static final double VERTIGO_MOVEMENT_PENALTY = 0.25;
	/** Flare added per second of a spell — the spinning itself turns the stomach. */
	public static final double FLARE_VERTIGO_PER_SEC = 2.0;

	// --- Grip (1.7): a Disabled arm's hand will not close ---
	/** Ticks between "your hand won't close" lines, so mashing use is not spam. */
	public static final int GRIP_MESSAGE_COOLDOWN_TICKS = 40;

	// --- Adrenaline (1.7): the last two hearts ---
	/** Health fraction at or below which adrenaline surges (two hearts of ten). */
	public static final double ADRENALINE_HP_FRACTION = 0.2;
	/** How long the surge lasts. */
	public static final int ADRENALINE_SURGE_TICKS = 160;
	/** Minimum length of the crash that follows; it lasts at least this, and for as long as health stays low. */
	public static final int ADRENALINE_CRASH_TICKS = 600;
	/** A second surge needs this long since the first AND health back above the heartbeat line. */
	public static final int ADRENALINE_REFRACTORY_TICKS = 2400;
	/** Surge: movement and attack speed bonuses, and the share of limb pain it masks. */
	public static final double ADRENALINE_SPEED_BONUS = 0.15;
	public static final double ADRENALINE_ATTACK_SPEED_BONUS = 0.10;
	public static final double ADRENALINE_PAIN_MASK = 0.5;
	/** Crash: movement penalty, and the breath budget for sprinting. */
	public static final double CRASH_MOVEMENT_PENALTY = 0.10;
	public static final double CRASH_BREATH_MAX = 60.0;
	/** Breath spent per sprinting tick, and recovered per tick otherwise. */
	public static final double CRASH_BREATH_SPEND = 1.0;
	public static final double CRASH_BREATH_RECOVER = 0.5;

	// --- The body after death, as everyone else sees it (1.7) ---

	/** A lethal blast's throw on the body, blocks a tick, at violence 0 and 1. */
	public static final double CORPSE_BLAST_SPEED_MIN = 0.30;
	public static final double CORPSE_BLAST_SPEED_MAX = 0.75;
	/** ...and its lift. */
	public static final double CORPSE_BLAST_LIFT_MIN = 0.30;
	public static final double CORPSE_BLAST_LIFT_MAX = 0.55;
	/**
	 * How long a ragdolled body stays in the world, ticks, before vanilla's
	 * removal (and its poof) may run. Vanilla's is 20: the body vanished mid-air.
	 */
	public static final int CORPSE_HOLD_TICKS = 100;
	/**
	 * The killing blow's kick on the one region it landed on (active ragdolls),
	 * blocks a tick at violence 0 and 1: a sword knocks the struck shoulder back,
	 * an arrow snaps the head, a blast throws the nearest limb hardest so the
	 * body tumbles about it. On top of the whole body's launch.
	 */
	public static final double CORPSE_KICK_BLOW_MIN = 0.10;
	public static final double CORPSE_KICK_BLOW_MAX = 0.30;
	public static final double CORPSE_KICK_SHOT_MIN = 0.15;
	public static final double CORPSE_KICK_SHOT_MAX = 0.35;
	public static final double CORPSE_KICK_BLAST_MIN = 0.20;
	public static final double CORPSE_KICK_BLAST_MAX = 0.50;

	// --- A dead body that is still falling (2.0) ---
	//
	// Death used to end the body's story: nothing after it could hurt it. Now a
	// body that dies in the air, or dies of the landing itself, is followed by
	// the server until it comes to rest, and what it strikes on the way can
	// still break a bone or take a limb off. Tuned so that a death where you
	// stood never loses anything, a death at the end of a long drop usually
	// loses a leg, and a blast adds nothing to what LethalBlast already took.

	/**
	 * A body travelling at least this fast when it died, blocks a tick, is
	 * followed. A sprint-jump is about 0.36 along the ground and a jump leaves at
	 * 0.42, so nobody who merely died running is followed; a body three blocks
	 * into a fall is. A hard lethal blast's launch is over it too, so those
	 * bodies are followed, and the thresholds below are what keep them whole.
	 */
	public static final double CORPSE_IMPACT_FOLLOW_SPEED = 0.55;
	/**
	 * ...or one with this much clear air under its feet, blocks: it died still,
	 * but it is about to fall far enough to matter (killed at the top of a jump
	 * over a pit).
	 */
	public static final double CORPSE_IMPACT_FOLLOW_DROP = 5.0;
	/**
	 * How many dead bodies the server follows at once. Each costs a small
	 * ragdoll simulation every tick until it lies still, so the number is fixed:
	 * past it, a new death simply is not followed and keeps what it had (a mob
	 * farm does not get to run forty ragdolls a second).
	 */
	public static final int CORPSE_IMPACT_MAX_BODIES = 6;
	/**
	 * Ticks between the server's body striking something and the result being
	 * applied. A watching client's copy of the body starts a tick or two of
	 * travel behind the server's (it was still gliding to where the server last
	 * said the body was), so its landing comes that much later; without the wait
	 * the limb would leave just above the ground. Measured in
	 * {@code CorpseAgreementTest}: two ticks puts it on or just after the
	 * landing for every copy.
	 */
	public static final int CORPSE_IMPACT_DELAY_TICKS = 2;
	/**
	 * A strike slower than this, blocks a second, is not looked at at all: the
	 * flop of a body going over where it stood tops out near 9.
	 */
	public static final double CORPSE_IMPACT_MIN_SPEED = 16.0;
	/**
	 * Fall-trauma at which a dead limb's bone breaks. The scale is the living
	 * landing's ({@link #FALL_TRAUMA_PER_BLOCK}, times what the surface gives
	 * back, less what armour takes), read off the speed the body struck at
	 * rather than off a height: see {@code CorpseImpactMath}. An arm at 5.0 is a
	 * strike of 20.5 blocks a second onto ordinary ground, which a real fall
	 * reaches after about eight blocks; a leg at 6.0 is 22.5, about nine.
	 */
	public static final double CORPSE_IMPACT_BREAK_ARM = 5.0;
	public static final double CORPSE_IMPACT_BREAK_LEG = 6.0;
	/**
	 * Fall-trauma at which a dead body's limb comes off, by how many it has
	 * already lost this way: the bar rises with each, as {@code LethalBlast}'s
	 * does. On ordinary ground the first goes at a strike of 29 blocks a second
	 * (a real fall of sixteen blocks), the second at 33 (twenty-four), the third
	 * at 36.7 (thirty-one), the last at 39.4 (thirty-six), just under the
	 * fastest the mod launches a body (40). So a fall that kills a healthy
	 * player outright (twenty-three blocks unsoftened, thirty-eight softened)
	 * costs the body one leg or both. The hardest lethal blast there is brings
	 * a body down at about 10 on flat ground and 15 into a wall
	 * ({@code CorpseAgreementTest}): under the floor above, and half of the
	 * first bar.
	 */
	public static final double[] CORPSE_IMPACT_SEVER = {10.0, 13.0, 16.0, 18.5};

	// --- Thrown off your feet without dying (active ragdolls phase 4) ---
	// Violence as DeathMath scores a blow: the raw damage against one and a half
	// health bars. For a 20-health body: a blast from 7.5, a big hitter from
	// 4.5, any other blow that lands on the body from 13.5.
	public static final double KNOCKDOWN_BLAST_VIOLENCE = 0.25;
	public static final double KNOCKDOWN_HEAVY_VIOLENCE = 0.15;
	public static final double KNOCKDOWN_BLOW_VIOLENCE = 0.45;
	/** How long a blast or a blow keeps the body down, ticks, at violence 0 and 1. */
	public static final int KNOCKDOWN_HIT_TICKS_MIN = 25;
	public static final int KNOCKDOWN_HIT_TICKS_MAX = 70;

	// --- Broken necks and whiplash (2.0) ---
	// A neck takes a "strain" from each thing that can wrench it: 1 snaps it, a
	// fraction of that (WHIPLASH_MIN_STRAIN and up) leaves whiplash. Every bar
	// below is the strain-1 figure for one cause.
	/**
	 * A body that lands head first (limp, knocked flat, or gliding): the drop,
	 * blocks over the safe distance times what the ground gave back, that snaps
	 * the neck. The server does not know a ragdoll's orientation, so "limp"
	 * stands in for "head first".
	 */
	public static final double NECK_LIMP_HEIGHT = 9.0;
	/**
	 * A landing on your feet never snaps the neck, but a hard one wrenches it:
	 * strain from the knockdown's own floor up to 0.95 at this drop.
	 */
	public static final double NECK_UPRIGHT_FULL_HEIGHT = 33.0;
	/**
	 * Flying into a wall: vanilla's own damage figure for the crash (ten times the
	 * speed lost, less three), raw, that snaps the neck. 12 is about 1.5 blocks a
	 * tick, 30 m/s: an elytra at cruise.
	 */
	public static final double NECK_WALL_DAMAGE = 12.0;
	/**
	 * A heavy blunt blow to the head (a mace smash, an iron golem, a ravager, an
	 * anvil): the localized head trauma that snaps the neck. Three steps on a
	 * sound head: from about 12 a blow like that puts you out cold (spec §19),
	 * from 16 it snaps the neck, and from 20, which empties the head in one go,
	 * it crushes the skull instead (the finish-off rule runs first). 16 is about
	 * 11.5 points of heavy blunt damage landing on a bare head.
	 */
	public static final double NECK_BLUNT_TRAUMA = 16.0;
	/** A plain blunt blow can wrench a neck but never snap it: its strain stops here. */
	public static final double NECK_BLUNT_STRAIN_CAP = 0.95;
	/** This much of a bar, and the neck is wrenched rather than left alone. */
	public static final double WHIPLASH_MIN_STRAIN = 0.35;
	/** A knockdown this violent wrenches the neck on the way down (never snaps it). */
	public static final double WHIPLASH_KNOCKDOWN_VIOLENCE = 0.35;
	/** A killing heavy blow to the head this violent takes the neck with it (the ragdoll's head hangs). */
	public static final double NECK_KILL_VIOLENCE = 0.5;
	/** Water this deep or shallower does not stop a fall: you hit the bottom. Blocks. */
	public static final int SHALLOW_WATER_DEPTH = 1;
	/** What one block of water takes off a fall before the bottom arrives. */
	public static final double SHALLOW_WATER_CUSHION = 0.8;
	/** How long whiplash lasts at its mildest and its worst, ticks: 30 s to 90 s. */
	public static final int WHIPLASH_TICKS_MIN = 600;
	public static final int WHIPLASH_TICKS_MAX = 1800;
	/** How stiff the neck starts, at the mildest and the worst: 0 free, 1 as stiff as it gets. */
	public static final double WHIPLASH_STIFFNESS_MIN = 0.35;
	public static final double WHIPLASH_STIFFNESS_MAX = 1.0;
	/**
	 * The fastest the view turns, degrees a second, at the faintest stiffness and
	 * at the worst. 75 is a quarter turn in 1.2 s; 540 only catches a flick.
	 */
	public static final double WHIPLASH_TURN_MILD = 540.0;
	public static final double WHIPLASH_TURN_WORST = 75.0;
	/** How much wider a whiplashed mob's shots go, at the worst. */
	public static final double MOB_WHIPLASH_AIM_SPREAD = 2.5;

	// --- The field kit's brewed effects (2.0, phase 3) ---
	// Everything below is behind a field kit switch; with both off none of it is read.

	// Haemorrhage (Weapons): the blood stops clotting.
	/** How hard a hit must be to open a bleed, against normal, at level I; each level beyond takes another step off. */
	public static final double HAEMORRHAGE_THRESHOLD_FACTOR = 0.6;
	public static final double HAEMORRHAGE_THRESHOLD_PER_LEVEL = 0.15;
	/** The threshold factor never goes below this, however strong the brew. */
	public static final double HAEMORRHAGE_THRESHOLD_FLOOR = 0.3;
	/** Wounds open this many severity steps worse (before armour takes its step back). */
	public static final int HAEMORRHAGE_SEVERITY_STEPS = 1;
	/** The stacked bleed-rate cap ({@link #WOUND_STACK_CAP}) is raised by this factor at level I, and more per level. */
	public static final double HAEMORRHAGE_CAP_FACTOR = 1.5;
	public static final double HAEMORRHAGE_CAP_PER_LEVEL = 0.5;
	/** A hand on the wound keeps this share of what it would normally hold back. */
	public static final double HAEMORRHAGE_PRESSURE_HOLD = 0.5;
	/** The wound a Haemorrhage arrow opens where it hits, on anything that bleeds. */
	public static final dev.captience.realisticdamage.body.WoundSeverity HAEMORRHAGE_ARROW_WOUND =
			dev.captience.realisticdamage.body.WoundSeverity.MODERATE;
	/** Potion durations, ticks: base, long (redstone), strong (glowstone, level II). */
	public static final int HAEMORRHAGE_TICKS = 900;
	public static final int HAEMORRHAGE_LONG_TICKS = 1800;
	public static final int HAEMORRHAGE_STRONG_TICKS = 432;

	// Adrenaline (Weapons): the last-two-hearts surge, on demand.
	/** How long a drunk surge lasts, ticks: base and long (redstone). The crash after it is the natural one. */
	public static final int ADRENALINE_POTION_TICKS = 400;
	public static final int ADRENALINE_POTION_LONG_TICKS = 800;
	/** Open wounds bleed this much harder while the heart is racing on it. */
	public static final double ADRENALINE_BLEED_FACTOR = 1.3;
	/** The heart races at least this hard (Cardio's health distress, 0-1) while it lasts. */
	public static final double ADRENALINE_HEART_DISTRESS = 0.6;

	// Sedation (Weapons): a dose builds into a level; level over body size is the stage.
	/** The drinkable potion's length, ticks. A tipped arrow carries an eighth of it. */
	public static final int SEDATION_TICKS = 800;
	/** An application this long or longer is one whole dose: a tipped arrow's share of the potion. */
	public static final int SEDATION_DOSE_TICKS = 100;
	/** How long a dose takes to take hold, ticks (the eyelids get heavy, then you go). */
	public static final int SEDATION_ABSORB_TICKS = 200;
	/**
	 * Once a dose has taken hold the body clears it by half-lives, like a real
	 * drug: half of what is in it every this many ticks. So one arrow has a
	 * player down for about 12 s and a strong one about 30 s, where a fixed
	 * clearing rate kept two doses on the floor for over two minutes (items.6).
	 */
	public static final int SEDATION_HALF_LIFE_TICKS = 200;
	/** Below this much (per unit of body size) what is left is gone, and the effect ends. */
	public static final double SEDATION_CLEAR_FLOOR = 0.05;
	/**
	 * A body that has stopped breathing barely clears it: a dose per this many
	 * ticks, the old steady rate, so an overdose still runs the hypoxia debt
	 * all the way up unless milk clears it first.
	 */
	public static final int SEDATION_OVERDOSE_CLEAR_TICKS = 2400;
	/** Stage bars, in doses per unit of body size (a zombie or a player is 1, a ravager 5). */
	public static final double SEDATION_DROWSY = 0.25;
	public static final double SEDATION_STUMBLING = 0.5;
	public static final double SEDATION_OUT = 0.85;
	public static final double SEDATION_OVERDOSE = 2.5;
	/**
	 * The highest consciousness each stage allows. Drowsy sits among the first
	 * symptoms; stumbling sits just above the wake bar and just under the
	 * microsleep one, so a body can wake into it and still nod off; out is zero.
	 */
	public static final double SEDATION_DROWSY_CAP = 60.0;
	public static final double SEDATION_STUMBLING_CAP = 21.0;
	/** Movement lost while drowsy and while stumbling. */
	public static final double SEDATION_DROWSY_SLOW = 0.15;
	public static final double SEDATION_STUMBLING_SLOW = 0.30;
	/** While stumbling, any blow this violent puts the body down (a sober one needs {@link #KNOCKDOWN_BLOW_VIOLENCE}). */
	public static final double SEDATION_STUMBLING_KNOCKDOWN = 0.02;
	/** Overdosed and out of breath for the whole hypoxia debt: this much health a second, times body size. */
	public static final double SEDATION_OVERDOSE_DAMAGE_PER_SEC = 1.0;

	// Numbing (Medicine): no pain, every injury still there.
	public static final int NUMBING_TICKS = 1200;
	public static final int NUMBING_LONG_TICKS = 2400;
	/** Condition a broken leg loses per second of sprinting on it while numb. */
	public static final double NUMB_SPRINT_LEG_COST = 1.0;
	/** Condition a broken arm loses per swing while numb. */
	public static final double NUMB_SWING_ARM_COST = 0.5;

	// --- The field kit's medicine (2.0, phase 4): bleeding ---
	/** How far a medic can reach a patient, blocks. */
	public static final double TREATMENT_REACH = 3.5;
	/** A bandage: about three seconds with both hands. */
	public static final int BANDAGE_USE_TICKS = 60;
	/** What a bandage lets through of a minor and a moderate wound. Arterial ones it cannot hold at all. */
	public static final double BANDAGE_MINOR_FACTOR = 0.25;
	public static final double BANDAGE_MODERATE_FACTOR = 0.4;
	/** A moderate wound soaks a bandage through in this long, and it needs changing. */
	public static final int BANDAGE_SOAK_TICKS = 900;
	/** A tourniquet: two seconds to get it on. */
	public static final int TOURNIQUET_USE_TICKS = 40;
	/** On this long and the limb starts to go cold; this long and it is dead. */
	public static final int TOURNIQUET_WARN_TICKS = 4800;
	public static final int TOURNIQUET_LIMIT_TICKS = 6000;
	/** Needle and thread: ten seconds of stitching, and a few wounds per needle. */
	public static final int NEEDLE_USE_TICKS = 200;
	public static final int NEEDLE_USES = 4;
	/** Cauterizing: consciousness lost to the pain, at once. Enough to drop a body that was already fading. */
	public static final double CAUTERIZE_SHOCK = 45.0;
	/** A blood bag: drawn from a donor, given to a patient. */
	public static final double BLOOD_BAG_DRAW = 25.0;
	public static final double BLOOD_BAG_GIVE = 25.0;
	/** A donor with less than this to give is turned away. */
	public static final double BLOOD_BAG_MIN_DONOR = 50.0;
	public static final int BLOOD_BAG_USE_TICKS = 100;
	/** How long a bag of blood keeps, ticks: three days. */
	public static final long BLOOD_BAG_KEEPS_TICKS = 72000L;

	// --- Field kit, phase 4 round 2: bones, head and senses (2.0) ---

	/** A splint: four seconds to bind on. A broken limb under one costs this share of its penalty. */
	public static final int SPLINT_USE_TICKS = 80;
	public static final double SPLINT_PENALTY_FACTOR = 0.5;
	/** A sling: three seconds to tie. A slung broken arm takes this much more from every heal. */
	public static final int SLING_USE_TICKS = 60;
	public static final double SLING_HEAL_FACTOR = 2.0;
	/** Smelling salts: a second under the nose. They bring a body up to this far over waking. */
	public static final int SALTS_USE_TICKS = 20;
	public static final double SALTS_WAKE_MARGIN = 5.0;
	/** The stethoscope's own volume: a heart at rest is loud enough to hear through it. */
	public static final double STETHOSCOPE_VOLUME = 0.85;
	/** Tinted goggles: how dark the view goes (alpha of the tint, 0 to 255). */
	public static final int GOGGLES_TINT_ALPHA = 92;

	// --- Field kit, phase 4 round 3: food and drink (2.0) ---

	/** A bottle of coffee keeps you up for three minutes. */
	public static final int COFFEE_TICKS = 3600;
	/**
	 * Sedation in a caffeinated body counts for this much less, as if the body
	 * were this much bigger: one arrow leaves it stumbling instead of out.
	 */
	public static final double COFFEE_SEDATION_RESIST = 1.5;
	/** A heart on coffee beats harder: open wounds bleed this much more. */
	public static final double COFFEE_BLEED_FACTOR = 1.15;
	/** Your own heart on coffee runs as if under this much distress (Adrenaline's is 0.6). */
	public static final double COFFEE_HEART_DISTRESS = 0.3;
	/** A bottle of spirits: two minutes tipsy. */
	public static final int SPIRITS_TICKS = 2400;
	/** Tipsy, a limp and a sore arm slow you by this share of what they would. */
	public static final double SPIRITS_PAIN_FACTOR = 0.5;
	/** Tipsy, open wounds bleed this much more. */
	public static final double SPIRITS_BLEED_FACTOR = 1.1;
	/** Tipsy, the world doubles as it moves: the afterimage at least this strong (before the slider). */
	public static final double SPIRITS_AFTERIMAGE = 0.35;
	/** A blood sausage: Blood back on top of what its food gives. */
	public static final double BLOOD_SAUSAGE_BLOOD = 20.0;
	/** An iron tonic: Blood comes back this much faster, for three minutes, or eight from the long brew. */
	public static final double IRON_TONIC_REGEN_FACTOR = 2.0;
	public static final int IRON_TONIC_TICKS = 3600;
	public static final int IRON_TONIC_LONG_TICKS = 9600;
}
