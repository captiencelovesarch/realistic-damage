package dev.captience.realisticdamage.fieldkit.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.captience.realisticdamage.damage.ArmorModifier;
import dev.captience.realisticdamage.fieldkit.medicine.Goggles;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The core counts anything worn on the head as a helmet. Tinted goggles (2.0,
 * phase 4 round 2) are glass and a strap: they take nothing off a blow.
 */
@Mixin(ArmorModifier.class)
abstract class GogglesHelmetMixin {
	@ModifyExpressionValue(method = "pieceReduction", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
	private static ItemStack realisticdamage$notAHelmet(ItemStack worn) {
		return Goggles.is(worn) ? ItemStack.EMPTY : worn;
	}
}
