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
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) ->
				locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				locked(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !locked(player));
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

		if (data.unconscious()
				&& data.unconsciousTicks() >= Balance.MIN_UNCONSCIOUS_TICKS
				&& data.consciousness() >= Balance.WAKE_THRESHOLD) {
			wake(player, data);
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
			if (trauma.part() == BodyPart.HEAD && trauma.primary()) {
				double hit = trauma.conditionDamage() * profile.consciousnessFactor()
						* Balance.CONSCIOUSNESS_HEAD_HIT_SCALE;
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
