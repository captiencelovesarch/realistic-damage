package dev.captience.realisticdamage.fieldkit.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.fieldkit.client.GearHolder;
import dev.captience.realisticdamage.fieldkit.client.SlungArm;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * An arm in a sling is held in it (items.7): forward and across the belly,
 * still, whatever the walk and the hands are doing. Right after the model has
 * been posed for the frame, so after every model's own animation (a zombie
 * raises its arms in its own setupAnim, after the humanoid one) and after the
 * core's injury pose, which would let a dead arm hang straight out of its
 * sling.
 */
@Mixin(LivingEntityRenderer.class)
abstract class SlingPoseMixin {
	@Shadow
	protected EntityModel<?> model;

	@Inject(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
					shift = At.Shift.AFTER))
	private void realisticdamage$inTheSling(LivingEntityRenderState state, PoseStack poseStack,
			SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		if (!(state instanceof GearHolder gear) || gear.realisticdamage$slings() == 0
				|| !(this.model instanceof HumanoidModel<?> humanoid)) {
			return;
		}
		if (GearHolder.has(gear.realisticdamage$slings(), BodyPart.RIGHT_ARM)) {
			SlungArm.pose(humanoid.rightArm, humanoid.body.xRot, true);
		}
		if (GearHolder.has(gear.realisticdamage$slings(), BodyPart.LEFT_ARM)) {
			SlungArm.pose(humanoid.leftArm, humanoid.body.xRot, false);
		}
	}
}
