package dev.captience.realisticdamage.client.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A simulated body is drawn wherever it went, not culled by the box of the
 * spot it died on: a body thrown out of view of that spot would otherwise
 * vanish in mid-air (or never appear where it landed).
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererCullMixin {
	@Inject(method = "affectedByCulling", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$drawSimulatedBodies(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (dev.captience.realisticdamage.client.RemoteCorpses.of(entity) != null) {
			cir.setReturnValue(false);
		}
	}
}
