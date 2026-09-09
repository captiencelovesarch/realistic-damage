package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A skeleton without its drawing arm cannot loose an arrow (1.7). */
@Mixin(AbstractSkeleton.class)
public abstract class SkeletonShootMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$noArmNoShot(LivingEntity target, float power, CallbackInfo ci) {
		if (!MobAnatomy.canShoot((Mob) (Object) this)) {
			ci.cancel();
		}
	}
}
