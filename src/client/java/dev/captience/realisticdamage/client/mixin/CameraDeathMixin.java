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

	@Shadow
	protected abstract void setPosition(net.minecraft.world.phys.Vec3 position);

	@Shadow
	private net.minecraft.world.phys.Vec3 position;

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	/** Your own death, riding the ragdoll (phase 4): the eyes and the turn of its head. */
	@org.spongepowered.asm.mixin.injection.Inject(method = "update", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
	private void realisticdamage$rideRagdoll(net.minecraft.client.DeltaTracker deltaTracker,
			org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (dev.captience.realisticdamage.client.RagdollView.orbiting()) {
			var shift = dev.captience.realisticdamage.client.RagdollView.orbitShift(
					deltaTracker.getGameTimeDeltaPartialTick(false));
			if (shift != null) {
				this.setPosition(this.position.add(shift));
			}
			return;
		}
		if (!dev.captience.realisticdamage.client.RagdollView.active()) {
			return;
		}
		var eye = dev.captience.realisticdamage.client.RagdollView.eye(deltaTracker.getGameTimeDeltaPartialTick(false));
		if (eye == null) {
			return;
		}
		this.setRotation(dev.captience.realisticdamage.client.RagdollView.yaw(),
				dev.captience.realisticdamage.client.RagdollView.pitch());
		this.setPosition(eye);
	}

	@ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;getEyeHeight()F"))
	private float realisticdamage$collapse(float standing) {
		return DeathCamera.eyeHeight(this.entity, standing);
	}
}
