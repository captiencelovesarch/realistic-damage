package dev.captience.realisticdamage.fieldkit.mixin;

import java.util.function.Predicate;

import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.Kit;
import dev.captience.realisticdamage.fieldkit.RulesView;
import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A trade rolled while its half was on, and still on the villager's list
 * after the switch went off, gives nothing (2.0): the result slot stays empty
 * and there is no offer to take. On both sides, so the client never predicts a
 * trade the server will refuse.
 */
@Mixin(MerchantContainer.class)
abstract class OfferGateMixin {
	@Shadow
	@Final
	private Merchant merchant;

	@Shadow
	@Final
	private NonNullList<ItemStack> itemStacks;

	@Shadow
	private @Nullable MerchantOffer activeOffer;

	@Shadow
	private int futureXp;

	@Inject(method = "updateSellItem", at = @At("TAIL"))
	private void realisticdamage$gateOffer(CallbackInfo ci) {
		Predicate<Half> on = merchant.isClientSide() ? RulesView::on : Half::on;
		if (Kit.offHalf(itemStacks.get(2), on).isPresent()) {
			itemStacks.set(2, ItemStack.EMPTY);
			activeOffer = null;
			futureXp = 0;
		}
	}
}
