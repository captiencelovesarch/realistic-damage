package dev.captience.realisticdamage.client.compat.mixin;

import dev.captience.realisticdamage.client.LimbStateHolder;
import dev.captience.realisticdamage.client.compat.VrLimbs;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.render.VRPlayerModel_WithArms;

/**
 * A VR player's body with split arms (1.7 alpha.11). Vivecraft draws the
 * forearm and hand as parts of their own, next to the vanilla arm that
 * {@code PlayerModelMixin} hides, and sets their sleeves visible again after
 * vanilla's pass — so a lost arm left its hand, sleeve and all, hanging where
 * the controller was. Hidden here, at the end of Vivecraft's own setup.
 */
@Mixin(VRPlayerModel_WithArms.class)
public abstract class VrPlayerModelArmsMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void realisticdamage$hideLostHands(AvatarRenderState state, CallbackInfo ci) {
		VrLimbs.hideLostHands((VRPlayerModel_WithArms) (Object) this, ((LimbStateHolder) state).realisticdamage$destroyedMask());
	}
}
