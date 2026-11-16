package dev.captience.realisticdamage.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.captience.realisticdamage.damage.BloodiedItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Blood changing on a pickaxe does not start the block over (2.0). Vanilla
 * abandons a half-broken block when the held item stops being exactly the
 * item the break began with, which is right for a swapped tool and wrong for
 * a tool whose grip your arm is bleeding onto, or one the rain is rinsing.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class BloodiedMiningMixin {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Shadow
	private BlockPos destroyBlockPos;
	@Shadow
	private ItemStack destroyingItem;

	@ModifyReturnValue(method = "sameDestroyTarget", at = @At("RETURN"))
	private boolean realisticdamage$sameTool(boolean same, BlockPos pos) {
		return same || (pos.equals(destroyBlockPos) && minecraft.player != null
				&& BloodiedItem.sameButForBlood(minecraft.player.getMainHandItem(), destroyingItem));
	}
}
