package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.jspecify.annotations.Nullable;

/**
 * Smelling salts (2.0, phase 4 round 2): held under the nose of someone out
 * cold, they bring them round, when what put them out was a blow or a faint.
 * Not when it was blood loss (they'd only drop again), no air, or Sedation:
 * {@link SaltsRule}.
 */
public final class SmellingSaltsItem extends TreatmentItem {
	public SmellingSaltsItem(Properties properties) {
		super(properties, Balance.SALTS_USE_TICKS, false, ItemUseAnimation.BRUSH);
	}

	static SaltsRule.@Nullable Why why(LivingEntity patient) {
		RealisticDamageData data = RDAttachments.get(patient);
		return SaltsRule.why(data.unconscious(), data.blood(), ConsciousnessSystem.starved(patient),
				Sedation.stage(patient));
	}

	@Override
	protected @Nullable Component refusal(Player medic, LivingEntity patient, ItemStack stack) {
		SaltsRule.Why why = why(patient);
		return why == null ? null : Component.translatable("realisticdamage.fieldkit.salts." + why.key);
	}

	@Override
	protected void treat(Player medic, LivingEntity patient, ItemStack stack, InteractionHand hand) {
		wake(patient);
		useUp(medic, stack);
		medic.sendOverlayMessage(Component.translatable("realisticdamage.fieldkit.salts.done", patient.getDisplayName())
				.withStyle(ChatFormatting.GRAY));
		if (patient instanceof Player other && other != medic) {
			other.sendOverlayMessage(Component.translatable("realisticdamage.fieldkit.salts.done.patient")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * Brings a body far enough up that the next consciousness tick wakes it,
	 * through the core's own waking (the message, the mob getting up).
	 */
	public static void wake(LivingEntity patient) {
		RealisticDamageData data = RDAttachments.get(patient);
		data.setConsciousness(Math.max(data.consciousness(), Balance.WAKE_THRESHOLD + Balance.SALTS_WAKE_MARGIN));
		data.setUnconsciousTicks(Math.max(data.unconsciousTicks(), Balance.MIN_UNCONSCIOUS_TICKS));
		RDAttachments.pushSnapshot(patient);
	}
}
