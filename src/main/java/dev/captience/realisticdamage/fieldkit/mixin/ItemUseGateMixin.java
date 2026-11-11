package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.fieldkit.Gate;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every use of a held stack asks its field kit half first (2.0). On
 * {@link ItemStack} rather than on each item, so a vanilla potion carrying a
 * field kit effect is caught by the same check as a bandage, and an item added
 * later cannot forget to ask. Anything not in the field kit passes untouched.
 */
@Mixin(ItemStack.class)
abstract class ItemUseGateMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$gateUse(Level level, Player player, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (!Gate.allows((ItemStack) (Object) this, level, player)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$gateUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
		if (!Gate.allows((ItemStack) (Object) this, context.getLevel(), context.getPlayer())) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}

	@Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
	private void realisticdamage$gateInteract(Player player, LivingEntity target, InteractionHand hand,
			CallbackInfoReturnable<InteractionResult> cir) {
		if (!Gate.allows((ItemStack) (Object) this, player.level(), player)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
