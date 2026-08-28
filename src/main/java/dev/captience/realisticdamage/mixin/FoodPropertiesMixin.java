package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.systems.HealingSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Eating restores Blood immediately, and golden foods act as strong medicine
 * (spec §18, §40). Hooked where vanilla finishes a food item so every eating
 * path (hand, dispensers, modded consumables with FOOD) is covered.
 */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void realisticdamage$onEat(Level level, LivingEntity user, ItemStack stack, Consumable consumable,
			CallbackInfo ci) {
		if (user instanceof ServerPlayer player) {
			HealingSystem.onEat(player, stack, (FoodProperties) (Object) this);
		}
	}
}
