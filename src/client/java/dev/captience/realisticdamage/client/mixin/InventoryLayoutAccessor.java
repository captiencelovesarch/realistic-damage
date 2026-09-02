package dev.captience.realisticdamage.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Whether the recipe book is open <em>beside</em> the inventory rather than on
 * top of it. Both fields are private in vanilla and between them they are the
 * whole state: the book only displaces the window when it is visible and the
 * screen is wide enough to put the two side by side ({@code widthTooNarrow},
 * set from {@code width < 379}), and when it does, the window slides 77 scaled
 * pixels right and the book fills the space it left behind.
 *
 * <p>Asked rather than inferred. The shift could be detected by comparing
 * {@code leftPos} against {@code (width - 176) / 2} — the two differ by exactly
 * 77 whenever the book is beside the window — but that infers a state
 * {@code isVisible()} states outright, and it would mis-read any other mod that
 * repositions the window for reasons of its own.
 */
@Mixin(AbstractRecipeBookScreen.class)
public interface InventoryLayoutAccessor {
	@Accessor("recipeBookComponent")
	RecipeBookComponent<?> realisticdamage$recipeBook();

	@Accessor("widthTooNarrow")
	boolean realisticdamage$widthTooNarrow();
}
