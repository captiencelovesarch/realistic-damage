package dev.captience.realisticdamage.fieldkit.medicine;

import com.mojang.serialization.Codec;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.fieldkit.Half;
import dev.captience.realisticdamage.fieldkit.Kit;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;

/**
 * The Medicine half's items (2.0, phase 4). Round 1, bleeding: the bandage,
 * the tourniquet, cauterizing, needle and thread and the blood bag. Round 2,
 * bones, head and senses: the splint, the sling, smelling salts, the
 * stethoscope and tinted goggles.
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
	public static final Item SPLINT = Kit.item("splint", SplintItem::new,
			new Item.Properties().stacksTo(8), Half.MEDICINE);
	public static final Item SLING = Kit.item("sling", SlingItem::new,
			new Item.Properties().stacksTo(4), Half.MEDICINE);
	public static final Item SMELLING_SALTS = Kit.item("smelling_salts", SmellingSaltsItem::new,
			new Item.Properties().stacksTo(16), Half.MEDICINE);
	public static final Item STETHOSCOPE = Kit.item("stethoscope", StethoscopeItem::new,
			new Item.Properties().stacksTo(1), Half.MEDICINE);
	public static final Item TINTED_GOGGLES = Kit.item("tinted_goggles", Item::new,
			new Item.Properties().stacksTo(1).component(DataComponents.EQUIPPABLE,
					Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build()),
			Half.MEDICINE);

	private static final Identifier FIRST = RealisticDamage.id("fieldkit_first");
	private static final Identifier LATE = RealisticDamage.id("fieldkit_treat");

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
					|| (Dressings.oldestTourniquet(patient) == null && Dressings.anySling(patient) == null)) {
				return InteractionResult.PASS;
			}
			return loosen(player, patient) ? InteractionResult.SUCCESS_SERVER : InteractionResult.PASS;
		});
		// A treatment on someone goes to the treatment, after the core has had
		// its say (a hand that can't grip, dragging a body with a sneak) and
		// before the body's own reaction to a right-click (a villager's trades,
		// a horse's saddle) would take it.
		UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LATE);
		UseEntityCallback.EVENT.register(LATE, (player, level, hand, entity, hit) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (player.isSpectator() || !(entity instanceof LivingEntity patient)
					|| !(stack.getItem() instanceof TreatmentItem || stack.getItem() instanceof StethoscopeItem)) {
				return InteractionResult.PASS;
			}
			return stack.interactLivingEntity(player, patient, hand);
		});
		// A main arm in a sling doesn't swing: no hitting, no digging, until it's out.
		AttackEntityCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE);
		AttackEntityCallback.EVENT.register(FIRST, (player, level, hand, entity, hit) -> armHeld(player, level));
		AttackBlockCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE);
		AttackBlockCallback.EVENT.register(FIRST, (player, level, hand, pos, direction) -> armHeld(player, level));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Dressings.loggedIn(handler.player));
	}

	/** FAIL when this player's main arm is in a sling, saying so now and then. Either side. */
	private static InteractionResult armHeld(Player player, Level level) {
		if (player.isCreative() || player.isSpectator()
				|| !Dressings.slung(player, dev.captience.realisticdamage.systems.ImpairmentSystem.armFor(player, true))) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server
				&& dev.captience.realisticdamage.RDAttachments.get(server).claimGripMessage(level.getGameTime())) {
			server.sendOverlayMessage(gray(Component.translatable("realisticdamage.fieldkit.sling.held",
					Dressings.partName(dev.captience.realisticdamage.systems.ImpairmentSystem.armFor(player, true)))));
		}
		return InteractionResult.FAIL;
	}

	/** Which of a player's hands is held in a sling for this use, or null. Read with the core's grip. */
	public static InteractionHand slungHand(ServerPlayer player, InteractionHand hand) {
		if (player.isCreative() || player.isSpectator()) {
			return null;
		}
		if (Dressings.slung(player, dev.captience.realisticdamage.systems.ImpairmentSystem.armFor(player,
				hand == InteractionHand.MAIN_HAND))) {
			return hand;
		}
		if (dev.captience.realisticdamage.systems.ImpairmentSystem.twoHanded(player.getItemInHand(hand))) {
			InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
			if (Dressings.slung(player, dev.captience.realisticdamage.systems.ImpairmentSystem.armFor(player,
					other == InteractionHand.MAIN_HAND))) {
				return other;
			}
		}
		return null;
	}

	/**
	 * Takes something off a body with a sneak-use and an empty hand: the
	 * longest-on tourniquet first, else an arm out of its sling. Hands it back.
	 */
	public static boolean loosen(Player medic, LivingEntity patient) {
		BodyPart part = Dressings.oldestTourniquet(patient);
		if (part == null) {
			BodyPart slung = Dressings.anySling(patient);
			if (slung == null || !Dressings.unsling(patient, slung)) {
				return false;
			}
			ItemStack sling = new ItemStack(SLING);
			if (!medic.getInventory().add(sling)) {
				medic.drop(sling, false);
			}
			TreatmentItem.tell(medic, patient, "realisticdamage.fieldkit.sling.off", slung);
			return true;
		}
		if (!Dressings.loosen(patient, part)) {
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
