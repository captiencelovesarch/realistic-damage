package dev.captience.realisticdamage.client.compat.mixin;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.LimbStateHolder;
import dev.captience.realisticdamage.client.compat.VrLimbs;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.render.VRPlayerModel_WithArmsLegs;

/**
 * The full-body-tracked VR model (1.7 alpha.11): split feet as well as split
 * hands, and its setup runs after the arms model's, so both are hidden again
 * here. See {@link VrPlayerModelArmsMixin}.
 */
@Mixin(VRPlayerModel_WithArmsLegs.class)
public abstract class VrPlayerModelLegsMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void realisticdamage$hideLostLimbs(AvatarRenderState state, CallbackInfo ci) {
		VRPlayerModel_WithArmsLegs model = (VRPlayerModel_WithArmsLegs) (Object) this;
		int mask = ((LimbStateHolder) state).realisticdamage$destroyedMask();
		VrLimbs.hideLostHands(model, mask);
		if (VrLimbs.gone(mask, BodyPart.LEFT_LEG)) {
			model.leftLeg.visible = false;
			model.leftPants.visible = false;
			model.leftFoot.visible = false;
			model.leftFootPants.visible = false;
		}
		if (VrLimbs.gone(mask, BodyPart.RIGHT_LEG)) {
			model.rightLeg.visible = false;
			model.rightPants.visible = false;
			model.rightFoot.visible = false;
			model.rightFootPants.visible = false;
		}
	}
}
