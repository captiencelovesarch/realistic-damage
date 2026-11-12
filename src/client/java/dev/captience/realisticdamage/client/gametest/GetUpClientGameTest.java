package dev.captience.realisticdamage.client.gametest;

import java.util.HashSet;
import java.util.Set;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.LiveRagdolls;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Getting up (2.0), in stages: a zombie knocked flat is photographed side on
 * at points along its get-up, held there, until it has been seen getting up
 * both face down and face up. The frames are for a person to judge.
 */
public class GetUpClientGameTest implements FabricClientGameTest {
	/** Where along the get-up each frame is taken: every 4%, enough to play back as an animation. */
	private static final double[] AT = java.util.stream.IntStream.rangeClosed(0, 25).mapToDouble(i -> i * 0.04).toArray();

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().ragdolls = ClientEffectsConfig.RagdollMode.FULL;
			ClientEffectsConfig.get().knockdownRagdolls = true;
		});
		Set<String> seen = new HashSet<>();
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, "time set noon");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
				server.getCommands().performPrefixedCommand(source, "gamemode creative @a");
			});
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(client -> {
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
				client.options.fov().set(50);
			});
			// Thrown one way and then the other, facing each way, until both routes have been seen.
			float[][] tries = {{0.0F, 0.6F}, {180.0F, 0.6F}, {90.0F, 0.6F}, {270.0F, 0.6F}, {0.0F, -0.6F}, {180.0F, -0.6F}};
			for (int attempt = 0; attempt < tries.length && seen.size() < 2; attempt++) {
				float facing = tries[attempt][0];
				float push = tries[attempt][1];
				int[] id = new int[1];
				singleplayer.getServer().runOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					ServerLevel level = player.level();
					LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
					zombie.snapTo(player.getX() + 0.5, player.getY(), player.getZ() + 8.5, facing, 0.0F);
					zombie.setYHeadRot(facing);
					zombie.yBodyRot = facing;
					((Mob) zombie).setNoAi(true);
					zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
							new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HELMET));
					level.addFreshEntity(zombie);
					RDAttachments.get(zombie).reset();
					RDAttachments.pushSnapshot(zombie);
					MobAnatomy.track(zombie);
					id[0] = zombie.getId();
				});
				context.waitTicks(10);
				singleplayer.getServer().runOnServer(server -> {
					ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
					LivingEntity zombie = (LivingEntity) level.getEntity(id[0]);
					zombie.setDeltaMovement(push, 0.2, 0.0);
					zombie.hurtServer(level, level.damageSources().explosion(null, null), 9.0F);
				});
				String route = null;
				for (int t = 0; t < 400 && route == null; t++) {
					context.waitTicks(1);
					route = context.computeOnClient(client -> {
						Entity e = client.level.getEntity(id[0]);
						return e == null ? null : LiveRagdolls.getUpRoute(e);
					});
				}
				if (route == null) {
					System.out.println("[rd] get-up: attempt " + attempt + " never got up");
					continue;
				}
				String name = route.split(" ")[0];
				float yaw = Float.parseFloat(route.split(" ")[1]);
				System.out.println("[rd] get-up: attempt " + attempt + " gets up " + route);
				boolean fresh = seen.add(name);
				context.runOnClient(client -> LiveRagdolls.heldProgress = 0.0);
				if (fresh) {
					// Side on: the camera to the body's right, looking across at it.
					context.runOnClient(client -> {
						Entity e = client.level.getEntity(id[0]);
						double r = Math.toRadians(yaw);
						double fx = -Math.sin(r);
						double fz = Math.cos(r);
						double side = 3.6;
						double cx = e.getX() - fz * side;
						double cz = e.getZ() + fx * side;
						client.player.setPos(cx, e.getY(), cz);
						double look = Math.toDegrees(Math.atan2(-(e.getX() - cx), e.getZ() - cz));
						client.player.setYRot((float) look);
						client.player.setXRot(12.0F);
						client.options.setCameraType(CameraType.FIRST_PERSON);
					});
					for (int i = 0; i < AT.length; i++) {
						double p = AT[i];
						context.runOnClient(client -> LiveRagdolls.heldProgress = p);
						context.waitTicks(1);
						context.takeScreenshot(String.format("rd_getup_%s_%02d", name, i));
					}
				}
				context.runOnClient(client -> LiveRagdolls.heldProgress = Double.NaN);
				context.waitTicks(GetUpCheck.TICKS_AFTER);
				singleplayer.getServer().runOnServer(server -> {
					Entity e = server.getPlayerList().getPlayers().getFirst().level().getEntity(id[0]);
					if (e != null) {
						e.discard();
					}
				});
				context.waitTicks(5);
			}
			System.out.println("[rd] get-up routes seen: " + seen);
			if (seen.isEmpty()) {
				throw new AssertionError("no knocked-down zombie ever got up");
			}
		} finally {
			context.runOnClient(client -> {
				LiveRagdolls.heldProgress = Double.NaN;
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
				client.options.fov().set(70);
				ClientEffectsConfig.load();
			});
		}
	}

	/** How long to let a released get-up finish. */
	private static final class GetUpCheck {
		static final int TICKS_AFTER = 45;
	}
}
