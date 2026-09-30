package dev.captience.realisticdamage.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.captience.realisticdamage.client.DeathCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The collapse (1.7): the eye height the camera eases toward sinks to the
 * floor while the player is dead. Rides the camera's own per-tick ease rather
 * than the entity's eye height, which gameplay reads.
 */
@Mixin(Camera.class)
public abstract class CameraDeathMixin {
	@Shadow
	private Entity entity;

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
	private float realisticdamage$collapse(float standing) {
		return DeathCamera.eyeHeight(this.entity, standing);
	}
}
