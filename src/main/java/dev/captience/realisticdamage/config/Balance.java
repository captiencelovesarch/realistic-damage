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
}
