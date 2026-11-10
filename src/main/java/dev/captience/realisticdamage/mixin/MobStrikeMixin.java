package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A mob with both arms gone has nothing to swing (1.7). */
@Mixin(Mob.class)
public abstract class MobStrikeMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$noArmsNoStrike(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		// Nor one that is down: out cold or knocked flat, it strikes at nothing.
		if (!MobAnatomy.canStrike((Mob) (Object) this) || MobAnatomy.down((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
