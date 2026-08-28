package dev.captience.realisticdamage.gametest;

import java.util.List;
import java.util.Map;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Milestone 2: hit location and routing determinism (spec §6-7, §55). */
public class DamageRoutingGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F; // facing south (+Z); player's right hand points west (-X)
		return player;
	}

	@GameTest
	public void verticalBandsResolve(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		helper.assertTrue(BodyHitResolver.classifyPoint(player, new Vec3(0.0, 1.6, 0.0)) == BodyPart.HEAD,
				"1.6y must be head");
		helper.assertTrue(BodyHitResolver.classifyPoint(player, new Vec3(0.0, 1.0, 0.0)) == BodyPart.TORSO,
				"1.0y center must be torso");
		helper.assertTrue(BodyHitResolver.classifyPoint(player, new Vec3(0.05, 0.3, 0.0)) == BodyPart.LEFT_LEG,
				"low east point must be LEFT leg (facing south, east is the player's left)");
		helper.assertTrue(BodyHitResolver.classifyPoint(player, new Vec3(-0.05, 0.3, 0.0)) == BodyPart.RIGHT_LEG,
				"low west point must be RIGHT leg");
		helper.succeed();
	}

	@GameTest
	public void leftRightArmOrientation(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		// Torso band, far lateral: west (-X) is the player's right side when facing south.
		helper.assertTrue(BodyHitResolver.classifyPoint(player, new Vec3(-0.25, 1.0, 0.0)) == BodyPart.RIGHT_ARM,
				"west point must be RIGHT arm");
		helper.assertTrue(BodyHitResolver.classifyPoint(player, new Vec3(0.25, 1.0, 0.0)) == BodyPart.LEFT_ARM,
				"east point must be LEFT arm");
		// Rotate 180° (facing north): sides must swap.
		player.yBodyRot = 180.0F;
		helper.assertTrue(BodyHitResolver.classifyPoint(player, new Vec3(-0.25, 1.0, 0.0)) == BodyPart.LEFT_ARM,
				"after 180° turn, west must be LEFT arm");
		helper.succeed();
	}

	@GameTest
	public void meleeRayResolvesAimedRegion(GameTestHelper helper) {
		ServerPlayer target = mockPlayer(helper);
		ServerPlayer attacker = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		attacker.setPos(0.0, 0.0, -2.5); // 2.5 blocks north, eye at y=1.62
		DamageSource melee = helper.getLevel().damageSources().playerAttack(attacker);

		// Aim level: ray at eye height crosses the head band (>=1.404).
		attacker.setXRot(0.0F);
		attacker.setYHeadRot(0.0F);
		helper.assertTrue(BodyHitResolver.resolve(target, melee) == BodyPart.HEAD,
				"level aim at eye height must hit head");

		// Aim down at the shins.
		float pitchDown = (float) Math.toDegrees(Math.atan2(1.62 - 0.3, 2.2));
		attacker.setXRot(pitchDown);
		BodyPart low = BodyHitResolver.resolve(target, melee);
		helper.assertTrue(low == BodyPart.LEFT_LEG || low == BodyPart.RIGHT_LEG,
				"downward aim must hit a leg, got " + low);
		helper.succeed();
	}

	@GameTest
	public void fallDamageRoutesToLegs(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		DamageSource fall = helper.getLevel().damageSources().fall();

		// 8 post-mitigation damage: below both secondary thresholds.
		List<TraumaPipeline.AppliedTrauma> applied = TraumaPipeline.handle(player, data, fall, 8, 8, 8, false);
		helper.assertTrue(applied.size() == 2, "moderate fall must damage exactly both legs, got " + applied.size());
		// 8 * 1.25 (FALL) * 0.5 share * 1.0 amplifier = 5.0 per leg.
		helper.assertValueEqual(data.part(BodyPart.LEFT_LEG).condition(), 11.0, "left leg condition");
		helper.assertValueEqual(data.part(BodyPart.RIGHT_LEG).condition(), 11.0, "right leg condition");
		helper.assertValueEqual(data.part(BodyPart.HEAD).condition(), 20.0, "head untouched by moderate fall");

		// Identical event -> identical result (no RNG, spec §55).
		data.reset();
		TraumaPipeline.handle(player, data, fall, 8, 8, 8, false);
		helper.assertValueEqual(data.part(BodyPart.LEFT_LEG).condition(), 11.0, "determinism");
		helper.succeed();
	}

	@GameTest
	public void bigFallAddsSecondaryCoreTrauma(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		DamageSource fall = helper.getLevel().damageSources().fall();
		TraumaPipeline.handle(player, data, fall, 16, 16, 16, false);
		helper.assertTrue(data.part(BodyPart.TORSO).condition() < 20.0, "big fall must shock the torso");
		helper.assertTrue(data.part(BodyPart.HEAD).condition() < 20.0, "extreme fall must shock the head");
		helper.assertTrue(data.part(BodyPart.LEFT_LEG).condition() < 11.0, "legs still take the bulk");
		helper.succeed();
	}

	@GameTest
	public void injuredLegsAmplifyFallTrauma(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		DamageSource fall = helper.getLevel().damageSources().fall();
		TraumaPipeline.handle(player, data, fall, 6, 6, 6, false);
		double healthyLegLoss = 16.0 - data.part(BodyPart.LEFT_LEG).condition();

		data.reset();
		data.part(BodyPart.LEFT_LEG).setCondition(4.0); // badly injured
		double before = data.part(BodyPart.LEFT_LEG).condition();
		TraumaPipeline.handle(player, data, fall, 6, 6, 6, false);
		double injuredLegLoss = before - data.part(BodyPart.LEFT_LEG).condition();
		helper.assertTrue(injuredLegLoss > healthyLegLoss,
				"injured leg must take amplified fall trauma (spec §13)");
		helper.succeed();
	}

	@GameTest
	public void explosionPicksNearestRegionAsPrimary(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		// Blast low and south: legs are nearest.
		Map<BodyPart, Double> shares = BodyHitResolver.explosionShares(player, new Vec3(0.0, 0.2, 2.0));
		double legShare = Math.max(shares.get(BodyPart.LEFT_LEG), shares.get(BodyPart.RIGHT_LEG));
		helper.assertTrue(legShare >= 0.5, "low blast must make a leg primary, got " + shares);
		// Blast at head height: head primary.
		Map<BodyPart, Double> headShares = BodyHitResolver.explosionShares(player, new Vec3(0.0, 1.7, 2.0));
		helper.assertTrue(headShares.get(BodyPart.HEAD) >= 0.5, "high blast must make the head primary");
		helper.succeed();
	}
}
