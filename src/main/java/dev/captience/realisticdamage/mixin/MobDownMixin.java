package dev.captience.realisticdamage.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A mob that is down (out cold, or knocked flat) does nothing of its own: its
 * AI step is skipped, as for a body that is dying, but it still falls, slides
 * and is thrown. Read at the one place the step asks, not by overriding the
 * question: a horse and a fox answer it themselves.
 */
@Mixin(LivingEntity.class)
public abstract class MobDownMixin {
	@ModifyExpressionValue(method = "aiStep",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isImmobile()Z"))
	private boolean realisticdamage$downDoesNothing(boolean immobile) {
		return immobile || MobAnatomy.down((LivingEntity) (Object) this);
	}
}
