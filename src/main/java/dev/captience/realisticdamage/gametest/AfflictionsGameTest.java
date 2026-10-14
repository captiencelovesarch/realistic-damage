package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.RealisticDamage;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import dev.captience.realisticdamage.systems.AdrenalineSystem;
import dev.captience.realisticdamage.systems.ConcussionMath;
import dev.captience.realisticdamage.systems.ConcussionSystem;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import dev.captience.realisticdamage.systems.HealingSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The 1.7 afflictions, server side: drowning and suffocation take the mind,
 * a Disabled arm loses its grip, the concussion is graded and played around,
 * and the last two hearts surge and crash.
 */
public class AfflictionsGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	// --- Hypoxia ---

	/**
	 * Out of air from full consciousness: down within about seven seconds, well
	 * before vanilla drowning (2 HP a second) could have killed a full-health
	 * player — and not in the first five, so the warning has room to happen.
	 */
	@GameTest
	public void noAirBlacksYouOutBeforeDrowningCould(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		int ticks = 0;
		while (!data.unconscious() && ticks < 400) {
			player.setAirSupply(0);
			ConsciousnessSystem.tick(player, data);
			ticks++;
		}
		helper.assertTrue(data.unconscious(), "a minute without air and still awake");
		helper.assertTrue(ticks > 100 && ticks < 180, "collapsed after " + ticks + " ticks without air");
		helper.assertTrue(data.hypoxia() > 4.0, "the oxygen debt is recorded, got " + data.hypoxia());

		// Air again: the debt is paid back, and while it is the mind comes back slowly.
		player.setAirSupply(player.getMaxAirSupply());
		double debt = data.hypoxia();
		data.setUnconscious(false);
		data.setConsciousness(30.0);
		ConsciousnessSystem.tick(player, data);
		double gained = data.consciousness() - 30.0;
		helper.assertTrue(data.hypoxia() < debt, "breathing repays the debt");
		helper.assertTrue(gained < Balance.CONSCIOUSNESS_RECOVERY_PER_SEC / 20.0 * 0.25,
				"recovery is held back while the debt is large, got +" + gained);
		helper.succeed();
	}

	/** A head inside a block is as airless as a head under water. */
	@GameTest
	public void aHeadInAWallCountsAsNoAir(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		BlockPos feet = helper.absolutePos(new BlockPos(1, 1, 1));
		player.setPos(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
		helper.assertTrue(!ConsciousnessSystem.starved(player), "in open air nothing is starved");
		helper.setBlock(new BlockPos(1, 2, 1), Blocks.STONE);
		helper.assertTrue(player.isInWall(), "the eyes are inside the stone");
		helper.assertTrue(ConsciousnessSystem.starved(player), "and that is no air at all");
		helper.succeed();
	}

	// --- Grip ---

	private static BodyPart offArm(ServerPlayer player) {
		return ImpairmentSystem.armFor(player, false);
	}

	@GameTest
	public void aDisabledArmCannotRaiseItsShield(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(ImpairmentSystem.gripBlocked(player, InteractionHand.OFF_HAND) == null,
				"a sound arm holds its shield");

		data.part(offArm(player)).setCondition(0.0);
		helper.assertTrue(ImpairmentSystem.gripBlocked(player, InteractionHand.OFF_HAND) == InteractionHand.OFF_HAND,
				"a Disabled arm's hand cannot grip");
		dev.captience.realisticdamage.util.Notifier.clearLastMessageKey();
		InteractionResult result = UseItemCallback.EVENT.invoker()
				.interact(player, helper.getLevel(), InteractionHand.OFF_HAND);
		helper.assertTrue(result == InteractionResult.FAIL, "raising the shield is refused, got " + result);
		String said = dev.captience.realisticdamage.util.Notifier.lastMessageKey();
		helper.assertTrue(("realisticdamage.msg.grip." + offArm(player).id()).equals(said),
				"and the player is told why, got " + said);

		// Rule off: back to the pre-1.7 behaviour.
		boolean previous = ServerConfig.get().disabledArmsLoseGrip;
		try {
			ServerConfig.get().disabledArmsLoseGrip = false;
			helper.assertTrue(ImpairmentSystem.gripBlocked(player, InteractionHand.OFF_HAND) == null,
					"with the rule off a Disabled arm still grips");
		} finally {
			ServerConfig.get().disabledArmsLoseGrip = previous;
		}

		// And it comes back with the arm.
		data.part(offArm(player)).heal(5.0);
		helper.assertTrue(ImpairmentSystem.gripBlocked(player, InteractionHand.OFF_HAND) == null,
				"healed out of Disabled, the hand works again");
		helper.succeed();
	}

	@GameTest
	public void aRaisedShieldDropsWhenTheArmGoes(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		player.startUsingItem(InteractionHand.OFF_HAND);
		helper.assertTrue(player.isUsingItem(), "the shield is up");
		data.part(offArm(player)).setCondition(0.0);
		ImpairmentSystem.tick(player, data);
		helper.assertTrue(!player.isUsingItem(), "the hand let go of the shield");
		helper.succeed();
	}

	@GameTest
	public void aBowNeedsBothHands(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		data.part(offArm(player)).setCondition(0.0);
		helper.assertTrue(ImpairmentSystem.gripBlocked(player, InteractionHand.MAIN_HAND) == InteractionHand.OFF_HAND,
				"the bow hand is fine, the string hand is not");
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(ImpairmentSystem.gripBlocked(player, InteractionHand.MAIN_HAND) == null,
				"a sword is one-handed");
		helper.succeed();
	}

	@GameTest
	public void aLimpMainArmFightsLikeAMissingOne(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(ImpairmentSystem.armFor(player, true)).setCondition(0.0);
		ImpairmentSystem.tick(player, data);
		AttributeModifier damage = player.getAttribute(Attributes.ATTACK_DAMAGE)
				.getModifier(RealisticDamage.id("arm_damage"));
		helper.assertTrue(damage != null, "a limp main arm costs damage");
		helper.assertTrue(Math.abs(damage.amount() + Balance.ONE_HANDED_PENALTY * 0.5) < 1e-9,
				"at the one-handed rate, got " + damage.amount());
		helper.succeed();
	}

	// --- Concussion ---

	private DamageSource headMace(GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setXRot(0.0F);
		return helper.getLevel().damageSources().mace(attacker);
	}

	/** A tap that would not concuss a clear head puts a severely concussed one on the floor. */
	@GameTest
	public void secondImpactOnASevereConcussionKnocksYouOut(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		TraumaPipeline.handle(player, data, headMace(helper), 2, 2, 2, false);
		helper.assertTrue(!data.concussion() && !data.unconscious(), "a clear head shrugs off the tap");

		ServerPlayer victim = mockPlayer(helper);
		RealisticDamageData concussed = RDAttachments.get(victim);
		concussed.reset();
		concussed.setConcussionSeverity(85.0);
		TraumaPipeline.handle(victim, concussed, headMace(helper), 2, 2, 2, false);
		helper.assertTrue(concussed.unconscious(), "the same tap on a severe concussion is lights out");
		helper.assertTrue(concussed.concussionSeverity() > 85.0, "and the concussion deepened");
		helper.succeed();
	}

	@GameTest
	public void aMildConcussionDeepensRatherThanKnocksOut(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setConcussionSeverity(20.0);
		TraumaPipeline.handle(player, data, headMace(helper), 2, 2, 2, false);
		helper.assertTrue(!data.unconscious(), "a mild concussion survives a tap");
		helper.assertTrue(data.concussionGrade() > ConcussionMath.MILD,
				"but the second impact pushes it up a grade, got " + data.concussionGrade());
		helper.assertTrue(data.concussionFlare() >= Balance.SECOND_IMPACT_FLARE - 1e-9, "and flares it");
		helper.succeed();
	}

	@GameTest
	public void atTheTopOfTheFlareYouVomit(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setConcussionSeverity(50.0);
		data.setConcussionFlare(Balance.FLARE_RETCH + 5.0);
		player.getFoodData().setFoodLevel(18);
		player.getFoodData().setSaturation(5.0F);
		ConcussionSystem.tick(player, data);
		helper.assertTrue(data.retching(), "doubled over");
		helper.assertTrue(player.getFoodData().getFoodLevel() == 18 - Balance.RETCH_FOOD_LOSS,
				"food lost, got " + player.getFoodData().getFoodLevel());
		helper.assertTrue(player.getFoodData().getSaturationLevel() == 0.0F, "saturation gone");
		helper.assertTrue(data.concussionFlare() <= Balance.FLARE_AFTER_RETCH + 1.0, "and relief after");
		helper.assertTrue(ImpairmentSystem.sprintBlocked(data), "no sprinting while retching");
		helper.succeed();
	}

	@GameTest
	public void vertigoSpellsComeOnTheirClock(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setConcussionSeverity(85.0);
		int ticks = 0;
		while (!data.vertigo() && ticks < 2000) {
			ConcussionSystem.tick(player, data);
			ticks++;
		}
		helper.assertTrue(data.vertigo(), "a severe concussion spins within its period");
		helper.assertTrue(ticks <= Balance.VERTIGO_PERIOD_SEVERE + 1, "spun after " + ticks + " ticks");
		helper.assertTrue(ImpairmentSystem.sprintBlocked(data), "no sprinting while the room spins");
		helper.succeed();
	}

	/** Standing still heals; the concussion is gone in minutes, not a lifetime. */
	@GameTest
	public void restHealsAConcussion(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setConcussionSeverity(10.0);
		for (int i = 0; i < 20 * 60 && data.concussion(); i++) {
			ConcussionSystem.tick(player, data);
		}
		helper.assertTrue(!data.concussion(), "a mild knock is gone after a minute of rest");
		helper.succeed();
	}

	@GameTest
	public void sleepClearsAConcussion(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setConcussionSeverity(90.0);
		HealingSystem.applySleepRecovery(player, data);
		helper.assertTrue(!data.concussion(), "a night's sleep is the rest a concussion wants");
		helper.succeed();
	}

	// --- Adrenaline ---

	@GameTest
	public void twoHeartsSurgesWithSpeed(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.adrenaline().reset();
		player.setHealth(player.getMaxHealth() * 0.15F);
		AdrenalineSystem.tick(player, data);
		helper.assertTrue(data.adrenaline().phase == AdrenalineMath.Phase.SURGE, "the surge fires");
		ImpairmentSystem.tick(player, data);
		AttributeModifier speed = player.getAttribute(Attributes.MOVEMENT_SPEED)
				.getModifier(RealisticDamage.id("adrenaline_speed"));
		helper.assertTrue(speed != null && Math.abs(speed.amount() - Balance.ADRENALINE_SPEED_BONUS) < 1e-9,
				"the surge is quicker on its feet");

		boolean previous = ServerConfig.get().enableAdrenaline;
		try {
			ServerConfig.get().enableAdrenaline = false;
			AdrenalineSystem.tick(player, data);
			ImpairmentSystem.tick(player, data);
			helper.assertTrue(data.adrenaline().phase == AdrenalineMath.Phase.NONE, "rule off: no adrenaline");
			helper.assertTrue(player.getAttribute(Attributes.MOVEMENT_SPEED)
					.getModifier(RealisticDamage.id("adrenaline_speed")) == null, "and no bonus left behind");
		} finally {
			ServerConfig.get().enableAdrenaline = previous;
		}
		helper.succeed();
	}
}
