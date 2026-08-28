package dev.captience.realisticdamage.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.systems.ConsciousnessSystem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 1.1 aggro modes. A body on the floor should be in danger from what it was
 * already fighting; whether anything else joins in is the server's call.
 *
 * <p>These drive {@code Mob.setTarget} directly rather than waiting on mob AI.
 * That is not a shortcut — {@code MobTargetMixin} is what enforces the policy,
 * and every vanilla targeting goal reaches it through exactly this call. An
 * earlier version of this file let the AI run and asserted a target was never
 * acquired, which passed for the wrong reason: mob AI does not tick inside a
 * single gametest tick, so it was asserting against something that had never
 * been given a chance to happen.
 */
public class UnconsciousAggroGameTest {
	/** Structure-relative, so entities land inside the test bounds and get cleaned up. */
	private static final BlockPos PLAYER_AT = new BlockPos(1, 2, 1);

	private ServerPlayer playerAt(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(Vec3.atLowerCornerOf(PLAYER_AT));
		player.setPos(pos.x, pos.y, pos.z);
		RDAttachments.get(player).reset();
		return player;
	}

	private void knockOut(GameTestHelper helper, ServerPlayer player) {
		RealisticDamageData data = RDAttachments.get(player);
		data.drainConsciousness(Balance.CONSCIOUSNESS_MAX);
		ConsciousnessSystem.tick(player, data);
		helper.assertTrue(data.unconscious(), "the player must actually be down");
	}

	private Zombie zombieNear(GameTestHelper helper, ServerPlayer player, double distance) {
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, PLAYER_AT);
		zombie.setPos(player.getX(), player.getY(), player.getZ() + distance);
		zombie.setTarget(null);
		return zombie;
	}

	private void withMode(ServerConfig.UnconsciousAggro mode, Runnable body) {
		ServerConfig.UnconsciousAggro previous = ServerConfig.get().unconsciousAggro;
		ServerConfig.get().unconsciousAggro = mode;
		try {
			body.run();
		} finally {
			ServerConfig.get().unconsciousAggro = previous;
		}
	}

	/** A player who is still up is never affected, whatever the mode. */
	@GameTest
	public void aConsciousPlayerIsAlwaysTargetable(GameTestHelper helper) {
		withMode(ServerConfig.UnconsciousAggro.NONE, () -> {
			ServerPlayer player = playerAt(helper);
			Zombie zombie = zombieNear(helper, player, 2.0);
			zombie.setTarget(player);
			helper.assertTrue(zombie.getTarget() == player,
					"the policy must only ever apply to unconscious players");
		});
		helper.succeed();
	}

	@GameTest
	public void attackersRefusesANewAcquisition(GameTestHelper helper) {
		withMode(ServerConfig.UnconsciousAggro.ATTACKERS, () -> {
			ServerPlayer player = playerAt(helper);
			knockOut(helper, player);
			Zombie zombie = zombieNear(helper, player, 2.0);
			zombie.setTarget(player);
			helper.assertTrue(zombie.getTarget() != player,
					"ATTACKERS must refuse a mob that was not already fighting, even point blank");
		});
		helper.succeed();
	}

	/** The promise of ATTACKERS: an engagement already under way is not interrupted. */
	@GameTest
	public void attackersKeepsAnExistingEngagement(GameTestHelper helper) {
		withMode(ServerConfig.UnconsciousAggro.ATTACKERS, () -> {
			ServerPlayer player = playerAt(helper);
			Zombie zombie = zombieNear(helper, player, 2.0);
			// Engaged while the player was still standing.
			zombie.setTarget(player);
			helper.assertTrue(zombie.getTarget() == player, "engaged before the collapse");

			knockOut(helper, player);
			// Re-asserting the same target must not be refused.
			zombie.setTarget(player);
			helper.assertTrue(zombie.getTarget() == player,
					"ATTACKERS must let whatever was already fighting you carry on");
		});
		helper.succeed();
	}

	@GameTest
	public void nearbyAcceptsCloseAndRefusesDistant(GameTestHelper helper) {
		withMode(ServerConfig.UnconsciousAggro.NEARBY, () -> {
			ServerPlayer player = playerAt(helper);
			knockOut(helper, player);

			Zombie close = zombieNear(helper, player, Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS - 2.0);
			close.setTarget(player);
			helper.assertTrue(close.getTarget() == player,
					"a hostile standing over the body must be allowed to notice it");

			Zombie far = zombieNear(helper, player, Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS + 3.0);
			far.setTarget(player);
			helper.assertTrue(far.getTarget() != player,
					"a distant hostile must not acquire an unconscious player");
		});
		helper.succeed();
	}

	@GameTest
	public void allAcceptsWellBeyondTheNearbyRadius(GameTestHelper helper) {
		withMode(ServerConfig.UnconsciousAggro.ALL, () -> {
			ServerPlayer player = playerAt(helper);
			knockOut(helper, player);
			Zombie far = zombieNear(helper, player, Balance.UNCONSCIOUS_AGGRO_NEARBY_RADIUS + 3.0);
			far.setTarget(player);
			helper.assertTrue(far.getTarget() == player,
					"ALL must keep 1.0's behaviour of acquiring at range");
		});
		helper.succeed();
	}

	/** The mercy setting: existing attackers are dropped and cannot come back. */
	@GameTest
	public void noneDropsAttackersAndBlocksReacquisition(GameTestHelper helper) {
		withMode(ServerConfig.UnconsciousAggro.ATTACKERS, () -> {
			ServerPlayer player = playerAt(helper);
			Zombie zombie = zombieNear(helper, player, 2.0);
			zombie.setTarget(player);
			knockOut(helper, player);

			ServerConfig.get().unconsciousAggro = ServerConfig.UnconsciousAggro.NONE;
			RealisticDamageData data = RDAttachments.get(player);
			// The clearing sweep runs on a ten-tick cadence; give it one.
			for (int i = 0; i < 12; i++) {
				player.tickCount++;
				ConsciousnessSystem.tick(player, data);
			}
			helper.assertTrue(zombie.getTarget() != player,
					"NONE must drop an attacker once the player is down");
			zombie.setTarget(player);
			helper.assertTrue(zombie.getTarget() != player,
					"NONE must also refuse to let it come back");
		});
		helper.succeed();
	}
}
