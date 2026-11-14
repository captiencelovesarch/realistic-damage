package dev.captience.realisticdamage.fieldkit.gametest;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.damage.ArmorModifier;
import dev.captience.realisticdamage.damage.CeilingLogic;
import dev.captience.realisticdamage.damage.DamageCategory;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.fieldkit.medicine.Medicine;
import dev.captience.realisticdamage.gametest.TestPlayer;
import dev.captience.realisticdamage.systems.ConcussionSystem;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import dev.captience.realisticdamage.systems.HealingSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 4, round 2: bones, head and senses, on real players (2.0). The splint,
 * the sling, smelling salts and tinted goggles. (The stethoscope is all sound,
 * on the client: the client game test listens with it.)
 *
 * <p>The batch's environment switches Medicine on before and off after.
 */
public class MedicineBonesGameTest {
	private static TestPlayer player(GameTestHelper helper, double x, String name) {
		TestPlayer player = TestPlayer.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(x, 2.0, 1.5)), 0.0F, name);
		RDAttachments.get(player).reset();
		RDAttachments.get(player).clearWounds();
		return player;
	}

	private static void gone(ServerPlayer... players) {
		for (ServerPlayer player : players) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("done"));
		}
	}

	/** Breaks a limb down to this share of its condition. */
	private static BodyPartData broken(RealisticDamageData data, BodyPart part, double fraction) {
		BodyPartData limb = data.part(part);
		limb.setCondition(part.maxCondition() * fraction);
		limb.setBroken(true);
		return limb;
	}

	/** What a penalty modifier takes off, as a positive share. */
	private static double penalty(ServerPlayer player, Holder<Attribute> attribute, String id) {
		AttributeModifier modifier = player.getAttribute(attribute).getModifier(RealisticDamage.id(id));
		return modifier == null ? 0.0 : -modifier.amount();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aSplintHalvesABrokenLegsLimp(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-splint-leg");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			broken(data, BodyPart.LEFT_LEG, 0.3);
			ImpairmentSystem.tick(patient, data);
			double bare = penalty(patient, Attributes.MOVEMENT_SPEED, "leg_speed");
			Dressings.splint(patient, BodyPart.LEFT_LEG);
			ImpairmentSystem.tick(patient, data);
			double splinted = penalty(patient, Attributes.MOVEMENT_SPEED, "leg_speed");
			helper.assertTrue(bare > 0.1, "a broken leg limps: " + bare);
			helper.assertTrue(Math.abs(splinted - bare / 2.0) < 1e-6, "a splint halves it: " + bare + " -> " + splinted);
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aSplintedArmSwingsAndDrawsBetter(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-splint-arm");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			BodyPart main = ImpairmentSystem.armFor(patient, true);
			broken(data, main, 0.3);
			ImpairmentSystem.tick(patient, data);
			double bare = penalty(patient, Attributes.ATTACK_SPEED, "arm_attack_speed");
			double bareDraw = ImpairmentSystem.twoHandedPenalty(patient, data);
			Dressings.splint(patient, main);
			ImpairmentSystem.tick(patient, data);
			double splinted = penalty(patient, Attributes.ATTACK_SPEED, "arm_attack_speed");
			double splintedDraw = ImpairmentSystem.twoHandedPenalty(patient, data);
			helper.assertTrue(bare > 0.2, "a broken arm swings slow: " + bare);
			helper.assertTrue(Math.abs(splinted - bare / 2.0) < 1e-6, "a splint halves it: " + bare + " -> " + splinted);
			helper.assertTrue(splintedDraw < bareDraw, "and eases the bow: " + bareDraw + " -> " + splintedDraw);
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	/** Long-term injuries: the break lowers the ceiling at once; knit in a splint, it is given back. */
	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aLimbKnitInASplintKeepsItsStrength(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-splint-ceiling");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			BodyPartData left = broken(data, BodyPart.LEFT_LEG, 0.3);
			BodyPartData right = broken(data, BodyPart.RIGHT_LEG, 0.3);
			CeilingLogic.evaluate(patient, data, List.of(
					new TraumaPipeline.AppliedTrauma(BodyPart.LEFT_LEG, 6.0, 6.0, true),
					new TraumaPipeline.AppliedTrauma(BodyPart.RIGHT_LEG, 6.0, 6.0, true)), 0, 0);
			helper.assertTrue(left.ceilingBand() == 1 && right.ceilingBand() == 1, "each break costs a band");
			helper.assertTrue(Dressings.peek(patient) != null
					&& Dressings.peek(patient).owed.get(BodyPart.LEFT_LEG) == 0, "and what it was is remembered");

			Dressings.splint(patient, BodyPart.LEFT_LEG);
			left.setCondition(left.effectiveMax());
			right.setCondition(right.effectiveMax());
			helper.assertTrue(!left.broken() && !right.broken(), "both knit, at their lowered ceiling");
			Dressings.tickBody(patient, Dressings.peek(patient));

			helper.assertTrue(left.ceilingBand() == 0, "the splinted leg gets its strength back");
			helper.assertTrue(right.ceilingBand() == 1, "the bare one doesn't");
			Dressings.State state = Dressings.peek(patient);
			helper.assertTrue(state.splints.isEmpty() && state.owed.isEmpty(), "the splint comes off; nothing's owed");
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aSlungArmIsHeldStill(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-sling");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			BodyPart main = ImpairmentSystem.armFor(patient, true);
			broken(data, main, 0.4);
			helper.assertTrue(ImpairmentSystem.gripBlocked(patient, InteractionHand.MAIN_HAND) == null,
					"a broken arm still grips");
			patient.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Medicine.SLING));
			InteractionResult tied = patient.getMainHandItem().use(helper.getLevel(), patient, InteractionHand.MAIN_HAND);
			patient.getMainHandItem().finishUsingItem(helper.getLevel(), patient);
			helper.assertTrue(tied != InteractionResult.FAIL && Dressings.slung(patient, main), "the sling goes on");
			helper.assertTrue(patient.getMainHandItem().isEmpty(), "and is used");

			helper.assertTrue(ImpairmentSystem.gripBlocked(patient, InteractionHand.MAIN_HAND) == InteractionHand.MAIN_HAND,
					"its hand can't be used");
			helper.assertTrue(ImpairmentSystem.gripBlocked(patient, InteractionHand.OFF_HAND) == null,
					"the other hand can");
			patient.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOW));
			helper.assertTrue(ImpairmentSystem.gripBlocked(patient, InteractionHand.OFF_HAND) == InteractionHand.MAIN_HAND,
					"but not for a bow, which wants both");
			helper.assertTrue(AttackEntityCallback.EVENT.invoker().interact(patient, helper.getLevel(),
					InteractionHand.MAIN_HAND, patient, null) == InteractionResult.FAIL, "a slung main arm can't swing");

			patient.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			helper.assertTrue(Medicine.loosen(patient, patient), "a sneak-use with an empty hand takes it out");
			helper.assertTrue(!Dressings.slung(patient, main)
					&& ImpairmentSystem.gripBlocked(patient, InteractionHand.MAIN_HAND) == null, "and the hand is free");
			helper.assertTrue(patient.getInventory().countItem(Medicine.SLING) == 1, "the sling comes back");
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aSlungArmHealsTwiceAsFast(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-sling-heal");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			BodyPartData left = broken(data, BodyPart.LEFT_ARM, 0.3);
			BodyPartData right = broken(data, BodyPart.RIGHT_ARM, 0.3);
			Dressings.sling(patient, BodyPart.LEFT_ARM);
			double left0 = left.condition();
			double right0 = right.condition();
			HealingSystem.onVanillaHeal(patient, 1.0F);
			double slung = left.condition() - left0;
			double bare = right.condition() - right0;
			helper.assertTrue(bare > 0.0, "a heal mends the bare arm: " + bare);
			helper.assertTrue(Math.abs(slung / bare - 2.0) < 0.05, "and the slung one twice as much: " + slung + " / " + bare);
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void smellingSaltsBringAKnockoutRound(GameTestHelper helper) {
		TestPlayer medic = player(helper, 1.5, "fk-salts-medic");
		TestPlayer patient = player(helper, 2.5, "fk-salts-out");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			data.setConsciousness(0.0);
			data.setUnconscious(true);
			ItemStack salts = new ItemStack(Medicine.SMELLING_SALTS, 2);
			medic.setItemInHand(InteractionHand.MAIN_HAND, salts);
			// Through the game's own right-click on a body, so the treatment gets it first.
			InteractionResult started = UseEntityCallback.EVENT.invoker().interact(medic, helper.getLevel(),
					InteractionHand.MAIN_HAND, patient, null);
			helper.assertTrue(started.consumesAction(), "salts go under the nose: " + started);
			salts.finishUsingItem(helper.getLevel(), medic);
			helper.assertTrue(salts.getCount() == 1, "one is used");
			ConsciousnessSystem.tick(patient, data);
			helper.assertTrue(!data.unconscious(), "and they come round");
		} finally {
			gone(medic, patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void smellingSaltsCantBeatBloodLoss(GameTestHelper helper) {
		TestPlayer medic = player(helper, 1.5, "fk-salts-medic2");
		TestPlayer patient = player(helper, 2.5, "fk-salts-bled");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			data.setBlood(30.0);
			data.setConsciousness(0.0);
			data.setUnconscious(true);
			ItemStack salts = new ItemStack(Medicine.SMELLING_SALTS, 2);
			medic.setItemInHand(InteractionHand.MAIN_HAND, salts);
			InteractionResult refused = UseEntityCallback.EVENT.invoker().interact(medic, helper.getLevel(),
					InteractionHand.MAIN_HAND, patient, null);
			helper.assertTrue(refused == InteractionResult.FAIL, "too little blood: refused, " + refused);
			helper.assertTrue(salts.getCount() == 2, "and nothing used");
			ConsciousnessSystem.tick(patient, data);
			helper.assertTrue(data.unconscious(), "they stay out");
		} finally {
			gone(medic, patient);
		}
		helper.succeed();
	}

	/** A right-click with a treatment on a villager treats it: its trades don't take the click. */
	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aTreatmentReachesAVillager(GameTestHelper helper) {
		TestPlayer medic = player(helper, 1.5, "fk-villager-medic");
		net.minecraft.world.entity.npc.villager.Villager villager = helper.spawn(
				net.minecraft.world.entity.EntityTypes.VILLAGER, new BlockPos(2, 2, 1));
		try {
			RDAttachments.get(villager).addWound(BodyPart.LEFT_ARM, dev.captience.realisticdamage.body.WoundSeverity.MINOR);
			ItemStack bandage = new ItemStack(Medicine.BANDAGE, 2);
			medic.setItemInHand(InteractionHand.MAIN_HAND, bandage);
			InteractionResult started = UseEntityCallback.EVENT.invoker().interact(medic, helper.getLevel(),
					InteractionHand.MAIN_HAND, villager, null);
			helper.assertTrue(started.consumesAction() && medic.isUsingItem(), "the bandaging starts: " + started);
			bandage.finishUsingItem(helper.getLevel(), medic);
			helper.assertTrue(Dressings.peek(villager) != null
					&& Dressings.peek(villager).bandages.containsKey(BodyPart.LEFT_ARM), "and lands on the villager");
			helper.assertTrue(!villager.isTrading(), "no trades opened");
		} finally {
			gone(medic);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void gogglesAreNotAHelmet(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-goggles-head");
		try {
			patient.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Medicine.TINTED_GOGGLES));
			double goggles = ArmorModifier.reduction(patient, BodyPart.HEAD, DamageCategory.GENERIC);
			patient.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			double helmet = ArmorModifier.reduction(patient, BodyPart.HEAD, DamageCategory.GENERIC);
			helper.assertTrue(goggles == 0.0, "goggles take nothing off a blow: " + goggles);
			helper.assertTrue(helmet > 0.0, "a helmet does: " + helmet);
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	/** Two concussed players walking about under a light: only the one in goggles isn't flared by it. */
	@GameTest(environment = "realisticdamage:fieldkit_medicine_on", maxTicks = 40)
	public void gogglesStopTheLightFlaringAConcussion(GameTestHelper helper) {
		TestPlayer bare = player(helper, 1.5, "fk-goggles-bare");
		TestPlayer shaded = player(helper, 3.5, "fk-goggles-on");
		shaded.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Medicine.TINTED_GOGGLES));
		for (int x : new int[] {1, 3}) {
			helper.setBlock(new BlockPos(x, 3, 1), Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15));
		}
		helper.runAfterDelay(10, () -> {
			try {
				double[] flares = new double[2];
				TestPlayer[] players = {bare, shaded};
				for (int i = 0; i < 2; i++) {
					TestPlayer player = players[i];
					RealisticDamageData data = RDAttachments.get(player);
					data.setConcussionSeverity(100.0);
					data.setConcussionFlare(0.0);
					double x = player.getX();
					for (int tick = 0; tick < 20; tick++) {
						// Pacing to and fro: light activity, so the flare doesn't simply rest away.
						player.setPos(x + (tick % 2 == 0 ? 0.06 : -0.06), player.getY(), player.getZ());
						ConcussionSystem.tick(player, data);
					}
					flares[i] = data.concussionFlare();
				}
				helper.assertTrue(flares[0] > 1.0, "bright light flares a concussion: " + flares[0]);
				helper.assertTrue(flares[1] < 0.01, "not behind tinted goggles: " + flares[1]);
			} finally {
				gone(bare, shaded);
			}
			helper.succeed();
		});
	}
}
