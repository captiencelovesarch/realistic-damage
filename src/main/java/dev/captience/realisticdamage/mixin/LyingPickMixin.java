package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.util.LyingBody;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A body lying down is picked out along its length (items.6): the crosshair,
 * a reaching hand, finds it across about two-thirds of its standing height,
 * though its collision box stays as narrow as it stands. Only the pick: what
 * it bumps into, and what it fits through, are unchanged.
 */
@Mixin(Entity.class)
abstract class LyingPickMixin {
	@Inject(method = "getPickRadius", at = @At("RETURN"), cancellable = true)
	private void realisticdamage$alongTheBody(CallbackInfoReturnable<Float> cir) {
		if (this instanceof LyingBody lying && lying.realisticdamage$lying()) {
			LivingEntity body = (LivingEntity) (Object) this;
			EntityDimensions standing = body.getType().getDimensions().scale(body.getScale());
			float reach = (Math.min(standing.height() * 0.6F, 1.4F) - standing.width()) * 0.5F + 0.15F;
			if (reach > cir.getReturnValueF()) {
				cir.setReturnValue(reach);
			}
		}
	}
}
