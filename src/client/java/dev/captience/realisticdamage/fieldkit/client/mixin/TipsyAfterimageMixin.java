package dev.captience.realisticdamage.fieldkit.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.captience.realisticdamage.client.Afterimage;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.medicine.Provisions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Spirits blur the view (2.0, phase 4): the world doubles as it moves, through
 * the core's own afterimage, as a concussion doubles it. So it is the same
 * symptom, behind the same slider, and VR-safe the same way.
 */
@Mixin(Afterimage.class)
abstract class TipsyAfterimageMixin {
	@ModifyExpressionValue(method = "frame", at = @At(value = "INVOKE",
			target = "Ldev/captience/realisticdamage/client/ConcussionEffects;doubled()D"))
	private static double realisticdamage$tipsy(double doubled) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && player.hasEffect(Provisions.TIPSY)
				? Math.max(doubled, Balance.SPIRITS_AFTERIMAGE) : doubled;
	}
}
