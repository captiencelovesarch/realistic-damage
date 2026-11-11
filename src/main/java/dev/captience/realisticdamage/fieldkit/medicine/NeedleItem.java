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
 * Needle and thread (2.0, phase 4): ten slow seconds, and the worst wound is
 * closed for good. Any hurt to either of you while you work spoils it. A
 * needle does a few wounds before the thread runs out.
 */
public final class NeedleItem extends TreatmentItem {
	public NeedleItem(Properties properties) {
		super(properties, Balance.NEEDLE_USE_TICKS, true, ItemUseAnimation.BRUSH);
	}

	@Override
	protected @Nullable Component refusal(Player medic, LivingEntity patient, ItemStack stack) {
		return RDAttachments.get(patient).bleeding() ? null
				: Component.translatable("realisticdamage.fieldkit.treat.not_bleeding");
	}

	@Override
	protected void treat(Player medic, LivingEntity patient, ItemStack stack, InteractionHand hand) {
		RealisticDamageData data = RDAttachments.get(patient);
		BleedWound wound = worst(data, w -> true);
		if (wound == null) {
			return;
		}
		data.wounds().remove(wound);
		Dressings.unbandage(patient, wound.part());
		if (!medic.hasInfiniteMaterials()) {
			stack.hurtAndBreak(1, medic, hand);
		}
		tell(medic, patient, "realisticdamage.fieldkit.needle.done", wound.part());
	}
}
