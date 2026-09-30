package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.ConsciousnessEffects;
import dev.captience.realisticdamage.client.DeathCamera;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * The camera stays with the body (1.7), run as the playtest that asked for it:
 * killed at the edge of a tall roof by a blow that knocks the body off it.
 *
 * <p>Vanilla froze the camera one second after death, wherever the body was;
 * the proof here is that the view reaches the ground far below, collapses to
 * floor height, thuds on landing, that the violent death's blackout waits for
 * all of that, and that the death screen fades in and only then lets you
 * respawn. Then the same death with the switch off must be vanilla exactly.
 */
public class DeathCameraClientGameTest implements FabricClientGameTest {
	/** Height of the roof above the spawn floor. */
	private static final int TOWER = 24;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().deathCamera = true;
			// Realistic, because its blackout is the one that goes fully black,
			// and a full-violence blow makes it a cut: the held blackout is only
			// tested if there is a blackout to hold.
			ClientEffectsConfig.get().realisticDeath = true;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			double roof = knockOffTower(context, singleplayer);
			double groundish = roof - TOWER;

			// The first moments: the screen has not arrived and cannot be used.
			context.waitTicks(3);
			if (context.computeOnClient(client -> DeathCamera.screenAlpha(1.0F)) > 0.05F) {
				throw new AssertionError("the death screen arrived at full strength on the death tick");
			}
			if (context.computeOnClient(client -> DeathCamera.buttonsReady())) {
				throw new AssertionError("the respawn button was live before the screen could be seen");
			}

			boolean midFallShot = false;
			double maxMovingBlack = 0.0;
			int ticks = 0;
			while (!context.computeOnClient(client -> DeathCamera.settled())) {
				if (context.computeOnClient(client -> client.player.isRemoved())) {
					throw new AssertionError("the body was removed from the world mid-fall, " + ticks + " ticks in");
				}
				maxMovingBlack = Math.max(maxMovingBlack,
						context.computeOnClient(client -> ConsciousnessEffects.lastBlackoutLevel()));
				double cameraY = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().y);
				if (!midFallShot && cameraY < roof - TOWER / 2.0) {
					context.takeScreenshot("rd_deathcam_00_falling");
					midFallShot = true;
				}
				context.waitTicks(1);
				if (++ticks > 200) {
					throw new AssertionError("the body never came to rest");
				}
			}
			if (maxMovingBlack > 0.6 + 1.0e-6) {
				throw new AssertionError("the blackout closed while the body was still falling: " + maxMovingBlack);
			}

			double cameraY = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().y);
			double bodyY = context.computeOnClient(client -> client.player.getY());
			if (bodyY > groundish + 1.5) {
				throw new AssertionError("the body did not reach the ground: y " + bodyY + ", ground about " + groundish);
			}
			if (cameraY - bodyY > 0.6) {
				throw new AssertionError("the view did not collapse to the floor: eye " + (cameraY - bodyY) + " above the feet");
			}
			if (context.computeOnClient(client -> DeathCamera.thuds()) < 1) {
				throw new AssertionError("a body fell " + TOWER + " blocks and landed without a thud");
			}
			// The corpse's movement stayed on this client.
			double serverY = singleplayer.getServer().computeOnServer(server ->
					server.getPlayerList().getPlayers().getFirst().getY());
			if (serverY < roof - 2.0) {
				throw new AssertionError("the dead body's position was sent to the server: server y " + serverY);
			}
			context.takeScreenshot("rd_deathcam_01_landed");

			// Then the screen fades in, and the blackout completes.
			ticks = 0;
			boolean halfShot = false;
			while (context.computeOnClient(client -> DeathCamera.screenAlpha(1.0F)) < 0.999F) {
				float alpha = context.computeOnClient(client -> DeathCamera.screenAlpha(1.0F));
				if (!halfShot && alpha > 0.4F) {
					context.takeScreenshot("rd_deathcam_02_screen_fading");
					halfShot = true;
				}
				context.waitTicks(1);
				if (++ticks > 120) {
					throw new AssertionError("the death screen never finished fading in, at " + alpha);
				}
			}
			context.waitTicks(20);
			if (!context.computeOnClient(client -> DeathCamera.buttonsReady())) {
				throw new AssertionError("the screen is up and the buttons are still asleep");
			}
			double finalBlack = context.computeOnClient(client -> ConsciousnessEffects.lastBlackoutLevel());
			if (finalBlack < 0.99) {
				throw new AssertionError("the held blackout was never released: " + finalBlack);
			}
			context.takeScreenshot("rd_deathcam_03_screen_up");

			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			if (context.computeOnClient(client -> ClientState.dying())) {
				throw new AssertionError("respawned and still dying");
			}
			if (context.computeOnClient(client -> DeathCamera.settled())) {
				throw new AssertionError("the death camera's state leaked into the next life");
			}

			// Switch off: vanilla exactly. The body is removed at twenty ticks,
			// the camera stays up on the roof, the screen is there at once.
			context.runOnClient(client -> ClientEffectsConfig.get().deathCamera = false);
			double roof2 = knockOffTower(context, singleplayer);
			context.waitTicks(2);
			if (context.computeOnClient(client -> DeathCamera.screenAlpha(1.0F)) < 1.0F) {
				throw new AssertionError("with Death Camera off the screen still faded");
			}
			context.waitTicks(40);
			if (!context.computeOnClient(client -> client.player.isRemoved())) {
				throw new AssertionError("with Death Camera off the body was kept past vanilla's twenty ticks");
			}
			double frozenY = context.computeOnClient(client -> client.gameRenderer.mainCamera().position().y);
			if (frozenY < roof2 - 6.0) {
				throw new AssertionError("with Death Camera off the camera still followed the body down: y " + frozenY);
			}
			context.takeScreenshot("rd_deathcam_04_off_frozen");
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}

	/**
	 * Builds a one-block roof {@link #TOWER} blocks up, stands the player at its
	 * edge, and kills them with a blow that throws them off it. Waits until the
	 * death has reached the client; returns the roof's top surface.
	 */
	private static double knockOffTower(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		double roof = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			BlockPos base = player.blockPosition();
			BlockPos top = base.above(TOWER);
			player.level().setBlockAndUpdate(top, Blocks.STONE.defaultBlockState());
			player.teleportTo(top.getX() + 0.5, top.getY() + 1.0, top.getZ() + 0.5);
			return top.getY() + 1.0;
		});
		context.waitTicks(10);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			// Sideways and up, the way a heavy swing throws a body.
			player.setDeltaMovement(0.9, 0.4, 0.0);
			player.hurtMarked = true;
			player.hurtServer(player.level(), player.level().damageSources().generic(), 40.0F);
		});
		int waited = 0;
		while (!context.computeOnClient(client -> ClientState.dying())) {
			context.waitTicks(1);
			if (++waited > 20) {
				throw new AssertionError("the blow did not kill");
			}
		}
		return roof;
	}
}
