package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.config.ServerConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Enforces {@link ServerConfig.UnconsciousAggro} at the one point every target
 * acquisition passes through.
 *
 * <p>The gentler modes cannot be implemented by clearing targets periodically.
 * Vanilla's targeting goals re-acquire the nearest player within the mob's
 * follow range — 35 blocks for a zombie, far outside either aggro radius — on
 * their own schedule, so a mod that only clears would be fighting the goal for
 * the target slot and losing about half the time. Refusing the acquisition here
 * makes the setting mean what it says.
 *
 * <p>Deliberately does not touch a target that is already set: dropping one
 * would also break {@code ATTACKERS}, whose entire promise is that whatever was
 * already fighting you carries on.
 */
@Mixin(Mob.class)
public abstract class MobTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$respectDownedPlayers(@Nullable LivingEntity target, CallbackInfo ci) {
		if (!(target instanceof ServerPlayer player) || !RDAttachments.get(player).unconscious()) {
			return;
		}
		Mob self = (Mob) (Object) this;
		boolean alreadyTargeting = self.getTarget() == player;
		if (!ServerConfig.get().unconsciousAggro.allowsTargeting(alreadyTargeting, self.distanceTo(player))) {
			ci.cancel();
		}
	}
}
