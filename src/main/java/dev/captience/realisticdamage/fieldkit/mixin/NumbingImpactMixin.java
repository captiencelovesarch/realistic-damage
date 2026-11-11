package dev.captience.realisticdamage.fieldkit.mixin;

import java.util.List;

import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.damage.DamageProfile;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.fieldkit.brew.Numbing;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Numb, a hit doesn't blur or ring: the impact that is pain is not sent (2.0, phase 3). */
@Mixin(TraumaPipeline.class)
abstract class NumbingImpactMixin {
	@Inject(method = "sendImpact", at = @At("HEAD"), cancellable = true)
	private static void realisticdamage$noPainBlur(ServerPlayer player, DamageSource source,
			List<TraumaPipeline.AppliedTrauma> traumas, double appliedDamage, DamageProfile profile,
			DamageCategory category, CallbackInfo ci) {
		if (Numbing.numb(player)) {
			ci.cancel();
		}
	}
}
