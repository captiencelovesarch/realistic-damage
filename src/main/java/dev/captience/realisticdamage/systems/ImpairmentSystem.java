package dev.captience.realisticdamage.systems;

import net.minecraft.world.entity.LivingEntity;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
	private static final Identifier ADRENALINE_SPEED_ID = RealisticDamage.id("adrenaline_speed");
	private static final Identifier ADRENALINE_ATTACK_ID = RealisticDamage.id("adrenaline_attack");

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

	public static void tick(LivingEntity player, RealisticDamageData data) {
		double leftLeg = legPenalty(data.part(BodyPart.LEFT_LEG));
		double rightLeg = legPenalty(data.part(BodyPart.RIGHT_LEG));
		double movement = ImpairmentMath.combinedLegPenalty(leftLeg, rightLeg);
		double jump = ImpairmentMath.jumpPenalty(movement);
		BodyPartData mainArm = data.part(armFor(player, true));
		// A limp main arm (1.7) swings like a missing one: you are fighting with
		// whatever the other hand and your shoulder can manage.
		boolean oneHanded = mainArm.destroyed() || !canGrip(mainArm);
		double armPenalty = oneHanded
				? Balance.ONE_HANDED_PENALTY
				: ImpairmentMath.armPenalty(mainArm.fraction(), mainArm.broken());
		// Adrenaline (1.7) pushes the pain to the back for a few seconds. It does
		// not mend anything — the vetoes below and the grip lock still apply — it
		// only takes the edge off what an injured limb costs you.
		AdrenalineMath.Phase rush = data.adrenaline().phase;
		if (rush == AdrenalineMath.Phase.SURGE) {
			movement *= 1.0 - Balance.ADRENALINE_PAIN_MASK;
			armPenalty *= 1.0 - Balance.ADRENALINE_PAIN_MASK;
		}
		// Low consciousness makes actions sluggish (spec §23).
		double sluggish = sluggishPenalty(ConsciousnessSystem.effective(data));
		// The injury caps bound what a wrecked *limb* costs you. Fading out is not
		// a limb injury and must not be filed under one: capping the sum at the arm
		// cap was what let a player at one point of consciousness still mine at
		// nearly two-thirds speed. The cap still applies to the arm's own share.
		double cap = oneHanded ? Balance.ONE_HANDED_PENALTY : Balance.ARM_PENALTY_TOTAL_CAP;
		double actionPenalty = Math.max(sluggish, Math.min(cap, armPenalty + sluggish));

		if (data.legless()) {
			// Dragging yourself along: slow, and you certainly cannot jump.
			movement = Math.max(movement, Balance.CRAWL_MOVEMENT_PENALTY);
			jump = 1.0;
		}
		if (data.dragging() != null) {
			// Hauling a body: slow, and no jumping with your hands full.
			movement = Math.max(movement, Balance.DRAG_MOVEMENT_PENALTY);
			jump = 1.0;
		}
		if (RescueSystem.rooted(data)) {
			// Kneeling with a hand on a wound. You can press or you can move.
			movement = 1.0;
			jump = 1.0;
		}
		if (rush == AdrenalineMath.Phase.CRASH) {
			movement = Math.max(movement, Math.min(1.0, movement + Balance.CRASH_MOVEMENT_PENALTY));
		}
		if (data.vertigo()) {
			// The floor will not stay where it is (1.7 concussion).
			movement = Math.max(movement, Math.min(1.0, movement + Balance.VERTIGO_MOVEMENT_PENALTY));
		}
		if (data.retching()) {
			// Doubled over: you can shuffle, not walk, and you are not jumping.
			movement = Math.max(movement, 0.85);
			jump = 1.0;
		}
		if (data.knockdownRecoveryTicks() > 0) {
			// Back on your feet but not yet steady (1.4).
			movement = Math.max(movement, Balance.KNOCKDOWN_RECOVERY_MOVEMENT_PENALTY);
			jump = 1.0;
		}
		if (data.knockedDown() || data.microsleeping()) {
			// Face-down after a landing, or gone for a moment: physics still owns
			// you, you just are not steering (1.4).
			movement = 1.0;
			jump = 1.0;
		}
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
		boolean surging = rush == AdrenalineMath.Phase.SURGE && !data.unconscious();
		applySigned(player, Attributes.MOVEMENT_SPEED, ADRENALINE_SPEED_ID,
				surging ? Balance.ADRENALINE_SPEED_BONUS : 0.0);
		applySigned(player, Attributes.ATTACK_SPEED, ADRENALINE_ATTACK_ID,
				surging ? Balance.ADRENALINE_ATTACK_SPEED_BONUS : 0.0);

		// A hand that has stopped gripping lets go of what it was using: the
		// shield drops, the half-drawn bow goes slack (1.7).
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.isUsingItem()
				&& gripBlocked(serverPlayer, serverPlayer.getUsedItemHand()) != null) {
			serverPlayer.stopUsingItem();
		}

		// A Disabled leg (or fading consciousness) disables sprinting (spec §12, §23).
		// The client re-requests, the server keeps vetoing; the speed bonus is
		// server-side anyway.
		if (player.isSprinting() && sprintBlocked(data)) {
			player.setSprinting(false);
		}
	}

	/**
	 * Smooth action penalty from low consciousness: 0 above 55, ramping below,
	 * and caving in over the last twenty points so that the approach to collapse
	 * is felt in the hands and not only on the screen. Takes <em>effective</em>
	 * consciousness, so a concussion brings the whole curve forward.
	 *
	 * <p>Below {@link Balance#SYMPTOM_FEEBLE} the curve stops being the whole
	 * story: ConsciousnessSystem refuses the swing outright.
	 */
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
	public static void clear(LivingEntity player) {
		remove(player, Attributes.MOVEMENT_SPEED, LEG_SPEED_ID);
		remove(player, Attributes.JUMP_STRENGTH, LEG_JUMP_ID);
		remove(player, Attributes.ATTACK_SPEED, ARM_ATTACK_SPEED_ID);
		remove(player, Attributes.BLOCK_BREAK_SPEED, ARM_MINING_ID);
		remove(player, Attributes.ATTACK_DAMAGE, ARM_DAMAGE_ID);
		remove(player, Attributes.MOVEMENT_SPEED, ADRENALINE_SPEED_ID);
		remove(player, Attributes.ATTACK_SPEED, ADRENALINE_ATTACK_ID);
	}

	public static boolean sprintBlocked(RealisticDamageData data) {
		return data.unconscious()
				|| data.knockedDown()
				|| data.microsleeping()
				|| data.legless()
				|| ConsciousnessSystem.effective(data) < Balance.SYMPTOM_SPRINT_LOSS
				|| data.vertigo()
				|| data.retching()
				|| data.adrenaline().winded
				|| (data.concussion() && data.concussionFlare() >= Balance.FLARE_NO_SPRINT)
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

	public static BodyPart armFor(LivingEntity player, boolean mainHand) {
		HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
		return arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
	}

	private static double legPenalty(BodyPartData leg) {
		return leg.destroyed()
				? ImpairmentMath.legPenaltyDestroyed()
				: ImpairmentMath.legPenalty(leg.fraction(), leg.broken());
	}

	/**
	 * A destroyed arm cannot use its hand at all (Arcade preset). You always keep
	 * the other one unless both are gone, so eating and fighting stay possible.
	 */
	public static boolean handDisabled(ServerPlayer player, InteractionHand hand) {
		if (player.isCreative() || player.isSpectator()) {
			return false;
		}
		RealisticDamageData data = RDAttachments.get(player);
		BodyPart arm = armFor(player, hand == InteractionHand.MAIN_HAND);
		return data.part(arm).destroyed();
	}

	/**
	 * Can this arm's hand close around anything (1.7)? Not when it is gone, and
	 * not when it has been driven all the way to Disabled — the grey arm on the
	 * body panel. It comes back with the arm: the moment the limb heals out of
	 * Disabled, the hand works again.
	 */
	public static boolean canGrip(BodyPartData arm) {
		if (arm.destroyed()) {
			return false;
		}
		return !ServerConfig.get().disabledArmsLoseGrip || arm.state() != BodyState.DISABLED;
	}

	/**
	 * Which hand stops this use, or null when nothing does (1.7). The hand itself
	 * if its own arm cannot grip; the other hand if the item takes two —
	 * a bow, a crossbow, a spear charge — and the other arm cannot help.
	 */
	public static net.minecraft.world.InteractionHand gripBlocked(ServerPlayer player, InteractionHand hand) {
		if (player.isCreative() || player.isSpectator()) {
			return null;
		}
		RealisticDamageData data = RDAttachments.get(player);
		BodyPartData own = data.part(armFor(player, hand == InteractionHand.MAIN_HAND));
		if (own.destroyed() ? ServerConfig.get().enableLimbDestruction : !canGrip(own)) {
			return hand;
		}
		if (twoHanded(player.getItemInHand(hand))) {
			InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			BodyPartData helper = data.part(armFor(player, other == InteractionHand.MAIN_HAND));
			if (helper.destroyed() ? ServerConfig.get().enableLimbDestruction : !canGrip(helper)) {
				return other;
			}
		}
		return null;
	}

	/** Items that need both hands to use: the same set the two-handed stall applies to. */
	public static boolean twoHanded(net.minecraft.world.item.ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		net.minecraft.world.item.ItemUseAnimation animation = stack.getUseAnimation();
		return animation == net.minecraft.world.item.ItemUseAnimation.BOW
				|| animation == net.minecraft.world.item.ItemUseAnimation.CROSSBOW
				|| animation == net.minecraft.world.item.ItemUseAnimation.SPEAR;
	}

	private static void strainIfSevere(Player player) {
		if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.isCreative() || serverPlayer.isSpectator()) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(serverPlayer);
		BodyPartData mainArm = data.part(armFor(serverPlayer, true));
		// A recovery pause is for an arm that still has recovering to do. An arm
		// sitting at a permanent Critical ceiling (1.6) is as good as it will ever
		// be, and pausing its repair every swing would make the ceiling cost more
		// than the ceiling — every fight and every mined block would re-arm a
		// five-second pause for the rest of that character's life.
		//
		// Byte-identical with no ceiling: a full arm cannot be CRITICAL, so
		// !atCeiling() is true wherever the 1.5 condition was. A destroyed arm
		// still strains, because atCeiling() is deliberately false while destroyed.
		if (mainArm.broken() || (!mainArm.atCeiling() && mainArm.state().isAtLeastAsBadAs(BodyState.CRITICAL))) {
			mainArm.strain(Balance.ARM_STRAIN_PAUSE_TICKS);
		}
	}

	private static void apply(LivingEntity player, Holder<Attribute> attribute, Identifier id, double value) {
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

	/** As {@link #apply}, for a modifier that may also be a bonus. */
	private static void applySigned(LivingEntity player, Holder<Attribute> attribute, Identifier id, double value) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (Math.abs(value) < 1.0e-9) {
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

	private static void remove(LivingEntity player, Holder<Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}
}
