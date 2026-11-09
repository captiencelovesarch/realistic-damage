package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.RemoteCorpses;
import dev.captience.realisticdamage.damage.Corpses;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.systems.MobAnatomy;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Active ragdolls, phase 0, in a real client: five bodies die in a little
 * scene built for it — off the top of a staircase, at the edge of a ledge, in
 * the open to a blast, a skeleton shot in the head, and a one-legged mannequin
 * — and each must end up lying on the blocks rather than in them, in one
 * piece. The screenshots (with the physics boxes drawn on some) are for a
 * person to judge how it looks; the assertions only say it is not broken.
 */
public class RagdollClientGameTest implements FabricClientGameTest {
	private record Victim(String name, double x, double y, double z, float yaw, Corpses.Script script) {}

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

			// The scene, in front of the player (who looks along +Z).
			List<Victim> victims = new ArrayList<>();
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				BlockPos o = player.blockPosition();
				// A staircase going down toward +X: columns 3, 2, 1 high.
				for (int k = 0; k < 3; k++) {
					for (int y = 0; y < 3 - k; y++) {
						for (int z = 6; z <= 8; z++) {
							level.setBlockAndUpdate(o.offset(-6 + k, y, z), Blocks.STONE.defaultBlockState());
						}
					}
				}
				// A ledge two blocks high, its edge at x = +4.
				for (int x = 2; x <= 3; x++) {
					for (int y = 0; y < 2; y++) {
						for (int z = 6; z <= 8; z++) {
							level.setBlockAndUpdate(o.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
						}
					}
				}
			});
			context.waitTicks(10);

			double px = context.computeOnClient(client -> client.player.getX());
			double py = context.computeOnClient(client -> client.player.getY());
			double pz = context.computeOnClient(client -> client.player.getZ());
			double bx = Math.floor(px);
			double by = Math.floor(py);
			double bz = Math.floor(pz);
			// Facing +X is yaw -90.
			victims.add(new Victim("stairs", bx - 5.5, by + 3.0, bz + 7.5, -90.0F,
					new Corpses.Script(DeathKind.BLOW, BodyPart.TORSO, new Vec3(1.0, 0.0, 0.0), 0.25, 0.5)));
			victims.add(new Victim("ledge", bx + 3.6, by + 2.0, bz + 7.5, -90.0F,
					new Corpses.Script(DeathKind.FADE, null, new Vec3(1.0, 0.0, 0.0), 0.0, 0.2)));
			victims.add(new Victim("blast", bx + 0.5, by, bz + 11.5, 0.0F,
					new Corpses.Script(DeathKind.EXPLOSION, BodyPart.LEFT_LEG, new Vec3(0.4, 0.0, -1.0), 0.4, 0.8)));
			victims.add(new Victim("headshot", bx - 1.5, by, bz + 5.5, 180.0F,
					new Corpses.Script(DeathKind.PROJECTILE, BodyPart.HEAD, new Vec3(0.0, 0.0, 1.0), 0.35, 0.6)));
			victims.add(new Victim("one_leg", bx + 0.7, by, bz + 4.5, 160.0F,
					new Corpses.Script(DeathKind.BLOW, BodyPart.RIGHT_ARM, new Vec3(0.3, 0.0, 1.0), 0.2, 0.4)));

			List<Integer> ids = new ArrayList<>();
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				for (Victim v : victims) {
					EntityType<? extends LivingEntity> type = switch (v.name()) {
						case "headshot" -> EntityTypes.SKELETON;
						case "one_leg" -> EntityTypes.MANNEQUIN;
						default -> EntityTypes.ZOMBIE;
					};
					LivingEntity body = type.create(level, EntitySpawnReason.COMMAND);
					if (body == null) {
						throw new AssertionError("could not create " + type);
					}
					body.snapTo(v.x(), v.y(), v.z(), v.yaw(), 0.0F);
					body.setYHeadRot(v.yaw());
					body.yBodyRot = v.yaw();
					if (body instanceof Mob mob) {
						mob.setNoAi(true);
						mob.setPersistenceRequired();
					}
					level.addFreshEntity(body);
					RealisticDamageData data = RDAttachments.get(body);
					data.reset();
					if (v.name().equals("one_leg")) {
						data.part(BodyPart.LEFT_LEG).setDestroyed(true);
					}
					RDAttachments.pushSnapshot(body);
					MobAnatomy.track(body);
					ids.add(body.getId());
				}
			});
			// Camera: back and up, looking at the scene.
			context.runOnClient(client -> {
				client.player.setPos(px, py, pz - 3.0);
				client.player.setYRot(0.0F);
				client.player.setXRot(22.0F);
			});
			context.waitTicks(30);
			context.takeScreenshot("rd_ragdoll_00_standing");

			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				for (int i = 0; i < victims.size(); i++) {
					Entity e = level.getEntity(ids.get(i));
					if (!(e instanceof LivingEntity body)) {
						throw new AssertionError("victim " + victims.get(i).name() + " is gone before its death");
					}
					Corpses.script(body, victims.get(i).script());
					body.invulnerableTime = 0;
					body.kill(level);
				}
			});
			context.waitTicks(2);
			int ragdolls = context.computeOnClient(client -> RemoteCorpses.ragdollCount());
			if (ragdolls != victims.size()) {
				throw new AssertionError("expected " + victims.size() + " ragdolls, got " + ragdolls
						+ " (and " + context.computeOnClient(client -> RemoteCorpses.count()) + " bodies in all)");
			}
			context.waitTicks(4);
			context.takeScreenshot("rd_ragdoll_01_falling");
			long linesBefore = context.computeOnClient(client -> dev.captience.realisticdamage.client.RagdollDebug.lines());
			context.runOnClient(client -> setDebug(true));
			context.waitTicks(4);
			context.takeScreenshot("rd_ragdoll_02_falling_boxes");
			if (context.computeOnClient(client -> dev.captience.realisticdamage.client.RagdollDebug.lines()) <= linesBefore) {
				throw new AssertionError("/rdragdoll debug drew no boxes");
			}
			context.runOnClient(client -> setDebug(false));
			context.waitTicks(50);
			context.takeScreenshot("rd_ragdoll_03_landed");
			context.runOnClient(client -> setDebug(true));
			context.waitTicks(1);
			context.takeScreenshot("rd_ragdoll_04_landed_boxes");
			context.runOnClient(client -> setDebug(false));

			StringBuilder report = new StringBuilder();
			List<String> failures = new ArrayList<>();
			for (int i = 0; i < victims.size(); i++) {
				Victim v = victims.get(i);
				int id = ids.get(i);
				double[] check = context.computeOnClient(client -> {
					Entity e = client.level.getEntity(id);
					return e == null ? null : RemoteCorpses.ragdollCheck(e);
				});
				if (check == null) {
					failures.add(v.name() + ": no ragdoll by the time it should have landed");
					continue;
				}
				report.append(String.format(java.util.Locale.ROOT,
						"[rd] ragdoll %s: inside %.3f, joint gap %.3f, lean %.1f, at rest %s, torso y %.2f (died at %.2f)%n",
						v.name(), check[0], check[1], check[2], check[3] > 0.5, check[4], v.y()));
				if (check[0] > 0.06) {
					failures.add(v.name() + " ended " + check[0] + " inside a block");
				}
				if (check[1] > 0.05) {
					failures.add(v.name() + " came apart at a joint by " + check[1]);
				}
				if (check[2] < 35.0) {
					failures.add(v.name() + " is still standing (lean " + check[2] + ")");
				}
			}
			System.out.print(report);
			double stairsY = victims.getFirst().y();
			double stairsTorso = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(ids.getFirst());
				double[] c = e == null ? null : RemoteCorpses.ragdollCheck(e);
				return c == null ? Double.NaN : c[4];
			});
			if (!(stairsTorso < stairsY + 0.4)) {
				failures.add("the body pushed off the top step did not go down it (torso y " + stairsTorso
						+ ", top step " + stairsY + ")");
			}
			if (!failures.isEmpty()) {
				throw new AssertionError(String.join("; ", failures));
			}

			// Closer looks for the person judging it.
			context.runOnClient(client -> {
				client.player.setPos(px - 3.5, py + 1.0, pz + 2.5);
				client.player.setYRot(-20.0F);
				client.player.setXRot(30.0F);
			});
			context.waitTicks(5);
			context.takeScreenshot("rd_ragdoll_05_stairs_close");
			context.runOnClient(client -> {
				client.player.setPos(px + 2.5, py + 1.0, pz + 3.0);
				client.player.setYRot(25.0F);
				client.player.setXRot(30.0F);
			});
			context.waitTicks(5);
			context.takeScreenshot("rd_ragdoll_06_ledge_close");

			muscles(context, singleplayer, px, py, pz);
			reflexes(context, singleplayer, px, py, pz);
			bodiesStay(context, singleplayer, px, py, pz);
			animals(context, singleplayer, px, py, pz);
		}
	}

	/**
	 * Phase 4: bodies that are not humanoids, from their own models: a cow, a
	 * pig, a spider, a chicken, a creeper and a villager. Each is ragdolled by
	 * the any-model rig, lands on the blocks in one piece, and ends over on its
	 * side (or toppled, on two legs).
	 */
	private static void animals(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double px, double py, double pz) {
		double bx = Math.floor(px) + 0.5;
		double by = Math.floor(py);
		double bz = Math.floor(pz) + 22.0;
		List<EntityType<? extends LivingEntity>> types = List.of(EntityTypes.COW, EntityTypes.PIG, EntityTypes.SPIDER,
				EntityTypes.CHICKEN, EntityTypes.CREEPER, EntityTypes.VILLAGER);
		List<Integer> ids = new ArrayList<>();
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			for (int i = 0; i < types.size(); i++) {
				LivingEntity body = types.get(i).create(level, EntitySpawnReason.COMMAND);
				body.snapTo(bx - 7.5 + i * 3.0, by, bz, 90.0F, 0.0F);
				body.setYHeadRot(90.0F);
				body.yBodyRot = 90.0F;
				if (body instanceof Mob mob) {
					mob.setNoAi(true);
					mob.setPersistenceRequired();
				}
				level.addFreshEntity(body);
				RDAttachments.get(body).reset();
				RDAttachments.pushSnapshot(body);
				MobAnatomy.track(body);
				ids.add(body.getId());
			}
		});
		context.runOnClient(client -> {
			client.player.setPos(bx, py + 2.0, bz - 9.0);
			client.player.setYRot(0.0F);
			client.player.setXRot(15.0F);
		});
		context.waitTicks(30);
		context.takeScreenshot("rd_ragdoll_40_animals_standing");
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			for (int id : ids) {
				LivingEntity body = (LivingEntity) level.getEntity(id);
				Corpses.script(body, new Corpses.Script(DeathKind.BLOW, BodyPart.TORSO, new Vec3(0.0, 0.0, 1.0), 0.15, 0.3));
				body.invulnerableTime = 0;
				body.kill(level);
			}
		});
		context.waitTicks(2);
		List<String> failures = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			int id = ids.get(i);
			boolean generic = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(id);
				return e != null && RemoteCorpses.generic(e);
			});
			if (!generic) {
				failures.add(types.get(i).toShortString() + " was not ragdolled by the any-model rig");
			}
		}
		context.waitTicks(8);
		context.takeScreenshot("rd_ragdoll_41_animals_falling");
		context.waitTicks(50);
		context.takeScreenshot("rd_ragdoll_42_animals_down");
		StringBuilder report = new StringBuilder();
		for (int i = 0; i < ids.size(); i++) {
			int id = ids.get(i);
			String name = types.get(i).toShortString();
			double[] check = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(id);
				return e == null ? null : RemoteCorpses.ragdollCheck(e);
			});
			if (check == null) {
				failures.add(name + ": no ragdoll by the time it should have landed");
				continue;
			}
			report.append(String.format(java.util.Locale.ROOT, "[rd] animal %s: inside %.3f, joint gap %.3f, lean %.1f, at rest %s%n",
					name, check[0], check[1], check[2], check[3] > 0.5));
			if (check[0] > 0.08) {
				failures.add(name + " ended " + check[0] + " inside a block");
			}
			if (check[1] > 0.06) {
				failures.add(name + " came apart at a joint by " + check[1]);
			}
			if (check[2] < 45.0) {
				failures.add(name + " is still standing (lean " + check[2] + ")");
			}
		}
		System.out.print(report);
		if (!failures.isEmpty()) {
			throw new AssertionError(String.join("; ", failures));
		}
	}

	/**
	 * Phase 4: the server removes a dead body after five seconds; this client
	 * keeps it, lying where it fell and drawn as it looked, for as long as
	 * Bodies Stay says, then lets it sink away.
	 */
	private static void bodiesStay(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double px, double py, double pz) {
		context.runOnClient(client -> ClientEffectsConfig.get().bodiesStay = 30);
		double bx = Math.floor(px) + 0.5;
		double by = Math.floor(py);
		double bz = Math.floor(pz) - 8.5;
		int[] id = new int[1];
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			LivingEntity body = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			body.snapTo(bx, by, bz, 0.0F, 0.0F);
			((Mob) body).setNoAi(true);
			body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
			level.addFreshEntity(body);
			RDAttachments.get(body).reset();
			RDAttachments.pushSnapshot(body);
			MobAnatomy.track(body);
			id[0] = body.getId();
		});
		context.runOnClient(client -> {
			client.player.setPos(bx, py + 1.0, bz + 4.0);
			client.player.setYRot(180.0F);
			client.player.setXRot(35.0F);
		});
		context.waitTicks(20);
		int orphansBefore = context.computeOnClient(client -> RemoteCorpses.orphanCount());
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			LivingEntity body = (LivingEntity) level.getEntity(id[0]);
			Corpses.script(body, new Corpses.Script(DeathKind.BLOW, BodyPart.TORSO, new Vec3(1.0, 0.0, 0.0), 0.2, 0.4));
			body.invulnerableTime = 0;
			body.kill(level);
		});
		context.waitTicks(125);
		boolean entityGone = context.computeOnClient(client -> client.level.getEntity(id[0]) == null);
		if (!entityGone) {
			throw new AssertionError("the server never removed the dead zombie; the test proves nothing");
		}
		int orphans = context.computeOnClient(client -> RemoteCorpses.orphanCount());
		long draws = context.computeOnClient(client -> RemoteCorpses.orphanDraws());
		context.waitTicks(5);
		long drawsLater = context.computeOnClient(client -> RemoteCorpses.orphanDraws());
		context.takeScreenshot("rd_ragdoll_30_kept_body");
		System.out.println("[rd] kept bodies " + orphansBefore + " -> " + orphans + ", draws " + draws + " -> " + drawsLater);
		if (orphans <= orphansBefore) {
			throw new AssertionError("the body went with the entity: kept bodies " + orphansBefore + " -> " + orphans);
		}
		if (drawsLater <= draws) {
			throw new AssertionError("a kept body is not being drawn");
		}
		// A shorter stay: the kept bodies sink and are gone.
		context.runOnClient(client -> ClientEffectsConfig.get().bodiesStay = 5);
		context.waitTicks(45);
		context.takeScreenshot("rd_ragdoll_31_bodies_sunk");
		int after = context.computeOnClient(client -> RemoteCorpses.orphanCount());
		context.runOnClient(client -> ClientEffectsConfig.get().bodiesStay = 30);
		if (after != 0) {
			throw new AssertionError(after + " kept bodies outlived a five-second stay");
		}
	}

	/**
	 * Phase 3: four zombies whose deaths each leave a reflex: burned (writhes),
	 * electrocuted (convulses), blasted (arms up over the face) and struck in
	 * the gut while standing (staggers, a hand to the belly, then the brace).
	 */
	private static void reflexes(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double px, double py, double pz) {
		double bx = Math.floor(px) - 14.0;
		double by = Math.floor(py);
		double bz = Math.floor(pz) + 6.0;
		List<Victim> row = List.of(
				new Victim("burned", bx - 3.5, by, bz + 0.5, 180.0F,
						new Corpses.Script(DeathKind.FIRE, null, new Vec3(0.0, 0.0, 1.0), 0.0, 0.3)),
				new Victim("shocked", bx - 1.0, by, bz + 0.5, 180.0F,
						new Corpses.Script(DeathKind.SHOCK, null, new Vec3(0.0, 0.0, 1.0), 0.0, 0.3)),
				new Victim("blasted", bx + 1.5, by, bz + 0.5, 180.0F,
						new Corpses.Script(DeathKind.EXPLOSION, BodyPart.LEFT_LEG, new Vec3(0.3, 0.0, 1.0), 0.3, 0.5)),
				new Victim("gut", bx + 4.0, by, bz + 0.5, 180.0F,
						new Corpses.Script(DeathKind.BLOW, BodyPart.TORSO, new Vec3(0.0, 0.0, 1.0), 0.2, 0.3)));
		List<java.util.EnumSet<dev.captience.realisticdamage.ragdoll.Behaviour.Reaction>> expect = List.of(
				java.util.EnumSet.of(dev.captience.realisticdamage.ragdoll.Behaviour.Reaction.WRITHE),
				java.util.EnumSet.of(dev.captience.realisticdamage.ragdoll.Behaviour.Reaction.CONVULSE),
				java.util.EnumSet.of(dev.captience.realisticdamage.ragdoll.Behaviour.Reaction.PROTECT_HEAD),
				java.util.EnumSet.of(dev.captience.realisticdamage.ragdoll.Behaviour.Reaction.STAGGER,
						dev.captience.realisticdamage.ragdoll.Behaviour.Reaction.CLUTCH,
						dev.captience.realisticdamage.ragdoll.Behaviour.Reaction.BRACE));
		List<Integer> ids = new ArrayList<>();
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			for (Victim v : row) {
				LivingEntity body = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				body.snapTo(v.x(), v.y(), v.z(), v.yaw(), 0.0F);
				body.setYHeadRot(v.yaw());
				body.yBodyRot = v.yaw();
				((Mob) body).setNoAi(true);
				((Mob) body).setPersistenceRequired();
				body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
				level.addFreshEntity(body);
				RealisticDamageData data = RDAttachments.get(body);
				data.reset();
				RDAttachments.pushSnapshot(body);
				MobAnatomy.track(body);
				ids.add(body.getId());
			}
		});
		context.runOnClient(client -> {
			client.player.setPos(bx + 0.25, py + 0.5, bz - 6.0);
			client.player.setYRot(0.0F);
			client.player.setXRot(12.0F);
		});
		context.waitTicks(30);
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			for (int i = 0; i < row.size(); i++) {
				LivingEntity body = (LivingEntity) level.getEntity(ids.get(i));
				Corpses.script(body, row.get(i).script());
				body.invulnerableTime = 0;
				body.kill(level);
			}
		});
		context.waitTicks(2);
		List<String> failures = new ArrayList<>();
		for (int i = 0; i < row.size(); i++) {
			int id = ids.get(i);
			var reactions = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(id);
				return e == null ? null : RemoteCorpses.ragdollReactions(e);
			});
			if (reactions == null || !reactions.containsAll(expect.get(i))) {
				failures.add(row.get(i).name() + " died with " + reactions + ", expected at least " + expect.get(i));
			}
		}
		context.waitTicks(3);
		context.takeScreenshot("rd_ragdoll_20_reflexes_quarter_second");
		context.waitTicks(7);
		context.takeScreenshot("rd_ragdoll_21_reflexes_half_second");
		context.waitTicks(20);
		context.takeScreenshot("rd_ragdoll_22_reflexes_one_and_a_half");
		context.waitTicks(30);
		context.takeScreenshot("rd_ragdoll_23_reflexes_three_seconds");
		StringBuilder report = new StringBuilder();
		for (int i = 0; i < row.size(); i++) {
			Victim v = row.get(i);
			int id = ids.get(i);
			double[] check = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(id);
				return e == null ? null : RemoteCorpses.ragdollCheck(e);
			});
			if (check == null) {
				failures.add(v.name() + ": no ragdoll by the time it should have landed");
				continue;
			}
			report.append(String.format(java.util.Locale.ROOT,
					"[rd] reflexes %s: inside %.3f, joint gap %.3f, lean %.1f, at rest %s%n",
					v.name(), check[0], check[1], check[2], check[3] > 0.5));
			if (check[0] > 0.06) {
				failures.add(v.name() + " ended " + check[0] + " inside a block");
			}
			if (check[1] > 0.06) {
				failures.add(v.name() + " came apart at a joint by " + check[1]);
			}
			if (check[2] < 35.0) {
				failures.add(v.name() + " is still standing (lean " + check[2] + ")");
			}
		}
		System.out.print(report);
		if (!failures.isEmpty()) {
			throw new AssertionError(String.join("; ", failures));
		}
	}

	/**
	 * Phase 2: four zombies in a row, facing the camera, each dying a way its
	 * muscles should show: shot in the head (strings cut, drops and folds at
	 * once), bled out (sinks, legs first), frozen (goes over straight), and
	 * struck with a broken arm (that arm swings free).
	 */
	private static void muscles(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			double px, double py, double pz) {
		double bx = Math.floor(px) + 12.0;
		double by = Math.floor(py);
		double bz = Math.floor(pz) + 6.0;
		List<Victim> row = List.of(
				new Victim("headshot", bx - 3.5, by, bz + 0.5, 180.0F,
						new Corpses.Script(DeathKind.PROJECTILE, BodyPart.HEAD, new Vec3(0.0, 0.0, 1.0), 0.35, 0.5)),
				new Victim("bleed_out", bx - 1.0, by, bz + 0.5, 180.0F,
						new Corpses.Script(DeathKind.FADE, null, new Vec3(0.0, 0.0, 1.0), 0.0, 0.2)),
				new Victim("frozen", bx + 1.5, by, bz + 0.5, 180.0F,
						new Corpses.Script(DeathKind.FREEZE, null, new Vec3(0.0, 0.0, -1.0), 0.0, 0.3)),
				new Victim("broken_arm", bx + 4.0, by, bz + 0.5, 180.0F,
						new Corpses.Script(DeathKind.BLOW, BodyPart.TORSO, new Vec3(0.0, 0.0, 1.0), 0.25, 0.3)));
		List<Integer> ids = new ArrayList<>();
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			for (Victim v : row) {
				LivingEntity body = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				body.snapTo(v.x(), v.y(), v.z(), v.yaw(), 0.0F);
				body.setYHeadRot(v.yaw());
				body.yBodyRot = v.yaw();
				((Mob) body).setNoAi(true);
				((Mob) body).setPersistenceRequired();
				// Out of the sun, or every one of them burns in the pictures.
				body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
				level.addFreshEntity(body);
				RealisticDamageData data = RDAttachments.get(body);
				data.reset();
				if (v.name().equals("broken_arm")) {
					data.part(BodyPart.LEFT_ARM).setBroken(true);
				}
				RDAttachments.pushSnapshot(body);
				MobAnatomy.track(body);
				ids.add(body.getId());
			}
		});
		context.runOnClient(client -> {
			client.player.setPos(bx + 0.25, py + 0.5, bz - 6.0);
			client.player.setYRot(0.0F);
			client.player.setXRot(12.0F);
		});
		context.waitTicks(30);
		context.takeScreenshot("rd_ragdoll_10_muscles_standing");
		singleplayer.getServer().runOnServer(server -> {
			ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
			for (int i = 0; i < row.size(); i++) {
				LivingEntity body = (LivingEntity) level.getEntity(ids.get(i));
				Corpses.script(body, row.get(i).script());
				body.invulnerableTime = 0;
				body.kill(level);
			}
		});
		context.waitTicks(2);
		// The right muscles reached this client.
		List<String> failures = new ArrayList<>();
		for (int i = 0; i < row.size(); i++) {
			String name = row.get(i).name();
			int id = ids.get(i);
			String muscles = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(id);
				var m = e == null ? null : RemoteCorpses.ragdollMuscles(e);
				return m == null ? "none" : m.stringsCut + "," + m.limb[3] + "," + m.floor;
			});
			String[] f = muscles.split(",");
			if (f.length < 3) {
				failures.add(name + " is not a ragdoll");
				continue;
			}
			boolean cut = Boolean.parseBoolean(f[0]);
			double leftArm = Double.parseDouble(f[1]);
			double floor = Double.parseDouble(f[2]);
			switch (name) {
				case "headshot" -> {
					if (!cut) {
						failures.add("a headshot did not cut the strings");
					}
				}
				case "bleed_out" -> {
					if (cut) {
						failures.add("a conscious bleed-out dropped like a headshot");
					}
				}
				case "frozen" -> {
					if (floor < 1.0) {
						failures.add("a frozen body is not stiff");
					}
				}
				case "broken_arm" -> {
					if (leftArm != 0.0) {
						failures.add("the broken arm kept its muscle (" + leftArm + ")");
					}
				}
				default -> {}
			}
		}
		context.waitTicks(4);
		context.takeScreenshot("rd_ragdoll_11_muscles_falling");
		context.waitTicks(6);
		context.takeScreenshot("rd_ragdoll_12_muscles_falling_late");
		context.waitTicks(50);
		context.takeScreenshot("rd_ragdoll_13_muscles_landed");
		StringBuilder report = new StringBuilder();
		for (int i = 0; i < row.size(); i++) {
			Victim v = row.get(i);
			int id = ids.get(i);
			double[] check = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(id);
				return e == null ? null : RemoteCorpses.ragdollCheck(e);
			});
			if (check == null) {
				failures.add(v.name() + ": no ragdoll by the time it should have landed");
				continue;
			}
			report.append(String.format(java.util.Locale.ROOT,
					"[rd] muscles %s: inside %.3f, joint gap %.3f, lean %.1f, at rest %s%n",
					v.name(), check[0], check[1], check[2], check[3] > 0.5));
			if (check[0] > 0.06) {
				failures.add(v.name() + " ended " + check[0] + " inside a block");
			}
			if (check[1] > 0.05) {
				failures.add(v.name() + " came apart at a joint by " + check[1]);
			}
			if (check[2] < 35.0) {
				failures.add(v.name() + " is still standing (lean " + check[2] + ")");
			}
		}
		System.out.print(report);
		if (!failures.isEmpty()) {
			throw new AssertionError(String.join("; ", failures));
		}
	}

	private static void setDebug(boolean on) {
		try {
			var f = Class.forName("dev.captience.realisticdamage.client.RagdollDebug").getDeclaredField("enabled");
			f.setAccessible(true);
			f.setBoolean(null, on);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}
}
