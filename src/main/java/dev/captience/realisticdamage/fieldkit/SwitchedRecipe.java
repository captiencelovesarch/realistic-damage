package dev.captience.realisticdamage.fieldkit;

import java.util.List;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * A crafting recipe behind a field kit switch (2.0): {@code realisticdamage:switched}.
 *
 * <pre>{@code
 * { "type": "realisticdamage:switched", "switch": "medicine",
 *   "recipe": { "type": "minecraft:crafting_shaped", ... } }
 * }</pre>
 *
 * <p>Any crafting recipe goes inside, unchanged, and the wrapper only adds the
 * question. With the half off the grid simply does not match, as if the recipe
 * did not exist; with it on, everything is the inner recipe's. Asked at craft
 * time, so a flipped switch is felt at once — no {@code /reload}, no restart —
 * and the recipe is always loaded, so a datapack that names it never breaks.
 *
 * <p>Crafting is matched on the server only, so the server's switch is the
 * only one asked.
 */
public final class SwitchedRecipe implements CraftingRecipe {
	public static final MapCodec<SwitchedRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
					Half.CODEC.fieldOf("switch").forGetter(SwitchedRecipe::half),
					Recipe.CODEC.comapFlatMap(SwitchedRecipe::crafting, recipe -> recipe)
							.fieldOf("recipe").forGetter(SwitchedRecipe::inner))
			.apply(i, SwitchedRecipe::new));

	public static final StreamCodec<RegistryFriendlyByteBuf, SwitchedRecipe> STREAM_CODEC = StreamCodec.composite(
			Half.STREAM_CODEC, SwitchedRecipe::half,
			Recipe.STREAM_CODEC.map(recipe -> (CraftingRecipe) recipe, recipe -> recipe), SwitchedRecipe::inner,
			SwitchedRecipe::new);

	public static final RecipeSerializer<SwitchedRecipe> SERIALIZER = new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private final Half half;
	private final CraftingRecipe inner;

	public SwitchedRecipe(Half half, CraftingRecipe inner) {
		this.half = half;
		this.inner = inner;
	}

	private static DataResult<CraftingRecipe> crafting(Recipe<?> recipe) {
		return recipe instanceof CraftingRecipe crafting
				? DataResult.success(crafting)
				: DataResult.error(() -> "a switched recipe wraps a crafting recipe, not " + recipe.getType());
	}

	public Half half() {
		return half;
	}

	public CraftingRecipe inner() {
		return inner;
	}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		return half.on(level) && inner.matches(input, level);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		return inner.assemble(input);
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		return inner.getRemainingItems(input);
	}

	@Override
	public boolean isSpecial() {
		return inner.isSpecial();
	}

	@Override
	public boolean showNotification() {
		return inner.showNotification();
	}

	@Override
	public String group() {
		return inner.group();
	}

	@Override
	public CraftingBookCategory category() {
		return inner.category();
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return inner.recipeBookCategory();
	}

	@Override
	public PlacementInfo placementInfo() {
		return inner.placementInfo();
	}

	@Override
	public List<RecipeDisplay> display() {
		return inner.display();
	}

	@Override
	public RecipeSerializer<SwitchedRecipe> getSerializer() {
		return SERIALIZER;
	}
}
