package dev.captience.realisticdamage.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;
import dev.captience.realisticdamage.body.BodyState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A destroyed arm in first person: no held item, and — since 1.6.2 — a stump
 * where the hand was rather than nothing at all.
 *
 * <p>The item goes because the hand that held it is gone. The arm itself is
 * routed down vanilla's own empty-hand path, {@code renderPlayerArm}, where
 * {@code AvatarRendererMixin} swaps the whole arm for the raised stump. Only
 * the main hand: vanilla never draws an empty off hand, and a stump that
 * appeared in the off-hand corner only once the arm was lost would be
 * stranger than its absence. Third-person item state is suppressed by
 * {@link AvatarRendererMixin}; the limb geometry by {@link PlayerModelMixin}.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Invoker("renderPlayerArm")
	abstract void realisticdamage$renderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector,
			int lightCoords, float inverseArmHeight, float attackValue, HumanoidArm arm);

	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$stumpForDestroyedArm(AbstractClientPlayer player, float frameInterp, float xRot,
			InteractionHand hand, float attack, ItemStack itemStack,
			float inverseArmHeight, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords,
			CallbackInfo ci) {
		BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
		if (snapshot == null) {
			return;
		}
		boolean mainHand = hand == InteractionHand.MAIN_HAND;
		HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
		BodyPart part = arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		if (!snapshot.destroyed(part)) {
			return;
		}
		// The same guards vanilla's own empty-hand branch applies.
		if (mainHand && !player.isInvisible() && !player.isScoping()) {
			poseStack.pushPose();
			realisticdamage$renderPlayerArm(poseStack, submitNodeCollector, lightCoords, inverseArmHeight, attack, arm);
			poseStack.popPose();
		}
		ci.cancel();
	}

	/** How many hands this frame were pushed into a droop or a shake and still owe a pop. */
	@Unique
	private int realisticdamage$pushed;

	/**
	 * A hand that will not grip, and a hand that shakes (1.7). A Disabled arm
	 * hangs: whatever it holds sits low and tilted at the edge of the view, which
	 * is the first-person half of "this arm is not working" and the reason the
	 * use you are about to try will fail. In the adrenaline crash both hands
	 * tremble. Declared after the stump handler, so a destroyed arm — which
	 * cancels there — never reaches this and never pushes.
	 */
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void realisticdamage$droopAndShake(AbstractClientPlayer player, float frameInterp, float xRot,
			InteractionHand hand, float attack, ItemStack itemStack,
			float inverseArmHeight, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords,
			CallbackInfo ci) {
		BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
		if (snapshot == null || player != Minecraft.getInstance().player) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
		BodyPart part = arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		boolean limp = snapshot.state(part) == BodyState.DISABLED
				&& dev.captience.realisticdamage.client.ClientEffectsConfig.get().showInjuryPose;
		double[] shake = dev.captience.realisticdamage.client.LowHealthEffects.handShake();
		if (!limp && shake == null) {
			return;
		}
		poseStack.pushPose();
		realisticdamage$pushed++;
		float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
		if (limp) {
			// Low and tilted, but still in view. Rotations pivot on the hand
			// (vanilla's resting hand position), not the camera: about the
			// camera, a dozen degrees swings the whole arm off the bottom of
			// the screen, which the first build did and which read as an
			// empty hand.
			poseStack.translate(side * 0.56F, -0.52F, -0.72F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(side * -12.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(-16.0F));
			poseStack.translate(side * -0.56F, 0.52F, 0.72F);
			poseStack.translate(0.0F, -0.08F, 0.0F);
		}
		if (shake != null) {
			poseStack.translate((float) shake[0], (float) shake[1], 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees((float) shake[2]));
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void realisticdamage$undoDroop(AbstractClientPlayer player, float frameInterp, float xRot,
			InteractionHand hand, float attack, ItemStack itemStack,
			float inverseArmHeight, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords,
			CallbackInfo ci) {
		if (realisticdamage$pushed > 0) {
			realisticdamage$pushed--;
			poseStack.popPose();
		}
	}
}
