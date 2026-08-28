package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.BleedingSystem;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import dev.captience.realisticdamage.systems.RescueSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/** 1.2: getting somebody out. Drag, pressure and feeding, with no item involved. */
public class RescueGameTest {
	private ServerPlayer player(GameTestHelper helper, double offsetZ) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		var pos = helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.0, 2.0, 1.0 + offsetZ));
		player.setPos(pos.x, pos.y, pos.z);
		RDAttachments.get(player).reset();
		return player;
	}

	private void knockOut(GameTestHelper helper, ServerPlayer player) {
		RealisticDamageData data = RDAttachments.get(player);
		data.drainConsciousness(Balance.CONSCIOUSNESS_MAX);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "must be down");
	}

	/** Pressure slows the bleed sharply without ever putting blood back. */
	@GameTest
	public void pressureSlowsBleedingButNeverHeals(GameTestHelper helper) {
		ServerPlayer player = player(helper, 0.0);
		RealisticDamageData data = RDAttachments.get(player);
		data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);

		data.setBlood(100.0);
		for (int i = 0; i < 20; i++) {
			BleedingSystem.tick(player, data);
		}
		double lostFreely = 100.0 - data.blood();

		data.setBlood(100.0);
		for (int i = 0; i < 20; i++) {
			data.refreshPressure(false);
			BleedingSystem.tick(player, data);
		}
		double lostUnderPressure = 100.0 - data.blood();

		helper.assertTrue(lostUnderPressure < lostFreely,
				"pressure must slow the bleed (" + lostUnderPressure + " vs " + lostFreely + ")");
		helper.assertTrue(lostUnderPressure > 0.0,
				"pressure must not stop the bleed outright — it buys time, it is not a cure");
		helper.succeed();
	}

	/** A companion's hands are better than your own; that is what makes fetching one worth it. */
	@GameTest
	public void aCompanionPressesBetterThanYouDo(GameTestHelper helper) {
		ServerPlayer player = player(helper, 0.0);
		RealisticDamageData data = RDAttachments.get(player);
		data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);

		data.setBlood(100.0);
		for (int i = 0; i < 20; i++) {
			data.refreshPressure(true);
			BleedingSystem.tick(player, data);
		}
		double alone = 100.0 - data.blood();

		data.setBlood(100.0);
		for (int i = 0; i < 20; i++) {
			data.refreshPressure(false);
			BleedingSystem.tick(player, data);
		}
		double helped = 100.0 - data.blood();

		helper.assertTrue(helped < alone,
				"a companion must beat self-pressure (" + helped + " vs " + alone + ")");
		helper.succeed();
	}

	/**
	 * The hold has to lapse on its own. A client that simply stops asking — key
	 * released, disconnected, crashed — must never leave someone rooted forever.
	 */
	@GameTest
	public void aPressureHoldLapsesWhenTheClientStopsAsking(GameTestHelper helper) {
		ServerPlayer player = player(helper, 0.0);
		RealisticDamageData data = RDAttachments.get(player);
		data.addWound(BodyPart.TORSO, WoundSeverity.MODERATE);

		RescueSystem.applyPressure(player, null);
		helper.assertTrue(RescueSystem.rooted(data), "pressing roots you in place");

		for (int i = 0; i < Balance.PRESSURE_GRACE_TICKS + 4; i++) {
			RescueSystem.tick(player, data);
		}
		helper.assertTrue(!RescueSystem.rooted(data),
				"the hold must lapse once the client stops renewing it");
		helper.assertTrue(!data.underPressure(), "and the bleed must go back to full rate");
		helper.succeed();
	}

	/** Pressing a wound with no wound to press is not a way to stand still. */
	@GameTest
	public void pressureNeedsAWound(GameTestHelper helper) {
		ServerPlayer player = player(helper, 0.0);
		RealisticDamageData data = RDAttachments.get(player);
		RescueSystem.applyPressure(player, null);
		helper.assertTrue(!RescueSystem.rooted(data), "nothing to press means nothing happens");
		helper.succeed();
	}

	@GameTest
	public void draggingCostsSpeedAndBothHands(GameTestHelper helper) {
		ServerPlayer rescuer = player(helper, 0.0);
		ServerPlayer victim = player(helper, 1.0);
		knockOut(helper, victim);

		RealisticDamageData rescuerData = RDAttachments.get(rescuer);
		rescuerData.setDragging(victim.getUUID());
		RDAttachments.get(victim).setDraggedBy(rescuer.getUUID());

		helper.assertTrue(RescueSystem.handsFull(rescuer), "hauling a body fills your hands");
		double before = rescuer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		ImpairmentSystem.tick(rescuer, rescuerData);
		double after = rescuer.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
		helper.assertTrue(after < before, "dragging must slow you down (" + after + " vs " + before + ")");

		RescueSystem.releaseDrag(rescuer, rescuerData);
		helper.assertTrue(!RescueSystem.handsFull(rescuer), "letting go frees your hands");
		// The held player repairs their own half from the dragger's claim, which
		// is what covers a dragger who cannot be resolved at all.
		RescueSystem.tick(victim, RDAttachments.get(victim));
		helper.assertTrue(RDAttachments.get(victim).draggedBy() == null,
				"letting go must clear both ends of the grip");
		helper.succeed();
	}

	/** A grip must not survive the person on the other end of it. */
	@GameTest
	public void detachClearsBothEnds(GameTestHelper helper) {
		ServerPlayer rescuer = player(helper, 0.0);
		ServerPlayer victim = player(helper, 1.0);
		knockOut(helper, victim);
		RDAttachments.get(rescuer).setDragging(victim.getUUID());
		RDAttachments.get(victim).setDraggedBy(rescuer.getUUID());

		RescueSystem.detach(victim);
		RescueSystem.tick(rescuer, RDAttachments.get(rescuer));
		helper.assertTrue(RDAttachments.get(rescuer).dragging() == null,
				"the dragger must let go when the body leaves");
		helper.succeed();
	}

	/** Being dragged is only for those who cannot walk themselves. */
	@GameTest
	public void aWakingVictimIsDropped(GameTestHelper helper) {
		ServerPlayer rescuer = player(helper, 0.0);
		ServerPlayer victim = player(helper, 1.0);
		knockOut(helper, victim);
		RealisticDamageData rescuerData = RDAttachments.get(rescuer);
		rescuerData.setDragging(victim.getUUID());
		RDAttachments.get(victim).setDraggedBy(rescuer.getUUID());

		RDAttachments.get(victim).setUnconscious(false);
		RescueSystem.tick(rescuer, rescuerData);
		helper.assertTrue(rescuerData.dragging() == null,
				"someone who has come round is not being carried anywhere");
		helper.succeed();
	}
}
