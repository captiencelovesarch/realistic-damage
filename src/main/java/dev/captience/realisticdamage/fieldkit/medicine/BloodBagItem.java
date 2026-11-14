package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A bag of blood (2.0, phase 4), drawn from a donor with a glass bottle
 * ({@link BloodDraw}) and given to a patient, or back to yourself: bank your
 * own while you're healthy. Five seconds to give. It keeps three days, then it
 * has gone off and does nothing.
 */
public final class BloodBagItem extends TreatmentItem {
	public BloodBagItem(Properties properties) {
		super(properties, Balance.BLOOD_BAG_USE_TICKS, false, ItemUseAnimation.BRUSH);
	}

	/** A fresh bag, drawn now. */
	public static ItemStack drawn(Level level) {
		ItemStack bag = new ItemStack(Medicine.BLOOD_BAG);
		bag.set(Medicine.DRAWN_AT, level.getGameTime());
		return bag;
	}

	public static boolean spoiled(ItemStack stack, @Nullable Level level) {
		Long drawn = stack.get(Medicine.DRAWN_AT);
		return level != null && drawn != null && level.getGameTime() - drawn > Balance.BLOOD_BAG_KEEPS_TICKS;
	}

	@Override
	protected @Nullable Component refusal(Player medic, LivingEntity patient, ItemStack stack) {
		if (spoiled(stack, medic.level())) {
			return Component.translatable("realisticdamage.fieldkit.blood_bag.spoiled");
		}
		if (!MobAnatomy.bleeds(patient)) {
			return Component.translatable("realisticdamage.fieldkit.blood_bag.bloodless");
		}
		if (RDAttachments.get(patient).blood() >= Balance.BLOOD_MAX - 1.0) {
			return Component.translatable("realisticdamage.fieldkit.blood_bag.full");
		}
		return null;
	}

	@Override
	protected void treat(Player medic, LivingEntity patient, ItemStack stack, InteractionHand hand) {
		if (spoiled(stack, medic.level())) {
			return;
		}
		RealisticDamageData data = RDAttachments.get(patient);
		data.restoreBlood(Balance.BLOOD_BAG_GIVE);
		useUp(medic, stack);
		if (!medic.hasInfiniteMaterials() && !medic.getInventory().add(new ItemStack(Items.GLASS_BOTTLE))) {
			medic.drop(new ItemStack(Items.GLASS_BOTTLE), false);
		}
		medic.sendOverlayMessage(medic == patient
				? Component.translatable("realisticdamage.fieldkit.blood_bag.given.self")
				: Component.translatable("realisticdamage.fieldkit.blood_bag.given", patient.getDisplayName()));
		if (patient instanceof Player other && other != medic) {
			other.sendOverlayMessage(Component.translatable("realisticdamage.fieldkit.blood_bag.given.patient",
					medic.getDisplayName()));
		}
	}
}
