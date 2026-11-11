package dev.captience.realisticdamage.client.compat.mixin;

import dev.captience.realisticdamage.client.StiffNeck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.MCVR;

/**
 * Vivecraft's smooth and snap turning (2.0 whiplash): every way it turns the
 * world happens inside {@code processBindings}, as a change to
 * {@code worldRotation}. What it turned by this frame is read off before and
 * after, and a stiff neck decides how much of it stands ({@link StiffNeck#vrTurn}).
 * The headset is never touched: nothing may turn a real head.
 */
@Mixin(value = MCVR.class, remap = false)
public abstract class VrTurnMixin {
	@Unique
	private float realisticdamage$rotationBefore;

	@Inject(method = "processBindings", at = @At("HEAD"))
	private void realisticdamage$before(CallbackInfo ci) {
		this.realisticdamage$rotationBefore = ClientDataHolderVR.getInstance().vrSettings.worldRotation;
	}

	@Inject(method = "processBindings", at = @At("RETURN"))
	private void realisticdamage$after(CallbackInfo ci) {
		var settings = ClientDataHolderVR.getInstance().vrSettings;
		float after = settings.worldRotation;
		if (after != this.realisticdamage$rotationBefore) {
			settings.worldRotation = StiffNeck.vrTurn(this.realisticdamage$rotationBefore, after);
		}
	}
}
