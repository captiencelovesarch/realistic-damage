package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;

/**
 * The concussion as something you play around (1.7 rework); the numbers live in
 * {@link ConcussionMath}. Ticks after {@link ConsciousnessSystem}, so a
 * concussion it set this tick starts healing next tick, not this one.
 *
 * <p>The loop it creates: a knock to the head is survivable, but pushing on —
 * sprinting, fighting, standing in the sun — makes it flare, and a flaring
 * concussion swims, spins and eventually empties your stomach. Stopping,
 * crouching somewhere dark, lets it settle and heal. A second knock before it
 * has healed is far worse than the first.
 */
public final class ConcussionSystem {
	private ConcussionSystem() {}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			exert(player, Balance.FLARE_PER_ATTACK);
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
				exert(player, Balance.FLARE_PER_BLOCK_BROKEN));
	}

	/** Exertion from an action: raises the flare and holds healing off for a second. */
	private static void exert(Player player, double flare) {
		if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(serverPlayer);
		if (!data.concussion()) {
			return;
		}
		data.markExertion(serverPlayer.level().getGameTime());
		data.setConcussionFlare(data.concussionFlare() + flare * ConcussionMath.flareGain(data.concussionGrade()));
	}

	/** A hit landed on a concussed body: the jolt flares it whatever region took it. */
	public static void onHitTaken(LivingEntity entity, RealisticDamageData data) {
		if (data.concussion()) {
			data.markExertion(entity.level().getGameTime());
			data.setConcussionFlare(data.concussionFlare()
					+ Balance.FLARE_PER_HIT_TAKEN * ConcussionMath.flareGain(data.concussionGrade()));
		}
	}

	public static void tick(LivingEntity entity, RealisticDamageData data) {
		if (!data.concussion()) {
			return;
		}
		if (!ServerConfig.get().enableConsciousness) {
			data.clearConcussion();
			return;
		}
		if (!(entity instanceof ServerPlayer player)) {
			// A mob mends on time alone; it has no stomach to lose and nobody to
			// watch the room spin.
			healMob(entity, data);
			return;
		}
		int grade = data.concussionGrade();
		ServerLevel level = player.level();
		long now = level.getGameTime();

		// What the player is doing, read from what the body actually did this tick.
		double moved = data.moveSinceLastTick(player.getX(), player.getZ());
		int jumps = data.jumpsSinceLastTick(player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)));
		if (jumps > 0) {
			data.markExertion(now);
		}
		boolean sprinting = player.isSprinting() || player.isSwimming();
		boolean recentlyExerted = now - data.lastExertion() < 20;
		ConcussionMath.Activity activity;
		if (sprinting || recentlyExerted) {
			activity = ConcussionMath.Activity.EXERTING;
		} else if (moved < 0.03 || player.isPassenger() || player.isSleeping() || data.unconscious()) {
			activity = ConcussionMath.Activity.RESTING;
		} else {
			activity = ConcussionMath.Activity.LIGHT;
		}
		int brightness = level.getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition()));
		boolean dark = brightness <= Balance.CONCUSSION_DARK_LIGHT;

		// The flare: up with effort and light, down with stillness and dark.
		double gain = ConcussionMath.flareGain(grade);
		double flarePerSec = 0.0;
		if (sprinting) {
			flarePerSec += Balance.FLARE_SPRINT_PER_SEC * gain;
		}
		if (!data.unconscious() && data.concussionFlare() < Balance.FLARE_LIGHT_CEILING) {
			flarePerSec += ConcussionMath.lightFlarePerSecond(brightness) * gain;
		}
		if (data.vertigo()) {
			flarePerSec += Balance.FLARE_VERTIGO_PER_SEC * gain;
		}
		flarePerSec -= ConcussionMath.flareDecayPerSecond(activity, dark);
		double flare = data.concussionFlare() + flarePerSec / 20.0 + jumps * Balance.FLARE_PER_JUMP * gain;
		data.setConcussionFlare(flare);

		heal(player, data, activity, dark);
		if (!data.concussion()) {
			return;   // healed this tick; everything below has nothing to act on
		}

		tickVertigo(player, data);
		tickRetch(player, data);
	}

	private static void heal(LivingEntity entity, RealisticDamageData data, ConcussionMath.Activity activity,
			boolean dark) {
		BodyPartData head = data.part(BodyPart.HEAD);
		boolean badHead = head.state().isAtLeastAsBadAs(BodyState.INJURED) && !head.atCeiling();
		double rate = ConcussionMath.healPerSecond(activity, badHead, dark)
				* ServerConfig.get().healingMultiplier;
		if (rate <= 0.0) {
			return;
		}
		int before = data.concussionGrade();
		data.setConcussionSeverity(data.concussionSeverity() - rate / 20.0);
		int after = data.concussionGrade();
		if (after == ConcussionMath.NONE) {
			Notifier.message(entity, "realisticdamage.msg.concussion_cleared");
		} else if (after < before) {
			Notifier.message(entity, "realisticdamage.msg.concussion_easing");
		}
	}

	private static void healMob(LivingEntity mob, RealisticDamageData data) {
		data.setConcussionSeverity(data.concussionSeverity()
				- Balance.CONCUSSION_HEAL_MOB * ServerConfig.get().healingMultiplier / 20.0);
	}

	/** Spells of the room spinning, on the same kind of clock as the microsleeps. */
	private static void tickVertigo(ServerPlayer player, RealisticDamageData data) {
		if (data.vertigo()) {
			data.tickVertigo();
			return;
		}
		if (data.unconscious() || data.retching()) {
			return;
		}
		double phase = data.vertigoPhase()
				+ 1.0 / ConcussionMath.vertigoPeriod(data.concussionGrade(), data.concussionFlare());
		if (phase < 1.0) {
			data.setVertigoPhase(phase);
			return;
		}
		data.setVertigoPhase(phase - 1.0);
		data.beginVertigo(ConcussionMath.vertigoDuration(data.concussionGrade(), data.concussionFlare()));
		if (player.isSprinting()) {
			player.setSprinting(false);
		}
	}

	/** At the top of the flare the stomach goes. Costs food, buys relief. */
	private static void tickRetch(ServerPlayer player, RealisticDamageData data) {
		if (data.retching()) {
			data.tickRetch();
			return;
		}
		if (data.unconscious() || data.concussionFlare() < Balance.FLARE_RETCH) {
			return;
		}
		data.beginRetch(Balance.RETCH_TICKS);
		data.setConcussionFlare(Balance.FLARE_AFTER_RETCH);
		FoodData food = player.getFoodData();
		food.setFoodLevel(Math.max(0, food.getFoodLevel() - Balance.RETCH_FOOD_LOSS));
		food.setSaturation(0.0F);
		player.setSprinting(false);
		player.stopUsingItem();
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
				SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.9F, 0.7F);
		Notifier.message(player, "realisticdamage.msg.retch");
	}
}
