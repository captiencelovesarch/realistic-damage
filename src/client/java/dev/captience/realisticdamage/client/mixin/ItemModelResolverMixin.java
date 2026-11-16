package dev.captience.realisticdamage.client.mixin;

import dev.captience.realisticdamage.client.BloodiedItems;
import dev.captience.realisticdamage.client.BloodiedState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where an item turns into what will be drawn (2.0): the blood on the item
 * is read here, once, and rides on the render state from then on. It is also
 * named as part of what the item looks like, because the inventory keeps its
 * pictures of items by exactly that, and a bloodied sword must not be handed
 * the picture of a clean one.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@Inject(method = "updateForTopItem", at = @At("TAIL"))
	private void realisticdamage$blood(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
			@Nullable Level level, @Nullable ItemOwner owner, int seed, CallbackInfo ci) {
		BloodiedItems.Look look = item.isEmpty() ? null : BloodiedItems.lookOf(item);
		((BloodiedState) output).realisticdamage$setBlood(look);
		if (look != null) {
			output.appendModelIdentityElement(look);
		}
	}
}
