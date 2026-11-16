package dev.captience.realisticdamage.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.captience.realisticdamage.damage.BloodiedItem;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A sword that just got bloody is still the sword in your hand (2.0).
 * Vanilla lowers and raises the held item whenever it changes, and lets a
 * handful of changes through without that (wear, for one); blood landing,
 * drying or washing off is one more of them.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class BloodiedHandMixin {
	@ModifyReturnValue(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"))
	private boolean realisticdamage$sameSword(boolean instantly, ItemStack visible, ItemStack expected) {
		return instantly || BloodiedItem.sameButForBlood(visible, expected);
	}
}
