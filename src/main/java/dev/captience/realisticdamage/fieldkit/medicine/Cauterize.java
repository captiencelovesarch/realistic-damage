package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.Half;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Cauterizing (2.0, phase 4): sneak and use flint and steel, or a fire charge,
 * in the air on yourself or on someone else's wound. The worst bleed stops at
 * once; the pain is a big blow to consciousness that can put you out; and it
 * leaves a burn. Lighting fires is unchanged: that is used on a block.
 */
public final class Cauterize {
	private Cauterize() {}

	public static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !tool(player.getItemInHand(hand)) || !player.isShiftKeyDown()
					|| !Half.MEDICINE.on() || !RDAttachments.get(player).bleeding()) {
				return InteractionResult.PASS;
			}
			return cauterize(player, player, hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(entity instanceof LivingEntity patient) || !tool(player.getItemInHand(hand))
					|| !player.isShiftKeyDown() || !Half.MEDICINE.on() || !RDAttachments.get(patient).bleeding()) {
				return InteractionResult.PASS;
			}
			if (!Consent.given(player, patient)) {
				player.sendOverlayMessage(Component.translatable("realisticdamage.fieldkit.treat.consent")
						.withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			return cauterize(player, patient, hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
	}

	static boolean tool(ItemStack stack) {
		return stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
	}

	/** Burns the worst wound shut. False when there was none. */
	public static boolean cauterize(Player medic, LivingEntity patient, InteractionHand hand) {
		RealisticDamageData data = RDAttachments.get(patient);
		BleedWound wound = TreatmentItem.worst(data, w -> true);
		if (wound == null) {
			return false;
		}
		data.wounds().remove(wound);
		Dressings.unbandage(patient, wound.part());
		data.drainConsciousness(Balance.CAUTERIZE_SHOCK);
		data.setBurned(true);
		RDAttachments.pushSnapshot(patient);
		patient.level().playSound(null, patient.getX(), patient.getY(), patient.getZ(), SoundEvents.FIRE_EXTINGUISH,
				SoundSource.PLAYERS, 0.8F, 1.4F);
		ItemStack stack = medic.getItemInHand(hand);
		if (!medic.hasInfiniteMaterials()) {
			if (stack.is(Items.FIRE_CHARGE)) {
				stack.shrink(1);
			} else {
				stack.hurtAndBreak(1, medic, hand);
			}
		}
		TreatmentItem.tell(medic, patient, "realisticdamage.fieldkit.cauterize.done", wound.part());
		return true;
	}
}
