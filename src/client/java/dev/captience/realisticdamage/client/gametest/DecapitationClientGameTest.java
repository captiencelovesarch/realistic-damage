package dev.captience.realisticdamage.client.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.HeadView;
import dev.captience.realisticdamage.client.RagdollView;
import dev.captience.realisticdamage.client.RemoteCorpses;
import dev.captience.realisticdamage.client.SeveredHeadRenderer;
import dev.captience.realisticdamage.damage.Decapitation;
import dev.captience.realisticdamage.entity.SeveredHead;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Heads off (2.0), in a real client. A row of bodies loses its heads in front
 * of the camera (a zombie, which walks on, a skeleton, a villager, a pillager
 * and a wither skeleton, whose skull hisses and flashes); the screenshots are
 * for a person to judge, the assertions only say it is not broken: heads are
 * drawn, the corpses fall without theirs, and the zombie walks headless.
 *
 * <p>Then your own: your head comes off and the view must go with it.
 */
public class DecapitationClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> {
			ClientEffectsConfig.load();
			ClientEffectsConfig.get().ragdolls = ClientEffectsConfig.RagdollMode.FULL;
			ClientEffectsConfig.get().deathCamera = true;
			ClientEffectsConfig.get().ownRagdoll = true;
			ClientEffectsConfig.get().decapitationView = true;
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, "time set noon");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
				// A roof over the scene: at noon the undead would burn, and the flames hide the bodies.
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				var o = player.blockPosition();
				for (int x = -7; x <= 7; x++) {
					for (int z = 2; z <= 12; z++) {
						player.level().setBlockAndUpdate(o.offset(x, 6, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
					}
				}
			});
			double px = context.computeOnClient(client -> client.player.getX());
			double py = context.computeOnClient(client -> client.player.getY());
			double pz = context.computeOnClient(client -> client.player.getZ());
			double bx = Math.floor(px) + 0.5;
			double by = Math.floor(py);
			double bz = Math.floor(pz) + 6.0;
			List<EntityType<? extends LivingEntity>> types = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON,
					EntityTypes.VILLAGER, EntityTypes.PILLAGER, EntityTypes.WITHER_SKELETON);
			List<Integer> ids = new ArrayList<>();
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				for (int i = 0; i < types.size(); i++) {
					LivingEntity body = types.get(i).create(level, EntitySpawnReason.COMMAND);
					body.snapTo(bx - 4.0 + i * 2.0, by, bz, 180.0F, 0.0F);
					body.setYHeadRot(180.0F);
					body.yBodyRot = 180.0F;
					Mob mob = (Mob) body;
					// The zombie keeps its AI: a headless zombie is moved by the mod, not by it.
					mob.setNoAi(i != 0);
					mob.setPersistenceRequired();
					level.addFreshEntity(body);
					RealisticDamageData data = RDAttachments.get(body);
					data.reset();
					RDAttachments.pushSnapshot(body);
					MobAnatomy.track(body);
					ids.add(body.getId());
				}
			});
			context.runOnClient(client -> {
				client.player.setPos(bx, py + 1.0, bz - 6.0);
				client.player.setYRot(0.0F);
				client.player.setXRot(12.0F);
			});
			context.waitTicks(30);
			context.takeScreenshot("rd_decap_00_standing");
			long drawnBefore = context.computeOnClient(client -> SeveredHeadRenderer.submits());
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				for (int id : ids) {
					LivingEntity body = (LivingEntity) level.getEntity(id);
					RealisticDamageData data = RDAttachments.get(body);
					data.part(BodyPart.HEAD).setCondition(0.0);
					// Swung from the camera's side, so the heads fly away from it.
					Decapitation.decapitate(body, data, new Vec3(0.3, 0.0, 1.0), null);
				}
			});
			context.waitTicks(4);
			context.takeScreenshot("rd_decap_01_heads_off");
			context.waitTicks(12);
			context.takeScreenshot("rd_decap_02_flying");
			context.waitTicks(16);
			context.takeScreenshot("rd_decap_03_down_zombie_walking");
			List<String> failures = new ArrayList<>();
			long drawn = context.computeOnClient(client -> SeveredHeadRenderer.submits()) - drawnBefore;
			if (drawn <= 0) {
				failures.add("no severed head was drawn");
			}
			int zombie = ids.getFirst();
			boolean walking = singleplayer.getServer().computeOnServer(server -> {
				Entity e = server.getPlayerList().getPlayers().getFirst().level().getEntity(zombie);
				return e instanceof LivingEntity living && living.isAlive() && MobAnatomy.headless(living);
			});
			StringBuilder report = new StringBuilder("[rd] decapitation: zombie walking headless " + walking);
			for (int i = 1; i < ids.size(); i++) {
				int id = ids.get(i);
				int head = context.computeOnClient(client -> {
					Entity e = client.level.getEntity(id);
					return e == null ? -2 : RemoteCorpses.ragdollHead(e);
				});
				report.append(", ").append(types.get(i).toShortString()).append(" ragdoll head ").append(head);
				if (head == 1) {
					failures.add(types.get(i).toShortString() + "'s ragdoll fell with its head on");
				}
			}
			int heads = context.computeOnClient(client -> {
				int n = 0;
				for (Entity e : client.level.entitiesForRendering()) {
					n += e instanceof SeveredHead ? 1 : 0;
				}
				return n;
			});
			report.append(", heads in the world ").append(heads).append(", drawn ").append(drawn);
			System.out.println(report);
			if (!walking) {
				failures.add("the zombie is not walking on headless");
			}
			context.waitTicks(40);
			context.takeScreenshot("rd_decap_04_zombie_dropped");

			// Your own.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				RealisticDamageData data = RDAttachments.get(player);
				data.part(BodyPart.HEAD).setCondition(0.0);
				Decapitation.decapitate(player, data, new Vec3(1.0, 0.0, 0.2), null);
			});
			int waited = 0;
			while (!context.computeOnClient(client -> ClientState.dying())) {
				context.waitTick();
				if (++waited > 100) {
					throw new AssertionError("losing your head did not kill you");
				}
			}
			context.waitTicks(3);
			String own = context.computeOnClient(client -> {
				SeveredHead head = HeadView.ownHead();
				Vec3 camera = client.gameRenderer.mainCamera().position();
				return "headless " + ClientState.headless() + ", ragdoll view " + RagdollView.active()
						+ ", riding " + HeadView.riding() + ", camera to head "
						+ (head == null ? "no head" : String.format("%.2f", camera.distanceTo(head.position().add(0.0, head.getBbHeight() / 2.0, 0.0))));
			});
			System.out.println("[rd] own decapitation: " + own);
			context.takeScreenshot("rd_decap_10_own_head");
			context.waitTicks(10);
			context.takeScreenshot("rd_decap_11_own_head_rolling");
			boolean riding = context.computeOnClient(client -> HeadView.riding());
			boolean near = context.computeOnClient(client -> {
				SeveredHead head = HeadView.ownHead();
				return head != null && client.gameRenderer.mainCamera().position()
						.distanceTo(head.position().add(0.0, head.getBbHeight() / 2.0, 0.0)) < 0.6;
			});
			if (!context.computeOnClient(client -> ClientState.headless())) {
				failures.add("the death camera was not told the head came off");
			}
			if (!riding || !near) {
				failures.add("the view is not riding your head: " + own);
			}
			if (!failures.isEmpty()) {
				throw new AssertionError(String.join("; ", failures) + " (" + report + ")");
			}
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}
}
