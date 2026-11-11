package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.fieldkit.brew.HaemorrhageMath;
import dev.captience.realisticdamage.systems.BleedingSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The brewed effects where blood is actually lost (2.0, phase 3).
 *
 * <ul>
 *   <li><b>Haemorrhage</b> lets the stacked wounds bleed past the usual cap,
 *   and a hand pressed on them holds back only part of what it would.</li>
 *   <li><b>Adrenaline</b> races the heart, so whatever is open bleeds harder
 *   while it lasts.</li>
 * </ul>
 */
@Mixin(BleedingSystem.class)
abstract class FieldKitBleedMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/body/RealisticDamageData;effectiveBleedRate()D"))
	private static double realisticdamage$brewedRate(double rate,
			@Local(argsOnly = true) LivingEntity body, @Local(argsOnly = true) RealisticDamageData data) {
		if (BrewedEffects.active(body, BrewedEffects.HAEMORRHAGE)) {
			double[] rates = data.wounds().stream().mapToDouble(BleedWound::bloodPerSecond).toArray();
			rate = HaemorrhageMath.stackedRate(rates,
					HaemorrhageMath.cap(BrewedEffects.level(body, BrewedEffects.HAEMORRHAGE)));
		}
		if (BrewedEffects.active(body, BrewedEffects.ADRENALINE)) {
			rate *= Balance.ADRENALINE_BLEED_FACTOR;
		}
		return rate;
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/body/RealisticDamageData;pressureMultiplier()D"))
	private static double realisticdamage$weakerHold(double multiplier, @Local(argsOnly = true) LivingEntity body) {
		return BrewedEffects.active(body, BrewedEffects.HAEMORRHAGE)
				? HaemorrhageMath.pressureMultiplier(multiplier) : multiplier;
	}
}
