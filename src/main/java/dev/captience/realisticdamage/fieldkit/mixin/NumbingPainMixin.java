package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.fieldkit.brew.Numbing;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Numb, a hurt limb costs nothing (2.0, phase 3): no limp from a sore leg and no
 * slow swing from a sore arm. What pain could not cause stays: a leg that is
 * gone is still gone (crawling), an arm that cannot grip still cannot.
 * Spirits (phase 4) take half of each instead of all of it.
 */
@Mixin(ImpairmentSystem.class)
abstract class NumbingPainMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/systems/ImpairmentSystem;walkingPenalty(Ldev/captience/realisticdamage/body/BodyPlan;Ldev/captience/realisticdamage/body/RealisticDamageData;)D"))
	private static double realisticdamage$noLimp(double penalty, @Local(argsOnly = true) LivingEntity body) {
		return Numbing.numb(body) ? 0.0 : penalty * spirits(body);
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/systems/ImpairmentMath;armPenalty(DZ)D"))
	private static double realisticdamage$noSoreArm(double penalty, @Local(argsOnly = true) LivingEntity body) {
		return Numbing.numb(body) ? 0.0 : penalty * spirits(body);
	}

	/** Spirits (phase 4) are a weaker numbing: the pain is there, with its edge off. */
	private static double spirits(LivingEntity body) {
		return dev.captience.realisticdamage.fieldkit.medicine.ProvisionsMath.painFactor(
				dev.captience.realisticdamage.fieldkit.medicine.Provisions.tipsy(body));
	}
}
