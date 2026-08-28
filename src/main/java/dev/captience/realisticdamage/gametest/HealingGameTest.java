package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.BodyState;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.HealingSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Milestone 6: food-powered recovery, sleep, golden apples, totem (spec §37-41, §55). */
public class HealingGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		return player;
	}

	@GameTest
	public void vanillaHealRepairsBodyAndBlood(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.LEFT_LEG).damage(8.0);
		data.setBlood(60.0);

		HealingSystem.onVanillaHeal(player, 1.0F);
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).condition() > 8.0, "regen must repair body condition");
		helper.assertTrue(data.blood() > 60.0, "regen must restore blood");
		helper.succeed();
	}

	@GameTest
	public void healingPrioritizesTheWorstInjury(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.LEFT_LEG).damage(14.0); // nearly disabled
		data.part(BodyPart.RIGHT_ARM).damage(1.0); // scratched
		double legBefore = data.part(BodyPart.LEFT_LEG).condition();
		double armBefore = data.part(BodyPart.RIGHT_ARM).condition();

		HealingSystem.onVanillaHeal(player, 1.0F);
		double legGain = data.part(BodyPart.LEFT_LEG).condition() - legBefore;
		double armGain = data.part(BodyPart.RIGHT_ARM).condition() - armBefore;
		helper.assertTrue(legGain > armGain, "the worse injury must get the larger share");
		helper.succeed();
	}

	@GameTest
	public void brokenLimbHealsThroughFoodAndAnnouncesMending(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.RIGHT_LEG).setCondition(2.0);
		data.part(BodyPart.RIGHT_LEG).setBroken(true);

		// Sustained regeneration eventually mends it — no bandages required.
		for (int i = 0; i < 40; i++) {
			HealingSystem.onVanillaHeal(player, 1.0F);
		}
		helper.assertTrue(!data.part(BodyPart.RIGHT_LEG).broken(),
				"food-powered recovery must clear Broken, condition = "
						+ data.part(BodyPart.RIGHT_LEG).condition());
		helper.succeed();
	}

	@GameTest
	public void goldenAppleIsStrongMedicine(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setBlood(30.0);
		data.addWound(BodyPart.TORSO, WoundSeverity.MODERATE);
		data.part(BodyPart.HEAD).damage(10.0);
		data.drainConsciousness(50.0);

		ItemStack apple = new ItemStack(Items.GOLDEN_APPLE);
		HealingSystem.onEat(player, apple, apple.get(net.minecraft.core.component.DataComponents.FOOD));

		helper.assertTrue(data.blood() >= 30.0 + Balance.GAPPLE_BLOOD_RESTORE - 0.01, "gapple restores a big chunk of blood");
		helper.assertTrue(data.consciousness() > 50.0, "gapple restores consciousness");
		helper.assertTrue(data.part(BodyPart.HEAD).condition() > 10.0, "gapple repairs body condition");
		helper.succeed();
	}

	@GameTest
	public void enchantedGoldenAppleIsNearlyMiraculous(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setBlood(10.0);
		data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
		data.addWound(BodyPart.LEFT_ARM, WoundSeverity.SEVERE);
		data.setConcussion(true);
		data.setBurned(true);
		data.part(BodyPart.LEFT_ARM).setCondition(1.0);

		ItemStack apple = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
		HealingSystem.onEat(player, apple, apple.get(net.minecraft.core.component.DataComponents.FOOD));

		helper.assertTrue(data.blood() >= Balance.BLOOD_MAX - 0.01, "notch apple fills blood");
		helper.assertTrue(!data.bleeding(), "notch apple clears bleeding");
		helper.assertTrue(!data.concussion() && !data.burned(), "notch apple clears concussion and burns");
		helper.assertTrue(data.part(BodyPart.LEFT_ARM).condition() > 9.0, "notch apple heavily repairs the body");
		helper.succeed();
	}

	@GameTest
	public void sleepIsExcellentButNotAFullHeal(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setBlood(20.0);
		data.drainConsciousness(70.0);
		data.part(BodyPart.TORSO).setCondition(4.0);
		data.part(BodyPart.LEFT_LEG).setCondition(2.0);

		HealingSystem.applySleepRecovery(player, data);

		helper.assertTrue(data.blood() >= 80.0 - 0.01, "sleep restores a large chunk of blood");
		helper.assertTrue(data.consciousness() >= 99.0, "sleep restores consciousness");
		helper.assertTrue(data.part(BodyPart.TORSO).condition() > 4.0, "sleep repairs the body");
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).condition() < 16.0,
				"a player who slept on the verge of death is not fully healed");
		helper.succeed();
	}

	@GameTest
	public void totemStabilizesWithoutErasingInjuries(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.RIGHT_LEG).setCondition(0.0);
		data.part(BodyPart.RIGHT_LEG).setBroken(true);
		data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
		data.setBlood(3.0);
		data.drainConsciousness(200.0);
		data.setUnconscious(true);
		data.setConcussion(true);

		data.totemStabilize();

		helper.assertTrue(!data.bleeding(), "totem stops bleeding");
		helper.assertTrue(data.blood() >= Balance.TOTEM_BLOOD_FLOOR - 0.01, "totem floors blood");
		helper.assertTrue(!data.unconscious(), "totem wakes the player");
		helper.assertTrue(data.part(BodyPart.RIGHT_LEG).state() == BodyState.CRITICAL,
				"disabled parts are floored to low CRITICAL, not healed");
		helper.assertTrue(data.part(BodyPart.RIGHT_LEG).broken() && data.concussion(),
				"Broken and Concussion survive the totem (spec §41)");
		helper.succeed();
	}

	@GameTest
	public void burnedSlowsHealing(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.TORSO).damage(10.0);
		double before = data.part(BodyPart.TORSO).condition();
		HealingSystem.onVanillaHeal(player, 1.0F);
		double normalGain = data.part(BodyPart.TORSO).condition() - before;

		data.reset();
		data.setBurned(true);
		data.part(BodyPart.TORSO).damage(10.0);
		before = data.part(BodyPart.TORSO).condition();
		HealingSystem.onVanillaHeal(player, 1.0F);
		double burnedGain = data.part(BodyPart.TORSO).condition() - before;

		helper.assertTrue(burnedGain < normalGain, "Burned must reduce healing efficiency (spec §27)");
		helper.succeed();
	}
}
