package dev.captience.realisticdamage.systems;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Movement/attack/mining penalties from limb condition (spec §11-12, §50).
 * Applied as idempotent transient attribute modifiers — never persisted, never
 * stacked, recomputed each tick and only touched when the value changes.
 * Vehicles are naturally unaffected: boats/horses/minecarts don't read the
 * rider's attributes (spec §12).
 */
public final class ImpairmentSystem {
	private ImpairmentSystem() {}

	private static final Identifier LEG_SPEED_ID = RealisticDamage.id("leg_speed");
	private static final Identifier LEG_JUMP_ID = RealisticDamage.id("leg_jump");
	private static final Identifier ARM_ATTACK_SPEED_ID = RealisticDamage.id("arm_attack_speed");
	private static final Identifier ARM_MINING_ID = RealisticDamage.id("arm_mining");
	private static final Identifier ARM_DAMAGE_ID = RealisticDamage.id("arm_damage");

	/** Main-arm penalty reduces melee damage "somewhat" (spec §11) — half weight. */
	private static final double ARM_DAMAGE_FACTOR = 0.5;

	public static void init() {
		// Strenuous use of a severely damaged arm pauses its recovery (spec §14).
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			strainIfSevere(player);
			return InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> strainIfSevere(player));
	}

	public static void tick(ServerPlayer player, RealisticDamageData data) {
		double leftLeg = legPenalty(data.part(BodyPart.LEFT_LEG));
		double rightLeg = legPenalty(data.part(BodyPart.RIGHT_LEG));
		double movement = ImpairmentMath.combinedLegPenalty(leftLeg, rightLeg);
		double jump = ImpairmentMath.jumpPenalty(movement);
		BodyPartData mainArm = data.part(armFor(player, true));
		double armPenalty = ImpairmentMath.armPenalty(mainArm.fraction(), mainArm.broken());
		// Low consciousness makes actions sluggish (spec §23).
		double sluggish = sluggishPenalty(data.consciousness());
		double actionPenalty = Math.min(Balance.ARM_PENALTY_TOTAL_CAP, armPenalty + sluggish);

		if (data.unconscious()) {
			// Voluntary movement fully suppressed; physics still applies (spec §25).
			movement = 1.0;
			jump = 1.0;
		}

		apply(player, Attributes.MOVEMENT_SPEED, LEG_SPEED_ID, -movement);
		apply(player, Attributes.JUMP_STRENGTH, LEG_JUMP_ID, -jump);
		apply(player, Attributes.ATTACK_SPEED, ARM_ATTACK_SPEED_ID, -actionPenalty);
		apply(player, Attributes.BLOCK_BREAK_SPEED, ARM_MINING_ID, -actionPenalty);
		apply(player, Attributes.ATTACK_DAMAGE, ARM_DAMAGE_ID, -armPenalty * ARM_DAMAGE_FACTOR);

		// A Disabled leg (or fading consciousness) disables sprinting (spec §12, §23).
		// The client re-requests, the server keeps vetoing; the speed bonus is
		// server-side anyway.
		if (player.isSprinting() && sprintBlocked(data)) {
			player.setSprinting(false);
		}
	}

	/** Smooth action penalty from low consciousness: 0 above 55, ramping below. */
	public static double sluggishPenalty(double consciousness) {
		if (consciousness >= Balance.SYMPTOM_ATTACK_RECOVERY) {
			return 0.0;
		}
		if (consciousness >= Balance.SYMPTOM_SPRINT_LOSS) {
			double t = (Balance.SYMPTOM_ATTACK_RECOVERY - consciousness)
					/ (Balance.SYMPTOM_ATTACK_RECOVERY - Balance.SYMPTOM_SPRINT_LOSS);
			return t * Balance.SLUGGISH_PENALTY_AT_20;
		}
		double t = (Balance.SYMPTOM_SPRINT_LOSS - consciousness) / Balance.SYMPTOM_SPRINT_LOSS;
		return Balance.SLUGGISH_PENALTY_AT_20
				+ t * (Balance.SLUGGISH_PENALTY_AT_0 - Balance.SLUGGISH_PENALTY_AT_20);
	}

	/** Removes every impairment modifier (creative/spectator switch, spec §46). */
	public static void clear(ServerPlayer player) {
		remove(player, Attributes.MOVEMENT_SPEED, LEG_SPEED_ID);
		remove(player, Attributes.JUMP_STRENGTH, LEG_JUMP_ID);
		remove(player, Attributes.ATTACK_SPEED, ARM_ATTACK_SPEED_ID);
		remove(player, Attributes.BLOCK_BREAK_SPEED, ARM_MINING_ID);
		remove(player, Attributes.ATTACK_DAMAGE, ARM_DAMAGE_ID);
	}

	public static boolean sprintBlocked(RealisticDamageData data) {
		return data.unconscious()
				|| data.consciousness() < Balance.SYMPTOM_SPRINT_LOSS
				|| data.part(BodyPart.LEFT_LEG).state() == BodyState.DISABLED
				|| data.part(BodyPart.RIGHT_LEG).state() == BodyState.DISABLED;
	}

	/** The two-handed (bow/crossbow/spear-charge) penalty for this player right now. */
	public static double twoHandedPenalty(Player player, RealisticDamageData data) {
		BodyPartData main = data.part(armFor(player, true));
		BodyPartData off = data.part(armFor(player, false));
		return ImpairmentMath.twoHandedPenalty(
				ImpairmentMath.armPenalty(main.fraction(), main.broken()),
				ImpairmentMath.armPenalty(off.fraction(), off.broken()));
	}

	public static BodyPart armFor(Player player, boolean mainHand) {
		HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
		return arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
	}

	private static double legPenalty(BodyPartData leg) {
		return ImpairmentMath.legPenalty(leg.fraction(), leg.broken());
	}

	private static void strainIfSevere(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(serverPlayer);
		BodyPartData mainArm = data.part(armFor(serverPlayer, true));
		if (mainArm.broken() || mainArm.state().isAtLeastAsBadAs(BodyState.CRITICAL)) {
			mainArm.strain(Balance.ARM_STRAIN_PAUSE_TICKS);
		}
	}

	private static void apply(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double value) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (value > -1.0e-9) {
			instance.removeModifier(id);
			return;
		}
		AttributeModifier existing = instance.getModifier(id);
		if (existing != null && Math.abs(existing.amount() - value) < 1.0e-9) {
			return;
		}
		instance.addOrUpdateTransientModifier(
				new AttributeModifier(id, value, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void remove(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}
