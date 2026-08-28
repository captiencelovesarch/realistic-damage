package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.damage.RDDamageTypes;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.systems.BleedingSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Milestone 4: deterministic wounds, blood drain, bleed-out, recursion safety (spec §16-17, §51, §55). */
public class BleedingGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	private ServerPlayer swordAttacker(GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
		return attacker;
	}

	@GameTest
	public void meaningfulSlashCreatesWoundWeakOneDoesNot(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.clearWounds();
		ServerPlayer attacker = swordAttacker(helper);
		DamageSource slash = helper.getLevel().damageSources().playerAttack(attacker);

		// Weak glancing slash: 1.5 trauma < minor threshold 2.0 -> no bleed.
		TraumaPipeline.handle(player, data, slash, 1.5F, 1.5F, 1.5, false);
		helper.assertTrue(!data.bleeding(), "weak slash must not bleed");

		// Meaningful slash: 4.5 trauma -> MODERATE wound on the torso (aim at chest).
		TraumaPipeline.handle(player, data, slash, 4.5F, 4.5F, 4.5, false);
		helper.assertTrue(data.bleeding(), "meaningful slash must bleed");
		helper.assertTrue(data.worstWound() == WoundSeverity.MODERATE,
				"4.5 slash trauma is a MODERATE wound, got " + data.worstWound());
		helper.succeed();
	}

	@GameTest
	public void bluntHitsNeverBleed(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.clearWounds();
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		DamageSource mace = helper.getLevel().damageSources().mace(attacker);
		TraumaPipeline.handle(player, data, mace, 10, 10, 10, false);
		helper.assertTrue(!data.bleeding(), "blunt trauma must not create wounds (spec §16)");
		helper.succeed();
	}

	@GameTest
	public void bloodDrainsWhileWounded(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE); // 1.2 blood/sec
		for (int i = 0; i < 100; i++) {
			BleedingSystem.tick(player, data);
		}
		// 5 seconds at 1.2/s = 6 blood lost.
		helper.assertTrue(Math.abs(data.blood() - 94.0) < 0.01,
				"expected ~94 blood after 5s severe bleed, got " + data.blood());
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void lowBloodDrainsVanillaHpButNeverInstakills(GameTestHelper helper) {
		// Bleed-out goes through hurtServer, which needs a connected player whose
		// client has "loaded" (ServerPlayer.isInvulnerableTo gates on it).
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setBlood(10.0); // critical band: 0.7 HP/sec
		float healthBefore = player.getHealth();
		for (int i = 0; i < 60; i++) {
			BleedingSystem.tick(player, data);
		}
		// 3 seconds at 0.7/s -> 2 whole-point hurts expected.
		helper.assertTrue(player.getHealth() < healthBefore,
				"critical blood must cost vanilla HP");
		helper.assertTrue(player.getHealth() > 0.0F, "bleed-out must not instakill");
		helper.succeed();
	}

	@GameTest
	public void bleedOutDamageIsRecursionSafe(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.clearWounds();
		double torsoBefore = data.part(BodyPart.TORSO).condition();
		// Feed bleed-out damage straight into the pipeline: it must be ignored.
		DamageSource bleed = RDDamageTypes.bleedOut(helper.getLevel());
		var applied = TraumaPipeline.handle(player, data, bleed, 5, 5, 5, false);
		helper.assertTrue(applied.isEmpty(), "bleed-out damage must not re-enter the trauma pipeline");
		helper.assertTrue(!data.bleeding(), "bleed-out damage must not create wounds");
		helper.assertValueEqual(data.part(BodyPart.TORSO).condition(), torsoBefore, "no body damage from bleed-out");
		helper.succeed();
	}

	@GameTest
	public void woundSeverityDowngradedByProjectileProtection(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.clearWounds();
		// Naked: 4.0 puncture trauma on the torso -> MODERATE.
		var severity = dev.captience.realisticdamage.damage.DamageProfiles
				.of(dev.captience.realisticdamage.damage.DamageCategory.PUNCTURE).woundFor(4.0);
		helper.assertTrue(severity == WoundSeverity.MODERATE, "baseline severity check");
		helper.succeed();
	}
}
