package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.Knockdowns;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Stumbling on Sedation, small things knock you down (2.0, phase 3): the
 * violence a blow needs to put a body on the ground drops to almost nothing.
 * A blast or a heavy hitter already had its own, lower bar.
 */
@Mixin(Knockdowns.class)
abstract class SedationKnockdownMixin {
	@ModifyConstant(method = "afterHit", constant = @Constant(doubleValue = Balance.KNOCKDOWN_BLOW_VIOLENCE))
	private static double realisticdamage$unsteady(double bar, @Local(argsOnly = true) LivingEntity body) {
		return Half.WEAPONS.on() && Sedation.stage(body) == SedationMath.Stage.STUMBLING
				? Balance.SEDATION_STUMBLING_KNOCKDOWN : bar;
	}
}
