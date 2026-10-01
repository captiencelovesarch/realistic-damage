package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.DeathCamera;
import dev.captience.realisticdamage.damage.DeathKind;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The death camera, second pass (1.7), run as the playtest that asked for it.
 *
 * <p>"Sometimes it moves a bunch and then just FREEZES", and it should move
 * "based on different kinds of deaths, like lava, large falls". The freeze was
 * measured before it was fixed: killed by an iron golem, every client entity
 * but the body was gone on the twentieth tick — the server had dropped the dead
 * player from its world — and from the landing to the death screen the frame
 * was pixel-still. So the proof here is the playtest's own death first: the
 * pig next to you is still there and still yours to watch a second and a half
 * in, and the view is still settling long after the body stopped. Then a blast
 * that has to spin you, and lava that has to take you under face-up.
 */
public class DeathKindsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().deathCamera = true;
			ClientEffectsConfig.get().realisticDeath = true;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			golemKill(context, singleplayer);
			blast(context, singleplayer);
			lava(context, singleplayer);
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}

	/** The playtest's death: an iron golem, a pig grazing nearby, flat ground. */
	private static void golemKill(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		int pigId = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			Pig pig = EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
			pig.snapTo(player.getX() - 4.0, player.getY(), player.getZ() + 3.0, 0.0F, 0.0F);
			player.level().addFreshEntity(pig);
			return pig.getId();
		});
		context.waitTicks(5);
		die(context, singleplayer, player -> {
			player.setHealth(2.0F);
			IronGolem golem = EntityTypes.IRON_GOLEM.create(player.level(), EntitySpawnReason.COMMAND);
			golem.snapTo(player.getX() + 2.0, player.getY(), player.getZ(), 90.0F, 0.0F);
			player.level().addFreshEntity(golem);
			golem.setTarget(player);
		});
		if (context.computeOnClient(client -> DeathCamera.kind()) != DeathKind.BLOW) {
			throw new AssertionError("a golem's blow arrived as " + context.computeOnClient(client -> DeathCamera.kind()));
		}

		waitSettled(context);
		double rollAtRest = context.computeOnClient(client -> DeathCamera.lastRoll());
		context.waitTicks(25);
		double rollLater = context.computeOnClient(client -> DeathCamera.lastRoll());
		if (Math.abs(rollLater - rollAtRest) < 1.0) {
			throw new AssertionError("the view went still the moment the body did: roll "
					+ rollAtRest + " then " + rollLater);
		}

		// Past vanilla's twenty ticks, the world is still being sent: the pig is here.
		int deathTime = context.computeOnClient(client -> client.player.deathTime);
		if (deathTime < 30) {
			throw new AssertionError("checked the world too early to prove anything: deathTime " + deathTime);
		}
		if (!context.computeOnClient(client -> client.level.getEntity(pigId) != null)) {
			throw new AssertionError("the pig vanished from the dead player's world at deathTime " + deathTime
					+ " — the server dropped the body and the world froze");
		}
		context.takeScreenshot("rd_deathkinds_00_golem_world_alive");

		respawn(context);
		if (context.computeOnClient(client -> DeathCamera.kind()) != DeathKind.BLOW
				|| context.computeOnClient(client -> ClientState.deathKind()) != DeathKind.BLOW) {
			throw new AssertionError("the death's kind leaked into the next life");
		}
		clearAround(singleplayer);
	}

	/** Point-blank blast: the body is thrown, and the view spins while it flies. */
	private static void blast(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		die(context, singleplayer, player -> {
			player.setHealth(1.0F);
			player.level().explode(null, player.getX() + 1.2, player.getY() + 0.5, player.getZ(), 3.0F,
					Level.ExplosionInteraction.NONE);
		});
		if (context.computeOnClient(client -> DeathCamera.kind()) != DeathKind.EXPLOSION) {
			throw new AssertionError("a blast arrived as " + context.computeOnClient(client -> DeathCamera.kind()));
		}
		double maxTumble = 0.0;
		for (int i = 0; i < 30; i++) {
			maxTumble = Math.max(maxTumble, context.computeOnClient(client -> Math.abs(DeathCamera.tumbled())));
			context.waitTicks(1);
		}
		if (maxTumble < 45.0) {
			throw new AssertionError("thrown by a blast and the view barely turned: " + maxTumble + " degrees");
		}
		context.takeScreenshot("rd_deathkinds_01_blast");
		respawn(context);
		clearAround(singleplayer);
	}

	/** Lava: the body sinks, the eye goes under, the face turns up toward the light. */
	private static void lava(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		die(context, singleplayer, player -> {
			BlockPos at = player.blockPosition();
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					player.level().setBlockAndUpdate(at.offset(dx, 0, dz), Blocks.LAVA.defaultBlockState());
				}
			}
		});
		if (context.computeOnClient(client -> DeathCamera.kind()) != DeathKind.LAVA) {
			throw new AssertionError("lava arrived as " + context.computeOnClient(client -> DeathCamera.kind()));
		}
		context.waitTicks(45);
		double pitch = context.computeOnClient(client -> DeathCamera.lastPitch());
		if (pitch > -10.0) {
			throw new AssertionError("sinking in lava, the face should have turned up: pitch " + pitch);
		}
		double eye = context.computeOnClient(client ->
				client.gameRenderer.mainCamera().position().y - client.player.getY());
		if (eye > 0.2) {
			throw new AssertionError("the eye stayed above the lava: " + eye + " over the feet");
		}
		context.takeScreenshot("rd_deathkinds_02_lava");
		respawn(context);
		clearAround(singleplayer);
	}

	private static void die(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			Consumer<ServerPlayer> kill) {
		singleplayer.getServer().runOnServer(server -> kill.accept(server.getPlayerList().getPlayers().getFirst()));
		int waited = 0;
		while (!context.computeOnClient(client -> ClientState.dying())) {
			context.waitTicks(1);
			if (++waited > 200) {
				throw new AssertionError("the setup never killed the player");
			}
		}
		context.waitTicks(2);
	}

	private static void waitSettled(ClientGameTestContext context) {
		int ticks = 0;
		while (!context.computeOnClient(client -> DeathCamera.settled())) {
			context.waitTicks(1);
			if (++ticks > 200) {
				throw new AssertionError("the body never came to rest");
			}
		}
		context.waitTicks(2);
	}

	/** Waits for the screen and its buttons, checks the body was released to the server, respawns. */
	private static void respawn(ClientGameTestContext context) {
		int ticks = 0;
		while (context.computeOnClient(client -> DeathCamera.screenAlpha(1.0F)) < 0.999F
				|| !context.computeOnClient(client -> DeathCamera.buttonsReady())) {
			context.waitTicks(1);
			if (++ticks > 300) {
				throw new AssertionError("the death screen never finished arriving");
			}
		}
		context.waitTicks(1);
		if (!context.computeOnClient(client -> DeathCamera.released())) {
			throw new AssertionError("the screen is up and the server was never told it may take the body");
		}
		context.waitTicks(10);
		context.clickScreenButton("deathScreen.respawn");
		context.waitTicks(20);
		if (context.computeOnClient(client -> ClientState.dying())) {
			throw new AssertionError("respawned and still dying");
		}
	}

	private static void clearAround(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.level().getEntities(player, player.getBoundingBox().inflate(40.0)).forEach(e -> e.discard());
			BlockPos at = player.blockPosition();
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					for (int dy = -3; dy <= 3; dy++) {
						BlockPos pos = at.offset(dx, dy, dz);
						if (player.level().getBlockState(pos).is(Blocks.LAVA)) {
							player.level().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		});
	}
}
