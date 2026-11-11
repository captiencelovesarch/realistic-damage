package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.damage.CeilingLogic;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.systems.CeilingMath;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A break lowers a limb's ceiling the moment it happens (long-term injuries).
 * The field kit remembers what it was (2.0, phase 4 round 2), so a splint on
 * the limb when it knits can give it back.
 */
@Mixin(CeilingLogic.class)
abstract class SplintCeilingMixin {
	@WrapOperation(method = "evaluate", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/body/BodyPartData;lowerCeilingTo(I)Z"))
	private static boolean realisticdamage$remember(BodyPartData part, int band, Operation<Boolean> original,
			@Local CeilingMath.Event event, @Local(argsOnly = true) LivingEntity body) {
		int prior = part.ceilingBand();
		boolean lowered = original.call(part, band);
		if (lowered && event == CeilingMath.Event.BROKEN && Half.MEDICINE.on()) {
			Dressings.owe(body, part.part(), prior);
		}
		return lowered;
	}
}
