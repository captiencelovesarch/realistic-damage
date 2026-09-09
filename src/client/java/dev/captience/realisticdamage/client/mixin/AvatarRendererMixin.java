package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.BloodBody;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.client.BloodStainHolder;
import dev.captience.realisticdamage.client.FirstPersonBlood;
import dev.captience.realisticdamage.client.LimbStateHolder;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.systems.LimpMath;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Copies the synced destroyed-limb and broken-limb masks, the limp rungs read
 * off them, the accumulated blood stains and the synced scar mask onto the
 * render state each frame.
 *
 * <p>Scars come off the snapshot rather than out of a client-side accumulator
 * the way blood does, and deliberately: entity ids are reused across a respawn
 * on both sides, so anything keyed by id would hand a fresh body the previous
 * one's marks. The server already carries the history through death; the client
 * only has to read it.
 *
 * <p>The broken mask has been on the wire since 1.0 ({@code BodySnapshot} sends
 * it to every tracking client) and was simply never read on this side. Reading
 * it costs one field: there is no packet, save or protocol change here, and a
 * 1.5 client and a 1.6 client see the same bytes.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void realisticdamage$carryDestroyedLimbs(Avatar entity, AvatarRenderState state, float partialTicks,
			CallbackInfo ci) {
		BodySnapshot snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
		int destroyedMask = snapshot == null ? 0 : snapshot.destroyedMask();
		((LimbStateHolder) state).realisticdamage$setDestroyedMask(destroyedMask);
		((LimbStateHolder) state).realisticdamage$setBrokenMask(
				snapshot == null ? 0 : snapshot.brokenMask());
		((LimbStateHolder) state).realisticdamage$setLimbRungs(realisticdamage$limbRungs(snapshot));
		((BloodStainHolder) state).realisticdamage$setBloodStains(BloodBody.stains(entity.getId()));
		((BloodStainHolder) state).realisticdamage$setScarMask(
				snapshot == null ? 0 : snapshot.scarMask());
		realisticdamage$hideDestroyedHand(state, destroyedMask, HumanoidArm.RIGHT);
		realisticdamage$hideDestroyedHand(state, destroyedMask, HumanoidArm.LEFT);
	}

	/**
	 * The first-person hand (1.6.1). Vanilla submits the bare arm part here and
	 * nothing else — no layers run — so the blood and scars on it are painted
	 * on afterwards, from the same meshes the third-person layer uses. See
	 * {@link FirstPersonBlood}.
	 */
	@Inject(method = "renderHand", at = @At("TAIL"))
	private void realisticdamage$paintFirstPersonArm(PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, Identifier skinTexture, ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
		PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
		FirstPersonBlood.submit(poseStack, collector, lightCoords, arm, arm == model.rightArm);
	}

	/**
	 * A destroyed arm in first person (1.6.2) is a stump, not an absence: the
	 * whole-arm submit is cancelled and the raised stump drawn in its place.
	 * {@code ItemInHandRendererMixin} is what lets this method be reached at all
	 * for a destroyed arm — it used to cancel the hand path outright.
	 */
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$stumpInsteadOfArm(PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, Identifier skinTexture, ModelPart arm, boolean hasSleeve, CallbackInfo ci) {
		PlayerModel model = ((AvatarRenderer<?>) (Object) this).getModel();
		boolean rightArm = arm == model.rightArm;
		if (FirstPersonBlood.armDestroyed(rightArm)) {
			arm.resetPose();
			arm.zRot = rightArm ? 0.1F : -0.1F;
			FirstPersonBlood.submitStump(poseStack, collector, lightCoords, arm, rightArm, skinTexture);
			ci.cancel();
		}
	}

	/**
	 * Vanilla carries held items in their own render states, outside the player
	 * model. Hiding an arm therefore does not hide what it was holding unless the
	 * matching physical hand is cleared here as well. This only edits the
	 * per-frame render state; the real stack remains in the player's inventory.
	 */
	@Unique
	private static void realisticdamage$hideDestroyedHand(AvatarRenderState state, int mask, HumanoidArm arm) {
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

		InteractionHand hand = arm == state.mainArm ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
		if (state.isUsingItem && state.useItemHand == hand) {
			state.heldOnHead.clear();
		}
	}

	/**
	 * The one place on this side that turns a body into limp rungs.
	 *
	 * <p>Four limbs and four branches per player per frame, which is nothing,
	 * and it buys the guarantee the whole feature rests on: the model reads the
	 * ladder from {@link LimpMath} and so does the prey score, so what a mob
	 * picks out and what a player sees cannot be two different opinions about
	 * the same leg. The core regions are skipped because a torso does not limp.
	 */
	@Unique
	private static int realisticdamage$limbRungs(BodySnapshot snapshot) {
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
