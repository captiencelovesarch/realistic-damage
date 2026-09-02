package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.damage.DamageHooks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * The server half of death violence (1.5): scoring the fatal blow.
 *
 * <p>This scoring hangs off ALLOW_DEATH, not the trauma pipeline, and the
 * distinction was found the hard way: Fabric's AFTER_DAMAGE is documented not
 * to fire when the damage kills, so the pipeline never sees a killing blow at
 * all. A first version stashed the violence there, its gametest "verified" it
 * by pre-setting health to zero and calling the pipeline directly — a path the
 * live game never takes — and the end-to-end client test came back with
 * violence 0.0. These tests call the real scoring entry instead, with real
 * damage sources; the event plumbing above it is covered by the client test,
 * where the payload either arrives or does not.
 */
public class DeathGameTest {
	private ServerPlayer mockPlayer(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(0.0, 0.0, 0.0);
		player.yBodyRot = 0.0F;
		return player;
	}

	@GameTest
	public void aMassiveKillingBlowScoresFullViolence(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		double violence = DamageHooks.scoreDeathViolence(player, data,
				helper.getLevel().damageSources().generic(), 60.0F);
		helper.assertTrue(violence >= 0.99,
				"sixty raw damage against twenty max health should be a cut, got " + violence);
		helper.assertTrue(data.consumeDeathViolence(player.tickCount) >= 0.99,
				"the score must also be stashed for AFTER_DEATH to send");
		helper.succeed();
	}

	/** A weak finishing blow is a quiet end, not an execution. */
	@GameTest
	public void aFinishingPokeScoresAlmostNothing(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		double violence = DamageHooks.scoreDeathViolence(player, data,
				helper.getLevel().damageSources().generic(), 2.0F);
		helper.assertTrue(violence < 0.1,
				"two damage that happens to kill should stay a slow ending, got " + violence);
		helper.succeed();
	}

	/** Falls score by height, not by their deliberately compressed HP damage. */
	@GameTest
	public void aHugeLethalFallScoresByItsHeight(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.setPendingFall(47.0, 1.0, true);

		// The fatal damage figure for this fall is modest by design (1.4's
		// lethality compression) — the violence must come from the height.
		double violence = DamageHooks.scoreDeathViolence(player, data,
				helper.getLevel().damageSources().fall(), 12.0F);
		helper.assertTrue(violence >= 0.99,
				"a 50-block drop should be a cut whatever its HP number, got " + violence);
		data.clearPendingFall();
		helper.succeed();
	}

	/** The void, /kill and starving are ignored by the classifier: slow ending. */
	@GameTest
	public void deathsTheClassifierIgnoresScoreNothing(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();

		double violence = DamageHooks.scoreDeathViolence(player, data,
				helper.getLevel().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
		helper.assertTrue(violence == 0.0,
				"falling out of the world is not a blow, got " + violence);
		helper.assertTrue(data.consumeDeathViolence(player.tickCount) == 0.0,
				"...and must stash nothing");
		helper.succeed();
	}

	/**
	 * The totem seam. ALLOW_DEATH fires before the totem check, so the stash is
	 * tick-stamped: one orphaned by a refused death cannot colour a later,
	 * calmer one — consuming on any other tick gets nothing, and the stash does
	 * not survive the attempt.
	 */
	@GameTest
	public void anOrphanedStashExpiresInsteadOfHaunting(GameTestHelper helper) {
		ServerPlayer player = mockPlayer(helper);
		RealisticDamageData data = RDAttachments.get(player);
		data.reset();
		data.stashDeathViolence(1.0, player.tickCount);

		helper.assertTrue(data.consumeDeathViolence(player.tickCount + 600) == 0.0,
				"a stale stash coloured a death thirty seconds later");
		helper.assertTrue(data.consumeDeathViolence(player.tickCount) == 0.0,
				"the stash survived being consumed");
		helper.succeed();
	}
}
