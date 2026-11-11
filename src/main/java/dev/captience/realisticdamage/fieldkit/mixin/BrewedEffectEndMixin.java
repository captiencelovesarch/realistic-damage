package dev.captience.realisticdamage.fieldkit.mixin;

import java.util.Collection;

import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When a brewed effect ends, however it ends — run out, milk, a switch turned
 * off — what it holds in the body goes with it (2.0, phase 3): a drunk
 * Adrenaline surge crashes, a Sedation dose is gone. What either already did
 * stays done.
 */
@Mixin(LivingEntity.class)
abstract class BrewedEffectEndMixin {
	@Inject(method = "onEffectsRemoved", at = @At("HEAD"))
	private void realisticdamage$effectEnded(Collection<MobEffectInstance> effects, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.level().isClientSide()) {
			return;
		}
		for (MobEffectInstance effect : effects) {
			if (effect.is(BrewedEffects.ADRENALINE)) {
				BrewedEffects.adrenalineRemoved(self);
			} else if (effect.is(BrewedEffects.SEDATION)) {
				Sedation.removed(self);
			}
		}
	}
}
