package dev.captience.realisticdamage.fieldkit.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.client.BloodEmitter;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.RulesView;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A dressed wound looks dressed (2.0, phase 4): the blood a body throws from a
 * bandaged wound drips at the bandage's share, and from a tourniqueted limb not
 * at all. Every read of the wound's rate in the emitter is scaled, so the
 * drips, the spurts and the arterial beat all agree.
 */
@Mixin(BloodEmitter.class)
abstract class DressedBloodMixin {
	@ModifyExpressionValue(method = "emitFor", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/body/WoundSeverity;bloodPerSecond()D"))
	private static double realisticdamage$dressed(double rate, @Local(argsOnly = true) LivingEntity body,
			@Local BodyPart part, @Local WoundSeverity wound) {
		if (!RulesView.on(Half.MEDICINE)) {
			return rate;
		}
		return rate * Dressings.factor(body.getAttached(Dressings.STATE), part, wound);
	}
}
