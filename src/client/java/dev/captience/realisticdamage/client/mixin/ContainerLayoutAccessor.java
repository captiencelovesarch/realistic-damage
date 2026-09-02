package dev.captience.realisticdamage.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Where the container window actually is. {@code leftPos} is {@code protected},
 * and it is the only honest answer to the question: vanilla seeds it from the
 * screen width in {@code init}, but the recipe book then moves it, and anything
 * that re-derives the centre from the width instead never hears about that.
 *
 * <p>Separate from {@link InventoryLayoutAccessor} because Mixin's
 * {@code @Accessor} has to name the class that declares the field, and
 * {@code leftPos} is declared here rather than on the recipe-book subclass.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerLayoutAccessor {
	@Accessor("leftPos")
	int realisticdamage$leftPos();
}
