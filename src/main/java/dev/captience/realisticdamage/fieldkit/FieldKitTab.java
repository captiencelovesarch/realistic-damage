package dev.captience.realisticdamage.fieldkit;

import java.util.List;

import dev.captience.realisticdamage.RealisticDamage;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;

/**
 * The "Field kit" creative tab (2.0): every field kit item, and every form of
 * every field kit potion, for whichever halves are switched on. A half that is
 * off is simply not there, and with both off the tab has nothing in it and
 * vanilla does not draw it at all.
 *
 * <p>Built on the client, from the switches the server sent. A flipped switch
 * forgets vanilla's cached tab contents (see {@code CreativeTabsAccessor}) so
 * the tab follows without a rejoin.
 */
public final class FieldKitTab {
	private FieldKitTab() {}

	public static final ResourceKey<CreativeModeTab> KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, RealisticDamage.id("field_kit"));

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, FabricCreativeModeTab.builder()
				.title(Component.translatable("itemGroup.realisticdamage.field_kit"))
				.icon(FieldKitTab::icon)
				.displayItems((parameters, output) -> output.acceptAll(contents()))
				.build());
	}

	/** What the tab shows right now, by the client's copy of the switches. */
	public static List<ItemStack> contents() {
		java.util.ArrayList<ItemStack> out = new java.util.ArrayList<>();
		for (Item item : Kit.items()) {
			ItemStack stack = new ItemStack(item);
			if (Kit.offHalf(stack, RulesView::on).isEmpty()) {
				out.add(stack);
			}
		}
		for (Holder<Potion> potion : Kit.potions()) {
			for (ItemStack stack : Kit.potionForms(potion)) {
				if (Kit.offHalf(stack, RulesView::on).isEmpty()) {
					out.add(stack);
				}
			}
		}
		return out;
	}

	private static ItemStack icon() {
		List<ItemStack> shown = contents();
		return shown.isEmpty() ? new ItemStack(Items.POTION) : shown.getFirst();
	}
}
