package dev.captience.realisticdamage.fieldkit.gametest;

import java.util.List;
import java.util.Optional;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyPartData;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.fieldkit.brew.Sedation;
import dev.captience.realisticdamage.fieldkit.brew.SedationMath.Stage;
import dev.captience.realisticdamage.fieldkit.medicine.Medicine;
import dev.captience.realisticdamage.fieldkit.medicine.Provisions;
import dev.captience.realisticdamage.gametest.TestPlayer;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import dev.captience.realisticdamage.systems.HealingSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;

/**
 * Phase 4, round 3: food and drink, on real players (2.0). Coffee, spirits,
 * the blood sausage and the iron tonic. (What spirits do to the view is drawn on
 * the client: the client game test drinks them.)
 *
 * <p>The batch's environment switches Medicine on before and off after.
 */
public class MedicineFoodGameTest {
	private static TestPlayer player(GameTestHelper helper, String name) {
		TestPlayer player = TestPlayer.spawn(helper.getLevel(), helper.absoluteVec(new Vec3(1.5, 2.0, 1.5)), 0.0F, name);
		RDAttachments.get(player).reset();
		RDAttachments.get(player).clearWounds();
		return player;
	}

	private static void gone(ServerPlayer... players) {
		for (ServerPlayer player : players) {
			player.connection.disconnect(net.minecraft.network.chat.Component.literal("done"));
		}
	}

	private static void drink(ServerPlayer player, Holder<MobEffect> effect) {
		player.addEffect(new MobEffectInstance(effect, 600));
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void coffeeTakesAnArrowAStageLessFar(GameTestHelper helper) {
		TestPlayer patient = player(helper, "fk-coffee-sedation");
		try {
			// One arrow's dose, taken hold.
			patient.getAttachedOrCreate(Sedation.STATE).level = 1.0;
			helper.assertTrue(Sedation.stage(patient) == Stage.OUT, "an arrow drops a person: " + Sedation.stage(patient));
			drink(patient, Provisions.CAFFEINATED);
			helper.assertTrue(Sedation.stage(patient) == Stage.STUMBLING,
					"on coffee it leaves them stumbling: " + Sedation.stage(patient));
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	/** Microsleeps under the bar, held there, with and without a coffee. */
	private static int blackouts(ServerPlayer player, RealisticDamageData data, int ticks) {
		int blackouts = 0;
		boolean wasOut = false;
		for (int i = 0; i < ticks; i++) {
			data.setConsciousness(Balance.SYMPTOM_MICROSLEEP - 4.0);
			ConsciousnessSystem.tick(player, data);
			boolean out = data.microsleeping();
			if (out && !wasOut) {
				blackouts++;
			}
			wasOut = out;
		}
		return blackouts;
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void coffeeKeepsTheMicrosleepsAway(GameTestHelper helper) {
		TestPlayer tired = player(helper, "fk-coffee-tired");
		TestPlayer awake = player(helper, "fk-coffee-awake");
		try {
			drink(awake, Provisions.CAFFEINATED);
			int bare = blackouts(tired, RDAttachments.get(tired), 600);
			int coffee = blackouts(awake, RDAttachments.get(awake), 600);
			helper.assertTrue(bare > 0, "a fading mind blinks out: " + bare);
			helper.assertTrue(coffee == 0, "on coffee it doesn't: " + coffee);
		} finally {
			gone(tired, awake);
		}
		helper.succeed();
	}

	/** Blood lost to one open wound over a second. */
	private static double bled(ServerPlayer player) {
		RealisticDamageData data = RDAttachments.get(player);
		data.clearWounds();
		data.setBlood(Balance.BLOOD_MAX);
		data.addWound(BodyPart.LEFT_ARM, WoundSeverity.MODERATE);
		for (int i = 0; i < 20; i++) {
			BleedingSystem.tick(player, data);
		}
		return Balance.BLOOD_MAX - data.blood();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void coffeeAndSpiritsBleedMore(GameTestHelper helper) {
		TestPlayer patient = player(helper, "fk-drink-bleed");
		try {
			double bare = bled(patient);
			drink(patient, Provisions.CAFFEINATED);
			double coffee = bled(patient);
			drink(patient, Provisions.TIPSY);
			double both = bled(patient);
			helper.assertTrue(bare > 0.1, "an open wound bleeds: " + bare);
			helper.assertTrue(Math.abs(coffee / bare - Balance.COFFEE_BLEED_FACTOR) < 0.01,
					"a heart on coffee bleeds more: " + bare + " -> " + coffee);
			helper.assertTrue(Math.abs(both / bare - Balance.COFFEE_BLEED_FACTOR * Balance.SPIRITS_BLEED_FACTOR) < 0.01,
					"and spirits on top, more again: " + both);
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void spiritsTakeHalfTheLimp(GameTestHelper helper) {
		TestPlayer patient = player(helper, "fk-spirits-limp");
		try {
			RealisticDamageData data = RDAttachments.get(patient);
			BodyPartData leg = data.part(BodyPart.LEFT_LEG);
			leg.setCondition(BodyPart.LEFT_LEG.maxCondition() * 0.3);
			leg.setBroken(true);
			ImpairmentSystem.tick(patient, data);
			double bare = limp(patient);
			drink(patient, Provisions.TIPSY);
			ImpairmentSystem.tick(patient, data);
			double tipsy = limp(patient);
			helper.assertTrue(bare > 0.1, "a broken leg limps: " + bare);
			helper.assertTrue(Math.abs(tipsy - bare * Balance.SPIRITS_PAIN_FACTOR) < 1e-6,
					"spirits take half of it: " + bare + " -> " + tipsy);
			helper.assertTrue(leg.broken(), "and the leg is still broken");
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	private static double limp(ServerPlayer player) {
		AttributeModifier modifier = player.getAttribute(Attributes.MOVEMENT_SPEED)
				.getModifier(RealisticDamage.id("leg_speed"));
		return modifier == null ? 0.0 : -modifier.amount();
	}

	/** Blood a food gives back, eaten from 40. */
	private static double ate(ServerPlayer player, ItemStack food) {
		RealisticDamageData data = RDAttachments.get(player);
		data.setBlood(40.0);
		player.getFoodData().setFoodLevel(4);
		food.finishUsingItem(player.level(), player);
		return data.blood() - 40.0;
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void aBloodSausageGivesBackMoreBlood(GameTestHelper helper) {
		TestPlayer eater = player(helper, "fk-sausage");
		try {
			double pork = ate(eater, new ItemStack(Items.COOKED_PORKCHOP));
			double sausage = ate(eater, new ItemStack(Provisions.BLOOD_SAUSAGE));
			double extra = Balance.BLOOD_SAUSAGE_BLOOD * ServerConfig.get().healingMultiplier;
			helper.assertTrue(pork > 0.0, "food gives Blood back: " + pork);
			helper.assertTrue(Math.abs(sausage - pork - extra) < 1e-6,
					"a blood sausage, the same food, gives back more: " + pork + " vs " + sausage);
		} finally {
			gone(eater);
		}
		helper.succeed();
	}

	/** Blood made back over ten seconds, not bleeding, not well fed. */
	private static double madeBack(ServerPlayer player) {
		RealisticDamageData data = RDAttachments.get(player);
		data.setBlood(50.0);
		player.getFoodData().setFoodLevel(10);
		for (int i = 0; i < 200; i++) {
			HealingSystem.tick(player, data);
		}
		return data.blood() - 50.0;
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void anIronTonicDoublesTheBloodComingBack(GameTestHelper helper) {
		TestPlayer patient = player(helper, "fk-tonic");
		try {
			double bare = madeBack(patient);
			drink(patient, Provisions.IRON_TONIC);
			double tonic = madeBack(patient);
			helper.assertTrue(bare > 0.0, "Blood comes back on its own: " + bare);
			helper.assertTrue(Math.abs(tonic - bare * Balance.IRON_TONIC_REGEN_FACTOR) < 1e-6,
					"twice as fast on the tonic: " + bare + " -> " + tonic);
		} finally {
			gone(patient);
		}
		helper.succeed();
	}

	private static Optional<RecipeHolder<CraftingRecipe>> crafted(GameTestHelper helper, ItemStack... grid) {
		return helper.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING,
				CraftingInput.of(grid.length, 1, List.of(grid)), helper.getLevel());
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void bloodGoneOffMakesNoSausage(GameTestHelper helper) {
		ItemStack fresh = new ItemStack(Medicine.BLOOD_BAG);
		fresh.set(Medicine.DRAWN_AT, helper.getLevel().getGameTime());
		ItemStack off = new ItemStack(Medicine.BLOOD_BAG);
		off.set(Medicine.DRAWN_AT, helper.getLevel().getGameTime() - Balance.BLOOD_BAG_KEEPS_TICKS - 1L);
		ItemStack pork = new ItemStack(Items.PORKCHOP);
		Optional<RecipeHolder<CraftingRecipe>> good = crafted(helper, pork, fresh);
		helper.assertTrue(good.isPresent(), "a porkchop and a fresh bag make a sausage");
		CraftingInput input = CraftingInput.of(2, 1, List.of(pork, fresh));
		helper.assertTrue(good.get().value().assemble(input).is(Provisions.RAW_BLOOD_SAUSAGE),
				"a raw one, to cook");
		helper.assertTrue(good.get().value().getRemainingItems(input).stream().anyMatch(s -> s.is(Items.GLASS_BOTTLE)),
				"and the bag's bottle comes back");
		helper.assertTrue(crafted(helper, pork, off).isEmpty(), "blood that has gone off makes nothing");
		helper.succeed();
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void coffeeWantsWaterNotAnyPotion(GameTestHelper helper) {
		ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
		ItemStack awkward = PotionContents.createItemStack(Items.POTION, Potions.AWKWARD);
		Optional<RecipeHolder<CraftingRecipe>> coffee = crafted(helper,
				new ItemStack(Items.COCOA_BEANS), water, new ItemStack(Items.SUGAR));
		helper.assertTrue(coffee.isPresent() && coffee.get().value().assemble(CraftingInput.of(3, 1,
						List.of(new ItemStack(Items.COCOA_BEANS), water, new ItemStack(Items.SUGAR))))
				.is(Provisions.COFFEE), "cocoa, water and sugar make coffee");
		helper.assertTrue(crafted(helper, new ItemStack(Items.COCOA_BEANS), awkward, new ItemStack(Items.SUGAR)).isEmpty(),
				"a potion that isn't water doesn't");
		helper.succeed();
	}

	private static boolean brews(GameTestHelper helper, Holder<Potion> from, ItemStack ingredient, Holder<Potion> to) {
		ItemStack out = helper.getLevel().potionBrewing().mix(ingredient,
				PotionContents.createItemStack(Items.POTION, from));
		PotionContents contents = out.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
		return contents != null && contents.is(to);
	}

	@GameTest(environment = "realisticdamage:fieldkit_medicine_on")
	public void spiritsAndTheTonicBrew(GameTestHelper helper) {
		helper.assertTrue(brews(helper, Potions.WATER, new ItemStack(Items.POTATO), Provisions.SPIRITS),
				"a potato in water brews spirits");
		helper.assertTrue(brews(helper, Potions.AWKWARD, new ItemStack(Items.IRON_NUGGET), Provisions.IRON_TONIC_POTION),
				"an iron nugget in an awkward potion brews the tonic");
		helper.assertTrue(brews(helper, Provisions.IRON_TONIC_POTION, new ItemStack(Items.REDSTONE),
				Provisions.LONG_IRON_TONIC), "and redstone makes it last");
		helper.succeed();
	}
}
