package dev.captience.realisticdamage.systems;

import java.util.Map;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.util.Notifier;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Food-powered recovery (spec §37-40): every real vanilla heal grants a body
 * budget, a full-HP well-fed player keeps recovering on the vanilla regen
 * cadence (paid in exhaustion), eating restores Blood immediately, golden
 * foods are strong medicine, and sleep is excellent. No medical items, ever.
 */
public final class HealingSystem {
	private HealingSystem() {}

	/** Vanilla's slow natural-regen cadence, mirrored for the full-HP repair path. */
	private static final int NORMAL_CYCLE_TICKS = 80;

	public static void init() {
		EntitySleepEvents.STOP_SLEEPING.register((entity, sleepingPos) -> {
			if (entity instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()
					&& player.isSleepingLongEnough()) {
				applySleepRecovery(player, RDAttachments.get(player));
				RDAttachments.pushSnapshot(player);
			}
		});
	}

	public static void tick(ServerPlayer player, RealisticDamageData data) {
		// Passive Blood regeneration (spec §18): slow, faster while well fed, never while bleeding.
		if (!data.bleeding()) {
			double perSecond = Balance.BLOOD_PASSIVE_PER_SEC;
			if (player.getFoodData().getFoodLevel() >= 18) {
				perSecond += Balance.BLOOD_WELLFED_PER_SEC;
			}
			data.restoreBlood(perSecond * ServerConfig.get().healingMultiplier / 20.0);
		}

		// Full-HP players keep repairing — otherwise a full-heart player with a
		// broken leg would never recover (spec §37). Deliberately the slow vanilla
		// cadence, not the saturation-boosted one: body repair should take tens of
		// seconds for a real injury, and hearts already got the fast path.
		if (!player.isHurt() && naturalRegenAllowed(player)) {
			FoodData food = player.getFoodData();
			if (food.getFoodLevel() >= 18 && player.tickCount % NORMAL_CYCLE_TICKS == 0 && hasAnythingToHeal(data)) {
				grantRecovery(player, data, 1.0);
				food.addExhaustion(Balance.HEAL_EXHAUSTION_PER_CYCLE);
			}
		}

		clearRecoveredStatuses(player, data);
	}

	/** Called from the heal mixin: any real vanilla heal (natural regen, Regeneration, Instant Health). */
	public static void onVanillaHeal(ServerPlayer player, float amount) {
		if (player.isCreative() || player.isSpectator() || amount <= 0.0F) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(player);
		grantRecovery(player, data, amount);
	}

	/** Called from the eat mixin when a food item is fully consumed (spec §18, §40). */
	public static void onEat(ServerPlayer player, ItemStack stack, FoodProperties food) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(player);
		double multiplier = ServerConfig.get().healingMultiplier;
		if (stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
			// Nearly miraculous (spec §40).
			data.restoreBlood(Balance.ENCH_GAPPLE_BLOOD_RESTORE);
			data.clearWounds();
			data.setConcussion(false);
			data.setBurned(false);
			for (BodyPartData part : data.allParts()) {
				part.heal(part.missing() * Balance.ENCH_GAPPLE_CONDITION_REPAIR_FRACTION);
			}
			data.restoreConsciousness(Balance.ENCH_GAPPLE_CONSCIOUSNESS_RESTORE);
		} else if (stack.is(Items.GOLDEN_APPLE)) {
			data.restoreBlood(Balance.GAPPLE_BLOOD_RESTORE);
			decayWounds(data, 1.0);
			distributeRepair(player, data, Balance.GAPPLE_CONDITION_REPAIR * multiplier);
			data.restoreConsciousness(Balance.GAPPLE_CONSCIOUSNESS_RESTORE);
		} else {
			data.restoreBlood(food.nutrition() * Balance.BLOOD_PER_NUTRITION * multiplier);
		}
		RDAttachments.pushSnapshot(player);
	}

	/** A successful night's sleep (spec §38). */
	public static void applySleepRecovery(ServerPlayer player, RealisticDamageData data) {
		data.restoreBlood(Balance.SLEEP_BLOOD_RESTORE);
		for (BodyPartData part : data.allParts()) {
			trackBrokenHeal(player, part, part.missing() * Balance.SLEEP_CONDITION_REPAIR_FRACTION);
		}
		decayWounds(data, 1.0);
		data.restoreConsciousness(Balance.CONSCIOUSNESS_MAX);
		clearRecoveredStatuses(player, data);
	}

	/** One unit of recovery = what a single vanilla regen HP grants (spec §37). */
	private static void grantRecovery(ServerPlayer player, RealisticDamageData data, double healedHp) {
		double multiplier = ServerConfig.get().healingMultiplier;
		if (data.burned()) {
			multiplier *= Balance.BURNED_HEALING_FACTOR;
		}
		distributeRepair(player, data, healedHp * Balance.HEAL_BUDGET_PER_REGEN_HP * multiplier);
		data.restoreBlood(healedHp * Balance.BLOOD_PER_REGEN_HP * multiplier);
		decayWounds(data, healedHp * Balance.WOUND_DECAY_PER_REGEN);
	}

	/** Budgeted body repair, worst-first with severe-part attention (spec §37). */
	private static void distributeRepair(ServerPlayer player, RealisticDamageData data, double budget) {
		Map<BodyPart, Double> allocation = HealingMath.distributeBudget(data, budget);
		for (Map.Entry<BodyPart, Double> entry : allocation.entrySet()) {
			trackBrokenHeal(player, data.part(entry.getKey()), entry.getValue());
		}
	}

	/** Heals a part and announces a Broken limb mending back to HEALTHY (spec §15, §43). */
	private static void trackBrokenHeal(ServerPlayer player, BodyPartData part, double amount) {
		boolean wasBroken = part.broken();
		part.heal(amount);
		if (wasBroken && !part.broken()) {
			Notifier.message(player, "realisticdamage.msg.mended." + part.part().id());
		}
	}

	private static void decayWounds(RealisticDamageData data, double steps) {
		if (steps <= 0 || !data.bleeding()) {
			return;
		}
		data.wounds().removeIf(wound -> wound.decay(steps));
	}

	private static void clearRecoveredStatuses(ServerPlayer player, RealisticDamageData data) {
		if (data.concussion()
				&& data.part(BodyPart.HEAD).state() == dev.captience.realisticdamage.body.BodyState.HEALTHY
				&& data.consciousness() >= Balance.CONCUSSION_CLEAR_CONSCIOUSNESS) {
			data.setConcussion(false);
			Notifier.message(player, "realisticdamage.msg.concussion_cleared");
		}
		if (data.burned() && !player.isOnFire() && averageFraction(data) >= Balance.BURNED_CLEAR_AVG_FRACTION) {
			data.setBurned(false);
			Notifier.message(player, "realisticdamage.msg.burns_healed");
		}
	}

	private static double averageFraction(RealisticDamageData data) {
		double total = 0.0;
		for (BodyPart part : BodyPart.values()) {
			total += data.part(part).fraction();
		}
		return total / BodyPart.values().length;
	}

	private static boolean hasAnythingToHeal(RealisticDamageData data) {
		for (BodyPartData part : data.allParts()) {
			if (part.missing() > 1.0e-9) {
				return true;
			}
		}
		return data.bleeding() || data.blood() < Balance.BLOOD_MAX;
	}

	private static boolean naturalRegenAllowed(ServerPlayer player) {
		return player.level().getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
	}
}
