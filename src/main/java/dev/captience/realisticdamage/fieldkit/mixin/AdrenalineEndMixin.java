package dev.captience.realisticdamage.fieldkit.mixin;

import java.util.Collection;

import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When Adrenaline ends, however it ends — run out, milk, a switch turned off —
 * the drunk surge ends with it and the body crashes (2.0, phase 3).
 */
@Mixin(LivingEntity.class)
abstract class AdrenalineEndMixin {
	@Inject(method = "onEffectsRemoved", at = @At("HEAD"))
	private void realisticdamage$endSurge(Collection<MobEffectInstance> effects, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		for (MobEffectInstance effect : effects) {
			if (effect.is(BrewedEffects.ADRENALINE)) {
				BrewedEffects.adrenalineRemoved(self);
			}
		}
	}
}
