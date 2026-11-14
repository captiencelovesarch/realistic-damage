package dev.captience.realisticdamage.client.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.RemoteCorpses;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.net.BodySnapshot;
import dev.captience.realisticdamage.ragdoll.CorpseLaunch;
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
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * A dead body still takes body damage (2.0), in a real client. A zombie is
 * killed in the air twenty-odd blocks up, falling; it reaches the ground dead;
 * the server, following its own copy of the fall, takes a leg off it; and the
 * ragdoll this client is drawing drops that same leg. A second zombie killed
 * where it stands, beside the first, keeps everything.
 *
 * <p>The screenshots are for a person to judge; the assertions say the limb
 * the server took is the limb the client's ragdoll no longer has.
 */
public class CorpseImpactClientGameTest implements FabricClientGameTest {
	private static final BodyPart[] LIMBS = {BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG};

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
				// Midnight: nothing burns. No spawning: nothing wanders into the scene.
				server.getCommands().performPrefixedCommand(source, "time set midnight");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				// A stone apron to land on, whatever the world put here, and clear air over it.
				var o = player.blockPosition();
				for (int x = -5; x <= 5; x++) {
					for (int z = 2; z <= 12; z++) {
						player.level().setBlockAndUpdate(o.offset(x, -1, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
						for (int y = 0; y <= 30; y++) {
							player.level().setBlockAndUpdate(o.offset(x, y, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
						}
					}
				}
			});
			double px = context.computeOnClient(client -> client.player.getX());
			double py = context.computeOnClient(client -> client.player.getY());
			double pz = context.computeOnClient(client -> client.player.getZ());
			double bx = Math.floor(px) + 0.5;
			double by = Math.floor(py);
			double bz = Math.floor(pz) + 7.5;
			List<Integer> ids = new ArrayList<>();
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				// The faller, high over the apron, and the one that stays on the ground.
				double[][] at = {{bx - 1.5, by + 22.0, bz}, {bx + 2.5, by, bz}};
				for (double[] p : at) {
					Zombie body = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
					body.snapTo(p[0], p[1], p[2], 180.0F, 0.0F);
					body.setYHeadRot(180.0F);
					body.yBodyRot = 180.0F;
					// It hangs where it is put until it is killed: no AI, no physics of its own.
					body.setNoAi(true);
					body.setBaby(false);
					body.setPersistenceRequired();
					for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
						body.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
					}
					level.addFreshEntity(body);
					RealisticDamageData data = RDAttachments.get(body);
					data.reset();
					RDAttachments.pushSnapshot(body);
					MobAnatomy.track(body);
					ids.add(body.getId());
				}
			});
			int faller = ids.get(0);
			int stander = ids.get(1);
			context.runOnClient(client -> {
				client.player.setPos(bx, py, bz - 7.0);
				client.player.setYRot(0.0F);
				client.player.setXRot(-8.0F);
			});
			context.waitTicks(30);
			context.takeScreenshot("rd_corpse_impact_00_before");

			// Both die of something with no direction and no blast. The faller is on
			// its way down at thirty blocks a second when it does.
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				LivingEntity falling = (LivingEntity) level.getEntity(faller);
				falling.setDeltaMovement(0.0, -1.5, 0.0);
				falling.hurtServer(level, level.damageSources().magic(), 1000.0F);
				LivingEntity standing = (LivingEntity) level.getEntity(stander);
				standing.hurtServer(level, level.damageSources().magic(), 1000.0F);
			});
			context.waitTicks(4);
			List<String> failures = new ArrayList<>();
			int[] inTheAir = new int[LIMBS.length];
			for (int i = 0; i < LIMBS.length; i++) {
				int part = CorpseLaunch.FROM_BODY_PART[LIMBS[i].ordinal()];
				inTheAir[i] = context.computeOnClient(client -> {
					Entity e = client.level.getEntity(faller);
					return e == null ? -2 : RemoteCorpses.ragdollPart(e, part);
				});
				if (inTheAir[i] != 1) {
					failures.add("in the air the ragdoll's " + LIMBS[i].id() + " reads " + inTheAir[i] + ", not whole");
				}
			}
			context.takeScreenshot("rd_corpse_impact_01_falling");

			// Down, and long enough after for the server's verdict to have arrived.
			context.waitTicks(36);
			context.takeScreenshot("rd_corpse_impact_02_landed");
			int serverLost = singleplayer.getServer().computeOnServer(server -> {
				LivingEntity body = (LivingEntity) server.getPlayerList().getPlayers().getFirst().level().getEntity(faller);
				return body == null ? -1 : BodySnapshot.of(RDAttachments.get(body)).destroyedMask();
			});
			int standerLost = singleplayer.getServer().computeOnServer(server -> {
				LivingEntity body = (LivingEntity) server.getPlayerList().getPlayers().getFirst().level().getEntity(stander);
				RealisticDamageData data = body == null ? null : body.getAttached(RDAttachments.DATA);
				return data == null ? 0 : BodySnapshot.of(data).destroyedMask() | BodySnapshot.of(data).brokenMask() << 8;
			});
			int clientLost = context.computeOnClient(client -> {
				Entity e = client.level.getEntity(faller);
				BodySnapshot snapshot = e == null ? null : e.getAttached(RDAttachments.SNAPSHOT);
				return snapshot == null ? -1 : snapshot.destroyedMask();
			});
			StringBuilder report = new StringBuilder("[rd] corpse impact: server lost mask " + serverLost + ", client snapshot "
					+ clientLost + ", stander " + standerLost);
			int gone = 0;
			for (BodyPart limb : LIMBS) {
				int part = CorpseLaunch.FROM_BODY_PART[limb.ordinal()];
				int has = context.computeOnClient(client -> {
					Entity e = client.level.getEntity(faller);
					return e == null ? -2 : RemoteCorpses.ragdollPart(e, part);
				});
				boolean lost = serverLost >= 0 && (serverLost & (1 << limb.ordinal())) != 0;
				report.append(", ").append(limb.id()).append(lost ? " lost" : " kept").append(" ragdoll ").append(has);
				if (lost) {
					gone++;
					if (has != 0) {
						failures.add("the server took the " + limb.id() + " and this client's ragdoll still has it (" + has + ")");
					}
				} else if (has != 1) {
					failures.add("the server left the " + limb.id() + " and this client's ragdoll reads " + has);
				}
			}
			int limbs = context.computeOnClient(client -> {
				int n = 0;
				for (Entity e : client.level.entitiesForRendering()) {
					// On the ground by the faller, not up where it died.
					n += e instanceof SeveredLimb && e.getY() < by + 4.0 ? 1 : 0;
				}
				return n;
			});
			report.append(", severed limbs on the ground ").append(limbs);
			System.out.println(report);
			if (serverLost <= 0 || gone == 0) {
				failures.add("a body killed in the air and landing at over forty blocks a second lost no limb");
			}
			if (serverLost != clientLost) {
				failures.add("the client's snapshot of the body does not match the server's");
			}
			if (gone > 0 && limbs < gone) {
				failures.add(gone + " limbs came off and " + limbs + " are lying on the ground");
			}
			if (standerLost != 0) {
				failures.add("the zombie killed where it stood lost or broke something: " + standerLost);
			}
			context.waitTicks(20);
			context.takeScreenshot("rd_corpse_impact_03_at_rest");
			if (!failures.isEmpty()) {
				throw new AssertionError(String.join("; ", failures) + " (" + report + ")");
			}
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}
}
