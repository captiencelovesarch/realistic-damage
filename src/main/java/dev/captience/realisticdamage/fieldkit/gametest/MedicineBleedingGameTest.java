package dev.captience.realisticdamage.fieldkit.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.fieldkit.medicine.BloodBagItem;
import dev.captience.realisticdamage.fieldkit.medicine.BloodDraw;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.fieldkit.medicine.Medicine;
import dev.captience.realisticdamage.gametest.TestPlayer;
import dev.captience.realisticdamage.systems.BleedingSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 4, round 1: the bleeding kit, on real players (2.0).
 *
 * <p>Treatments are vanilla's hold-to-use: these start one the way the game
 * does ({@code use} in the air, {@code interactLivingEntity} on someone) and
 * then land it with {@code finishUsingItem}, the call vanilla makes when the
 * time is up — nothing ticks a test player for the timer to run out on its own.
 * Uses of vanilla items (a glass bottle, flint and steel) go through
 * {@code ServerPlayerGameMode.useItem}, where the server sends a player's use.
 *
 * <p>The batch's environment switches Medicine on before and off after.
 */
public class MedicineBleedingGameTest {
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

	/** Blood lost over a second, ticking the bleed as the game does. */
	private static double drain(ServerPlayer body) {
		RealisticDamageData data = RDAttachments.get(body);
		double before = data.blood();
		for (int tick = 0; tick < 20; tick++) {
			BleedingSystem.tick(body, data);
		}
		double lost = before - data.blood();
		data.setBlood(before);
		return lost;
	}

	/** Holds a treatment on yourself through to the end. */
	private static InteractionResult treatSelf(GameTestHelper helper, ServerPlayer medic, ItemStack stack) {
		medic.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult started = stack.use(helper.getLevel(), medic, InteractionHand.MAIN_HAND);
		if (started != InteractionResult.FAIL) {
			stack.finishUsingItem(helper.getLevel(), medic);
		}
		return started;
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aBandageSlowsAWoundButNotAnArtery(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-bandaged");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			data.addWound(BodyPart.LEFT_ARM, WoundSeverity.MINOR);
			double bare = drain(patient);
			InteractionResult started = treatSelf(helper, patient, new ItemStack(Medicine.BANDAGE, 2));
			double dressed = drain(patient);
			helper.assertTrue(started != InteractionResult.FAIL, "a bleeding body takes a bandage");
			helper.assertTrue(Math.abs(dressed / bare - Balance.BANDAGE_MINOR_FACTOR) < 0.02,
					"a bandaged minor wound bleeds at a quarter: " + bare + " -> " + dressed);
			helper.assertTrue(patient.getMainHandItem().getCount() == 1, "one bandage used");

			data.clearWounds();
			data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
			InteractionResult refused = treatSelf(helper, patient, new ItemStack(Medicine.BANDAGE));
			helper.assertTrue(refused == InteractionResult.FAIL, "an arterial bleed won't take a bandage");
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aModerateWoundSoaksTheBandageThrough(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-soaked");
		try {
			RDAttachments.get(patient).addWound(BodyPart.RIGHT_LEG, WoundSeverity.MODERATE);
			double bare = drain(patient);
			treatSelf(helper, patient, new ItemStack(Medicine.BANDAGE));
			double fresh = drain(patient);
			for (int tick = 0; tick < Balance.BANDAGE_SOAK_TICKS; tick++) {
				Dressings.tickBody(patient, Dressings.peek(patient));
			}
			double soaked = drain(patient);
			helper.assertTrue(fresh < bare * 0.5, "fresh, it holds: " + bare + " -> " + fresh);
			helper.assertTrue(Dressings.peek(patient).bandages.get(BodyPart.RIGHT_LEG).soaked, "in time it soaks through");
			helper.assertTrue(Math.abs(soaked - bare) < 1.0e-6, "soaked, it holds nothing: " + soaked + " vs " + bare);
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	/** On someone else: right-click them with it, hold, and it lands on their worst wound. */
	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aMedicTiesOffAPatientsLegAndItGoesDead(GameTestHelper helper) {
		TestPlayer medic = player(helper, 1.5, "fk-medic");
		TestPlayer patient = player(helper, 3.0, "fk-tied");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			data.addWound(BodyPart.LEFT_LEG, WoundSeverity.CRITICAL);
			BodyPartData leg = data.part(BodyPart.LEFT_LEG);
			double condition = leg.condition();
			ItemStack tourniquet = new ItemStack(Medicine.TOURNIQUET);
			medic.setItemInHand(InteractionHand.MAIN_HAND, tourniquet);
			InteractionResult started = tourniquet.interactLivingEntity(medic, patient, InteractionHand.MAIN_HAND);
			tourniquet.finishUsingItem(helper.getLevel(), medic);
			helper.assertTrue(started != InteractionResult.FAIL, "a medic can tie off someone else's limb");
			helper.assertTrue(drain(patient) < 1.0e-6, "a tourniquet stops a limb bleed dead");
			helper.assertTrue(leg.condition() == 0.0, "and the limb is Disabled while it is on");

			boolean off = Medicine.loosen(medic, patient);
			helper.assertTrue(off && Math.abs(leg.condition() - condition) < 1.0e-6,
					"loosened, the limb gets back what was held: " + condition + " -> " + leg.condition());
			helper.assertTrue(medic.getInventory().countItem(Medicine.TOURNIQUET) == 1, "and the tourniquet comes back");
		} finally {
			gone(medic, patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aTourniquetLeftOnTooLongKillsTheLimb(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-forgot");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			data.addWound(BodyPart.RIGHT_ARM, WoundSeverity.SEVERE);
			treatSelf(helper, patient, new ItemStack(Medicine.TOURNIQUET));
			Dressings.peek(patient).tourniquets.get(BodyPart.RIGHT_ARM).ticks = Balance.TOURNIQUET_LIMIT_TICKS - 1;
			Dressings.tickBody(patient, Dressings.peek(patient));
			helper.assertTrue(data.part(BodyPart.RIGHT_ARM).destroyed(), "left on too long, the arm is dead");
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aNeedleClosesAWoundForGoodUnlessYoureHurt(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-stitched");
		TestPlayer flinched = player(helper, 3.0, "fk-unstitched");
		try {
			RDAttachments.get(patient).addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
			ItemStack needle = new ItemStack(Medicine.NEEDLE_AND_THREAD);
			treatSelf(helper, patient, needle);
			helper.assertFalse(RDAttachments.get(patient).bleeding(), "stitched, the wound is closed for good");
			helper.assertTrue(needle.getDamageValue() == 1, "and the thread is a little shorter");

			RDAttachments.get(flinched).addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
			ItemStack second = new ItemStack(Medicine.NEEDLE_AND_THREAD);
			flinched.setItemInHand(InteractionHand.MAIN_HAND, second);
			second.use(helper.getLevel(), flinched, InteractionHand.MAIN_HAND);
			flinched.hurtServer(helper.getLevel(), helper.getLevel().damageSources().generic(), 1.0F);
			second.finishUsingItem(helper.getLevel(), flinched);
			helper.assertTrue(RDAttachments.get(flinched).bleeding(), "hurt mid-stitch, the stitching is spoiled");
		} finally {
			gone(patient, flinched);
		}
		helper.succeed();
	}

	/** Sneak-use flint and steel in the air: the worst wound is burned shut, and it hurts. */
	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void cauterizingBurnsAWoundShut(GameTestHelper helper) {
		TestPlayer patient = player(helper, 1.5, "fk-burned");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			data.addWound(BodyPart.LEFT_LEG, WoundSeverity.CRITICAL);
			double before = data.consciousness();
			ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
			patient.setItemInHand(InteractionHand.MAIN_HAND, flint);
			patient.setShiftKeyDown(true);
			patient.gameMode.useItem(patient, helper.getLevel(), flint, InteractionHand.MAIN_HAND);
			helper.assertFalse(data.bleeding(), "the bleed stops at once");
			helper.assertTrue(before - data.consciousness() >= Balance.CAUTERIZE_SHOCK - 1.0e-6,
					"and the pain takes a big bite of consciousness: " + before + " -> " + data.consciousness());
			helper.assertTrue(data.burned(), "and it leaves a burn");
			helper.assertTrue(flint.getDamageValue() == 1, "it wears the flint and steel");
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	/** Bank your own blood with a glass bottle; give it to someone who needs it; it goes off in time. */
	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void bloodIsBankedGivenAndGoesOff(GameTestHelper helper) {
		TestPlayer donor = player(helper, 1.5, "fk-donor");
		TestPlayer patient = player(helper, 3.0, "fk-pale");
		try {
			donor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
			donor.setShiftKeyDown(true);
			donor.gameMode.useItem(donor, helper.getLevel(), donor.getMainHandItem(), InteractionHand.MAIN_HAND);
			ItemStack bag = donor.getMainHandItem();
			helper.assertTrue(bag.is(Medicine.BLOOD_BAG) && bag.get(Medicine.DRAWN_AT) != null,
					"a sneak-used bottle fills with your own blood, got " + bag);
			helper.assertTrue(Math.abs(RDAttachments.get(donor).blood() - (Balance.BLOOD_MAX - Balance.BLOOD_BAG_DRAW)) < 1.0e-6,
					"and you lose that blood");

			RDAttachments.get(patient).setBlood(40.0);
			donor.setShiftKeyDown(false);
			bag.interactLivingEntity(donor, patient, InteractionHand.MAIN_HAND);
			bag.finishUsingItem(helper.getLevel(), donor);
			helper.assertTrue(Math.abs(RDAttachments.get(patient).blood() - (40.0 + Balance.BLOOD_BAG_GIVE)) < 1.0e-6,
					"given, it restores the patient's blood: " + RDAttachments.get(patient).blood());
			helper.assertTrue(donor.getInventory().countItem(Items.GLASS_BOTTLE) == 1, "and the bottle comes back");

			ItemStack old = BloodBagItem.drawn(helper.getLevel());
			old.set(Medicine.DRAWN_AT, helper.getLevel().getGameTime() - Balance.BLOOD_BAG_KEEPS_TICKS - 1);
			donor.setItemInHand(InteractionHand.MAIN_HAND, old);
			helper.assertTrue(old.use(helper.getLevel(), donor, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
					"three days on, it has gone off and does nothing");

			RDAttachments.get(patient).setBlood(Balance.BLOOD_MAX);
			patient.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			donor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
			helper.assertFalse(BloodDraw.draw(donor, patient, InteractionHand.MAIN_HAND),
					"a player who is awake can't be bled by someone else");
		} finally {
			gone(donor, patient);
		}
		helper.succeed();
	}

	/** With Medicine off, a bandage does nothing and says so. */
	@GameTest(environment = "realisticdamage:isolated")
	public void switchedOffMedicineDoesNothing(GameTestHelper helper) {
		ServerConfig config = ServerConfig.get();
		boolean medicine = config.enableMedicine;
		TestPlayer patient = player(helper, 1.5, "fk-unbandaged");
		try {
			config.enableMedicine = false;
			RDAttachments.get(patient).addWound(BodyPart.LEFT_ARM, WoundSeverity.MINOR);
			ItemStack bandage = new ItemStack(Medicine.BANDAGE);
			patient.setItemInHand(InteractionHand.MAIN_HAND, bandage);
			helper.assertTrue(bandage.use(helper.getLevel(), patient, InteractionHand.MAIN_HAND) == InteractionResult.FAIL,
					"Medicine off: the bandage is refused");
			helper.assertTrue(Dressings.peek(patient) == null || Dressings.peek(patient).empty(), "and nothing is on");
		} finally {
			config.enableMedicine = medicine;
			ServerConfig.save();
			gone(patient);
		}
		helper.succeed();
	}
}
