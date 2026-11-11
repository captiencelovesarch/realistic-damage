package dev.captience.realisticdamage.body;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import dev.captience.realisticdamage.systems.ConcussionMath;
import dev.captience.realisticdamage.systems.WoundMath;
import org.jspecify.annotations.Nullable;

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
	/**
	 * How bad the concussion is, 0 (none) to 100 (1.7). Read as a grade by
	 * {@link ConcussionMath#grade}; healed by time, deepened by further head hits.
	 */
	private double concussionSeverity;
	/**
	 * Transient concussion state (1.7). The flare is how hard the bruised brain
	 * is being pushed right now; vertigo and retching are the episodes it
	 * produces. None of it persists: a restart is a long sit-down.
	 */
	private transient double concussionFlare;
	private transient double vertigoPhase;
	private transient int vertigoTicks;
	/** Spells so far, so their direction alternates without a die roll. */
	private transient int vertigoCount;
	private transient int retchTicks;
	/** Seconds of oxygen debt (1.7): how long the brain has gone without air, repaid by breathing. */
	private transient double hypoxia;
	/** The last-two-hearts surge and crash (1.7). Players only. */
	private final transient AdrenalineMath.State adrenaline = new AdrenalineMath.State();
	/** Where the body was last tick and how many jumps it had made, for reading exertion (1.7). */
	private transient double lastSeenX = Double.NaN;
	private transient double lastSeenZ;
	private transient int lastJumpCount = -1;
	/** Game time of the last exertion that holds the flare up (an attack, a jump). */
	private transient long lastExertion = -1_000_000L;
	/** Game time of the last "your hand won't close" line, for the cooldown. */
	private transient long lastGripMessage = -1_000_000L;
	private boolean burned;
	/** Transient: health captured at ALLOW_DAMAGE so AFTER_DAMAGE can compute the true post-mitigation damage. */
	private transient double preHurtHealth = Double.NaN;
	/** Transient: fractional progress toward stalling one item-use tick (two-handed impairment, spec §11). */
	private transient double useStallProgress;
	/** Transient: fractional vanilla-HP bleed-out damage accumulated toward the next whole point (spec §17). */
	private transient double bleedHpProgress;
	/** Transient: last blood band announced, so warnings fire once per crossing. */
	private transient int lastBloodBand = -1;
	/**
	 * Transient rescue state (spec 1.2). None of it is persisted: a grip on
	 * someone's arm should not survive a server restart, and a stale UUID
	 * pointing at a player who is no longer here would be worse than nothing.
	 */
	private transient UUID dragging;
	private transient UUID draggedBy;
	private transient UUID pressureTarget;
	/** Counts down; refreshed each tick by whoever is pressing on this body. */
	private transient int pressureTicks;
	/** Whether that hold is the player's own hands rather than a companion's. */
	private transient boolean pressureBySelf;
	/**
	 * Counts down on the presser's own side. Without it, a client that simply
	 * stopped asking would leave {@code pressureTarget} set forever and the
	 * player rooted to the spot with no way out.
	 */
	private transient int pressureHoldTicks;
	/** Transient: last "who you are treating" id sent, so it is only sent on change. */
	private transient int lastTreatingSent = -1;

	/**
	 * Transient landing state (1.4). Set by the fall mixin on the tick the ground
	 * arrives and consumed by the trauma pipeline in the same tick, so that falls
	 * can be scored on how far you dropped rather than on what your boots let
	 * through. None of it is persisted: you are not still mid-fall after a
	 * restart.
	 */
	private transient double pendingFallHeight;
	private transient double pendingFallSurface = 1.0;
	private transient boolean pendingFallLeadingLeft;
	/**
	 * Transient blast state (1.6), the same shape as the landing record and for
	 * the same reason. Set by {@code ServerExplosionMixin} on the tick the
	 * explosion hurts this player and consumed by the trauma pipeline inside the
	 * same call. Not persisted: you are not still mid-blast after a restart, and
	 * {@code RDCodecs} never sees it.
	 */
	private transient @Nullable PendingExplosion pendingExplosion;
	/**
	 * The head came off (2.0), as opposed to being crushed: both leave the head
	 * Destroyed, but only this one leaves a body with nothing on its shoulders.
	 * Not persisted: a player is dead before anything could save it, and a
	 * headless zombie that is saved mid-stride is let die on load (MobAnatomy).
	 */
	private transient boolean headSevered;
	/** Ticks a headless zombie has left to walk (2.0). */
	private transient int headlessTicks;
	/**
	 * The neck snapped (2.0): its ragdoll's head hangs with nothing holding it.
	 * Not persisted: it kills.
	 */
	private transient boolean neckBroken;
	/**
	 * Whiplash (2.0): ticks left, the ticks it started with, and how stiff it
	 * started (see {@code NeckMath}). Not persisted: a restart is a long rest.
	 */
	private transient int whiplashTicks;
	private transient int whiplashTotal;
	private transient double whiplashStart;
	/** The landing being scored came head first: a limp body's or a glider's (2.0). */
	private transient boolean pendingFallHeadFirst;
	/**
	 * A dive into water too shallow to stop it is being scored as a landing on
	 * the bottom (2.0), and whether it went in head first. Only for the length
	 * of that one call.
	 */
	private transient boolean diving;
	private transient boolean divingHeadFirst;
	/**
	 * A lethal blow that would also have snapped the neck, seen at ALLOW_DEATH
	 * (2.0), stamped with its tick like the death violence: a totem can still
	 * refuse the death, and a neck that was never broken must not hang loose on
	 * a body that got up again.
	 */
	private transient int neckStashTick = Integer.MIN_VALUE;
	/** The tick a totem last pulled this body back (2.0): that blow has spent its death. */
	private transient int totemSavedTick = Integer.MIN_VALUE;
	/** Counts down while you are on the ground after a hard landing. */
	private transient int knockdownTicks;
	/** ...and then while you are back up but not yet steady. */
	private transient int knockdownRecoveryTicks;
	/**
	 * Free-running phase toward the next microsleep, in [0,1). Advanced by a rate
	 * derived from consciousness, so the cadence is a pure function of how far
	 * gone the player is — no die rolls anywhere in losing consciousness.
	 */
	private transient double microsleepPhase;
	/** Ticks left in the current blackout; 0 when awake enough to be present. */
	private transient int microsleepTicks;

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

	/** 0 stable, 1 light-headed, 2 greying out, 3 barely upright, 4 bleeding out. */
	public int bloodBand() {
		return Vitals.bloodBand(blood);
	}

	public int lastBloodBand() {
		return lastBloodBand;
	}

	public void setLastBloodBand(int band) {
		lastBloodBand = band;
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
		if (concussion()) {
			cap = Math.max(Balance.WAKE_THRESHOLD + Balance.CONCUSSION_CAP_FLOOR_MARGIN,
					cap - ConcussionMath.capDrop(concussionGrade()));
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

	// --- Rescue (1.2) ---

	@Nullable
	public UUID dragging() {
		return dragging;
	}

	public void setDragging(@Nullable UUID victim) {
		dragging = victim;
	}

	@Nullable
	public UUID draggedBy() {
		return draggedBy;
	}

	public void setDraggedBy(@Nullable UUID dragger) {
		draggedBy = dragger;
	}

	@Nullable
	public UUID pressureTarget() {
		return pressureTarget;
	}

	public void setPressureTarget(@Nullable UUID target) {
		pressureTarget = target;
	}

	/** True while someone (possibly this player) is holding a wound shut. */
	public boolean underPressure() {
		return pressureTicks > 0;
	}

	/** Called by whoever is pressing, every tick they keep it up. */
	public void refreshPressure(boolean bySelf) {
		pressureTicks = Balance.PRESSURE_GRACE_TICKS;
		pressureBySelf = bySelf;
	}

	/** How effective the current hold is: your own hands are the worse option. */
	public double pressureMultiplier() {
		if (pressureTicks <= 0) {
			return 1.0;
		}
		return pressureBySelf
				? Balance.PRESSURE_BLEED_MULTIPLIER_SELF
				: Balance.PRESSURE_BLEED_MULTIPLIER_COMPANION;
	}

	public void tickPressure() {
		if (pressureTicks > 0) {
			pressureTicks--;
		}
	}

	/** Renewed by the presser every tick they keep the key down. */
	public void refreshPressureHold() {
		pressureHoldTicks = Balance.PRESSURE_GRACE_TICKS;
	}

	/** Ticks the hold down; false once it has lapsed and the grip should be dropped. */
	public boolean tickPressureHold() {
		if (pressureHoldTicks > 0) {
			pressureHoldTicks--;
		}
		return pressureHoldTicks > 0;
	}

	public int lastTreatingSent() {
		return lastTreatingSent;
	}

	public void setLastTreatingSent(int id) {
		lastTreatingSent = id;
	}

	/** Rescue state is interaction state, not body state: dropped on death and respawn. */
	public void clearRescue() {
		dragging = null;
		draggedBy = null;
		pressureTarget = null;
		pressureTicks = 0;
	}

	public void setUnconsciousTicks(int ticks) {
		unconsciousTicks = Math.max(0, ticks);
	}

	// --- Landing, knockdown and microsleeps (1.4) ---

	/** Records the landing the pipeline is about to be asked about. */
	public void setPendingFall(double height, double surfaceFactor, boolean leadingLeft) {
		pendingFallHeight = Math.max(0.0, height);
		pendingFallSurface = surfaceFactor;
		pendingFallLeadingLeft = leadingLeft;
	}

	public double pendingFallHeight() {
		return pendingFallHeight;
	}

	public double pendingFallSurface() {
		return pendingFallSurface;
	}

	public boolean pendingFallLeadingLeft() {
		return pendingFallLeadingLeft;
	}

	/**
	 * Single-use, like the pre-hurt capture: a fall scores exactly one damage
	 * event, and a stale height left lying around would quietly re-score the next
	 * unrelated hit as though the player had dropped off a cliff.
	 */
	public void clearPendingFall() {
		pendingFallHeight = 0.0;
		pendingFallSurface = 1.0;
		pendingFallHeadFirst = false;
	}

	// --- Blasts (1.6) ---

	/** Records the blast the pipeline is about to be asked about. */
	public void setPendingExplosion(PendingExplosion blast) {
		pendingExplosion = blast;
	}

	/** Non-consuming read, for the mixin's bookkeeping and for tests. */
	public @Nullable PendingExplosion pendingExplosion() {
		return pendingExplosion;
	}

	public boolean hasPendingExplosion() {
		return pendingExplosion != null;
	}

	/**
	 * Single-use, for the same reason as {@link #consumePreHurt} and the pending
	 * fall: a blast scores exactly one damage event, and a stale one left lying
	 * around would re-score the next unrelated hit as though you had been standing
	 * on a barrel of TNT.
	 */
	public @Nullable PendingExplosion consumePendingExplosion() {
		PendingExplosion blast = pendingExplosion;
		pendingExplosion = null;
		return blast;
	}

	public void clearPendingExplosion() {
		pendingExplosion = null;
	}

	/**
	 * How violent the blow that is about to kill this player was, held for the
	 * one tick between the trauma pipeline seeing a lethal hit and AFTER_DEATH
	 * confirming it. Tick-stamped rather than merely single-use because the gap
	 * between "lethal-looking" and "dead" is where a totem fires: a stash with
	 * no expiry would sit through the save and then colour some unrelated
	 * bleed-out death minutes later with the mace hit's violence.
	 */
	private transient double deathViolence;
	private transient int deathViolenceTick = Integer.MIN_VALUE;

	public void stashDeathViolence(double violence, int tick) {
		deathViolence = Math.clamp(violence, 0.0, 1.0);
		deathViolenceTick = tick;
	}

	/** The stashed violence if it was scored this same tick, else -1; leaves it stashed. */
	public double peekDeathViolence(int tick) {
		return tick == deathViolenceTick ? deathViolence : -1.0;
	}

	/** The stashed violence if it was scored this same tick, else 0. Clears either way. */
	public double consumeDeathViolence(int tick) {
		double value = tick == deathViolenceTick ? deathViolence : 0.0;
		deathViolence = 0.0;
		deathViolenceTick = Integer.MIN_VALUE;
		return value;
	}

	public boolean headSevered() {
		return headSevered;
	}

	public void setHeadSevered(boolean severed) {
		this.headSevered = severed;
	}

	public int headlessTicks() {
		return headlessTicks;
	}

	public void setHeadlessTicks(int ticks) {
		this.headlessTicks = Math.max(0, ticks);
	}

	public boolean neckBroken() {
		return neckBroken;
	}

	public void setNeckBroken(boolean broken) {
		this.neckBroken = broken;
	}

	/** Whether the neck is stiff from whiplash right now. */
	public boolean whiplashed() {
		return whiplashTicks > 0;
	}

	/** How stiff the neck is now, 0 (free) to 1. */
	public double whiplash() {
		return dev.captience.realisticdamage.systems.NeckMath.stiffness(whiplashStart, whiplashTicks, whiplashTotal);
	}

	public int whiplashTicks() {
		return whiplashTicks;
	}

	/**
	 * Wrenches the neck. The worse of the old and the new wins: a mild jolt on
	 * top of a bad whiplash changes nothing, a bad one on a mild one starts
	 * over from the bad one.
	 *
	 * @return whether it made the neck any stiffer
	 */
	public boolean wrench(double severity) {
		double start = dev.captience.realisticdamage.systems.NeckMath.startingStiffness(severity);
		if (start <= whiplash() + 1.0e-6) {
			return false;
		}
		whiplashStart = start;
		whiplashTotal = dev.captience.realisticdamage.systems.NeckMath.whiplashTicks(severity);
		whiplashTicks = whiplashTotal;
		return true;
	}

	public void tickWhiplash() {
		if (whiplashTicks > 0) {
			whiplashTicks--;
		}
	}

	public void clearWhiplash() {
		whiplashTicks = 0;
		whiplashTotal = 0;
		whiplashStart = 0.0;
	}

	public boolean pendingFallHeadFirst() {
		return pendingFallHeadFirst;
	}

	public void setPendingFallHeadFirst(boolean headFirst) {
		this.pendingFallHeadFirst = headFirst;
	}

	public void markTotemSaved(int tick) {
		totemSavedTick = tick;
	}

	public boolean totemSaved(int tick) {
		return totemSavedTick == tick;
	}

	public void stashNeck(int tick) {
		neckStashTick = tick;
	}

	/** Whether a broken neck was stashed this same tick. Clears either way. */
	public boolean consumeNeck(int tick) {
		boolean stashed = neckStashTick == tick;
		neckStashTick = Integer.MIN_VALUE;
		return stashed;
	}

	public boolean diving() {
		return diving;
	}

	public boolean divingHeadFirst() {
		return divingHeadFirst;
	}

	/** See {@link #diving}. Always paired with {@link #endDive}, in a finally. */
	public void beginDive(boolean headFirst) {
		diving = true;
		divingHeadFirst = headFirst;
	}

	public void endDive() {
		diving = false;
		divingHeadFirst = false;
	}

	public boolean knockedDown() {
		return knockdownTicks > 0;
	}

	public int knockdownTicks() {
		return knockdownTicks;
	}

	/** Never shortens an existing knockdown: the worse of two landings wins. */
	public void knockDown(int ticks) {
		knockdownTicks = Math.max(knockdownTicks, ticks);
	}

	public int knockdownRecoveryTicks() {
		return knockdownRecoveryTicks;
	}

	/** Ticks the knockdown down, rolling into the unsteady tail as it ends. */
	public void tickKnockdown(int recoveryTicks) {
		if (knockdownTicks > 0) {
			knockdownTicks--;
			if (knockdownTicks == 0) {
				knockdownRecoveryTicks = recoveryTicks;
			}
		} else if (knockdownRecoveryTicks > 0) {
			knockdownRecoveryTicks--;
		}
	}

	public void clearKnockdown() {
		knockdownTicks = 0;
		knockdownRecoveryTicks = 0;
	}

	public boolean microsleeping() {
		return microsleepTicks > 0;
	}

	public int microsleepTicks() {
		return microsleepTicks;
	}

	public double microsleepPhase() {
		return microsleepPhase;
	}

	public void setMicrosleepPhase(double phase) {
		microsleepPhase = phase;
	}

	public void beginMicrosleep(int ticks) {
		microsleepTicks = Math.max(microsleepTicks, ticks);
	}

	public void tickMicrosleep() {
		if (microsleepTicks > 0) {
			microsleepTicks--;
		}
	}

	public void clearMicrosleep() {
		microsleepTicks = 0;
		microsleepPhase = 0.0;
	}

	// --- Injuries ---

	public boolean concussion() {
		return concussionSeverity > 0.0;
	}

	/**
	 * Kept for everything that only knows "concussed or not": true starts a
	 * concussion of the default (moderate) severity unless one is already worse,
	 * false clears it and everything it was doing.
	 */
	public void setConcussion(boolean value) {
		if (value) {
			concussionSeverity = Math.max(concussionSeverity, Balance.CONCUSSION_DEFAULT_SEVERITY);
		} else {
			clearConcussion();
		}
	}

	public double concussionSeverity() {
		return concussionSeverity;
	}

	public void setConcussionSeverity(double severity) {
		concussionSeverity = ConcussionMath.clampSeverity(severity);
		if (concussionSeverity <= 0.0) {
			clearConcussion();
		}
	}

	/** 0 none, 1 mild, 2 moderate, 3 severe. A band, never a figure. */
	public int concussionGrade() {
		return ConcussionMath.grade(concussionSeverity);
	}

	public void clearConcussion() {
		concussionSeverity = 0.0;
		concussionFlare = 0.0;
		vertigoPhase = 0.0;
		vertigoTicks = 0;
		retchTicks = 0;
	}

	public double concussionFlare() {
		return concussionFlare;
	}

	public void setConcussionFlare(double flare) {
		concussionFlare = Math.clamp(flare, 0.0, Balance.FLARE_MAX);
	}

	public double vertigoPhase() {
		return vertigoPhase;
	}

	public void setVertigoPhase(double phase) {
		vertigoPhase = phase;
	}

	public boolean vertigo() {
		return vertigoTicks > 0;
	}

	public int vertigoTicks() {
		return vertigoTicks;
	}

	/** Starts a spell; returns its direction (true = the room turns left). */
	public boolean beginVertigo(int ticks) {
		vertigoTicks = Math.max(vertigoTicks, ticks);
		vertigoCount++;
		return vertigoLeft();
	}

	/** Which way the room is turning: alternates spell by spell. */
	public boolean vertigoLeft() {
		return (vertigoCount & 1) == 1;
	}

	public void tickVertigo() {
		if (vertigoTicks > 0) {
			vertigoTicks--;
		}
	}

	public boolean retching() {
		return retchTicks > 0;
	}

	public void beginRetch(int ticks) {
		retchTicks = Math.max(retchTicks, ticks);
	}

	public void tickRetch() {
		if (retchTicks > 0) {
			retchTicks--;
		}
	}

	// --- Hypoxia (1.7) ---

	public double hypoxia() {
		return hypoxia;
	}

	public void setHypoxia(double seconds) {
		hypoxia = Math.clamp(seconds, 0.0, Balance.HYPOXIA_DEBT_MAX);
	}

	// --- Adrenaline (1.7) ---

	public AdrenalineMath.State adrenaline() {
		return adrenaline;
	}

	/** Horizontal distance moved since the last call, and remembers this position. */
	public double moveSinceLastTick(double x, double z) {
		double moved = Double.isNaN(lastSeenX) ? 0.0 : Math.hypot(x - lastSeenX, z - lastSeenZ);
		lastSeenX = x;
		lastSeenZ = z;
		return moved;
	}

	/** Jumps made since the last call, given the running total; 0 on the first call. */
	public int jumpsSinceLastTick(int total) {
		int jumps = lastJumpCount < 0 ? 0 : Math.max(0, total - lastJumpCount);
		lastJumpCount = total;
		return jumps;
	}

	public long lastExertion() {
		return lastExertion;
	}

	public void markExertion(long gameTime) {
		lastExertion = gameTime;
	}

	/** True when a grip refusal may be announced again; stamps the time if so. */
	public boolean claimGripMessage(long gameTime) {
		if (gameTime - lastGripMessage < Balance.GRIP_MESSAGE_COOLDOWN_TICKS) {
			return false;
		}
		lastGripMessage = gameTime;
		return true;
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
	 * Advances the deterministic item-use stall accumulator by {@code penalty} and
	 * returns how many use ticks to stall this tick. A penalty of 0.3 makes bow
	 * draws take ~1/0.7x longer, smoothly and reproducibly.
	 *
	 * <p>Returns a count rather than a flag (1.4) because eating needed to get
	 * genuinely slow, and one stalled tick per tick caps the slowdown at exactly
	 * two-fold — enough for an injured arm on a bowstring, nowhere near enough
	 * for a man trying to get bread into himself while greying out.
	 */
	public int advanceUseStall(double penalty) {
		useStallProgress += penalty;
		int whole = (int) useStallProgress;
		useStallProgress -= whole;
		return whole;
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

	/**
	 * Full reset to a freshly-spawned body (spec §46; debug heal §54). Ceilings go
	 * too: this is the pristine-body primitive behind {@code /rdmg heal} and the gametests'
	 * TestPlayer, not the death path — that is {@code RespawnCarryover}.
	 */
	public void reset() {
		for (BodyPart part : BodyPart.values()) {
			BodyPartData data = parts.get(part);
			data.setDestroyed(false);
			// Before setCondition, which clamps to whatever the ceiling still is.
			data.restoreCeiling();
			data.setCondition(part.maxCondition());
			data.setBroken(false);
			data.strain(0);
			// Last: a full restore forgets the history too. Nothing above may
			// re-raise it, which is why this is the final statement and not the
			// tidiest-looking one.
			data.clearScar();
		}
		wounds.clear();
		blood = Balance.BLOOD_MAX;
		consciousness = Balance.CONSCIOUSNESS_MAX;
		unconscious = false;
		unconsciousTicks = 0;
		clearConcussion();
		hypoxia = 0.0;
		adrenaline.reset();
		burned = false;
		clearKnockdown();
		clearMicrosleep();
		clearPendingFall();
		clearPendingExplosion();
		clearWhiplash();
		neckBroken = false;
	}

	/**
	 * The body a player is reborn into (1.6). Everything is forgotten — condition,
	 * fractures, wounds, blood, statuses, pending state — except the marks.
	 *
	 * <p>Scars always: a scar that died with the body would make "once lost" a
	 * mark nobody could ever see, because a destroyed limb never heals inside one
	 * life. Ceilings only under the hardcore toggle, and each carried part starts
	 * AT its ceiling — full, for the body it now is.
	 *
	 * <p>Forget by default, carry what is listed. Pure, so a unit test can prove
	 * nothing else leaks across a death.
	 */
	public static RealisticDamageData freshCarrying(RealisticDamageData old, boolean keepCeilings) {
		RealisticDamageData fresh = new RealisticDamageData();
		for (BodyPart part : BodyPart.values()) {
			// Unconditional, and deliberately outside the toggle below: the two
			// carries answer different questions, and one of them is not a setting.
			fresh.part(part).markScar(old.part(part).scar());
			if (keepCeilings) {
				fresh.part(part).lowerCeilingTo(old.part(part).ceilingBand());
			}
		}
		return fresh;
	}

	/**
	 * Totem stabilization (spec §41): floors, not a full reset. Injury statuses
	 * persist, and so do ceilings — a totem is a rescue, not a cure. The floor is
	 * written through setCondition, which since 1.6 clamps to the ceiling, but the
	 * clamp never binds: the deepest legal ceiling is 0.25 of max and the totem
	 * floor is {@link Balance#TOTEM_CONDITION_FLOOR_FRACTION} 0.15.
	 */
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
		// A totem that leaves you face-down and blacking out has not saved you.
		clearKnockdown();
		clearMicrosleep();
	}
}
