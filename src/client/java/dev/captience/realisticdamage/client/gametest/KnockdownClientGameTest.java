package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.LiveRagdolls;
import dev.captience.realisticdamage.client.RagdollView;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Knocked down, not dead (active ragdolls phase 4): a zombie and then the
 * player are thrown by a blast that does not kill. Each goes down as a
 * ragdoll, lies there while the knockdown lasts, gets up, and ends standing;
 * the player's camera rides the head while down and is back at standing eye
 * height after.
 */
public class KnockdownClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().ragdolls = ClientEffectsConfig.RagdollMode.FULL;
			ClientEffectsConfig.get().knockdownRagdolls = true;
			ClientEffectsConfig.get().ownRagdoll = true;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, "time set noon");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
			});
			int[] id = new int[1];
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				zombie.snapTo(player.getX() + 0.5, player.getY(), player.getZ() + 5.5, 180.0F, 0.0F);
				((Mob) zombie).setNoAi(true);
				zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_HELMET));
				level.addFreshEntity(zombie);
				RDAttachments.get(zombie).reset();
				RDAttachments.pushSnapshot(zombie);
				MobAnatomy.track(zombie);
				id[0] = zombie.getId();
			});
			context.runOnClient(client -> {
				client.player.setYRot(0.0F);
				client.player.setXRot(15.0F);
			});
			context.waitTicks(20);

			// Phase 5 first: a light blow it shrugs off. It flinches, and the flinch
			// is over inside a second.
			long playedBefore = context.computeOnClient(client -> dev.captience.realisticdamage.client.Flinches.played());
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				LivingEntity zombie = (LivingEntity) level.getEntity(id[0]);
				zombie.hurtServer(level, level.damageSources().playerAttack(player), 4.0F);
			});
			context.waitTicks(3);
			context.takeScreenshot("rd_knock_20_zombie_flinch");
			long playedAfter = context.computeOnClient(client -> dev.captience.realisticdamage.client.Flinches.played());
			if (playedAfter <= playedBefore) {
				throw new AssertionError("a blow the zombie shrugged off played no flinch");
			}
			context.waitTicks(20);
			int still = context.computeOnClient(client -> dev.captience.realisticdamage.client.Flinches.active());
			if (still != 0) {
				throw new AssertionError("a flinch was still playing a second later");
			}
			if (context.computeOnClient(client -> {
				Entity e = client.level.getEntity(id[0]);
				return e == null ? 0 : LiveRagdolls.stage(e);
			}) >= 0) {
				throw new AssertionError("a light blow knocked the zombie down");
			}
			context.waitTicks(20);

			// The zombie, thrown sideways by a blast it lives through.
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				LivingEntity zombie = (LivingEntity) level.getEntity(id[0]);
				zombie.setDeltaMovement(0.5, 0.2, 0.0);
				zombie.hurtServer(level, level.damageSources().explosion(null, null), 9.0F);
				if (!zombie.isAlive()) {
					throw new AssertionError("the blast was meant not to kill the zombie");
				}
			});
			boolean wentDown = false;
			boolean gotUp = false;
			boolean done = false;
			double farthest = 0.0;
			double lowBox = 99.0;
			boolean pickable = false;
			// Smooth: no physics tick in which the torso moves farther than a body
			// flies in one (the old hard pull back to the entity jerked it).
			context.runOnClient(client -> LiveRagdolls.biggestStep(true));
			long reportsBefore = context.computeOnClient(client -> LiveRagdolls.reports());
			for (int t = 0; t < 240 && !done; t++) {
				context.waitTicks(1);
				int stage = context.computeOnClient(client -> {
					Entity e = client.level.getEntity(id[0]);
					return e == null ? -2 : LiveRagdolls.stage(e);
				});
				if (stage == 0 && !wentDown) {
					wentDown = true;
				}
				if (stage == 0) {
					// Once it has come to rest the mob is put where it lies, and its box lies down with it.
					farthest = Math.max(farthest, context.computeOnClient(client ->
							LiveRagdolls.apart(client.level.getEntity(id[0]))));
					lowBox = Math.min(lowBox, context.computeOnClient(client ->
							(double) client.level.getEntity(id[0]).getBbHeight()));
					// Where it lies, settled, is where the crosshair finds it (to drag it, to hit it).
					pickable = context.computeOnClient(client -> LiveRagdolls.torsoPickable(client.level.getEntity(id[0])));
				}
				if (stage == 1 && !gotUp) {
					gotUp = true;
					context.takeScreenshot("rd_knock_02_zombie_getting_up");
				}
				if (wentDown && gotUp && stage == -1) {
					done = true;
				}
				if (t == 4) {
					context.takeScreenshot("rd_knock_00_zombie_thrown");
				}
				if (t == 30) {
					context.takeScreenshot("rd_knock_01_zombie_down");
				}
			}
			context.waitTicks(5);
			context.takeScreenshot("rd_knock_03_zombie_standing");
			System.out.println("[rd] zombie knockdown: down " + wentDown + ", got up " + gotUp + ", done " + done
					+ ", ragdoll led its entity by up to " + String.format("%.2f", farthest) + ", box as low as "
					+ String.format("%.2f", lowBox));
			double biggestStep = context.computeOnClient(client -> LiveRagdolls.biggestStep(false));
			System.out.println("[rd] knocked-down torso's biggest step in a tick: " + String.format("%.2f", biggestStep));
			if (biggestStep > 1.0) {
				throw new AssertionError("the flung ragdoll jumped " + biggestStep + " blocks in one tick: choppy");
			}
			if (reportsBefore == context.computeOnClient(client -> LiveRagdolls.reports())) {
				throw new AssertionError("the zombie's ragdoll came to rest and nobody told the server where");
			}
			if (!pickable) {
				throw new AssertionError("the settled zombie's body lay outside where the crosshair picks it: "
						+ "the server did not put the mob where its ragdoll came to rest");
			}
			if (lowBox > 0.6) {
				throw new AssertionError("the knocked-down zombie's hitbox never lay down: " + lowBox + " tall");
			}
			if (!wentDown) {
				throw new AssertionError("a blast the zombie lived through did not ragdoll it");
			}
			if (!gotUp || !done) {
				throw new AssertionError("the knocked-down zombie never got back up (up " + gotUp + ", done " + done + ")");
			}

			// The player.
			double standingEye = context.computeOnClient(client ->
					client.gameRenderer.mainCamera().position().y - client.player.getY());
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setDeltaMovement(-0.6, 0.3, 0.2);
				player.hurtMarked = true;
				player.invulnerableTime = 0;
				player.hurtServer(player.level(), player.level().damageSources().explosion(null, null), 9.0F);
			});
			boolean rode = false;
			double lowestEye = Double.POSITIVE_INFINITY;
			boolean back = false;
			for (int t = 0; t < 260 && !back; t++) {
				context.waitTicks(1);
				boolean riding = context.computeOnClient(client -> RagdollView.active());
				rode |= riding;
				double eye = context.computeOnClient(client ->
						client.gameRenderer.mainCamera().position().y - client.player.getY());
				lowestEye = Math.min(lowestEye, eye);
				if (t == 25) {
					context.takeScreenshot("rd_knock_10_player_down_first_person");
				}
				if (rode && !riding && t > 10) {
					back = true;
				}
			}
			context.waitTicks(10);
			context.takeScreenshot("rd_knock_11_player_back_up");
			double eyeAfter = context.computeOnClient(client ->
					client.gameRenderer.mainCamera().position().y - client.player.getY());
			System.out.println("[rd] player knockdown: rode " + rode + ", lowest eye " + lowestEye + ", back " + back
					+ ", eye after " + eyeAfter + " (standing " + standingEye + ")");
			if (!rode) {
				throw new AssertionError("knocked down, and the camera never rode the ragdoll");
			}
			if (lowestEye > 0.9) {
				throw new AssertionError("knocked down, and the view never came near the floor: " + lowestEye);
			}
			if (!back) {
				throw new AssertionError("the player never got back up");
			}
			if (Math.abs(eyeAfter - standingEye) > 0.2) {
				throw new AssertionError("up again, and the eye is not where a standing eye is: " + eyeAfter
						+ " against " + standingEye);
			}
			if (!context.computeOnClient(client -> client.player.isAlive())) {
				throw new AssertionError("the blast was meant not to kill the player");
			}
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}
}
