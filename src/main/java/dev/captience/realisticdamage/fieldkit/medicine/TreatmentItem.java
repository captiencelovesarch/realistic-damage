package dev.captience.realisticdamage.fieldkit.medicine;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BleedWound;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A field kit medical item that is held to work (2.0, phase 4): use it in the
 * air to treat yourself, or on someone (a player or any body) to treat them.
 * Hold use until it lands; let go and nothing happens; it's lost if they
 * leave your reach, and the delicate ones are lost if either of you is hurt.
 *
 * <p>Whether there is anything to treat is the server's call, made when you
 * start, and again when it lands: a wound can close, or open, in between.
 */
public abstract class TreatmentItem extends Item {
	private final int useTicks;
	private final boolean interruptible;
	private final ItemUseAnimation animation;

	protected TreatmentItem(Properties properties, int useTicks, boolean interruptible, ItemUseAnimation animation) {
		super(properties);
		this.useTicks = useTicks;
		this.interruptible = interruptible;
		this.animation = animation;
	}

	public boolean interruptible() {
		return interruptible;
	}

	/** Why this patient can't be treated now (to show the medic), or null when they can. Server only. */
	protected abstract @Nullable Component refusal(Player medic, LivingEntity patient, ItemStack stack);

	/** The treatment lands. Use up or wear the stack as the item does. Server only. */
	protected abstract void treat(Player medic, LivingEntity patient, ItemStack stack, InteractionHand hand);

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		return begin(level, player, player, hand);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		return begin(player.level(), player, target, hand);
	}

	private InteractionResult begin(Level level, Player medic, LivingEntity patient, InteractionHand hand) {
		if (!level.isClientSide()) {
			Component no = patient instanceof Player || MobAnatomy.isMobWithBody(patient)
					? refusal(medic, patient, medic.getItemInHand(hand))
					: Component.translatable("realisticdamage.fieldkit.treat.no_body");
			if (no != null) {
				medic.sendOverlayMessage(no.copy().withStyle(ChatFormatting.GRAY));
				return InteractionResult.FAIL;
			}
			Treatments.begin(medic, patient);
		}
		medic.startUsingItem(hand);
		return InteractionResult.CONSUME;
	}

	@Override
	public int getUseDuration(ItemStack stack, LivingEntity user) {
		return useTicks;
	}

	@Override
	public ItemUseAnimation getUseAnimation(ItemStack stack) {
		return animation;
	}

	@Override
	public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
		if (!level.isClientSide() && entity instanceof Player medic && Treatments.treating(medic)
				&& Treatments.patient(medic) == null) {
			Treatments.end(medic);
			medic.stopUsingItem();
			medic.sendOverlayMessage(Component.translatable("realisticdamage.fieldkit.treat.lost")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		if (!level.isClientSide() && entity instanceof Player medic) {
			LivingEntity patient = Treatments.patient(medic);
			Treatments.end(medic);
			if (patient != null) {
				Component no = refusal(medic, patient, stack);
				if (no != null) {
					medic.sendOverlayMessage(no.copy().withStyle(ChatFormatting.GRAY));
				} else {
					InteractionHand hand = medic.getUsedItemHand();
					treat(medic, patient, stack, hand);
					RDAttachments.pushSnapshot(patient);
				}
			}
		}
		return stack;
	}

	@Override
	public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
		if (!level.isClientSide() && entity instanceof Player medic) {
			Treatments.end(medic);
		}
		return false;
	}

	// --- shared helpers ---

	/** The worst wound matching a test: highest severity, ties to the region listed first. */
	static @Nullable BleedWound worst(RealisticDamageData data, java.util.function.Predicate<BleedWound> ok) {
		BleedWound worst = null;
		for (BodyPart part : BodyPart.values()) {
			BleedWound wound = Dressings.woundOn(data, part);
			if (wound != null && ok.test(wound)
					&& (worst == null || wound.severity().ordinal() > worst.severity().ordinal())) {
				worst = wound;
			}
		}
		return worst;
	}

	/** "your left arm" or "Steve's left arm". */
	protected static Component whose(Player medic, LivingEntity patient, BodyPart part) {
		return medic == patient
				? Component.translatable("realisticdamage.fieldkit.treat.your", Dressings.partName(part))
				: Component.translatable("realisticdamage.fieldkit.treat.their", patient.getDisplayName(),
						Dressings.partName(part));
	}

	/** Tells the medic what was done, and a player patient who did it. */
	static void tell(Player medic, LivingEntity patient, String key, BodyPart part) {
		medic.sendOverlayMessage(Component.translatable(key, whose(medic, patient, part)));
		if (patient instanceof Player other && other != medic) {
			other.sendOverlayMessage(Component.translatable(key + ".patient", medic.getDisplayName(),
					Dressings.partName(part)));
		}
	}

	protected static void useUp(Player medic, ItemStack stack) {
		if (!medic.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
	}
}
