package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

/**
 * The 1.4 approach to collapse: blackouts arrive before unconsciousness does,
 * and they are deterministic.
 */
public class MicrosleepGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		return player;
	}

	/**
	 * Holds consciousness at a level by topping it back up each tick, since the
	 * system itself would otherwise recover the player straight out of the band
	 * under test.
	 */
	private int countBlackouts(ServerPlayer player, RealisticDamageData data, double level, int ticks) {
		int blackouts = 0;
		boolean wasOut = false;
		for (int i = 0; i < ticks; i++) {
			data.setConsciousness(level);
			ConsciousnessSystem.tick(player, data);
			boolean out = data.microsleeping();
			if (out && !wasOut) {
				blackouts++;
			}
			wasOut = out;
		}
		return blackouts;
	}

	@GameTest
	public void aSteadyPlayerNeverBlacksOut(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		helper.assertTrue(countBlackouts(player, data, 80.0, 400) == 0,
				"a healthy player must never lose the thread");
		helper.succeed();
	}

	@GameTest
	public void blackoutsBeginBeforeCollapseDoes(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		int blackouts = countBlackouts(player, data, Balance.SYMPTOM_MICROSLEEP - 4.0, 600);
		helper.assertTrue(blackouts > 0, "below the threshold the player must start cutting out");
		helper.assertTrue(!data.unconscious(),
				"...while still being conscious: the blackouts are the warning, not the collapse");
		helper.succeed();
	}

	@GameTest
	public void theWorseYouAreTheMoreOftenYouGo(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		int mild = countBlackouts(player, data, Balance.SYMPTOM_MICROSLEEP - 2.0, 1200);
		data.reset();
		int severe = countBlackouts(player, data, 1.0, 1200);
		helper.assertTrue(severe > mild,
				"blackouts must crowd together as you fade: " + severe + " vs " + mild);
		helper.succeed();
	}

	/** No RNG in outcomes — the same descent must produce the same blackouts. */
	@GameTest
	public void identicalDescentsBlackOutIdentically(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		int first = countBlackouts(player, data, 6.0, 800);
		data.reset();
		int second = countBlackouts(player, data, 6.0, 800);
		helper.assertTrue(first == second,
				"the same descent must black out the same way: " + first + " vs " + second);
		helper.succeed();
	}

	@GameTest
	public void aBlackoutTakesTheControlsAway(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setConsciousness(5.0);
		data.beginMicrosleep(Balance.MICROSLEEP_DURATION_TICKS_MAX);
		ImpairmentSystem.tick(player, data);
		helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < 0.001,
				"a player who is gone for a moment must not be steering");
		helper.succeed();
	}

	@GameTest
	public void blackoutsEndOnTheirOwn(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.beginMicrosleep(Balance.MICROSLEEP_DURATION_TICKS_MAX);
		for (int i = 0; i < Balance.MICROSLEEP_DURATION_TICKS_MAX; i++) {
			data.tickMicrosleep();
		}
		helper.assertTrue(!data.microsleeping(), "a microsleep must always hand the controls back");
		helper.succeed();
	}

	/** Going fully under supersedes blinking out, rather than stacking with it. */
	@GameTest
	public void collapsingClearsTheBlackoutState(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.beginMicrosleep(Balance.MICROSLEEP_DURATION_TICKS_MAX);
		data.drainConsciousness(200.0);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "collapsed");
		helper.assertTrue(!data.microsleeping(),
				"unconsciousness must replace the microsleep, not layer under it");
		helper.succeed();
	}

	/**
	 * Waking happens at 20, which is below the microsleep threshold — so without
	 * clearing the accumulated phase you would blink straight back out on credit
	 * built up while unconscious.
	 */
	@GameTest
	public void wakingDoesNotImmediatelyBlackOutAgain(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.drainConsciousness(200.0);
		ConsciousnessSystem.tick(player, data);
		data.setUnconsciousTicks(Balance.MIN_UNCONSCIOUS_TICKS + 1);
		data.restoreConsciousness(Balance.WAKE_THRESHOLD + 1.0);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(!data.unconscious(), "awake");
		helper.assertTrue(!data.microsleeping(), "and not instantly gone again");
		helper.assertTrue(data.microsleepPhase() < 0.5,
				"the phase must not have banked a blackout while out cold");
		helper.succeed();
	}

	/** A concussion brings the whole ladder forward, blackouts included. */
	@GameTest
	public void concussionMakesYouBlackOutSooner(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		double level = Balance.SYMPTOM_MICROSLEEP + 5.0;

		data.reset();
		int clear = countBlackouts(player, data, level, 600);
		data.reset();
		data.setConcussion(true);
		int concussed = countBlackouts(player, data, level, 600);
		helper.assertTrue(clear == 0, "clear-headed at this level, nothing should happen");
		helper.assertTrue(concussed > 0, "concussed at the same level, the blackouts should have started");
		helper.succeed();
	}
}
