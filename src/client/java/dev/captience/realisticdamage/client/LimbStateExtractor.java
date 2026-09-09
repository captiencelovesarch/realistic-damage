package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.LimpMath;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Copies a body onto a humanoid render state (1.7): the destroyed and broken
 * masks, the limp rungs read off them, the accumulated blood, the scars, and
 * the emptying of a hand that is no longer there. One method for the player
 * renderer and every humanoid mob renderer, so a zombie's leg and a player's
 * leg are carried onto the model by the same words.
 */
public final class LimbStateExtractor {
	private LimbStateExtractor() {}

	public static void extract(LivingEntity entity, HumanoidRenderState state) {
		BodySnapshot snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
		int destroyedMask = snapshot == null ? 0 : snapshot.destroyedMask();
		LimbStateHolder limbs = (LimbStateHolder) state;
		limbs.realisticdamage$setDestroyedMask(destroyedMask);
		limbs.realisticdamage$setBrokenMask(snapshot == null ? 0 : snapshot.brokenMask());
		limbs.realisticdamage$setLimbRungs(limbRungs(snapshot));
		BloodStainHolder stains = (BloodStainHolder) state;
		stains.realisticdamage$setBloodStains(BloodBody.stains(entity.getId()));
		stains.realisticdamage$setScarMask(snapshot == null ? 0 : snapshot.scarMask());
		hideDestroyedHand(state, destroyedMask, HumanoidArm.RIGHT);
		hideDestroyedHand(state, destroyedMask, HumanoidArm.LEFT);
	}

	/**
	 * Vanilla carries held items in their own render states, outside the model.
	 * Hiding an arm therefore does not hide what it was holding unless the
	 * matching physical hand is cleared here as well. This only edits the
	 * per-frame render state; the real stack remains in the inventory.
	 */
	public static void hideDestroyedHand(HumanoidRenderState state, int mask, HumanoidArm arm) {
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
}
