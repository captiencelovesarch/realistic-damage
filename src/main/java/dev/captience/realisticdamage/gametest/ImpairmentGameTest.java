package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/** Milestone 3: attribute penalties and deterministic breaking (spec §11-15, §50, §55). */
public class ImpairmentGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		return player;
	}

	@GameTest
	public void legDamageSlowsMovement(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		ImpairmentSystem.tick(player, data);
		double healthySpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);

		data.part(BodyPart.LEFT_LEG).setCondition(3.0); // CRITICAL
		ImpairmentSystem.tick(player, data);
		double injuredSpeed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(injuredSpeed < healthySpeed, "critical leg must slow movement");

		data.reset();
		ImpairmentSystem.tick(player, data);
		helper.assertValueEqual(player.getAttributeValue(Attributes.MOVEMENT_SPEED), healthySpeed,
				"recovery must fully remove the modifier");
		helper.succeed();
	}

	@GameTest
	public void brokenArmSlowsAttackAndMining(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		ImpairmentSystem.tick(player, data);
		double attackSpeed = player.getAttributeValue(Attributes.ATTACK_SPEED);
		double mining = player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
		double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);

		BodyPart mainArm = ImpairmentSystem.armFor(player, true);
		data.part(mainArm).setCondition(5.0);
		data.part(mainArm).setBroken(true);
		ImpairmentSystem.tick(player, data);
		helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_SPEED) < attackSpeed, "attack speed penalty");
		helper.assertTrue(player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED) < mining, "mining penalty");
		helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) < damage, "melee damage penalty");
		// Off-arm damage must NOT slow the main hand.
		data.reset();
		data.part(ImpairmentSystem.armFor(player, false)).setCondition(2.0);
		ImpairmentSystem.tick(player, data);
		helper.assertValueEqual(player.getAttributeValue(Attributes.ATTACK_SPEED), attackSpeed,
				"off-arm injury must not affect main-hand attack speed");
		helper.succeed();
	}

	@GameTest
	public void heavyBluntImpactBreaksLimb(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		// Iron-golem-class hit: mob HEAVY_BLUNT. 8 applied * 1.4 = 11.2 trauma to one
		// region; if it lands on a leg (aim low), it exceeds BREAK_IMPACT_LEG.
		ServerPlayer golemStandIn = mockPlayer(helper);
		golemStandIn.setPos(0.0, 0.0, -2.0);
		DamageSource mace = helper.getLevel().damageSources().mace(golemStandIn);
		golemStandIn.setXRot((float) Math.toDegrees(Math.atan2(1.62 - 0.3, 1.7)));
		TraumaPipeline.handle(player, data, mace, 8, 8, 8, false);
		boolean anyLegBroken = data.part(BodyPart.LEFT_LEG).broken() || data.part(BodyPart.RIGHT_LEG).broken();
		helper.assertTrue(anyLegBroken, "massive blunt leg hit must break the leg");
		helper.succeed();
	}

	@GameTest
	public void slashNeverBreaksHealthyLimbButBreaksCriticalOne(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setXRot((float) Math.toDegrees(Math.atan2(1.62 - 0.3, 1.7)));
		attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
		DamageSource playerAttack = helper.getLevel().damageSources().playerAttack(attacker);

		// Big sword-class hit on a healthy leg: hurts a lot, must NOT break.
		TraumaPipeline.handle(player, data, playerAttack, 7, 7, 7, false);
		helper.assertTrue(!data.part(BodyPart.LEFT_LEG).broken() && !data.part(BodyPart.RIGHT_LEG).broken(),
				"slash-class damage must not break a healthy limb");

		// Drive a leg into CRITICAL, then one more meaningful hit breaks it.
		BodyPart hitLeg = data.part(BodyPart.LEFT_LEG).condition() < 16.0 ? BodyPart.LEFT_LEG : BodyPart.RIGHT_LEG;
		data.part(hitLeg).setCondition(3.0);
		TraumaPipeline.handle(player, data, playerAttack, 4, 4, 4, false);
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).broken() || data.part(BodyPart.RIGHT_LEG).broken(),
				"meaningful hit on a CRITICAL limb must set Broken (accumulation path)");
		helper.succeed();
	}

	@GameTest
	public void brokenClearsOnHealingToHealthy(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.part(BodyPart.RIGHT_LEG).setCondition(2.0);
		data.part(BodyPart.RIGHT_LEG).setBroken(true);
		data.part(BodyPart.RIGHT_LEG).heal(11.0); // 13/16 = 81% -> HEALTHY
		helper.assertTrue(!data.part(BodyPart.RIGHT_LEG).broken(), "Broken clears at HEALTHY (spec §15)");
		helper.succeed();
	}
}
