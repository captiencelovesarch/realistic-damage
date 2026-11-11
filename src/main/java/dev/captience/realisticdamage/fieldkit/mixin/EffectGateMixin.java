package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.Kit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A switched-off field kit effect is never added (2.0): a tipped arrow, a
 * splash, a lingering cloud, a command, a beacon datapack — whatever tries, the
 * body refuses it, the same way an undead body refuses Poison. The effect's own
 * tick ({@code FieldKitEffect}) removes one that was already running.
 *
 * <p>Server only: the client's copy of an effect is whatever the server says.
 */
@Mixin(LivingEntity.class)
abstract class EffectGateMixin {
	@Inject(method = "canBeAffected", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$gateEffect(MobEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (!self.level().isClientSide() && Kit.halfOf(effect.getEffect()).map(half -> !half.on()).orElse(false)) {
			cir.setReturnValue(false);
		}
	}
}
