package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two-handed actions (bow draw, crossbow load, spear charge) slow down with
 * injured arms (spec §11): deterministically stall use ticks so the server-side
 * charge takes proportionally longer. Eating and other one-handed uses are
 * deliberately untouched — food is the recovery path.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUseItemMixin {
	@Shadow
	protected int useItemRemaining;

	@Inject(method = "updateUsingItem", at = @At("HEAD"))
	private void realisticdamage$stallTwoHandedUse(ItemStack useItem, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
			return;
		}
		ItemUseAnimation animation = useItem.getUseAnimation();
		if (animation != ItemUseAnimation.BOW && animation != ItemUseAnimation.CROSSBOW
				&& animation != ItemUseAnimation.SPEAR) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(player);
		double penalty = ImpairmentSystem.twoHandedPenalty(player, data);
		if (penalty > 0.0 && data.advanceUseStall(penalty)) {
			this.useItemRemaining++;
		}
	}
}
