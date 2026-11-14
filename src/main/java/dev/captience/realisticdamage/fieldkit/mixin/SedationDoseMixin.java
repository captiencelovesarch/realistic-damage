package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every Sedation application is a dose (2.0, phase 3), counted where vanilla
 * accepts an effect, after the body's own refusals: a second arrow adds to the
 * first even though vanilla only keeps the longer of the two instances.
 */
@Mixin(LivingEntity.class)
abstract class SedationDoseMixin {
	@Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/effect/MobEffectInstance;onEffectStarted(Lnet/minecraft/world/entity/LivingEntity;)V"))
	private void realisticdamage$dose(MobEffectInstance effect, @Nullable Entity source,
			CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && effect.is(BrewedEffects.SEDATION)) {
			Sedation.dose(self, effect);
		}
	}
}
