package dev.captience.realisticdamage.fieldkit.mixin;

import java.util.List;

import dev.captience.realisticdamage.damage.Flinches;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.fieldkit.brew.Numbing;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Numb, a blow lands without a flinch (2.0, phase 3). */
@Mixin(Flinches.class)
abstract class NumbingFlinchMixin {
	@Inject(method = "afterHit", at = @At("HEAD"), cancellable = true)
	private static void realisticdamage$noFlinch(LivingEntity body, DamageSource source,
			List<TraumaPipeline.AppliedTrauma> applied, double damage, CallbackInfo ci) {
		if (Numbing.numb(body)) {
			ci.cancel();
		}
	}
}
