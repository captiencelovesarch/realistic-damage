package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.jspecify.annotations.Nullable;

/**
 * A bandage (2.0, phase 4): about three seconds with both hands, on the worst
 * wound that will take one. It slows that wound; a moderate one soaks it
 * through in time and it needs changing; an arterial one it cannot hold at all.
 */
public final class BandageItem extends TreatmentItem {
	public BandageItem(Properties properties) {
		super(properties, Balance.BANDAGE_USE_TICKS, false, ItemUseAnimation.BRUSH);
	}

	/** The wound this bandage would go on: the worst one that takes a bandage and hasn't a good one. */
	static @Nullable BleedWound target(LivingEntity patient) {
		RealisticDamageData data = RDAttachments.get(patient);
		Dressings.State dressed = Dressings.peek(patient);
		return worst(data, wound -> Dressings.bandageable(wound.severity())
				&& (dressed == null || !dressed.tourniquets.containsKey(wound.part()))
				&& (dressed == null || !dressed.bandages.containsKey(wound.part())
						|| dressed.bandages.get(wound.part()).soaked));
	}

	@Override
	protected @Nullable Component refusal(Player medic, LivingEntity patient, ItemStack stack) {
		RealisticDamageData data = RDAttachments.get(patient);
		if (!data.bleeding()) {
			return Component.translatable("realisticdamage.fieldkit.treat.not_bleeding");
		}
		if (target(patient) != null) {
			return null;
		}
		return worst(data, wound -> !Dressings.bandageable(wound.severity())) != null
				? Component.translatable("realisticdamage.fieldkit.bandage.arterial")
				: Component.translatable("realisticdamage.fieldkit.bandage.already");
	}

	@Override
	protected void treat(Player medic, LivingEntity patient, ItemStack stack, InteractionHand hand) {
		BleedWound wound = target(patient);
		if (wound == null) {
			return;
		}
		Dressings.bandage(patient, wound.part());
		useUp(medic, stack);
		tell(medic, patient, "realisticdamage.fieldkit.bandage.done", wound.part());
	}
}
