package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.fieldkit.brew.HaemorrhageMath;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.fieldkit.medicine.Provisions;
import dev.captience.realisticdamage.fieldkit.medicine.ProvisionsMath;
import dev.captience.realisticdamage.systems.BleedingSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The brewed effects where blood is actually lost (2.0, phase 3).
 *
 * <ul>
 *   <li><b>Bandages and tourniquets</b> (phase 4) let through only their share of
 *   the wound they sit on.</li>
 *   <li><b>Haemorrhage</b> lets the stacked wounds bleed past the usual cap,
 *   and a hand pressed on them holds back only part of what it would.</li>
 *   <li><b>Adrenaline</b> races the heart, so whatever is open bleeds harder
 *   while it lasts.</li>
 *   <li><b>Coffee</b> and <b>spirits</b> (phase 4) do the same, by less.</li>
 * </ul>
 */
@Mixin(BleedingSystem.class)
abstract class FieldKitBleedMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/body/RealisticDamageData;effectiveBleedRate()D"))
	private static double realisticdamage$brewedRate(double rate,
			@Local(argsOnly = true) LivingEntity body, @Local(argsOnly = true) RealisticDamageData data) {
		boolean haemorrhage = BrewedEffects.active(body, BrewedEffects.HAEMORRHAGE);
		// What a bandage or a tourniquet lets through, wound by wound (phase 4).
		double[] rates = new double[data.wounds().size()];
		boolean dressed = false;
		for (int i = 0; i < rates.length; i++) {
			BleedWound wound = data.wounds().get(i);
			double factor = Dressings.factor(body, wound);
			dressed |= factor != 1.0;
			rates[i] = wound.bloodPerSecond() * factor;
		}
		if (haemorrhage || dressed) {
			rate = HaemorrhageMath.stackedRate(rates, haemorrhage
					? HaemorrhageMath.cap(BrewedEffects.level(body, BrewedEffects.HAEMORRHAGE))
					: Balance.WOUND_STACK_CAP);
		}
		if (BrewedEffects.active(body, BrewedEffects.ADRENALINE)) {
			rate *= Balance.ADRENALINE_BLEED_FACTOR;
		}
		return rate * ProvisionsMath.bleedFactor(Provisions.caffeinated(body), Provisions.tipsy(body));
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/body/RealisticDamageData;pressureMultiplier()D"))
	private static double realisticdamage$weakerHold(double multiplier, @Local(argsOnly = true) LivingEntity body) {
		return BrewedEffects.active(body, BrewedEffects.HAEMORRHAGE)
				? HaemorrhageMath.pressureMultiplier(multiplier) : multiplier;
	}
}
