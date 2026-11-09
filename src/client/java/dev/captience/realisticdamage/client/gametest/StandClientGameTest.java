package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.RemoteCorpses;
import dev.captience.realisticdamage.ragdoll.Stepper;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.phys.AABB;

/**
 * Stumbling deaths, IK phase 0, in a real client: {@code /rdmg ragdoll shove}
 * places a mannequin, puts its ragdoll on its feet and shoves it away from
 * the player. It has to stumble after its balance, its planted feet not
 * sliding, and go over by itself the way it was shoved, well away from where
 * it stood, before its time on its feet is up. The screenshots, taken from
 * the side every other tick through the stumble, are for a person to judge
 * the look.
 */
public class StandClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().ragdolls = ClientEffectsConfig.RagdollMode.FULL;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, "time set noon");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
			});
			double px = context.computeOnClient(client -> client.player.getX());
			double py = context.computeOnClient(client -> client.player.getY());
			double pz = context.computeOnClient(client -> client.player.getZ());
			context.runOnClient(client -> {
				client.player.setYRot(0.0F);
				client.player.setXRot(0.0F);
			});
			context.waitTicks(5);
			// Placed three blocks along +Z, facing the player, shoved away (+Z) a second later.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.snapTo(px, py, pz, 0.0F, 0.0F);
				// As the player, with the server's permission (a test world's player is not an operator).
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
						"execute as " + player.getScoreboardName() + " at @s rotated as @s run rdmg ragdoll shove 3 away");
			});
			context.waitTicks(4);
			int id = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				List<Mannequin> found = player.level().getEntitiesOfClass(Mannequin.class,
						new AABB(px - 5, py - 2, pz - 2, px + 5, py + 3, pz + 8));
				if (found.isEmpty()) {
					throw new AssertionError("the shove command placed no mannequin");
				}
				return found.getFirst().getId();
			});
			// Watch from the side: off to -X, level with the body, looking +X.
			context.runOnClient(client -> {
				client.player.setPos(px - 3.4, py, pz + 4.2);
				client.player.setYRot(-90.0F);
				client.player.setXRot(8.0F);
			});
			context.waitTicks(16);
			context.takeScreenshot("rd_stand_00_before");
			for (int i = 0; i < 40 && context.computeOnClient(client -> RemoteCorpses.standStepper(id)) == null; i++) {
				context.waitTick();
			}
			if (context.computeOnClient(client -> RemoteCorpses.standStepper(id)) == null) {
				throw new AssertionError("the shoved mannequin never became a standing ragdoll");
			}
			org.joml.Vector3d from = context.computeOnClient(client -> RemoteCorpses.torsoOf(id));
			// The stumble: a frame every other tick for a second and a quarter.
			int lostAt = -1;
			for (int frame = 0; frame < 13; frame++) {
				context.takeScreenshot(String.format("rd_stand_%02d_stumble", frame + 1));
				for (int i = 0; i < 2; i++) {
					context.waitTick();
					if (lostAt < 0 && context.computeOnClient(client -> RemoteCorpses.standLetGo(id))) {
						lostAt = frame * 2 + i + 1;
					}
				}
			}
			// On its feet for 1.6 seconds at the very most; shoved like this it is over well before.
			for (int i = 26; i < 60 && lostAt < 0; i++) {
				context.waitTick();
				if (context.computeOnClient(client -> RemoteCorpses.standLetGo(id))) {
					lostAt = i + 1;
				}
			}
			Stepper stepper = context.computeOnClient(client -> RemoteCorpses.standStepper(id));
			int steps = stepper.steps();
			double drift = stepper.worstDrift();
			String why = stepper.lost();
			System.out.printf(java.util.Locale.ROOT,
					"RD stand test: %d steps, worst planted-foot slide %.3f, off its feet after %d ticks (%s)%n",
					steps, drift, lostAt, why);
			if (lostAt < 0) {
				throw new AssertionError("held on its feet and never let go");
			}
			if (steps < 1) {
				throw new AssertionError("shoved at 3 blocks a second and took no step");
			}
			if (drift > 0.02) {
				throw new AssertionError("a planted foot slid " + drift + " blocks");
			}
			if (lostAt > 30) {
				throw new AssertionError("shoved at 3 blocks a second and still on its feet after " + lostAt + " ticks");
			}
			context.waitTicks(40);
			double down = context.computeOnClient(client -> RemoteCorpses.leanOf(id));
			org.joml.Vector3d to = context.computeOnClient(client -> RemoteCorpses.torsoOf(id));
			double went = to.z - from.z;
			System.out.printf(java.util.Locale.ROOT, "RD stand test: down, lean %.1f, %.2f blocks along the shove%n",
					down, went);
			context.takeScreenshot("rd_stand_14_down");
			if (down < 60.0) {
				throw new AssertionError("off its feet and still up: lean " + down);
			}
			if (went < 0.8) {
				throw new AssertionError("it was to stumble away with the shove, and went " + went + " blocks");
			}
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}
}
