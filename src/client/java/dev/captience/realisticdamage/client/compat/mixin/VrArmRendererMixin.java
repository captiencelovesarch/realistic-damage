package dev.captience.realisticdamage.client.compat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.client.FirstPersonBlood;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.provider.ControllerType;
import org.vivecraft.client_vr.render.VRArmRenderer;

/**
 * The hands in the headset (1.7 alpha.11). Vivecraft draws each controller's
 * arm through its own renderer rather than vanilla's first-person hand, so
 * neither of the things {@code AvatarRendererMixin} does there reached VR: a
 * lost arm was drawn whole, and an arm's blood and scars were never painted.
 * Both are done here, with the same calls flat play makes — the stump raised
 * along the forearm so its cut end sits just short of where the hand was.
 */
@Mixin(VRArmRenderer.class)
public abstract class VrArmRendererMixin {
	@Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$stump(ControllerType side, PoseStack poseStack,
			SubmitNodeCollector collector, int lightCoords, Identifier skin, ModelPart arm, boolean sleeve,
			CallbackInfo ci) {
		boolean rightArm = arm == ((PlayerModel) ((VRArmRenderer) (Object) this).getModel()).rightArm;
		if (FirstPersonBlood.armDestroyed(rightArm)) {
			arm.resetPose();
			FirstPersonBlood.submitStump(poseStack, collector, lightCoords, arm, rightArm, skin);
			ci.cancel();
		}
	}

	@Inject(method = "renderHand", at = @At("TAIL"))
	private void realisticdamage$blood(ControllerType side, PoseStack poseStack,
			SubmitNodeCollector collector, int lightCoords, Identifier skin, ModelPart arm, boolean sleeve,
			CallbackInfo ci) {
		boolean rightArm = arm == ((PlayerModel) ((VRArmRenderer) (Object) this).getModel()).rightArm;
		FirstPersonBlood.submit(poseStack, collector, lightCoords, arm, rightArm);
	}
}
