package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * A stethoscope (2.0, phase 4 round 2): hold use on someone, or in the air for
 * yourself, and you hear their heart for as long as you hold it: how fast it
 * runs, how hard it beats, whether it skips. It is sound only (the meters stay
 * hidden), and only the listener hears it.
 *
 * <p>The heart is the core's own, which beats on the client, so the listening
 * is all done there: {@link #listeningTo} is the body this client's player has
 * the stethoscope on, written only on the client side.
 */
public final class StethoscopeItem extends Item {
	/** The entity id the local player is listening to, or -1. Client side only. */
	public static volatile int listeningTo = -1;

	public StethoscopeItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return listen(level, player, player, hand);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
			InteractionHand hand) {
		if (!(target instanceof Player) && !MobAnatomy.isMobWithBody(target)) {
			return InteractionResult.PASS;
		}
		return listen(player.level(), player, target, hand);
	}

	private static InteractionResult listen(Level level, Player player, LivingEntity target, InteractionHand hand) {
		if (level.isClientSide()) {
			listeningTo = target.getId();
		}
		player.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return 72000;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return ItemUseAnimation.NONE;
	}
}
