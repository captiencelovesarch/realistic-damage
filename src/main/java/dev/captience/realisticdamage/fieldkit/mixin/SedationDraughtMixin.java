package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.fieldkit.brew.BrewedEffects;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drinking Sedation is the sleeping draught (2.0, phase 3): marked before the
 * drink's effects are applied, so the dose it gives is known to be a drunk one.
 */
@Mixin(PotionContents.class)
abstract class SedationDraughtMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void realisticdamage$draught(Level level, LivingEntity user, ItemStack stack, Consumable consumable,
			CallbackInfo ci) {
		if (level.isClientSide() || !(user instanceof ServerPlayer player)) {
			return;
		}
		for (MobEffectInstance effect : ((PotionContents) (Object) this).getAllEffects()) {
			if (effect.is(BrewedEffects.SEDATION)) {
				Sedation.drunk(player);
				return;
			}
		}
	}
}
