package dev.captience.realisticdamage.fieldkit.mixin;

import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a brewing stand has started, for the game test that holds it to the switch (2.0). */
@Mixin(BrewingStandBlockEntity.class)
public interface BrewingStandAccessor {
	@Accessor("brewTime")
	int realisticdamage$brewTime();
}
