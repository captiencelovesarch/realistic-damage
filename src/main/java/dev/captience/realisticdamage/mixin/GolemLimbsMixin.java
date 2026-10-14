package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An iron golem with both arms gone has nothing to swing either (1.7 slice 3).
 * Its strike overrides {@code Mob.doHurtTarget} without calling it, so the
 * general rule in {@link MobStrikeMixin} never sees it.
 */
@Mixin(IronGolem.class)
public abstract class GolemLimbsMixin {
	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$noArmsNoStrike(ServerLevel level, Entity target, CallbackInfoReturnable<Boolean> cir) {
		if (!MobAnatomy.canStrike((IronGolem) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
