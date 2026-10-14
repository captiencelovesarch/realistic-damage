package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A snow golem throws with its stick arm; without it, no snowball (1.7 slice 3). */
@Mixin(SnowGolem.class)
public abstract class SnowGolemThrowMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$noArmNoThrow(LivingEntity target, float power, CallbackInfo ci) {
		if (!MobAnatomy.canShoot((SnowGolem) (Object) this)) {
			ci.cancel();
		}
	}
}
