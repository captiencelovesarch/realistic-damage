package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.captience.realisticdamage.fieldkit.medicine.Medicine;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A hand whose arm is in a sling (2.0, phase 4 round 2) is held like one that
 * can't grip: nothing in it can be used, a use under way stops, and a bow
 * wants both hands. The core's grip does the refusing; this only adds the sling.
 */
@Mixin(ImpairmentSystem.class)
abstract class SlingGripMixin {
	@ModifyReturnValue(method = "gripBlocked", at = @At("RETURN"))
	private static InteractionHand realisticdamage$sling(InteractionHand blocked,
			@Local(argsOnly = true) ServerPlayer player, @Local(argsOnly = true) InteractionHand hand) {
		return blocked != null ? blocked : Medicine.slungHand(player, hand);
	}
}
