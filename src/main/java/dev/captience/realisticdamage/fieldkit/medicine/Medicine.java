package dev.captience.realisticdamage.fieldkit.medicine;

import com.mojang.serialization.Codec;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.Kit;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The Medicine half's items (2.0, phase 4). Round 1, bleeding: the bandage,
 * the tourniquet, cauterizing, needle and thread and the blood bag.
 */
public final class Medicine {
	private Medicine() {}

	/** When a blood bag was drawn, in game ticks: it keeps three days. */
	public static final DataComponentType<Long> DRAWN_AT = DataComponentType.<Long>builder()
			.persistent(Codec.LONG)
			.networkSynchronized(ByteBufCodecs.VAR_LONG)
			.build();

	public static final Item BANDAGE = Kit.item("bandage", BandageItem::new,
			new Item.Properties().stacksTo(16), Half.MEDICINE);
	public static final Item TOURNIQUET = Kit.item("tourniquet", TourniquetItem::new,
			new Item.Properties().stacksTo(4), Half.MEDICINE);
	public static final Item NEEDLE_AND_THREAD = Kit.item("needle_and_thread", NeedleItem::new,
			new Item.Properties().durability(Balance.NEEDLE_USES), Half.MEDICINE);
	public static final Item BLOOD_BAG = Kit.item("blood_bag", BloodBagItem::new,
			new Item.Properties().stacksTo(1), Half.MEDICINE);

	private static final Identifier FIRST = RealisticDamage.id("fieldkit_first");

	public static void init() {
		Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, RealisticDamage.id("drawn_at"), DRAWN_AT);
		Dressings.init();
		Treatments.init();
		BloodDraw.init();
		Cauterize.init();
		PayloadTypeRegistry.serverboundPlay().register(LoosenPayload.TYPE, LoosenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(LoosenPayload.TYPE, (payload, context) -> {
			if (Half.MEDICINE.on()) {
				loosen(context.player(), context.player());
			}
		});
		// Taking a tourniquet off someone comes before the core's drag, which
		// would otherwise take the same sneak-use on a body that is out.
		UseEntityCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(FIRST, (player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(entity instanceof LivingEntity patient) || !player.isShiftKeyDown()
					|| !player.getItemInHand(hand).isEmpty() || !Half.MEDICINE.on()
					|| Dressings.oldestTourniquet(patient) == null) {
				return InteractionResult.PASS;
			}
			return loosen(player, patient) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Dressings.loggedIn(handler.player));
	}

	/** Takes the longest-on tourniquet off a body, and hands it back. */
	public static boolean loosen(Player medic, LivingEntity patient) {
		BodyPart part = Dressings.oldestTourniquet(patient);
		if (part == null || !Dressings.loosen(patient, part)) {
			return false;
		}
		ItemStack back = new ItemStack(TOURNIQUET);
		if (!medic.getInventory().add(back)) {
			medic.drop(back, false);
		}
		TreatmentItem.tell(medic, patient, "realisticdamage.fieldkit.tourniquet.off", part);
		return true;
	}

	/** Message helper for gray action-bar lines. */
	static Component gray(Component line) {
		return line.copy().withStyle(ChatFormatting.GRAY);
	}
}
