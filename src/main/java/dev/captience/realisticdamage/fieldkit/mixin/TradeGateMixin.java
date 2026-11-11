package dev.captience.realisticdamage.fieldkit.mixin;

import dev.captience.realisticdamage.fieldkit.Kit;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A trade is checked when it is rolled (2.0). Our own trades carry
 * {@code realisticdamage:field_kit_on} as their merchant predicate; this is the
 * net under that for any trade, from any datapack, that would sell or buy a
 * switched-off item. A refused roll is simply no offer, which vanilla already
 * handles by rolling another.
 */
@Mixin(VillagerTrade.class)
abstract class TradeGateMixin {
	@Inject(method = "getOffer", at = @At("RETURN"), cancellable = true)
	private void realisticdamage$gateRoll(LootContext context, CallbackInfoReturnable<MerchantOffer> cir) {
		MerchantOffer offer = cir.getReturnValue();
		if (offer != null && (Kit.blocked(offer.getResult()) || Kit.blocked(offer.getCostA())
				|| Kit.blocked(offer.getCostB()))) {
			cir.setReturnValue(null);
		}
	}
}
