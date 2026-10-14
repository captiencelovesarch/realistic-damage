package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.GenericLimbs;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Around every model draw (1.7 slice 3): lost limbs hidden and region draws
 * isolated after the model is posed for its submit, and put back after it
 * renders. See {@link GenericLimbs}.
 */
@Mixin(ModelFeatureRenderer.class)
public abstract class ModelFeatureRendererMixin {
	@Inject(method = "prepareModel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/Model;setupAnim(Ljava/lang/Object;)V",
					shift = At.Shift.AFTER))
	private void realisticdamage$afterPose(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		GenericLimbs.afterPose(submit.model(), submit.state());
	}

	@Inject(method = "prepareModel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
					shift = At.Shift.AFTER))
	private void realisticdamage$afterRender(ModelFeatureRenderer.Submit<?> submit, CallbackInfo ci) {
		GenericLimbs.afterRender();
	}
}
