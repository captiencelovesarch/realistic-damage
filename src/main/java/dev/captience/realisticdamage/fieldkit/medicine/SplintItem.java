package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
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
 * A splint (2.0, phase 4 round 2): bound onto the worst broken limb, it halves
 * what the break costs (the limp, the slow swing, the stalled draw) and stays
 * on until the bone has knit. Knit in a splint, a limb keeps its long-term
 * strength: the break's lowered ceiling is given back.
 */
public final class SplintItem extends TreatmentItem {
	public SplintItem(Properties properties) {
		super(properties, Balance.SPLINT_USE_TICKS, false, ItemUseAnimation.BRUSH);
	}

	/** The limb this splint would go on: the most broken-down broken limb without one. */
	static @Nullable BodyPart target(LivingEntity patient) {
		RealisticDamageData data = RDAttachments.get(patient);
		Dressings.State dressed = Dressings.peek(patient);
		return worstBroken(data, part -> dressed == null || !dressed.splints.contains(part));
	}

	/** The broken limb with the least condition left that passes a test, or null. */
	static @Nullable BodyPart worstBroken(RealisticDamageData data, java.util.function.Predicate<BodyPart> ok) {
		BodyPart worst = null;
		for (BodyPart part : BodyPart.values()) {
			BodyPartData limb = data.part(part);
			if (part.isLimb() && limb.broken() && !limb.destroyed() && ok.test(part)
					&& (worst == null || limb.fraction() < data.part(worst).fraction())) {
				worst = part;
			}
		}
		return worst;
	}

	@Override
	protected @Nullable Component refusal(Player medic, LivingEntity patient, ItemStack stack) {
		if (target(patient) != null) {
			return null;
		}
		return worstBroken(RDAttachments.get(patient), part -> true) != null
				? Component.translatable("realisticdamage.fieldkit.splint.already")
				: Component.translatable("realisticdamage.fieldkit.treat.nothing_broken");
	}

	@Override
	protected void treat(Player medic, LivingEntity patient, ItemStack stack, InteractionHand hand) {
		BodyPart part = target(patient);
		if (part == null) {
			return;
		}
		Dressings.splint(patient, part);
		useUp(medic, stack);
		tell(medic, patient, "realisticdamage.fieldkit.splint.done", part);
	}
}
