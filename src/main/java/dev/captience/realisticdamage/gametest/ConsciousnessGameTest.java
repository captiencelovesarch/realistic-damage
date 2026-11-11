package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.TraumaPipeline;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.GameType;

/** Milestone 5: consciousness hits, collapse/wake hysteresis, concussion, prone (spec §19-26, §55). */
public class ConsciousnessGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	/** A heavy blunt attacker aimed at the target's head. */
	private DamageSource headMace(GameTestHelper helper) {
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setXRot(0.0F); // level aim at eye height = head band
		return helper.getLevel().damageSources().mace(attacker);
	}

	@GameTest
	public void hugeHeadHitKnocksOutDespiteFullHearts(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		// Spec §19: many hearts remaining, huge golem/mace hit to the head -> out cold.
		// 11 applied * 1.4 (heavy blunt) = 15.4 head trauma -> consciousness hit
		// 15.4 * 1.2 * 4.0 = 74; concussion triggers (>= 6 head trauma). Since 2.0
		// the step above this (16) snaps the neck and the one above that (20)
		// crushes the skull, so "out cold" is the band under both.
		TraumaPipeline.handle(player, data, headMace(helper), 11, 11, 11, false);
		helper.assertTrue(data.concussion(), "massive head hit must concuss");
		helper.assertTrue(data.consciousness() < Balance.WAKE_THRESHOLD,
				"massive head hit must crush consciousness, got " + data.consciousness());
		helper.succeed();
	}

	@GameTest
	public void lightHitBarelyTouchesConsciousness(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		ServerPlayer attacker = mockPlayer(helper);
		attacker.setPos(0.0, 0.0, -2.0);
		attacker.setXRot((float) Math.toDegrees(Math.atan2(1.62 - 1.0, 1.7))); // torso aim
		DamageSource punch = helper.getLevel().damageSources().playerAttack(attacker);
		TraumaPipeline.handle(player, data, punch, 1, 1, 1, false);
		helper.assertTrue(data.consciousness() > 95.0,
				"a light torso punch must not meaningfully dent consciousness");
		helper.assertTrue(!data.concussion(), "no concussion from light hits");
		helper.succeed();
	}

	@GameTest
	public void collapseAndWakeHysteresis(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.drainConsciousness(200.0);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "consciousness 0 must collapse");

		// Recovery runs while out cold, but waking needs BOTH the minimum downtime
		// AND the 20-point threshold (spec §25).
		data.setUnconsciousTicks(Balance.MIN_UNCONSCIOUS_TICKS + 1);
		data.restoreConsciousness(Balance.WAKE_THRESHOLD - 5.0);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "below the wake threshold the player stays down");

		data.restoreConsciousness(10.0);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(!data.unconscious(), "wake at >= 20 after the minimum downtime");
		helper.succeed();
	}

	@GameTest
	public void minimumUnconsciousTimePreventsFlicker(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.drainConsciousness(200.0);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "collapsed");
		data.restoreConsciousness(100.0); // instantly fine on paper...
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "...but the minimum unconscious time still holds");
		helper.succeed();
	}

	@GameTest
	public void seriousBloodLossDrainsConsciousness(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setBlood(25.0); // serious band
		double before = data.consciousness();
		for (int i = 0; i < 100; i++) {
			ConsciousnessSystem.tick(player, data);
		}
		helper.assertTrue(data.consciousness() < before,
				"serious blood loss must continuously drain consciousness");

		// Stable blood: recovery instead.
		data.setBlood(90.0);
		double low = data.consciousness();
		for (int i = 0; i < 100; i++) {
			ConsciousnessSystem.tick(player, data);
		}
		helper.assertTrue(data.consciousness() > low, "stable player must recover consciousness");
		helper.succeed();
	}

	@GameTest
	public void concussionSlowsRecoveryAndCapsConsciousness(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.drainConsciousness(50.0);
		for (int i = 0; i < 40; i++) {
			ConsciousnessSystem.tick(player, data);
		}
		double healthyRecovered = data.consciousness();

		data.reset();
		data.setConcussion(true);
		data.drainConsciousness(50.0);
		for (int i = 0; i < 40; i++) {
			ConsciousnessSystem.tick(player, data);
		}
		helper.assertTrue(data.consciousness() < healthyRecovered,
				"concussion must slow consciousness recovery");
		data.setConsciousness(100.0);
		helper.assertTrue(data.consciousness() < 100.0, "concussion caps max consciousness");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void unconsciousPlayerGoesProneAndImmobile(GameTestHelper helper) {
		// Full Player.tick needs a connected player whose client has "loaded".
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.drainConsciousness(200.0);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "collapsed");
		// Pose recomputes in Player.tick, reached via ServerPlayer.doTick.
		player.doTick();
		helper.assertTrue(player.getPose() == Pose.SWIMMING,
				"unconscious player must lie prone, got " + player.getPose());

		dev.captience.realisticdamage.systems.ImpairmentSystem.tick(player, data);
		helper.assertTrue(player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) < 0.001,
				"unconscious movement speed must be ~0");
		helper.succeed();
	}
}
