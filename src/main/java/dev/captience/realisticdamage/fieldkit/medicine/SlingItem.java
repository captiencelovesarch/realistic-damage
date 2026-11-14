package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.jspecify.annotations.Nullable;

/**
 * A sling (2.0, phase 4 round 2): ties a broken arm still against the chest.
 * Its hand can't hold anything to use, the main arm can't swing, and the arm
 * rests: it takes twice as much from every heal and can't be strained. It
 * comes back when the arm has knit, or with a sneak-use and an empty hand.
 */
public final class SlingItem extends TreatmentItem {
	public SlingItem(Properties properties) {
		super(properties, Balance.SLING_USE_TICKS, false, ItemUseAnimation.BRUSH);
	}

	static @Nullable BodyPart target(LivingEntity patient) {
		Dressings.State dressed = Dressings.peek(patient);
		return SplintItem.worstBroken(RDAttachments.get(patient),
				part -> part.isArm() && (dressed == null || !dressed.slings.contains(part)));
	}

	@Override
	protected @Nullable Component refusal(Player medic, LivingEntity patient, ItemStack stack) {
		if (target(patient) != null) {
			return null;
		}
		return SplintItem.worstBroken(RDAttachments.get(patient), BodyPart::isArm) != null
				? Component.translatable("realisticdamage.fieldkit.sling.already")
				: Component.translatable("realisticdamage.fieldkit.sling.no_arm");
	}

	@Override
	protected void treat(Player medic, LivingEntity patient, ItemStack stack, InteractionHand hand) {
		BodyPart part = target(patient);
		if (part == null) {
			return;
		}
		Dressings.sling(patient, part);
		if (patient instanceof Player player) {
			player.stopUsingItem();
		}
		useUp(medic, stack);
		tell(medic, patient, "realisticdamage.fieldkit.sling.done", part);
	}
}
