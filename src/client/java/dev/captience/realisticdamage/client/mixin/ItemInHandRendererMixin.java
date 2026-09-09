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
import org.spongepowered.asm.mixin.Mixin;
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
}
