package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Drawing blood (2.0, phase 4): sneak and use a glass bottle on a donor, or in
 * the air for yourself, and it fills into a blood bag. The donor loses that
 * blood. Water still fills a bottle as it always did, and so does any use that
 * isn't a sneak.
 */
public final class BloodDraw {
	private BloodDraw() {}

	public static void init() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() || !stack.is(Items.GLASS_BOTTLE) || !player.isShiftKeyDown()
					|| !Half.MEDICINE.on() || aimingAtWater(level, player)) {
				return InteractionResult.PASS;
			}
			return draw(player, player, hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(entity instanceof LivingEntity donor)
					|| !player.getItemInHand(hand).is(Items.GLASS_BOTTLE) || !player.isShiftKeyDown()
					|| !Half.MEDICINE.on()) {
				return InteractionResult.PASS;
			}
			return draw(player, donor, hand) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
		});
	}

	/** Fills the bottle in this hand from the donor. False, with the reason said, when it can't. */
	public static boolean draw(Player medic, LivingEntity donor, InteractionHand hand) {
		Component no = refusal(medic, donor);
		if (no != null) {
			medic.sendOverlayMessage(no.copy().withStyle(ChatFormatting.GRAY));
			return false;
		}
		RealisticDamageData data = RDAttachments.get(donor);
		data.drainBlood(Balance.BLOOD_BAG_DRAW);
		RDAttachments.pushSnapshot(donor);
		ItemStack bottle = medic.getItemInHand(hand);
		medic.setItemInHand(hand, ItemUtils.createFilledResult(bottle, medic, BloodBagItem.drawn(medic.level())));
		medic.sendOverlayMessage(donor == medic
				? Component.translatable("realisticdamage.fieldkit.blood_bag.drawn.self")
				: Component.translatable("realisticdamage.fieldkit.blood_bag.drawn", donor.getDisplayName()));
		return true;
	}

	private static Component refusal(Player medic, LivingEntity donor) {
		if (!(donor instanceof Player) && !MobAnatomy.isMobWithBody(donor) || !MobAnatomy.bleeds(donor)) {
			return Component.translatable("realisticdamage.fieldkit.blood_bag.bloodless");
		}
		if (!Consent.given(medic, donor)) {
			return Component.translatable("realisticdamage.fieldkit.treat.consent");
		}
		if (RDAttachments.get(donor).blood() < Balance.BLOOD_BAG_MIN_DONOR) {
			return Component.translatable("realisticdamage.fieldkit.blood_bag.too_weak");
		}
		return null;
	}

	/** A bottle aimed at water fills with water, as vanilla says. */
	private static boolean aimingAtWater(Level level, Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 reach = eye.add(player.getLookAngle().scale(player.blockInteractionRange()));
		BlockHitResult hit = level.clip(new ClipContext(eye, reach, ClipContext.Block.OUTLINE,
				ClipContext.Fluid.SOURCE_ONLY, player));
		return hit.getType() == HitResult.Type.BLOCK
				&& !level.getFluidState(hit.getBlockPos()).isEmpty();
	}
}
