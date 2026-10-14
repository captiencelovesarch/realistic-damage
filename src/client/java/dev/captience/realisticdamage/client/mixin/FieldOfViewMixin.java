package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.LowHealthEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tunnel vision in the adrenaline surge (1.7). The surge's own speed bonus
 * widens vanilla's field of view, which is the opposite of what adrenaline does
 * to sight; this narrows it back past where it started.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class FieldOfViewMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void realisticdamage$tunnel(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		float factor = LowHealthEffects.fovFactor();
		if (factor < 0.999F) {
			cir.setReturnValue(cir.getReturnValueF() * factor);
		}
	}
}
