package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sedation through the existing consciousness system (2.0, phase 3), so every
 * symptom of a fading mind — the vignette, the slow hands, the microsleeps, the
 * collapse, the wake — is the one the mod already has.
 *
 * <ul>
 *   <li>Each stage holds consciousness under its cap, before the tick runs; at
 *   zero the tick's own collapse takes the body down, and it wakes the
 *   ordinary way once the cap lets it back over the wake bar.</li>
 *   <li>An overdose stops the breathing: the body counts as starved of air,
 *   so the hypoxia debt builds exactly as it does under water.</li>
 * </ul>
 */
@Mixin(ConsciousnessSystem.class)
abstract class SedationConsciousnessMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private static void realisticdamage$hold(LivingEntity body, RealisticDamageData data, CallbackInfo ci) {
		if (!Half.WEAPONS.on()) {
			return;
		}
		double cap = SedationMath.cap(Sedation.stage(body));
		if (data.consciousness() > cap) {
			data.setConsciousness(cap);
		}
	}

	@Inject(method = "starved", at = @At("RETURN"), cancellable = true)
	private static void realisticdamage$notBreathing(LivingEntity body, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && Sedation.overdosed(body)) {
			cir.setReturnValue(true);
		}
	}
}
