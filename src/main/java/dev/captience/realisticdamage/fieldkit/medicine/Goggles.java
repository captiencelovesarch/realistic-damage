package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.fieldkit.Half;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Tinted goggles (2.0, phase 4 round 2): worn in the head slot instead of a
 * helmet. Bright light stops making a concussion worse, and the world looks
 * darker. They are glass and leather, not a helmet: they take nothing off a
 * blow to the head.
 */
public final class Goggles {
	private Goggles() {}

	public static boolean is(ItemStack stack) {
		return stack.is(Medicine.TINTED_GOGGLES);
	}

	/** Worn, and working (Medicine on). Either side. */
	public static boolean worn(LivingEntity body) {
		return is(body.getItemBySlot(EquipmentSlot.HEAD)) && Half.MEDICINE.on(body.level());
	}
}
