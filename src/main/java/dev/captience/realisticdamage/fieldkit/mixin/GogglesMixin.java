package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.fieldkit.medicine.Goggles;
import dev.captience.realisticdamage.systems.ConcussionSystem;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Behind tinted goggles (2.0, phase 4 round 2), bright light stops flaring a
 * concussion. The dark still soothes only in the dark: goggles are not a dark room.
 */
@Mixin(ConcussionSystem.class)
abstract class GogglesMixin {
	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/systems/ConcussionMath;lightFlarePerSecond(I)D"))
	private static double realisticdamage$shaded(double flare, @Local(argsOnly = true) LivingEntity body) {
		return Goggles.worn(body) ? 0.0 : flare;
	}
}
