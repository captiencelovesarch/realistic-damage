package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
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
		if (self.level().isClientSide()) {
			return;
		}
		if (Kit.halfOf(effect.getEffect()).map(half -> !half.on()).orElse(false)) {
			cir.setReturnValue(false);
			return;
		}
		// Bosses and the mindless can't be put to sleep (2.0, phase 3).
		if (effect.is(BrewedEffects.SEDATION) && dev.captience.realisticdamage.fieldkit.brew.Sedation.refuses(self)) {
			cir.setReturnValue(false);
			return;
		}
		// The heart is still recovering from its last surge (2.0, phase 3).
		if (effect.is(BrewedEffects.ADRENALINE) && BrewedEffects.refusesAdrenaline(self)) {
			if (self instanceof Player player) {
				player.sendOverlayMessage(Component.translatable("realisticdamage.fieldkit.adrenaline.spent")
						.withStyle(ChatFormatting.RED));
			}
			cir.setReturnValue(false);
		}
	}
}
