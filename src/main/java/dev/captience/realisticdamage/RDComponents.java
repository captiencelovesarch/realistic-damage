package dev.captience.realisticdamage;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;

/**
 * The mod's item data (2.0).
 *
 * <p>{@link #SEVERED} marks a head taken off a body. Such a head can be carried
 * and placed but never worn: the mark goes on together with the removal of
 * vanilla's {@code equippable} component, which is what every way of putting
 * something on your head (the use key, a shift-click, a dispenser) asks for.
 * The mark is the part that lasts: a placed block keeps the components its
 * item added, so a severed head broken off a wall comes back severed, and the
 * removal is put back with it.
 */
public final class RDComponents {
	private RDComponents() {}

	public static final DataComponentType<Unit> SEVERED = DataComponentType.<Unit>builder()
			.persistent(Unit.CODEC)
			.networkSynchronized(Unit.STREAM_CODEC)
			.build();

	public static void init() {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, RealisticDamage.id("severed"), SEVERED);
	}

	/** Makes this head a severed one: carried and placed, never worn. */
	public static ItemStack sever(ItemStack stack) {
		stack.set(SEVERED, Unit.INSTANCE);
		stack.remove(DataComponents.EQUIPPABLE);
		return stack;
	}

	public static boolean severed(ItemStack stack) {
		return stack.has(SEVERED);
	}
}
