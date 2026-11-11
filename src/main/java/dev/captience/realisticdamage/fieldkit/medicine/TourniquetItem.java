package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.jspecify.annotations.Nullable;

/**
 * A tourniquet (2.0, phase 4): on the worst-bleeding limb, it stops that bleed
 * dead, and the limb is Disabled while it is on. Leave it on too long and the
 * limb dies. Sneak and use with an empty hand to take it off (yours, or theirs).
 */
public final class TourniquetItem extends TreatmentItem {
	public TourniquetItem(Properties properties) {
		super(properties, Balance.TOURNIQUET_USE_TICKS, false, ItemUseAnimation.BRUSH);
	}

	static @Nullable BleedWound target(LivingEntity patient) {
		Dressings.State dressed = Dressings.peek(patient);
		return worst(RDAttachments.get(patient), wound -> Dressings.limb(wound.part())
				&& (dressed == null || !dressed.tourniquets.containsKey(wound.part())));
	}

	@Override
	protected @Nullable Component refusal(Player medic, LivingEntity patient, ItemStack stack) {
		if (target(patient) != null) {
			return null;
		}
		return RDAttachments.get(patient).bleeding()
				? Component.translatable("realisticdamage.fieldkit.tourniquet.not_a_limb")
				: Component.translatable("realisticdamage.fieldkit.treat.not_bleeding");
	}

	@Override
	protected void treat(Player medic, LivingEntity patient, ItemStack stack, InteractionHand hand) {
		BleedWound wound = target(patient);
		if (wound == null) {
			return;
		}
		Dressings.tourniquet(patient, wound.part());
		useUp(medic, stack);
		tell(medic, patient, "realisticdamage.fieldkit.tourniquet.done", wound.part());
	}
}
