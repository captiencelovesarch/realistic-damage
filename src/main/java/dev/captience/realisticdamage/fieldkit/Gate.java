package dev.captience.realisticdamage.fieldkit;

import java.util.Optional;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * "Checked when it's used" (2.0): the one place a field kit item is refused.
 *
 * <p>Every way a held item is used — the use key in the air, on a block, on a
 * creature — goes through here first (see {@code ItemUseGateMixin}), so an item
 * cannot forget to ask. A switched-off item does nothing, and says why on the
 * action bar: a bandage that silently fails to bandage reads as a bug, a bandage
 * that says "Medicine is switched off" reads as a rule.
 */
public final class Gate {
	private Gate() {}

	/** True when the stack may be used; otherwise tells the player why not and returns false. */
	public static boolean allows(ItemStack stack, Level level, Player player) {
		Optional<Half> off = Kit.offHalf(stack, level);
		if (off.isEmpty()) {
			return true;
		}
		if (!level.isClientSide() && player != null) {
			player.sendOverlayMessage(message(off.get()));
		}
		return false;
	}

	/** "Medicine is switched off here." */
	public static Component message(Half half) {
		return Component.translatable("realisticdamage.fieldkit.off", half.title()).withStyle(ChatFormatting.RED);
	}
}
