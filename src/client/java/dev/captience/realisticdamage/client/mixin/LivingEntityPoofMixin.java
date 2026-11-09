package dev.captience.realisticdamage.client.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No poof of smoke where a ragdoll died when this client keeps the body
 * (phase 4): the body is not there any more, and it is not going.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPoofMixin {
	@Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$noPoofForKeptBodies(byte id, CallbackInfo ci) {
		if (id == 60 && dev.captience.realisticdamage.client.RemoteCorpses.keeping((LivingEntity) (Object) this)) {
			ci.cancel();
		}
	}
}
