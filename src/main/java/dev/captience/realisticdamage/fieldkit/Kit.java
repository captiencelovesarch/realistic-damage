package dev.captience.realisticdamage.fieldkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.captience.realisticdamage.RealisticDamage;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;

/**
 * Which half every field kit thing belongs to (2.0), and the one question every
 * switch check asks: is this stack switched off?
 *
 * <p>Things join a half when they are registered here, so there is no list to
 * keep in step by hand. An item is in its half outright. A potion is in the
 * half of the effects it carries — and that covers every form vanilla makes of
 * it (drinkable, splash, lingering, tipped arrow) and anything else that carries
 * potion contents, because they all carry the same component.
 *
 * <p>Everything here is always registered, whatever the switches say: a world
 * must never fail to load because a switch was flipped, and a chest full of
 * bandages must still be a chest full of bandages when it is flipped back.
 */
public final class Kit {
	private Kit() {}

	private static final Map<Item, Half> ITEMS = new IdentityHashMap<>();
	private static final Map<MobEffect, Half> EFFECTS = new IdentityHashMap<>();
	private static final List<Item> ITEM_ORDER = new ArrayList<>();
	private static final List<Holder<Potion>> POTION_ORDER = new ArrayList<>();

	public static <T extends Item> T item(String name, Function<Item.Properties, T> factory,
			Item.Properties properties, Half half) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, RealisticDamage.id(name));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
		ITEMS.put(item, half);
		ITEM_ORDER.add(item);
		return item;
	}

	public static Holder<MobEffect> effect(String name, MobEffect effect, Half half) {
		EFFECTS.put(effect, half);
		return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, RealisticDamage.id(name), effect);
	}

	/** A potion of field kit effects; its half is theirs. Listed in the creative tab in every form. */
	public static Holder<Potion> potion(String name, Potion potion) {
		Holder<Potion> holder = Registry.registerForHolder(BuiltInRegistries.POTION, RealisticDamage.id(name), potion);
		POTION_ORDER.add(holder);
		return holder;
	}

	public static Optional<Half> halfOf(Holder<MobEffect> effect) {
		return Optional.ofNullable(EFFECTS.get(effect.value()));
	}

	/** Every half this stack belongs to: one for an item, possibly two for a mixed potion, none for vanilla. */
	public static Set<Half> halves(ItemStack stack) {
		if (stack.isEmpty()) {
			return Set.of();
		}
		Half itemHalf = ITEMS.get(stack.getItem());
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (itemHalf == null && contents == null) {
			return Set.of();
		}
		EnumSet<Half> out = EnumSet.noneOf(Half.class);
		if (itemHalf != null) {
			out.add(itemHalf);
		}
		if (contents != null) {
			for (MobEffectInstance effect : contents.getAllEffects()) {
				Half half = EFFECTS.get(effect.getEffect().value());
				if (half != null) {
					out.add(half);
				}
			}
		}
		return out;
	}

	/** The first half that switches this stack off, by the given switches; empty when it is free to use. */
	public static Optional<Half> offHalf(ItemStack stack, Predicate<Half> on) {
		for (Half half : halves(stack)) {
			if (!on.test(half)) {
				return Optional.of(half);
			}
		}
		return Optional.empty();
	}

	/** Server-side: is this stack switched off right now? */
	public static boolean blocked(ItemStack stack) {
		return offHalf(stack, Half::on).isPresent();
	}

	/** Either side: the server's switches on a server level, the synced copy on a client one. */
	public static Optional<Half> offHalf(ItemStack stack, Level level) {
		return offHalf(stack, half -> half.on(level));
	}

	public static List<Item> items() {
		return Collections.unmodifiableList(ITEM_ORDER);
	}

	public static List<Holder<Potion>> potions() {
		return Collections.unmodifiableList(POTION_ORDER);
	}

	/** Every form vanilla makes of a potion, in the order its own creative tabs list them. */
	public static List<ItemStack> potionForms(Holder<Potion> potion) {
		return List.of(
				PotionContents.createItemStack(Items.POTION, potion),
				PotionContents.createItemStack(Items.SPLASH_POTION, potion),
				PotionContents.createItemStack(Items.LINGERING_POTION, potion),
				PotionContents.createItemStack(Items.TIPPED_ARROW, potion));
	}
}
