package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creeper that is down lets its fuse die away: the hiss is its own doing,
 * and it is in no state to keep it up. One lit with flint and steel still
 * goes.
 */
@Mixin(Creeper.class)
public abstract class CreeperDownMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void realisticdamage$downFizzles(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		if (!self.isIgnited() && MobAnatomy.down(self)) {
			self.setSwellDir(-1);
		}
	}
}
