package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.LimpMath;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Copies a body onto a render state (1.7): the destroyed and broken masks, the
 * limp rungs read off them, the accumulated blood, the scars, whether the body
 * is down, and the emptying of a hand that is no longer there. Runs for every
 * living entity from the living renderer's own extraction, so a zombie's leg,
 * a pillager's leg and a player's leg are carried onto the model by the same
 * words.
 */
public final class LimbStateExtractor {
	private LimbStateExtractor() {}

	public static final int DOWN_UNCONSCIOUS = 1;
	public static final int DOWN_KNOCKED = 2;

	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		BodySnapshot snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
		int destroyedMask = snapshot == null ? 0 : snapshot.destroyedMask();
		LimbStateHolder limbs = (LimbStateHolder) state;
		limbs.realisticdamage$setDestroyedMask(destroyedMask);
		limbs.realisticdamage$setBrokenMask(snapshot == null ? 0 : snapshot.brokenMask());
		limbs.realisticdamage$setLimbRungs(limbRungs(snapshot));
		limbs.realisticdamage$setDownMask(snapshot == null ? 0
				: (snapshot.unconscious() ? DOWN_UNCONSCIOUS : 0) | (snapshot.knockedDown() ? DOWN_KNOCKED : 0));
		BloodStainHolder stains = (BloodStainHolder) state;
		stains.realisticdamage$setBloodStains(BloodBody.stains(entity.getId()));
		stains.realisticdamage$setScarMask(snapshot == null ? 0 : snapshot.scarMask());
	}

	/**
	 * Empties the hands of destroyed arms. Called from the <em>tail</em> of the
	 * concrete renderers' extraction (player, humanoid mob, illager), not from
	 * the living renderer's: vanilla fills the hand item states after the
	 * living base has run, so clearing them there was undone a moment later —
	 * the first slice-two build had an item floating beside a missing arm.
	 */
	public static void hideDestroyedHands(LivingEntity entity, ArmedEntityRenderState state) {
		BodySnapshot snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
		if (snapshot == null) {
			return;
		}
		int destroyedMask = snapshot.destroyedMask();
		hideDestroyedHand(state, destroyedMask, HumanoidArm.RIGHT);
		hideDestroyedHand(state, destroyedMask, HumanoidArm.LEFT);
	}

	/**
	 * Vanilla carries held items in their own render states, outside the model.
	 * Hiding an arm therefore does not hide what it was holding unless the
	 * matching physical hand is cleared here as well. This only edits the
	 * per-frame render state; the real stack remains in the inventory.
	 */
	public static void hideDestroyedHand(ArmedEntityRenderState state, int mask, HumanoidArm arm) {
		BodyPart part = arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		if ((mask & (1 << part.ordinal())) == 0) {
			return;
		}
		if (arm == HumanoidArm.RIGHT) {
			state.rightHandItemState.clear();
			state.rightHandItemStack = ItemStack.EMPTY;
			state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
		} else {
			state.leftHandItemState.clear();
			state.leftHandItemStack = ItemStack.EMPTY;
			state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
		}
	}

	/**
	 * The one place on this side that turns a body into limp rungs. The model
	 * reads the ladder from {@link LimpMath} and so does the prey score, so what
	 * a mob picks out and what a player sees cannot be two opinions about the
	 * same leg. Core regions are skipped because a torso does not limp.
	 */
	public static int limbRungs(BodySnapshot snapshot) {
		if (snapshot == null) {
			return 0;
		}
		int rungs = 0;
		for (BodyPart part : BodyPart.values()) {
			if (!part.isLimb()) {
				continue;
			}
			rungs = LimpMath.withRung(rungs, part, LimpMath.rung(snapshot.destroyed(part),
					snapshot.broken(part), snapshot.fraction(part)));
		}
		return rungs;
	}

	/** One region's bit out of a limb mask. */
	public static boolean gone(int mask, BodyPart part) {
		return (mask & (1 << part.ordinal())) != 0;
	}
}
