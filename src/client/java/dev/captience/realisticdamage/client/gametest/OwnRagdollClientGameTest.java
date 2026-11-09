package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.DeathCamera;
import dev.captience.realisticdamage.client.RagdollView;
import dev.captience.realisticdamage.client.RemoteCorpses;
import dev.captience.realisticdamage.damage.Corpses;
import dev.captience.realisticdamage.damage.DeathKind;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Your own death in your own ragdoll (active ragdolls, phase 4): knocked off a
 * tower, the camera rides the ragdoll's eyes all the way down, ends on the
 * floor, the blackout waits for the body, third person shows the ragdoll, and
 * after the respawn the body you left stays where it fell.
 */
public class OwnRagdollClientGameTest implements FabricClientGameTest {
	private static final int TOWER = 6;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().deathCamera = true;
			ClientEffectsConfig.get().ownRagdoll = true;
			ClientEffectsConfig.get().ragdolls = ClientEffectsConfig.RagdollMode.FULL;
			ClientEffectsConfig.get().bodiesStay = 30;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> server.getCommands().performPrefixedCommand(
					server.createCommandSourceStack(), "time set noon"));
			double roof = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				BlockPos top = player.blockPosition().above(TOWER);
				player.level().setBlockAndUpdate(top, Blocks.STONE.defaultBlockState());
				player.teleportTo(top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5);
				return top.getY() + 1.0;
			});
			double ground = roof - TOWER - 1.0;
			context.waitTicks(10);
			context.runOnClient(client -> {
				client.player.setYRot(-90.0F);
				client.player.setXRot(10.0F);
			});
			context.waitTicks(5);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				// Struck in the chest, thrown off the edge toward +X.
				Corpses.script(player, new Corpses.Script(DeathKind.BLOW, BodyPart.TORSO, new Vec3(1.0, 0.0, 0.0), 1.6, 0.5));
				player.invulnerableTime = 0;
				player.hurtServer(player.level(), player.level().damageSources().generic(), 40.0F);
			});
			int waited = 0;
			while (!context.computeOnClient(client -> ClientState.dying())) {
				context.waitTicks(1);
				if (++waited > 20) {
					throw new AssertionError("the blow did not kill");
				}
			}
			context.waitTicks(3);
			if (!context.computeOnClient(client -> RagdollView.active())) {
				throw new AssertionError("dead, and the camera is not riding a ragdoll");
			}
			double worstOffEye = 0.0;
			double worstStillOff = 0.0;
			double lowest = Double.POSITIVE_INFINITY;
			for (int i = 0; i < 80; i++) {
				context.waitTicks(1);
				double off = context.computeOnClient(client -> {
					Vec3 eye = RagdollView.eye(1.0F);
					Vec3 cam = client.gameRenderer.mainCamera().position();
					return eye == null ? 99.0 : eye.distanceTo(cam);
				});
				// While the body moves, the camera (placed at the last frame's
				// partial tick) can trail the end-of-tick eye by a tick of motion;
				// once it is still they must agree.
				boolean still = context.computeOnClient(client -> !RagdollView.moving());
				if (still) {
					worstStillOff = Math.max(worstStillOff, off);
				} else {
					worstOffEye = Math.max(worstOffEye, off);
				}
				lowest = Math.min(lowest, context.computeOnClient(client -> client.gameRenderer.mainCamera().position().y));
				if (i == 6) {
					context.takeScreenshot("rd_own_00_falling_first_person");
				}
				if (i == 30) {
					// Third person: vanilla's camera, looking at the ragdoll.
					context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
					context.waitTicks(2);
					context.takeScreenshot("rd_own_02_third_person");
					if (context.computeOnClient(client -> RagdollView.active())) {
						throw new AssertionError("third person is still riding the head");
					}
					context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
					context.waitTicks(1);
				}
			}
			context.takeScreenshot("rd_own_01_landed_first_person");
			System.out.println("[rd] own ragdoll: camera off the eyes by at most " + worstOffEye + " moving, "
					+ worstStillOff + " still, lowest " + (lowest - ground) + " above the ground");
			if (worstOffEye > 1.5) {
				throw new AssertionError("the camera was not at the ragdoll's eyes while it fell: off by " + worstOffEye);
			}
			if (worstStillOff > 0.15) {
				throw new AssertionError("the camera was not at the still ragdoll's eyes: off by " + worstStillOff);
			}
			if (lowest - ground > 0.7) {
				throw new AssertionError("the view never came down to the floor: " + (lowest - ground) + " above it");
			}
			int settle = 0;
			while (!context.computeOnClient(client -> DeathCamera.settled())) {
				context.waitTicks(1);
				if (++settle > 300) {
					throw new AssertionError("the body never came to rest");
				}
			}
			int kept = context.computeOnClient(client -> RemoteCorpses.orphanCount());
			int screen = 0;
			while (context.computeOnClient(client -> DeathCamera.screenAlpha(1.0F)) < 0.99F) {
				context.waitTicks(1);
				if (++screen > 400) {
					throw new AssertionError("the death screen never finished arriving");
				}
			}
			context.waitTicks(80);
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(30);
			if (context.computeOnClient(client -> ClientState.dying())) {
				throw new AssertionError("respawned and still dying");
			}
			if (context.computeOnClient(client -> RagdollView.active())) {
				throw new AssertionError("the ragdoll view leaked into the next life");
			}
			int keptAfter = context.computeOnClient(client -> RemoteCorpses.orphanCount());
			System.out.println("[rd] own body kept: " + kept + " -> " + keptAfter);
			if (keptAfter <= kept) {
				throw new AssertionError("the body you left did not stay: kept " + kept + " -> " + keptAfter);
			}
			context.takeScreenshot("rd_own_03_respawned");
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}
}
