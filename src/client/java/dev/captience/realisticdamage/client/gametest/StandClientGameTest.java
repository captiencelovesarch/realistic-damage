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
 * places a mannequin, holds its ragdoll on its feet and shoves it away from
 * the player. It has to catch itself in a few steps without its planted feet
 * sliding, stand, and drop once it has stood still for three seconds. The
 * screenshots, taken from the side every few ticks through the steps, are for
 * a person to judge the look.
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
				client.player.setPos(px - 2.6, py, pz + 3.4);
				client.player.setYRot(-90.0F);
				client.player.setXRot(8.0F);
			});
			context.waitTicks(16);
			context.takeScreenshot("rd_stand_00_before");
			// The death, then the steps: a frame every two ticks.
			int shotTick = 0;
			for (int i = 0; i < 40 && context.computeOnClient(client -> RemoteCorpses.standStepper(id)) == null; i++) {
				context.waitTick();
			}
			if (context.computeOnClient(client -> RemoteCorpses.standStepper(id)) == null) {
				throw new AssertionError("the shoved mannequin never became a standing ragdoll");
			}
			for (int frame = 0; frame < 10; frame++) {
				context.takeScreenshot(String.format("rd_stand_%02d_step", frame + 1));
				context.waitTick();
				shotTick++;
			}
			context.waitTicks(30);
			Stepper stepper = context.computeOnClient(client -> RemoteCorpses.standStepper(id));
			int steps = stepper.steps();
			double drift = stepper.worstDrift();
			double lean = context.computeOnClient(client -> RemoteCorpses.leanOf(id));
			boolean standing = stepper.standing();
			System.out.printf(java.util.Locale.ROOT,
					"RD stand test: %d steps, worst planted-foot slide %.3f, lean %.1f, standing %s%n",
					steps, drift, lean, standing);
			context.takeScreenshot("rd_stand_11_caught");
			if (steps < 1) {
				throw new AssertionError("shoved at 3 blocks a second and took no step");
			}
			if (drift > 0.02) {
				throw new AssertionError("a planted foot slid " + drift + " blocks");
			}
			if (lean > 15.0) {
				throw new AssertionError("not back on its feet: lean " + lean);
			}
			// Let go after three seconds standing still, then down.
			for (int i = 0; i < 160 && !context.computeOnClient(client -> RemoteCorpses.standLetGo(id)); i++) {
				context.waitTick();
			}
			if (!context.computeOnClient(client -> RemoteCorpses.standLetGo(id))) {
				throw new AssertionError("held on its feet and never let go");
			}
			context.waitTicks(40);
			double down = context.computeOnClient(client -> RemoteCorpses.leanOf(id));
			System.out.printf(java.util.Locale.ROOT, "RD stand test: let go, lean %.1f%n", down);
			context.takeScreenshot("rd_stand_12_dropped");
			if (down < 60.0) {
				throw new AssertionError("let go and still up: lean " + down);
			}
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}
}
