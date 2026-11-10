package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A skeleton without its drawing arm cannot loose an arrow, and one with a
 * broken arm or a rattled head looses it wide (1.7).
 */
@Mixin(AbstractSkeleton.class)
public abstract class SkeletonShootMixin {
	@Inject(method = "performRangedAttack", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$noArmNoShot(LivingEntity target, float power, CallbackInfo ci) {
		// Nor one that is down.
		if (!MobAnatomy.canShoot((Mob) (Object) this) || MobAnatomy.down((Mob) (Object) this)) {
			ci.cancel();
		}
	}

	/** The last argument of the shoot is vanilla's inaccuracy; the body multiplies it. */
	@ModifyArg(method = "performRangedAttack",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;spawnProjectileUsingShoot(Lnet/minecraft/world/entity/projectile/Projectile;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;DDDFF)Lnet/minecraft/world/entity/projectile/Projectile;"),
			index = 7)
	private float realisticdamage$shootWide(float uncertainty) {
		return (float) (uncertainty * MobAnatomy.aimSpread((Mob) (Object) this));
	}
}
