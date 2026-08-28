package dev.captience.realisticdamage.systems;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
				locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				locked(player) || handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
				locked(player) || handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				locked(player) || handGone(player, hand) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !locked(player));
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

		if (data.unconscious()
				&& data.unconsciousTicks() >= Balance.MIN_UNCONSCIOUS_TICKS
				&& data.consciousness() >= Balance.WAKE_THRESHOLD) {
			wake(player, data);
		}
	}

	/**
	 * A body on the floor does not stop being prey. While unconscious, nearby
	 * hostiles that have lost interest (or never had it) re-acquire the player,
	 * so collapsing in a fight is genuinely dangerous rather than a way to be
	 * forgotten about.
	 *
	 * <p>Only hostile mobs, only ones that can actually see you, and only every
	 * half second so it costs nothing.
	 */
	private static void keepMobsInterested(ServerPlayer player) {
		if (player.tickCount % 10 != 0) {
			return;
		}
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(Balance.UNCONSCIOUS_AGGRO_RADIUS),
				candidate -> candidate instanceof Enemy && candidate.isAlive()
						&& candidate.getTarget() != player)) {
			if (mob.getSensing().hasLineOfSight(player)) {
				mob.setTarget(player);
			}
		}
	}

	/** Event-driven consciousness consequences of one damage event (called by the pipeline). */
	public static void onTrauma(ServerPlayer player, RealisticDamageData data, ClassifiedDamage classified,
			DamageProfile profile, List<TraumaPipeline.AppliedTrauma> traumas, double appliedDamage) {
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
				maybeConcuss(player, data, profile, trauma.conditionDamage());
			} else {
				drain += trauma.conditionDamage() * profile.consciousnessFactor()
						* Balance.CONSCIOUSNESS_BODY_HIT_SCALE * (trauma.primary() ? 1.0 : 0.5);
			}
		}

		// Hard impacts jolt the whole body, not just whichever region took the hit.
		if (profile.concussionCapable()) {
			drain += appliedDamage * profile.consciousnessFactor() * Balance.CONSCIOUSNESS_IMPACT_SCALE;
			maybeConcuss(player, data, profile, appliedDamage * profile.consciousnessFactor());
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

	/** Deterministic concussion (spec §21): big single head trauma, or meaningful trauma on an already-battered head. */
	private static void maybeConcuss(ServerPlayer player, RealisticDamageData data, DamageProfile profile,
			double headTrauma) {
		if (data.concussion() || !profile.concussionCapable()) {
			return;
		}
		boolean massive = headTrauma >= Balance.CONCUSSION_HEAD_TRAUMA_THRESHOLD;
		boolean compounded = headTrauma >= Balance.CONCUSSION_HEAD_TRAUMA_INJURED_THRESHOLD
				&& data.part(BodyPart.HEAD).state().isAtLeastAsBadAs(BodyState.INJURED);
		if (massive || compounded) {
			data.setConcussion(true);
			Notifier.message(player, "realisticdamage.msg.concussion");
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
		Notifier.message(player, "realisticdamage.msg.unconscious");
		player.level().playSound(null, player.blockPosition(),
				SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	private static void wake(ServerPlayer player, RealisticDamageData data) {
		data.setUnconscious(false);
		Notifier.message(player, "realisticdamage.msg.wake");
	}
}
