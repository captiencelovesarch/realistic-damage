package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.util.Notifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A triggered Totem stabilizes the body instead of erasing every injury
 * (spec §41): "you are alive and capable of escaping", not "nothing happened".
 */
@Mixin(LivingEntity.class)
public abstract class TotemDeathProtectionMixin {
	@Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
	private void realisticdamage$stabilizeAfterTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValue() && (Object) this instanceof ServerPlayer player
				&& !player.isCreative() && !player.isSpectator()) {
			RDAttachments.get(player).totemStabilize();
			RDAttachments.pushSnapshot(player);
			Notifier.message(player, "realisticdamage.msg.totem_stabilized");
		}
	}
}
