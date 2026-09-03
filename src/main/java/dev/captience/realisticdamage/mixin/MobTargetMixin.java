package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.systems.PreyMath;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resolves every target acquisition at the one point they all pass through.
 *
 * <p>Two policies live here, in one injection so their order is explicit:
 * {@link ServerConfig.UnconsciousAggro} (1.1), which may refuse a body on the
 * floor, and {@link ServerConfig.PreyOnWounded} (1.6), which may put a more
 * visibly hurt player into the slot than the one vanilla asked for.
 *
 * <p>Neither can be done by clearing or pushing targets on a timer. Vanilla's
 * goals re-acquire the nearest player from up to the mob's follow range — 35
 * blocks for a zombie, far outside either aggro radius — on their own schedule,
 * so a mod that only clears is fighting the goal for the slot and losing about
 * half the time. And a veto cannot express a preference:
 * {@code NearestAttackableTargetGoal.start()} never records its pick in
 * {@code TargetGoal.targetMob}, so a refused acquisition leaves the goal
 * target-less and it simply asks for the same nearest player again. Vanilla only
 * ever asks about the nearest, so the wounded player is never named and refusing
 * can never name them. The only way to say "not that one, this one" is to write
 * this one into the slot. Because {@code TargetGoal.canContinueToUse} re-asserts
 * its target through this same call every other tick, the substitution is
 * re-decided continuously and an engaged mob drifts to the limper the moment one
 * comes into view — enforcement, not a timer of ours to lose a race with.
 *
 * <p>The write is exactly {@code Mob.setTarget}'s own body with a different
 * argument: {@code this.target = this.asValidTarget(best)}, then cancel. Never a
 * re-entrant {@code setTarget} call — {@code EnderMan.setTarget} would then run
 * twice with two different arguments, and the re-entrancy flag it would need is
 * easy to leave set on an exception path.
 *
 * <p>Scope: the 1.1 veto applies to every {@link Mob}, because a refusal can
 * only ever reduce aggression. The 1.6 substitution applies only to
 * {@link Enemy}, because it can invent it — a tamed wolf avenging its owner must
 * go for the player who hit them, not for the limping bystander behind them. A
 * {@link NeutralMob} is only ever redirected onto somebody it is already angry
 * at, since its own anger selector lives in the goal's targeting conditions and
 * this call has no access to it; those are two different fixes, and endermen and
 * zombified piglins are both {@code Enemy} and {@code NeutralMob}, so a reviewer
 * who sees one and assumes it covers the other will delete the wrong line.
 *
 * <p><b>Brain-driven hostiles are out of scope, and always were.</b> Piglins,
 * piglin brutes, hoglins, zoglins, wardens, breezes and creakings read their
 * target out of {@code MemoryModuleType.ATTACK_TARGET} through
 * {@code getTargetFromBrain()} and never call this method, so neither aggro
 * setting has ever touched them. Biasing their sensors instead would reach
 * acquisition only — {@code StartAttacking} runs only while the memory is absent
 * — which is the "not enforcement" objection that ruled out re-ranking vanilla's
 * own target search. An honest exclusion beats a third category called
 * "partially covered".
 *
 * <p>Cost: score before geometry, line of sight last. A candidate that cannot
 * out-score the requested player is dropped before any bounding-box test or
 * raycast, so a server where nobody is visibly hurt performs no raycasts at all
 * and pays one attachment lookup and a little integer arithmetic per player per
 * resolve; a hurt player costs at most one raycast per engaged hostile per two
 * ticks, and {@code Sensing} caches that per tick anyway. There is deliberately
 * no per-mob memo: the ordering is the cost bound, and a memo would hold a
 * strong reference to a player the mob has finished with while answering from a
 * scan that a mid-tick blow has already made wrong.
 *
 * <p>Deliberate non-behaviours: {@code setTarget(null)} always clears; a
 * non-player request (golem, villager) is never substituted; a request refused
 * by the unconscious policy is cancelled and never replaced with a lesser
 * candidate, because NONE and ATTACKERS mean "hostiles lose interest in you",
 * not "hostiles pick someone else". {@code EnderMan.setTarget} inspects its own
 * argument before calling super, so under substitution its "creepy" state
 * follows the requested player while the slot holds the substitute; both are
 * non-null players, so the visible result is identical — but do not assume
 * {@code getTarget() == argument} inside an override.
 */
@Mixin(Mob.class)
public abstract class MobTargetMixin {
	@Shadow private @Nullable LivingEntity target;

	@Shadow protected abstract @Nullable LivingEntity asValidTarget(@Nullable LivingEntity target);

	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$resolveTarget(@Nullable LivingEntity requested, CallbackInfo ci) {
		// Clearing always clears, and mob-versus-mob is vanilla's business.
		// TargetGoal.stop() and NeutralMob.stopBeingAngry both depend on the first
		// half of that, so it is the very first thing here.
		if (!(requested instanceof ServerPlayer player)) {
			return;
		}
		Mob self = (Mob) (Object) this;
		if (!(self.level() instanceof ServerLevel level)) {
			return;
		}

		ServerConfig config = ServerConfig.get();
		RealisticDamageData requestedData = RDAttachments.get(player);
		boolean engaged = self.getTargetUnchecked() == player;

		// The 1.1 veto, unchanged in meaning and still applied to every Mob. The
		// raw slot rather than getTarget(): identical for a survival player, and
		// the honest answer to "was this mob already fighting you", which is the
		// whole promise of ATTACKERS.
		if (requestedData.unconscious()
				&& !config.unconsciousAggro.allowsTargeting(engaged, self.distanceTo(player))) {
			ci.cancel();  // slot untouched, and we never shop for a replacement
			return;
		}

		ServerConfig.PreyOnWounded mode = config.preyOnWounded;
		if (mode == ServerConfig.PreyOnWounded.OFF || !(self instanceof Enemy)) {
			return;  // exactly the 1.5 method from here
		}

		int threshold = PreyMath.threshold(PreyMath.score(requestedData, mode), engaged,
				self.getLastHurtByMob() == player);
		ServerPlayer best = realisticdamage$preferredPrey(self, level, player, threshold, mode, config);
		if (best == null) {
			return;  // vanilla writes the requested player
		}
		LivingEntity resolved = this.asValidTarget(best);
		if (resolved == null) {
			return;  // never empty the slot as a side effect of preferring
		}
		this.target = resolved;  // exactly Mob.setTarget's body, with the substitute
		ci.cancel();
	}

	/**
	 * The best-scoring player, other than the requested one, who strictly beats
	 * {@code threshold}, is inside this mob's follow-range box, is somebody a
	 * neutral mob is already angry at, is permitted by the unconscious policy,
	 * and passes vanilla's own combat targeting test. Ties go to the nearer
	 * player and then to the lower entity id, so the answer is a pure function of
	 * world state and two equally hurt players can never make a zombie dither.
	 * Null when nobody qualifies.
	 *
	 * <p>Score first, geometry second, line of sight last: the raycast is the
	 * only expensive step by an order of magnitude, and it is only ever paid for
	 * a player who could actually win. A candidate that has already lost the tie
	 * is skipped before it too. If this is ever "tidied" into
	 * {@code getNearbyPlayers(conditions, self, box)} first, every engaged mob
	 * raycasts every in-box player every two ticks regardless of injuries, and
	 * the worst case becomes the typical one.
	 */
	@Unique
	private @Nullable ServerPlayer realisticdamage$preferredPrey(Mob self, ServerLevel level,
			ServerPlayer requested, int threshold, ServerConfig.PreyOnWounded mode, ServerConfig config) {
		ServerPlayer best = null;
		int bestScore = threshold;
		double bestDistanceSqr = Double.MAX_VALUE;
		AABB box = null;                      // built on the first candidate that could win
		TargetingConditions conditions = null;

		for (ServerPlayer candidate : level.players()) {
			// Creative is filtered here and not left to asValidTarget, and the
			// reason is behavioural rather than tidiness: TargetingConditions.test
			// accepts a creative player (only spectators fail canBeSeenByAnyone),
			// so an unfiltered one would be accepted as best, advance bestScore,
			// and then be nulled on the write — suppressing the substitution and
			// handing the slot back to vanilla while the real limper standing next
			// to them was never chosen.
			if (candidate == requested || candidate.isCreative() || candidate.hasDisconnected()) {
				continue;
			}
			// getAttachedOrCreate, deliberately, and this is the one read path in
			// the mod that creates state. Every player gets a DATA on their first
			// tick anyway; DATA is not synced and a pristine body scores 0, so
			// attaching early costs a fresh object and changes nothing.
			// RespawnCarryover's opposite rule ("getAttached, never
			// RDAttachments.get()") is about a *removed* entity, and
			// hasDisconnected() is filtered one line up.
			RealisticDamageData data = RDAttachments.get(candidate);
			int score = PreyMath.score(data, mode);
			if (score <= threshold || score < bestScore) {
				continue;                     // cannot win: no geometry, no raycast
			}
			if (box == null) {
				double followRange = self.getAttributeValue(Attributes.FOLLOW_RANGE);
				box = self.getBoundingBox().inflate(followRange);
				// TargetingConditions is mutable — range() writes a field — so this
				// is built per scan and never shared.
				conditions = TargetingConditions.forCombat().range(followRange);
			}
			if (!box.contains(candidate.getX(), candidate.getY(), candidate.getZ())) {
				continue;
			}
			// A neutral mob's own anger selector lives in its goal, not here, and
			// TargetGoal.canContinueToUse never re-checks it. Per candidate rather
			// than per mob, so a piglin angry at two players still prefers the more
			// wounded of them.
			if (self instanceof NeutralMob neutral && !neutral.isAngryAt(candidate, level)) {
				continue;
			}
			// Policy composition, on every candidate rather than only the requested
			// one: a body the aggro mode would refuse is never chosen, so the
			// resolved target is always one that policy permits.
			if (data.unconscious() && !config.unconsciousAggro.allowsTargeting(
					self.getTargetUnchecked() == candidate, self.distanceTo(candidate))) {
				continue;
			}
			double distanceSqr = self.distanceToSqr(candidate);
			if (score == bestScore && best != null
					&& (distanceSqr > bestDistanceSqr
							|| (distanceSqr == bestDistanceSqr && candidate.getId() > best.getId()))) {
				continue;                     // loses the tie: do not spend a raycast on it
			}
			if (!conditions.test(level, self, candidate)) {
				continue;                     // range, peaceful, teams, invisibility, then line of sight
			}
			best = candidate;
			bestScore = score;
			bestDistanceSqr = distanceSqr;
		}
		return best;
	}
}
