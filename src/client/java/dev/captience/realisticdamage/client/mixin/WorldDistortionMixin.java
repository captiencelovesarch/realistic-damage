package dev.captience.realisticdamage.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.captience.realisticdamage.client.VisionEffects;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Real world-space distortion as consciousness fails, by feeding vanilla's own
 * nausea warp. Riding the vanilla value means it inherits the projection-matrix
 * skew and, importantly, the player's "Distortion Effects" accessibility slider.
 */
@Mixin(GameRenderer.class)
public abstract class WorldDistortionMixin {
	@ModifyExpressionValue(
			method = "renderLevel",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
	private float realisticdamage$addInjuryWarp(float vanilla) {
		return Math.max(vanilla, (float) VisionEffects.distortion());
	}
}
