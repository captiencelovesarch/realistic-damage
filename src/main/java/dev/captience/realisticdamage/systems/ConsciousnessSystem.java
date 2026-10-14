package dev.captience.realisticdamage.systems;

import net.minecraft.world.entity.LivingEntity;
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
		return data.microsleeping() || data.knockedDown() || data.retching();
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
		if (data.microsleeping() || data.knockedDown() || data.retching()) {
			return true;
		}
		return effective(data) < Balance.SYMPTOM_FEEBLE;
	}

	/** Consciousness as the body experiences it, concussion grade and flare included. */
	public static double effective(RealisticDamageData data) {
		return Vitals.effectiveConsciousness(data.consciousness(), data.concussionGrade(), data.concussionFlare());
	}

	/**
	 * A hand that cannot close: destroyed (Arcade preset), or on an arm driven to
	 * Disabled (1.7). Says why on the action bar, now and then, so that a use
	 * that silently does nothing never reads as a bug.
	 */
	private static boolean handGone(Player player, net.minecraft.world.InteractionHand hand) {
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return false;
		}
		net.minecraft.world.InteractionHand blocked = ImpairmentSystem.gripBlocked(serverPlayer, hand);
		if (blocked == null) {
			return false;
		}
		RealisticDamageData data = RDAttachments.get(serverPlayer);
		if (data.claimGripMessage(serverPlayer.level().getGameTime())) {
			BodyPart arm = ImpairmentSystem.armFor(serverPlayer, blocked == net.minecraft.world.InteractionHand.MAIN_HAND);
			Notifier.message(serverPlayer, blocked == hand
					? "realisticdamage.msg.grip." + arm.id()
					: "realisticdamage.msg.grip_two_handed." + arm.id());
		}
		return true;
	}

	private static boolean locked(Player player) {
		return player instanceof ServerPlayer serverPlayer
				&& !serverPlayer.isCreative() && !serverPlayer.isSpectator()
				&& RDAttachments.get(serverPlayer).unconscious();
	}

	public static void tick(LivingEntity player, RealisticDamageData data) {
		ServerConfig config = ServerConfig.get();
		if (!config.enableConsciousness) {
			if (data.unconscious()) {
				wake(player, data);
			}
			data.clearMicrosleep();
			return;
		}

		double drain = bloodDrainPerSecond(data.blood());
		// Out of air (1.7): drowning, or a head inside a block. The drain climbs
		// with the debt, so you black out before the drowning damage kills you —
		// and once you breathe again the debt has to be paid back before the
		// mind comes back properly.
		if (starved(player)) {
			data.setHypoxia(data.hypoxia() + 1.0 / 20.0);
			drain += HypoxiaMath.drainPerSecond(data.hypoxia());
		} else if (data.hypoxia() > 0.0) {
			data.setHypoxia(data.hypoxia() - Balance.HYPOXIA_REPAY_PER_SEC / 20.0);
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
			recovery *= HypoxiaMath.recoveryFactor(data.hypoxia());
			data.restoreConsciousness(recovery * bloodFactor / 20.0);
		}

		if (player instanceof ServerPlayer serverPlayer) {
			// The two that only make sense for a player: mobs noticing a body,
			// and the stutter before the stop. A mob that is out is simply out.
			if (data.unconscious()) {
				keepMobsInterested(serverPlayer);
			}
			tickMicrosleep(serverPlayer, data);
		} else {
			data.clearMicrosleep();
		}

		if (data.unconscious()
				&& data.unconsciousTicks() >= Balance.MIN_UNCONSCIOUS_TICKS
				&& data.consciousness() >= Balance.WAKE_THRESHOLD) {
			wake(player, data);
		}
	}

	/**
	 * No air reaching the lungs: under water with the air bar empty, or with the
	 * head inside a solid block. Creatures that breathe water never run dry, so
	 * the first test already excludes them.
	 */
	public static boolean starved(LivingEntity entity) {
		return entity.getAirSupply() <= 0 || entity.isInWall();
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
	public static void onTrauma(LivingEntity player, RealisticDamageData data, ClassifiedDamage classified,
			DamageProfile profile, List<TraumaPipeline.AppliedTrauma> traumas, double appliedDamage,
			boolean limbLost) {
		ServerConfig config = ServerConfig.get();
		if (!config.enableConsciousness) {
			return;
		}
		double drain = 0.0;
		if (appliedDamage > 0.0) {
			ConcussionSystem.onHitTaken(player, data);
		}
		// The concussion is judged once per event, on the worst candidate (1.7).
		// It used to be asked per candidate, which was harmless while "already
		// concussed" meant "nothing more to do" — with second impact it would
		// have counted one blow twice.
		double headMax = 0.0;
		double impact = 0.0;
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
				headMax = Math.max(headMax, trauma.conditionDamage());
			} else {
				drain += trauma.conditionDamage() * profile.consciousnessFactor()
						* Balance.CONSCIOUSNESS_BODY_HIT_SCALE * (trauma.primary() ? 1.0 : 0.5);
			}
		}

		// Hard impacts jolt the whole body, not just whichever region took the hit.
		if (profile.concussionCapable()) {
			drain += appliedDamage * profile.consciousnessFactor() * Balance.CONSCIOUSNESS_IMPACT_SCALE;
			impact = appliedDamage * profile.consciousnessFactor();
		}
		maybeConcuss(player, data, profile, headMax, impact, limbLost);
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
	private static void maybeConcuss(LivingEntity player, RealisticDamageData data, DamageProfile profile,
			double headHit, double impact, boolean limbLost) {
		if (!profile.concussionCapable()) {
			return;
		}
		if (data.concussion()) {
			// A second impact is a blow to the head. A heavy blow anywhere else
			// still rattles it, at half weight and only past the bar a fresh
			// concussion would need — otherwise any zombie's punch to the ribs
			// would floor a concussed player.
			double jolt = impact >= Balance.CONCUSSION_HEAD_TRAUMA_THRESHOLD ? impact * 0.5 : 0.0;
			secondImpact(player, data, Math.max(headHit, jolt), limbLost);
			return;
		}
		double headTrauma = Math.max(headHit, impact);
		boolean massive = headTrauma >= Balance.CONCUSSION_HEAD_TRAUMA_THRESHOLD;
		boolean compounded = headTrauma >= Balance.CONCUSSION_HEAD_TRAUMA_INJURED_THRESHOLD
				&& data.part(BodyPart.HEAD).state().isAtLeastAsBadAs(BodyState.INJURED);
		if (massive || compounded) {
			data.setConcussionSeverity(ConcussionMath.freshSeverity(headTrauma));
			if (!limbLost) {
				Notifier.message(player, "realisticdamage.msg.concussion." + data.concussionGrade());
			}
		}
	}

	/**
	 * Second impact (1.7): a brain that has not healed from the last knock takes
	 * the next one far worse. Any concussive head hit worth {@link
	 * Balance#SECOND_IMPACT_MIN_TRAUMA} deepens the concussion and flares it on
	 * the spot, and past a bar that drops as the grade rises it puts you
	 * straight out — a severe concussion is one solid tap from the floor.
	 */
	private static void secondImpact(LivingEntity player, RealisticDamageData data, double headTrauma,
			boolean limbLost) {
		if (headTrauma < Balance.SECOND_IMPACT_MIN_TRAUMA) {
			return;
		}
		int before = data.concussionGrade();
		boolean knockout = ConcussionMath.secondImpactKnocksOut(before, headTrauma);
		data.setConcussionSeverity(data.concussionSeverity() + ConcussionMath.secondImpactSeverity(headTrauma));
		data.setConcussionFlare(data.concussionFlare() + Balance.SECOND_IMPACT_FLARE);
		if (knockout && !data.unconscious()) {
			data.drainConsciousness(data.consciousness());
			collapse(player, data);
			return;   // "everything goes black" is the line; collapse already sent it
		}
		if (!limbLost && data.concussionGrade() > before) {
			Notifier.message(player, "realisticdamage.msg.second_impact");
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

	private static void collapse(LivingEntity player, RealisticDamageData data) {
		data.setUnconscious(true);
		if (player.isSprinting()) {
			player.setSprinting(false);
		}
		player.stopUsingItem();
		// Going all the way out supersedes blinking out: leaving the flag set
		// would have the client fading a blackout in underneath a blackout.
		data.clearMicrosleep();
		// A mob that is out stops thinking (1.7); its AI comes back with it.
		MobAnatomy.collapse(player);
		Notifier.message(player, "realisticdamage.msg.unconscious");
		Notifier.injurySound(player, dev.captience.realisticdamage.net.InjurySound.COLLAPSE);
	}

	private static void wake(LivingEntity player, RealisticDamageData data) {
		data.setUnconscious(false);
		MobAnatomy.wake(player);
		// You come round at WAKE_THRESHOLD, which is below the microsleep line —
		// so without clearing the phase you would blink straight back out on the
		// strength of time spent unconscious.
		data.clearMicrosleep();
		Notifier.message(player, "realisticdamage.msg.wake");
	}
}
