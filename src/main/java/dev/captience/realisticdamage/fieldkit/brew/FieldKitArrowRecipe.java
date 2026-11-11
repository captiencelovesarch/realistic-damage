package dev.captience.realisticdamage.fieldkit.brew;

import com.mojang.serialization.MapCodec;
import dev.captience.realisticdamage.fieldkit.Kit;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 8 arrows around one drinkable field kit potion make 8 tipped arrows (2.0,
 * phase 3): {@code realisticdamage:field_kit_tipped_arrow}.
 *
 * <p>Vanilla tips arrows from lingering potions, which need dragon's breath and
 * so wait for the End; the field kit's poisons are meant for the early game, so
 * they also take the drinkable route. Only field kit potions: vanilla's tipped
 * arrows are unchanged. The bottle comes back. The potion's own half decides
 * whether it crafts, so one recipe serves both switches.
 */
public final class FieldKitArrowRecipe extends CustomRecipe {
	public static final FieldKitArrowRecipe INSTANCE = new FieldKitArrowRecipe();
	public static final MapCodec<FieldKitArrowRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
	public static final StreamCodec<RegistryFriendlyByteBuf, FieldKitArrowRecipe> STREAM_CODEC =
			StreamCodec.unit(INSTANCE);
	public static final RecipeSerializer<FieldKitArrowRecipe> SERIALIZER =
			new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

	private FieldKitArrowRecipe() {}

	@Override
	public boolean matches(CraftingInput input, Level level) {
		if (input.width() != 3 || input.height() != 3 || input.ingredientCount() != 9) {
			return false;
		}
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 3; x++) {
				ItemStack stack = input.getItem(x, y);
				if (x == 1 && y == 1) {
					if (!isFieldKitPotion(stack) || Kit.offHalf(stack, level).isPresent()) {
						return false;
					}
				} else if (!stack.is(Items.ARROW)) {
					return false;
				}
			}
		}
		return true;
	}

	/** A drinkable bottle of one of the field kit's own potions, with nothing mixed in. */
	static boolean isFieldKitPotion(ItemStack stack) {
		if (!stack.is(Items.POTION)) {
			return false;
		}
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.customEffects().isEmpty()
				&& contents.potion().map(Kit.potions()::contains).orElse(false);
	}

	@Override
	public ItemStack assemble(CraftingInput input) {
		ItemStack arrows = new ItemStack(Items.TIPPED_ARROW, 8);
		arrows.set(DataComponents.POTION_CONTENTS, input.getItem(1, 1).get(DataComponents.POTION_CONTENTS));
		return arrows;
	}

	@Override
	public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
		NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
		remaining.set(4, new ItemStack(Items.GLASS_BOTTLE));
		return remaining;
	}

	@Override
	public RecipeSerializer<FieldKitArrowRecipe> getSerializer() {
		return SERIALIZER;
	}
}
