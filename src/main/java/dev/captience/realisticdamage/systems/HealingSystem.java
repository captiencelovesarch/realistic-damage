package dev.captience.realisticdamage.systems;

import net.minecraft.world.entity.LivingEntity;
import java.util.Map;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
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

	public static void tick(LivingEntity entity, RealisticDamageData data) {
		ServerPlayer player = entity instanceof ServerPlayer p ? p : null;
		// Passive Blood regeneration (spec §18): slow, faster while well fed, never while bleeding.
		if (!data.bleeding()) {
			double perSecond = Balance.BLOOD_PASSIVE_PER_SEC;
			if (player != null && player.getFoodData().getFoodLevel() >= 18) {
				perSecond += Balance.BLOOD_WELLFED_PER_SEC;
			}
			data.restoreBlood(perSecond * ServerConfig.get().healingMultiplier / 20.0);
		}

		if (player != null) {
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
		} else if (entity.hurtTime == 0 && entity.tickCount % NORMAL_CYCLE_TICKS == 0
				&& entity.getLastHurtByMobTimestamp() + Balance.MOB_MEND_CALM_TICKS < entity.tickCount
				&& hasAnythingToHeal(data)) {
			// A mob mends on time alone (1.7): no food, no sleep, and nothing to
			// spend, so a fraction of a regen-HP every cycle once nothing has hurt
			// it for a while. Enough that a zombie that walks away from a fight is
			// whole in a couple of minutes, and a minor wound is not a slow death.
			grantRecovery(entity, data, Balance.MOB_MEND_PER_CYCLE);
		}

		clearRecoveredStatuses(entity, data);
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
	private static void grantRecovery(LivingEntity player, RealisticDamageData data, double healedHp) {
		double multiplier = ServerConfig.get().healingMultiplier;
		if (data.burned()) {
			multiplier *= Balance.BURNED_HEALING_FACTOR;
		}
		distributeRepair(player, data, healedHp * Balance.HEAL_BUDGET_PER_REGEN_HP * multiplier);
		data.restoreBlood(healedHp * Balance.BLOOD_PER_REGEN_HP * multiplier);
		decayWounds(data, healedHp * Balance.WOUND_DECAY_PER_REGEN);
	}

	/** Budgeted body repair, worst-first with severe-part attention (spec §37). */
	private static void distributeRepair(LivingEntity player, RealisticDamageData data, double budget) {
		Map<BodyPart, Double> allocation = HealingMath.distributeBudget(data, budget);
		for (Map.Entry<BodyPart, Double> entry : allocation.entrySet()) {
			trackBrokenHeal(player, data.part(entry.getKey()), entry.getValue());
		}
	}

	/** Heals a part and announces a Broken limb mending (spec §15, §43). */
	private static void trackBrokenHeal(LivingEntity player, BodyPartData part, double amount) {
		boolean wasBroken = part.broken();
		part.heal(amount);
		if (wasBroken && !part.broken()) {
			// Mended at a ceiling is not mended whole, and the line has to say so.
			// With no ceiling anywhere, clearing Broken implies HEALTHY, so this
			// picks the 1.5 wording verbatim while the hardcore toggle is off.
			String key = part.state() == BodyState.HEALTHY
					? "realisticdamage.msg.mended."
					: "realisticdamage.msg.mended_ceiling.";
			Notifier.message(player, key + part.part().id());
		}
	}

	private static void decayWounds(RealisticDamageData data, double steps) {
		if (steps <= 0 || !data.bleeding()) {
			return;
		}
		data.wounds().removeIf(wound -> wound.decay(steps));
	}

	private static void clearRecoveredStatuses(LivingEntity player, RealisticDamageData data) {
		if (data.concussion()) {
			// Stable means: head mended, consciousness sitting at its ceiling, not
			// bleeding. Only then does the clock toward recovery run, and it has to
			// run for a while — a concussion is supposed to linger.
			//
			// "Mended" is HEALTHY or, since 1.6, the head sitting at its own
			// ceiling: a capped head that will never read HEALTHY again would
			// otherwise carry a permanent concussion, and a concussion nothing can
			// clear is a second injury the ceiling never promised.
			BodyPartData head = data.part(BodyPart.HEAD);
			boolean stable = (head.state() == BodyState.HEALTHY || head.atCeiling())
					&& data.consciousness() >= data.consciousnessCap() - Balance.CONCUSSION_STABLE_MARGIN
					&& !data.bleeding();
			data.setConcussionStableTicks(stable ? data.concussionStableTicks() + 1 : 0);
			if (data.concussionStableTicks() >= Balance.CONCUSSION_STABLE_TICKS) {
				data.setConcussion(false);
				Notifier.message(player, "realisticdamage.msg.concussion_cleared");
			}
		}
		if (data.burned() && !player.isOnFire() && averageRecovery(data) >= Balance.BURNED_CLEAR_AVG_FRACTION) {
			data.setBurned(false);
			Notifier.message(player, "realisticdamage.msg.burns_healed");
		}
	}

	/**
	 * How far along the body is toward the best it can be, not how close it is to
	 * new. Burns clear when the body has recovered, and a capped body has done all
	 * the recovering it will ever do — measuring it against an unreachable maximum
	 * would leave it Burned for good.
	 */
	private static double averageRecovery(RealisticDamageData data) {
		double total = 0.0;
		for (BodyPart part : BodyPart.values()) {
			total += data.part(part).recoveryFraction();
		}
		return total / BodyPart.values().length;
	}

	/**
	 * Is there any point running the full-HP repair cycle? Public because it is
	 * the only observable answer to "this body is as good as it gets", which is
	 * what a ceiling makes possible for the first time.
	 *
	 * <p>Keyed on {@link BodyPartData#missing()}, which measures against the
	 * ceiling — so a fully capped body stops paying exhaustion for a repair that
	 * can never land, instead of burning a hunger point every eighty ticks
	 * forever.
	 */
	public static boolean hasAnythingToHeal(RealisticDamageData data) {
		for (BodyPartData part : data.allParts()) {
			if (part.missing() > Balance.CONDITION_EPSILON) {
				return true;
			}
		}
		return data.bleeding() || data.blood() < Balance.BLOOD_MAX;
	}

	private static boolean naturalRegenAllowed(LivingEntity player) {
		return ((net.minecraft.server.level.ServerLevel) player.level()).getGameRules().get(GameRules.NATURAL_HEALTH_REGENERATION);
	}
}
