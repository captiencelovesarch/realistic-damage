package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.DeathHold;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The server side of the death camera (1.7, see {@link DeathHold}): while a
 * dead player's client is watching the world, the death clock keeps counting
 * and the twenty-tick removal waits. The client's own body is kept by
 * {@code LocalPlayerDeathMixin}; this keeps the world it is lying in alive.
 */
@Mixin(LivingEntity.class)
public abstract class DeathHoldMixin {
	@Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$holdBody(CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player && DeathHold.holding(player)) {
			player.deathTime++;
			ci.cancel();
		}
	}
}
