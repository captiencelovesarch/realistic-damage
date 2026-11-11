package dev.captience.realisticdamage.mixin;

import java.util.List;

import dev.captience.realisticdamage.RDComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A severed head that was placed comes back severed when it is broken (2.0).
 *
 * <p>The block remembers: a block entity keeps every component its item added,
 * so the {@link RDComponents#SEVERED} mark sits in the skull's components from
 * the moment it is placed. What it cannot keep is the removal of
 * {@code equippable}, and vanilla's loot tables drop a fresh head for most
 * skulls anyway, so the mark is put back on the way out here: the one place
 * every drop (a player's pickaxe, a piston, an explosion) goes through.
 */
@Mixin(BlockBehaviour.class)
public abstract class SeveredSkullDropMixin {
	@Inject(method = "getDrops", at = @At("RETURN"))
	private void realisticdamage$keepSevered(BlockState state, LootParams.Builder params,
			CallbackInfoReturnable<List<ItemStack>> cir) {
		BlockEntity entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
		if (!(entity instanceof SkullBlockEntity) || !entity.components().has(RDComponents.SEVERED)) {
			return;
		}
		for (ItemStack stack : cir.getReturnValue()) {
			if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof AbstractSkullBlock) {
				RDComponents.sever(stack);
			}
		}
	}
}
