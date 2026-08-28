package dev.captience.realisticdamage.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import dev.captience.realisticdamage.client.MouseInertia;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Fading consciousness makes looking around sluggish (spec §23-24). Scales the
 * single sensitivity read inside {@code turnPlayer}, so smooth-camera and
 * scoping stay consistent. Capped at 35% — the mouse must never feel broken,
 * and the whole effect is disableable client-side.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@ModifyExpressionValue(
			method = "turnPlayer",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
	private Object realisticdamage$dampenSensitivity(Object original) {
		if (!(original instanceof Double sensitivity) || !ClientState.active()) {
			return original;
		}
		double intensity = ClientEffectsConfig.get().sensitivityReductionIntensity;
		if (intensity <= 0.0) {
			return original;
		}
		double ramp = ClientState.ramp(Balance.SYMPTOM_SENSITIVITY, 0.0);
		if (ramp <= 0.0) {
			return original;
		}
		double reduction = ramp * Balance.SENSITIVITY_REDUCTION_CAP * Math.min(1.0, intensity);
		return sensitivity * (1.0 - reduction);
	}

	/**
	 * Feeds mouse input through the momentum buffer so aiming goes soft and
	 * slippery as consciousness fades (see MouseInertia).
	 */
	@ModifyArgs(method = "turnPlayer",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void realisticdamage$meltAiming(Args args) {
		double[] filtered = MouseInertia.absorb(args.<Double>get(0), args.<Double>get(1));
		if (filtered != null) {
			args.set(0, filtered[0]);
			args.set(1, filtered[1]);
		}
	}
}
