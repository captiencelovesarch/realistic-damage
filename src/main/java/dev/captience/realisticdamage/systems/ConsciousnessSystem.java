package dev.captience.realisticdamage.systems;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.Vitals;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.ClassifiedDamage;
import dev.captience.realisticdamage.damage.DamageProfile;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * The hidden Consciousness meter (spec §19-25): event hits from trauma, a
 * continuous tick coupling to Blood and air, quick stable recovery, and the
 * collapse/wake cycle with hysteresis. While unconscious, voluntary actions are
 * vetoed server-side; the player can still look around and chat.
 */
public final class ConsciousnessSystem {
	private ConsciousnessSystem() {}

	public static void init() {
		// Voluntary-action locks while unconscious (spec §25).
		// Mining and swinging stay possible with a destroyed main arm — you work
		// one-handed, very slowly (see ImpairmentSystem). Only item *use* is
		// genuinely impossible without that hand.
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				locked(player) || tooFeeble(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				locked(player) || tooFeeble(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				locked(player) || handGone(player, hand) || absent(player)
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
				locked(player) || handGone(player, hand) || absent(player)
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				locked(player) || handGone(player, hand) || absent(player)
						? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!locked(player) && !tooFeeble(player));
	}

	/**
	 * Blacked out for the moment, or face-down after a landing: the body is
	 * present but the player is not driving it. Distinct from {@link #locked}
	 * because this is measured in fractions of a second and recovers on its own —
	 * the point is that collapse arrives as a stutter you can feel coming rather
	 * than a switch that flips once.
	 */
	private static boolean absent(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)
				|| serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return false;
		}
		RealisticDamageData data = RDAttachments.get(serverPlayer);
		return data.microsleeping() || data.knockedDown();
	}

	/**
	 * Past the bottom of the ladder your arms will not answer at all. Everything
	 * above {@link Balance#SYMPTOM_FEEBLE} is merely slow (see {@link
	 * ImpairmentSystem#sluggishPenalty}); this is the floor under the slowness,
	 * so that "about to lose consciousness" stops meaning "mining at 70%".
	 */
	private static boolean tooFeeble(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer)
				|| serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return false;
		}
		if (!ServerConfig.get().enableConsciousness) {
			return false;
		}
		RealisticDamageData data = RDAttachments.get(serverPlayer);
		if (data.microsleeping() || data.knockedDown()) {
			return true;
		}
		return effective(data) < Balance.SYMPTOM_FEEBLE;
	}

	/** Consciousness as the body experiences it, concussion shift included. */
	public static double effective(RealisticDamageData data) {
		return Vitals.effectiveConsciousness(data.consciousness(), data.concussion());
	}

	/** A destroyed arm's hand simply does not work any more (Arcade preset). */
	private static boolean handGone(Player player, net.minecraft.world.InteractionHand hand) {
		return player instanceof ServerPlayer serverPlayer
				&& ServerConfig.get().enableLimbDestruction
				&& ImpairmentSystem.handDisabled(serverPlayer, hand);
	}

	private static boolean locked(Player player) {
		return player instanceof ServerPlayer serverPlayer
				&& !serverPlayer.isCreative() && !serverPlayer.isSpectator()
				&& RDAttachments.get(serverPlayer).unconscious();
	}

	public static void tick(ServerPlayer player, RealisticDamageData data) {
		ServerConfig config = ServerConfig.get();
		if (!config.enableConsciousness) {
			if (data.unconscious()) {
				wake(player, data);
			}
			data.clearMicrosleep();
			return;
		}

		double drain = bloodDrainPerSecond(data.blood());
		if (player.getAirSupply() <= 0) {
			drain += Balance.CONSCIOUSNESS_DRAIN_ASPHYXIA;
		}
		if (drain > 0.0) {
			data.drainConsciousness(drain * config.consciousnessLossMultiplier / 20.0);
		}

		// Collapse is checked before recovery so a hit-to-zero never "outheals"
		// the check within the same tick.
		if (!data.unconscious() && data.consciousness() <= 0.0) {
			collapse(player, data);
		}

		if (drain <= 0.0) {
			// Stable: recover quickly (spec §22) — slower with concussion or low blood.
			double recovery = Balance.CONSCIOUSNESS_RECOVERY_PER_SEC;
			if (data.concussion()) {
				recovery *= Balance.CONCUSSION_RECOVERY_FACTOR;
			}
			double bloodFactor = Balance.CONSCIOUSNESS_RECOVERY_BLOOD_FLOOR
					+ (1.0 - Balance.CONSCIOUSNESS_RECOVERY_BLOOD_FLOOR) * (data.blood() / Balance.BLOOD_MAX);
			data.restoreConsciousness(recovery * bloodFactor / 20.0);
		}

		if (data.unconscious()) {
			keepMobsInterested(player);
		}

		tickMicrosleep(player, data);

		if (data.unconscious()
				&& data.unconsciousTicks() >= Balance.MIN_UNCONSCIOUS_TICKS
				&& data.consciousness() >= Balance.WAKE_THRESHOLD) {
			wake(player, data);
		}
	}

	/**
	 * The stutter before the stop (1.4). Consciousness does not dim smoothly to
	 * nothing; it drops out in episodes that get longer and closer together, and
	 * those episodes are the warning that the meter itself cannot give you
	 * without being displayed.
	 *
	 * <p>The cadence is a phase accumulator, not a die roll: advance by one
	 * period's worth each tick and black out when it wraps. Two identical
	 * descents therefore produce identical blackouts, which is the same promise
	 * the mod makes about which leg breaks.
	 */
	private static void tickMicrosleep(ServerPlayer player, RealisticDamageData data) {
		if (!ServerConfig.get().enableMicrosleeps) {
			data.clearMicrosleep();
			return;
		}
		if (data.microsleeping()) {
			data.tickMicrosleep();
			return;
		}
		// Being fully out supersedes blinking out; so does simply being well
		// enough. In both cases the phase unwinds so that dipping back under the
		// threshold does not instantly black you out on the strength of credit
		// accumulated on the way down.
		double effective = effective(data);
		if (data.unconscious() || effective >= Balance.SYMPTOM_MICROSLEEP) {
			data.setMicrosleepPhase(Math.max(0.0, data.microsleepPhase() - 0.02));
			return;
		}
		double phase = data.microsleepPhase() + 1.0 / Vitals.microsleepPeriod(effective);
		if (phase < 1.0) {
			data.setMicrosleepPhase(phase);
			return;
		}
		data.setMicrosleepPhase(phase - 1.0);
		data.beginMicrosleep(Vitals.microsleepDuration(effective));
		if (player.isSprinting()) {
			player.setSprinting(false);
		}
	}

	/**
	 * A body on the floor does not stop being prey. How much attention it draws
	 * is a server setting (see {@link ServerConfig.UnconsciousAggro}): by default
	 * anything already fighting you carries on, and anything close enough to
	 * trip over you acquires you, but distant mobs do not come running.
	 *
	 * <p>Only hostile mobs, only ones that can actually see you, and only every
	 * half second so it costs nothing.
	 *
	 * <p>The push goes through {@code MobTargetMixin} like every other
	 * acquisition, so it can never land on a target the composed policy would
	 * refuse, and a body on the floor outranks any limper by the prey constants
	 * alone (1.6) — nothing this method pushes can be talked out of it by
	 * somebody nearby with a bad leg. A bypass would be worse than useless: it
	 * would let NEARBY and ALL force a mob off a <em>more</em> wounded target.
	 */
	private static void keepMobsInterested(ServerPlayer player) {
		if (player.tickCount % 10 != 0) {
			return;
		}
		ServerConfig.UnconsciousAggro mode = ServerConfig.get().unconsciousAggro;
		if (mode == ServerConfig.UnconsciousAggro.NONE) {
			// MobTargetMixin blocks re-acquisition, so one sweep is enough to
			// clear the slate and it stays cleared.
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
					player.getBoundingBox().inflate(Balance.UNCONSCIOUS_AGGRO_RADIUS),
					candidate -> candidate instanceof Enemy && candidate.getTarget() == player)) {
				mob.setTarget(null);
			}
			return;
		}
		if (mode == ServerConfig.UnconsciousAggro.ATTACKERS) {
			// Nothing to push: the mixin already refuses every new acquisition,
			// and whatever was engaged stays engaged.
			return;
		}
		// NEARBY and ALL still actively pull mobs in, so that a body on the floor
		// is noticed rather than merely allowed to be noticed.
		double radius = mode == ServerConfig.UnconsciousAggro.ALL
				? Balance.UNCONSCIOUS_AGGRO_RADIUS
				: Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS;
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(radius),
				candidate -> candidate instanceof Enemy && candidate.isAlive()
						&& candidate.getTarget() != player)) {
			if (mob.getSensing().hasLineOfSight(player)) {
				mob.setTarget(player);
			}
		}
	}

	/**
	 * Event-driven consciousness consequences of one damage event (called by the
	 * pipeline).
	 *
	 * @param limbLost a limb came off earlier in this same event, so the sentence
	 *                 announcing that keeps the frame and the concussion line is
	 *                 held back. Only the line: the concussion itself is set, the
	 *                 status icon and the body panel say so, and every
	 *                 consciousness effect runs exactly as it would have. This is
	 *                 the same rule the bleeding escalation line already follows
	 *                 in {@code createWounds}, for the same reason — {@code
	 *                 sendOverlayMessage} means the last line written is the only
	 *                 one read, and losing a limb is never the smaller piece of
	 *                 news. It matters far more here: this system runs last in
	 *                 the event, and any blast worth a limb is worth well over
	 *                 the 6.0 concussion bar, so without this the line that makes
	 *                 distance, cover and Blast Protection learnable reaches the
	 *                 player essentially never.
	 */
	public static void onTrauma(ServerPlayer player, RealisticDamageData data, ClassifiedDamage classified,
			DamageProfile profile, List<TraumaPipeline.AppliedTrauma> traumas, double appliedDamage,
			boolean limbLost) {
		ServerConfig config = ServerConfig.get();
		if (!config.enableConsciousness) {
			return;
		}
		double drain = 0.0;
		for (TraumaPipeline.AppliedTrauma trauma : traumas) {
			if (trauma.part() == BodyPart.HEAD) {
				// Head trauma is head trauma whether or not it was the primary
				// region. A long fall routes most damage to the legs, but the head
				// it does deliver still rattles your brain — without this, falls
				// could never knock anyone out.
				double weight = trauma.primary() ? 1.0 : Balance.CONSCIOUSNESS_SECONDARY_HEAD_WEIGHT;
				double hit = trauma.conditionDamage() * profile.consciousnessFactor()
						* Balance.CONSCIOUSNESS_HEAD_HIT_SCALE * weight;
				if (data.concussion()) {
					hit *= Balance.CONCUSSION_EXTRA_HEAD_DRAIN;
				}
				drain += hit;
				maybeConcuss(player, data, profile, trauma.conditionDamage(), limbLost);
			} else {
				drain += trauma.conditionDamage() * profile.consciousnessFactor()
						* Balance.CONSCIOUSNESS_BODY_HIT_SCALE * (trauma.primary() ? 1.0 : 0.5);
			}
		}

		// Hard impacts jolt the whole body, not just whichever region took the hit.
		if (profile.concussionCapable()) {
			drain += appliedDamage * profile.consciousnessFactor() * Balance.CONSCIOUSNESS_IMPACT_SCALE;
			maybeConcuss(player, data, profile, appliedDamage * profile.consciousnessFactor(), limbLost);
		}
		if (classified.routing() == ClassifiedDamage.Routing.NONE) {
			// Sonic boom, drowning ticks: pure consciousness pressure, no limb trauma.
			drain += appliedDamage * profile.consciousnessFactor() * Balance.CONSCIOUSNESS_BODY_HIT_SCALE;
		}
		if (drain > 0.0) {
			data.drainConsciousness(drain * config.consciousnessLossMultiplier);
			if (!data.unconscious() && data.consciousness() <= 0.0) {
				collapse(player, data);
			}
		}
	}

	/**
	 * Deterministic concussion (spec §21): big single head trauma, or meaningful
	 * trauma on an already-battered head.
	 *
	 * @param limbLost suppresses the announcement and nothing else. The flag is
	 *                 read only around {@code Notifier.message}: the state is set
	 *                 first and unconditionally, so a player who loses a leg to a
	 *                 blast that also rattles their skull is concussed in every
	 *                 way the mod can express except the one sentence the frame
	 *                 has room for.
	 */
	private static void maybeConcuss(ServerPlayer player, RealisticDamageData data, DamageProfile profile,
			double headTrauma, boolean limbLost) {
		if (data.concussion() || !profile.concussionCapable()) {
			return;
		}
		boolean massive = headTrauma >= Balance.CONCUSSION_HEAD_TRAUMA_THRESHOLD;
		boolean compounded = headTrauma >= Balance.CONCUSSION_HEAD_TRAUMA_INJURED_THRESHOLD
				&& data.part(BodyPart.HEAD).state().isAtLeastAsBadAs(BodyState.INJURED);
		if (massive || compounded) {
			data.setConcussion(true);
			if (!limbLost) {
				Notifier.message(player, "realisticdamage.msg.concussion");
			}
		}
	}

	private static double bloodDrainPerSecond(double blood) {
		if (blood <= 0.0) {
			return Balance.CONSCIOUSNESS_DRAIN_BLOOD_ZERO;
		}
		if (blood < Balance.BLOOD_CRITICAL) {
			return Balance.CONSCIOUSNESS_DRAIN_BLOOD_CRITICAL;
		}
		if (blood < Balance.BLOOD_SERIOUS) {
			return Balance.CONSCIOUSNESS_DRAIN_BLOOD_SERIOUS;
		}
		if (blood < Balance.BLOOD_MILD) {
			return Balance.CONSCIOUSNESS_DRAIN_BLOOD_MILD;
		}
		return 0.0;
	}

	private static void collapse(ServerPlayer player, RealisticDamageData data) {
		data.setUnconscious(true);
		if (player.isSprinting()) {
			player.setSprinting(false);
		}
		player.stopUsingItem();
		// Going all the way out supersedes blinking out: leaving the flag set
		// would have the client fading a blackout in underneath a blackout.
		data.clearMicrosleep();
		Notifier.message(player, "realisticdamage.msg.unconscious");
		Notifier.injurySound(player, dev.captience.realisticdamage.net.InjurySound.COLLAPSE);
	}

	private static void wake(ServerPlayer player, RealisticDamageData data) {
		data.setUnconscious(false);
		// You come round at WAKE_THRESHOLD, which is below the microsleep line —
		// so without clearing the phase you would blink straight back out on the
		// strength of time spent unconscious.
		data.clearMicrosleep();
		Notifier.message(player, "realisticdamage.msg.wake");
	}
}
