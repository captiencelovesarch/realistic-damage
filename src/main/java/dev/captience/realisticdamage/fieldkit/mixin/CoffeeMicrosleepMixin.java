package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.fieldkit.medicine.Provisions;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Coffee keeps the microsleeps away (2.0, phase 4): a caffeinated mind fades as
 * it would, and doesn't blink out. One already under way runs its course, and
 * the clock towards the next unwinds as it does for a mind that is well, so
 * the coffee wearing off doesn't drop you at once on the credit built up.
 */
@Mixin(ConsciousnessSystem.class)
abstract class CoffeeMicrosleepMixin {
	@Inject(method = "tickMicrosleep", at = @At("HEAD"), cancellable = true)
	private static void realisticdamage$awake(ServerPlayer player, RealisticDamageData data, CallbackInfo ci) {
		if (!data.microsleeping() && Provisions.caffeinated(player)) {
			data.setMicrosleepPhase(Math.max(0.0, data.microsleepPhase() - 0.02));
			ci.cancel();
		}
	}
}
