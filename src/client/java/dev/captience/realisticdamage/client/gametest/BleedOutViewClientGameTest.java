package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.DeathCamera;
import dev.captience.realisticdamage.client.RagdollView;
import dev.captience.realisticdamage.client.RemoteCorpses;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * Your own death by bleeding out, in your own ragdoll: the blood runs out, you
 * pass out, and the body dies where it lies. The view has to ride the
 * ragdoll, as it does for every other death, and not the body vanilla
 * animates (which, lying in front of the camera, you would see).
 */
public class BleedOutViewClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().deathCamera = true;
			ClientEffectsConfig.get().ownRagdoll = true;
			ClientEffectsConfig.get().ragdolls = ClientEffectsConfig.RagdollMode.FULL;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setHealth(3.0F);
				RealisticDamageData data = RDAttachments.get(player);
				data.setBlood(0.0);
				RDAttachments.pushSnapshot(player);
			});
			int waited = 0;
			boolean wasOut = false;
			while (!context.computeOnClient(client -> ClientState.dying())) {
				context.waitTick();
				wasOut |= singleplayer.getServer().computeOnServer(server ->
						RDAttachments.get(server.getPlayerList().getPlayers().getFirst()).unconscious());
				if (++waited > 600) {
					throw new AssertionError("did not bleed out in 30 seconds");
				}
			}
			System.out.println("[rd] bled out after " + waited + " ticks, unconscious first: " + wasOut);
			context.waitTicks(4);
			int id = context.computeOnClient(client -> client.player.getId());
			String state = context.computeOnClient(client -> "death camera " + DeathCamera.active() + ", ragdoll view "
					+ RagdollView.active() + ", own ragdoll lean " + RemoteCorpses.leanOf(id) + ", detached "
					+ client.gameRenderer.mainCamera().isDetached() + ", pose " + client.player.getPose());
			System.out.println("[rd] bled out: " + state);
			context.takeScreenshot("rd_bleedout_00_first_person");
			context.waitTicks(20);
			context.takeScreenshot("rd_bleedout_01_first_person");
			if (!context.computeOnClient(client -> RagdollView.active())) {
				throw new AssertionError("bled out, and the camera is not riding a ragdoll: " + state);
			}
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}
}
