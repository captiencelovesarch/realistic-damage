package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A destroyed arm is gone in first person too — no floating hand, no held item.
 * The third-person model is handled separately by PlayerModelMixin.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$hideDestroyedArm(AbstractClientPlayer player, float frameInterp, float xRot,
			InteractionHand hand, float attack, net.minecraft.world.item.ItemStack itemStack,
			float inverseArmHeight, com.mojang.blaze3d.vertex.PoseStack poseStack,
			net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, int lightCoords,
			CallbackInfo ci) {
		BodySnapshot snapshot = player.getAttached(RDAttachments.SNAPSHOT);
		if (snapshot == null) {
			return;
		}
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND
				? player.getMainArm()
				: player.getMainArm().getOpposite();
		BodyPart part = arm == HumanoidArm.RIGHT ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
		if (snapshot.destroyed(part)) {
			ci.cancel();
		}
	}
}
