package dev.captience.realisticdamage.client.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.RemoteCorpses;
import dev.captience.realisticdamage.client.StiffNeck;
import dev.captience.realisticdamage.damage.BrokenNeck;
import dev.captience.realisticdamage.ragdoll.Muscles;
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
import net.minecraft.world.level.block.Blocks;

/**
 * Necks (2.0), in a real client.
 *
 * <p>First the stiff neck: with whiplash, a quarter turn asked for all at
 * once gets only the neck's give, and hurts; the same turn on a sound neck
 * goes through whole; a turn the mod makes for you (a blow's kick) is never
 * resisted; and "Stiff Neck: Off" lets the turn through but still hurts.
 *
 * <p>Then a broken one: a skeleton and a zombie whose necks snap in front of
 * the camera, and a limp zombie dropped head first onto stone. The ragdolls
 * must come down with their necks loose; the screenshots are for a person to
 * judge how that reads.
 */
public class NeckClientGameTest implements FabricClientGameTest {
	/** A quarter turn, in Entity.turn units (0.15 of a degree each). */
	private static final double QUARTER = 90.0 / 0.15;

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().ragdolls = ClientEffectsConfig.RagdollMode.FULL;
			ClientEffectsConfig.get().stiffNeck = true;
		});
		List<String> failures = new ArrayList<>();
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, "time set noon");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
				server.getCommands().performPrefixedCommand(source, "gamemode survival @a");
			});
			context.waitTicks(5);

			// A sound neck turns as far as it is asked.
			double free = turnOnce(context, false);
			if (Math.abs(free - 90.0) > 0.5) {
				failures.add("a sound neck turned " + free + " degrees of the 90 asked");
			}

			// Whiplash, as bad as it gets.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				RealisticDamageData data = RDAttachments.get(player);
				data.clearWhiplash();
				BrokenNeck.wrench(player, data, 1.0);
			});
			context.waitTicks(10);
			long painsBefore = context.computeOnClient(client -> StiffNeck.pains());
			double stiff = turnOnce(context, false);
			long painsAfter = context.computeOnClient(client -> StiffNeck.pains());
			context.waitTicks(2);
			context.takeScreenshot("rd_neck_00_pain_flash");
			double cap = context.computeOnClient(client -> StiffNeck.lastCap());
			System.out.println("[rd] stiff neck: sound turned 90 -> " + free + ", whiplashed -> "
					+ String.format("%.2f", stiff) + " (cap " + String.format("%.0f", cap) + " deg/s), pains "
					+ painsBefore + " -> " + painsAfter);
			if (stiff > 15.0 || stiff <= 0.0) {
				failures.add("a whiplashed neck turned " + stiff + " degrees of a 90 asked all at once");
			}
			if (painsAfter <= painsBefore) {
				failures.add("a quick quarter turn on a whiplashed neck did not hurt");
			}
			if (cap > 100.0) {
				failures.add("the cap at the worst whiplash is " + cap + " degrees a second");
			}

			// A blow's kick is not the player's own turn: the neck does not resist it.
			context.waitTicks(30);
			double kicked = turnOnce(context, true);
			if (Math.abs(kicked - 90.0) > 0.5) {
				failures.add("an involuntary turn was resisted: " + kicked + " of 90");
			}

			// Stiff Neck off: nothing slows the view, but it still hurts.
			context.runOnClient(client -> ClientEffectsConfig.get().stiffNeck = false);
			context.waitTicks(30);
			long before = context.computeOnClient(client -> StiffNeck.pains());
			double unslowed = turnOnce(context, false);
			long after = context.computeOnClient(client -> StiffNeck.pains());
			if (Math.abs(unslowed - 90.0) > 0.5) {
				failures.add("Stiff Neck off still slowed the view: " + unslowed + " of 90");
			}
			if (after <= before) {
				failures.add("Stiff Neck off took the pain away too");
			}
			context.runOnClient(client -> ClientEffectsConfig.get().stiffNeck = true);
			singleplayer.getServer().runOnServer(server ->
					RDAttachments.get(server.getPlayerList().getPlayers().getFirst()).clearWhiplash());

			// Broken ones.
			double px = context.computeOnClient(client -> client.player.getX());
			double py = context.computeOnClient(client -> client.player.getY());
			double pz = context.computeOnClient(client -> client.player.getZ());
			double bx = Math.floor(px) + 0.5;
			double by = Math.floor(py);
			double bz = Math.floor(pz) + 5.0;
			List<Integer> ids = new ArrayList<>();
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				// A stone pad to drop the third body on.
				level.setBlockAndUpdate(net.minecraft.core.BlockPos.containing(bx + 2.0, by - 1.0, bz),
						Blocks.STONE.defaultBlockState());
				ids.add(standing(level, EntityTypes.SKELETON, bx - 2.0, by, bz));
				ids.add(standing(level, EntityTypes.ZOMBIE, bx, by, bz));
			});
			context.runOnClient(client -> {
				client.player.setPos(bx, py, bz - 4.0);
				client.player.setYRot(0.0F);
				client.player.setXRot(14.0F);
			});
			context.waitTicks(20);
			context.takeScreenshot("rd_neck_01_standing");
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				for (int id : ids) {
					LivingEntity body = (LivingEntity) level.getEntity(id);
					BrokenNeck.snap(body, RDAttachments.get(body), null);
				}
				// And one out cold, dropped from sixteen blocks onto the stone, head first.
				LivingEntity dropped = (LivingEntity) EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				dropped.snapTo(bx + 2.0, by + 16.0, bz, 180.0F, 0.0F);
				((Mob) dropped).setPersistenceRequired();
				dropped.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
				level.addFreshEntity(dropped);
				RealisticDamageData data = RDAttachments.get(dropped);
				data.reset();
				data.setConsciousness(0.0);
				data.setUnconscious(true);
				RDAttachments.pushSnapshot(dropped);
				MobAnatomy.track(dropped);
				ids.add(dropped.getId());
			});
			context.waitTicks(6);
			context.takeScreenshot("rd_neck_02_snapped");
			context.waitTicks(20);
			context.takeScreenshot("rd_neck_03_down_and_landing");
			context.waitTicks(20);
			context.takeScreenshot("rd_neck_04_all_three");
			StringBuilder report = new StringBuilder("[rd] broken necks:");
			for (int i = 0; i < ids.size(); i++) {
				int id = ids.get(i);
				boolean dead = singleplayer.getServer().computeOnServer(server -> {
					Entity e = server.getPlayerList().getPlayers().getFirst().level().getEntity(id);
					return e instanceof LivingEntity living && !living.isAlive()
							&& RDAttachments.get(living).neckBroken();
				});
				double loose = context.computeOnClient(client -> {
					Entity e = client.level.getEntity(id);
					return e == null ? -2.0 : RemoteCorpses.ragdollNeckLooseness(e);
				});
				report.append(' ').append(i).append(dead ? " dead, neck " : " NOT dead of its neck, neck ")
						.append(String.format("%.2f", loose));
				if (!dead) {
					failures.add("body " + i + " did not die of a broken neck");
				}
				if (loose < Muscles.BROKEN_NECK_LOOSENESS - 1.0e-6) {
					failures.add("body " + i + "'s ragdoll neck is not loose: " + loose);
				}
			}
			System.out.println(report);
			if (!failures.isEmpty()) {
				throw new AssertionError(String.join("; ", failures) + " (" + report + ")");
			}
		} finally {
			context.runOnClient(client -> {
				ClientEffectsConfig.load();
				StiffNeck.reset();
			});
		}
	}

	/** A body standing still in front of the camera, facing it. */
	private static int standing(ServerLevel level, net.minecraft.world.entity.EntityType<? extends LivingEntity> type,
			double x, double y, double z) {
		LivingEntity body = type.create(level, EntitySpawnReason.COMMAND);
		body.snapTo(x, y, z, 180.0F, 0.0F);
		body.setYHeadRot(180.0F);
		body.yBodyRot = 180.0F;
		((Mob) body).setNoAi(true);
		((Mob) body).setPersistenceRequired();
		// A helmet keeps the undead from burning at noon, and the flames from hiding the body.
		body.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
		level.addFreshEntity(body);
		RDAttachments.get(body).reset();
		RDAttachments.pushSnapshot(body);
		MobAnatomy.track(body);
		return body.getId();
	}

	/** Asks for a quarter turn to the right all at once; how many degrees the view actually turned. */
	private static double turnOnce(ClientGameTestContext context, boolean involuntary) {
		return context.computeOnClient(client -> {
			float before = client.player.getYRot();
			if (involuntary) {
				StiffNeck.turnInvoluntarily(client.player, QUARTER, 0.0);
			} else {
				client.player.turn(QUARTER, 0.0);
			}
			return (double) (client.player.getYRot() - before);
		});
	}
}
