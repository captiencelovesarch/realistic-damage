package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A splint (2.0, phase 4 round 2) halves what a broken limb costs: a leg's
 * limp (and with it the jump), the main arm's slow swing, dig and blow, and
 * either arm's share of a bow's or crossbow's stalled draw.
 *
 * <p>The per-leg penalty is a private helper with no body in hand, so the body
 * being ticked is kept for it here for the length of the tick.
 */
@Mixin(ImpairmentSystem.class)
abstract class SplintPenaltyMixin {
	@Unique
	private static LivingEntity realisticdamage$ticking;

	@Inject(method = "tick", at = @At("HEAD"))
	private static void realisticdamage$enter(LivingEntity body, RealisticDamageData data, CallbackInfo ci) {
		realisticdamage$ticking = body;
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private static void realisticdamage$leave(LivingEntity body, RealisticDamageData data, CallbackInfo ci) {
		realisticdamage$ticking = null;
	}

	@ModifyExpressionValue(method = "legPenalty", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/systems/ImpairmentMath;legPenalty(DZ)D"))
	private static double realisticdamage$splintedLeg(double penalty, @Local(argsOnly = true) BodyPartData leg) {
		LivingEntity body = realisticdamage$ticking;
		return body != null && Dressings.splinted(body, leg.part()) ? penalty * Balance.SPLINT_PENALTY_FACTOR : penalty;
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/systems/ImpairmentMath;armPenalty(DZ)D"))
	private static double realisticdamage$splintedArm(double penalty, @Local(argsOnly = true) LivingEntity body) {
		return Dressings.splinted(body, ImpairmentSystem.armFor(body, true))
				? penalty * Balance.SPLINT_PENALTY_FACTOR : penalty;
	}

	@ModifyExpressionValue(method = "twoHandedPenalty", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/systems/ImpairmentMath;armPenalty(DZ)D", ordinal = 0))
	private static double realisticdamage$splintedMainDraw(double penalty, @Local(argsOnly = true) Player player) {
		return Dressings.splinted(player, ImpairmentSystem.armFor(player, true))
				? penalty * Balance.SPLINT_PENALTY_FACTOR : penalty;
	}

	@ModifyExpressionValue(method = "twoHandedPenalty", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/systems/ImpairmentMath;armPenalty(DZ)D", ordinal = 1))
	private static double realisticdamage$splintedOffDraw(double penalty, @Local(argsOnly = true) Player player) {
		return Dressings.splinted(player, ImpairmentSystem.armFor(player, false))
				? penalty * Balance.SPLINT_PENALTY_FACTOR : penalty;
	}
}
