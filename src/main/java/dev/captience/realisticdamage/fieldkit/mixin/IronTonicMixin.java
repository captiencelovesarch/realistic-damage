package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.medicine.Provisions;
import dev.captience.realisticdamage.systems.HealingSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * An iron tonic (2.0, phase 4): the Blood a body makes back on its own, slow and
 * faster while well fed, comes twice as fast. It still waits for the bleeding
 * to stop, as it always does.
 */
@Mixin(HealingSystem.class)
abstract class IronTonicMixin {
	@ModifyArg(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/body/RealisticDamageData;restoreBlood(D)V", ordinal = 0))
	private static double realisticdamage$tonic(double amount, @Local(argsOnly = true) LivingEntity body) {
		return Provisions.tonic(body) ? amount * Balance.IRON_TONIC_REGEN_FACTOR : amount;
	}
}
