package dev.captience.realisticdamage.mixin;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import dev.captience.realisticdamage.systems.ImpairmentMath;
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
 * Item uses that take real time slow down as the body fails: two-handed actions
 * (bow draw, crossbow load, spear charge) with injured arms (spec §11), and —
 * since 1.4 — eating and drinking as consciousness fades.
 *
 * <p>Both work by deterministically stalling use ticks, so the server-side
 * progress takes proportionally longer without anything ever being cancelled.
 * That distinction is the whole design of the eating case: food is the only
 * cure in this mod, so a fading player must always be <em>able</em> to eat. What
 * changes is that getting a meal down while greying out takes long enough to be
 * a decision — you have to buy the time, rather than tapping a key and being
 * fine.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUseItemMixin {
	@Shadow
	protected int useItemRemaining;

	@Inject(method = "updateUsingItem", at = @At("HEAD"))
	private void realisticdamage$stallUse(ItemStack useItem, CallbackInfo ci) {
		if (!((Object) this instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(player);
		double stall = stallFor(player, data, useItem);
		if (stall > 0.0) {
			this.useItemRemaining += data.advanceUseStall(stall);
		}
	}

	/**
	 * Stalled ticks per tick. The two-handed penalty is already in those units
	 * and capped well under one; the eating curve is a duration factor and goes
	 * through {@link ImpairmentMath#useStallPerTick}, which also holds the whole
	 * meal to five seconds (1.6.2).
	 */
	private static double stallFor(ServerPlayer player, RealisticDamageData data, ItemStack useItem) {
		ItemUseAnimation animation = useItem.getUseAnimation();
		if (animation == ItemUseAnimation.BOW || animation == ItemUseAnimation.CROSSBOW
				|| animation == ItemUseAnimation.SPEAR) {
			return ImpairmentSystem.twoHandedPenalty(player, data);
		}
		if (animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK) {
			return ImpairmentMath.useStallPerTick(
					ImpairmentMath.eatingStallPenalty(ConsciousnessSystem.effective(data)),
					useItem.getUseDuration(player));
		}
		return 0.0;
	}
}
