package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.fieldkit.Half;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A blood sausage (2.0, phase 4, round 3): eaten like any food, which gives back
 * Blood by its nutrition as every food does, and then {@code BLOOD_SAUSAGE_BLOOD}
 * more, scaled by the healing setting like the rest of the food's.
 */
public class BloodSausageItem extends Item {
	public BloodSausageItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack left = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide() && entity instanceof ServerPlayer player && !player.isCreative()
				&& !player.isSpectator() && Half.MEDICINE.on()) {
			RDAttachments.get(player).restoreBlood(Balance.BLOOD_SAUSAGE_BLOOD * ServerConfig.get().healingMultiplier);
			RDAttachments.pushSnapshot(player);
		}
		return left;
	}
}
