package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.fieldkit.brew.HaemorrhageMath;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Haemorrhage where a wound is opened (2.0, phase 3): the hit is judged
 * against lowered thresholds, and what opens is a step worse. Armour still takes
 * its step back afterwards, so a good helmet still counts.
 *
 * <p>A mixin on the core pipeline rather than a call in it, so the core mod
 * does not know the field kit exists.
 */
@Mixin(TraumaPipeline.class)
abstract class HaemorrhageWoundMixin {
	@ModifyArg(method = "createWounds", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/damage/DamageProfile;woundFor(D)Ldev/captience/realisticdamage/body/WoundSeverity;"))
	private static double realisticdamage$lowerThreshold(double conditionDamage,
			@Local(argsOnly = true) LivingEntity body) {
		int level = BrewedEffects.active(body, BrewedEffects.HAEMORRHAGE)
				? BrewedEffects.level(body, BrewedEffects.HAEMORRHAGE) : -1;
		return level < 0 ? conditionDamage : HaemorrhageMath.judgedDamage(conditionDamage, level);
	}

	@ModifyExpressionValue(method = "createWounds", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/damage/ArmorModifier;downgradeWound(Lnet/minecraft/world/entity/LivingEntity;Ldev/captience/realisticdamage/body/BodyPart;Ldev/captience/realisticdamage/damage/DamageCategory;Ldev/captience/realisticdamage/body/WoundSeverity;)Ldev/captience/realisticdamage/body/WoundSeverity;"))
	private static WoundSeverity realisticdamage$worse(WoundSeverity severity,
			@Local(argsOnly = true) LivingEntity body) {
		return BrewedEffects.active(body, BrewedEffects.HAEMORRHAGE) ? HaemorrhageMath.worse(severity) : severity;
	}
}
