package dev.captience.realisticdamage.fieldkit.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.client.ConcussionEffects;
import dev.captience.realisticdamage.fieldkit.medicine.Goggles;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Behind tinted goggles (2.0, phase 4 round 2) a concussion stops wincing at
 * the light: no glare veil, no squint, as the server stops the flare.
 */
@Mixin(ConcussionEffects.class)
abstract class GogglesGlareMixin {
	@ModifyReturnValue(method = "brightnessPain", at = @At("RETURN"))
	private static double realisticdamage$shaded(double pain, @Local(argsOnly = true) LocalPlayer player) {
		return Goggles.worn(player) ? 0.0 : pain;
	}
}
