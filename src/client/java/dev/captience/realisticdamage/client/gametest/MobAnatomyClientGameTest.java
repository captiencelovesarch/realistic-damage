package dev.captience.realisticdamage.client.gametest;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.client.BloodBody;
import dev.captience.realisticdamage.client.BloodBodyLayer;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.LimbMeshes;
import dev.captience.realisticdamage.client.SeveredLimbRenderer;
import dev.captience.realisticdamage.damage.DestructionLogic;
import dev.captience.realisticdamage.entity.SeveredLimb;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Mobs with bodies, on screen (1.7): a zombie's wound stains its model, its
 * lost leg is a stump, its severed leg lies on the floor in its own texture,
 * and a skeleton's stump is a bone.
 */
public class MobAnatomyClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> ClientEffectsConfig.load());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			// Midnight: a zombie in the noon sun is a bonfire, which is both a
			// worse screenshot and a second source of trauma the assertions did
			// not ask for.
			singleplayer.getServer().runOnServer(server ->
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set midnight"));
			context.runOnClient(client -> {
				client.player.setYRot(0.0F);
				client.player.setXRot(12.0F);
			});

			// A zombie three blocks ahead, wounded on the left arm and missing
			// its right leg, standing still so the shot is repeatable.
			spawn(singleplayer, EntityTypes.ZOMBIE, 0.0, 3.0, data -> {
				data.addWound(BodyPart.LEFT_ARM, WoundSeverity.CRITICAL);
				data.part(BodyPart.RIGHT_LEG).setDestroyed(true);
			});
			context.waitTicks(40);
			int zombieId = context.computeOnClient(client -> {
				List<Zombie> zombies = client.level.getEntitiesOfClass(Zombie.class,
						client.player.getBoundingBox().inflate(16.0));
				if (zombies.isEmpty()) {
					throw new AssertionError("the zombie never reached the client");
				}
				Zombie zombie = zombies.getFirst();
				var snapshot = zombie.getAttached(RDAttachments.SNAPSHOT);
				if (snapshot == null || !snapshot.destroyed(BodyPart.RIGHT_LEG)) {
					throw new AssertionError("the zombie's body did not sync: " + snapshot);
				}
				return zombie.getId();
			});
			double stain = context.computeOnClient(client -> BloodBody.stain(zombieId, BodyPart.LEFT_ARM));
			if (stain <= 0.0) {
				throw new AssertionError("a zombie's bleeding arm gathered no blood on the client");
			}
			long mobSubmits = context.computeOnClient(client -> BloodBodyLayer.mobSubmits());
			long stumps = context.computeOnClient(client -> BloodBodyLayer.stumpSubmits());
			context.waitTicks(5);
			if (context.computeOnClient(client -> BloodBodyLayer.mobSubmits()) <= mobSubmits) {
				throw new AssertionError("the blood layer drew nothing on the zombie");
			}
			if (context.computeOnClient(client -> BloodBodyLayer.stumpSubmits()) <= stumps) {
				throw new AssertionError("the zombie's missing leg drew no stump");
			}
			context.takeScreenshot("rd_mob_00_zombie_wounded");

			// Take the other leg through the production choke point: the limb
			// entity has to arrive wearing the zombie's own texture.
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					for (Zombie zombie : player.level().getEntitiesOfClass(Zombie.class,
							player.getBoundingBox().inflate(16.0))) {
						DestructionLogic.destroy(zombie, RDAttachments.get(zombie), BodyPart.LEFT_LEG,
								WoundSeverity.CRITICAL, new Vec3(0.0, 0.0, 1.0));
						RDAttachments.pushSnapshot(zombie);
					}
				}
			});
			context.waitTicks(25);
			boolean zombieLimb = context.computeOnClient(client -> client.level.getEntitiesOfClass(SeveredLimb.class,
					client.player.getBoundingBox().inflate(24.0)).stream()
					.anyMatch(limb -> limb.ownerType() == EntityTypes.ZOMBIE));
			if (!zombieLimb) {
				throw new AssertionError("the zombie's leg left no limb on the client, or one that forgot whose it was");
			}
			long limbSubmits = context.computeOnClient(client -> SeveredLimbRenderer.submits());
			context.waitTicks(5);
			if (context.computeOnClient(client -> SeveredLimbRenderer.submits()) <= limbSubmits) {
				throw new AssertionError("the zombie's severed leg was never drawn");
			}
			context.takeScreenshot("rd_mob_01_zombie_severed");

			// A skeleton beside it missing its drawing arm: the stump is a bone,
			// two pixels wide, on the skeleton's own texture. Counted by build, so
			// the zombie's stumps still being drawn cannot stand in for it — the
			// first version counted every stump and would have passed with the
			// skeleton hidden behind the zombie, which is where it was standing.
			spawn(singleplayer, EntityTypes.SKELETON, 2.0, 4.0, data -> data.part(BodyPart.RIGHT_ARM).setDestroyed(true));
			context.waitTicks(30);
			long boneStumps = context.computeOnClient(client -> BloodBodyLayer.stumpSubmits(LimbMeshes.Kind.THIN));
			context.waitTicks(5);
			if (context.computeOnClient(client -> BloodBodyLayer.stumpSubmits(LimbMeshes.Kind.THIN)) <= boneStumps) {
				throw new AssertionError("the skeleton's missing arm drew no bone stump");
			}
			context.takeScreenshot("rd_mob_02_skeleton_stump");
		}
	}

	/** Spawns a still, persistent mob {@code ahead} blocks in front of and {@code aside} to the player's left, with the given body. */
	private static <T extends Mob> void spawn(TestSingleplayerContext singleplayer, EntityType<T> type, double aside,
			double ahead, java.util.function.Consumer<RealisticDamageData> body) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				ServerLevel level = player.level();
				T mob = type.create(level, EntitySpawnReason.COMMAND);
				if (mob == null) {
					throw new AssertionError("could not create " + type);
				}
				mob.setPos(player.getX() + aside, player.getY(), player.getZ() + ahead);
				// Face the camera with head and body both: a body whose head is
				// left at zero swings round to follow it and shows its back.
				mob.setYRot(180.0F);
				mob.setYHeadRot(180.0F);
				mob.yBodyRot = 180.0F;
				mob.setNoAi(true);
				mob.setPersistenceRequired();
				level.addFreshEntity(mob);
				RealisticDamageData data = RDAttachments.get(mob);
				data.reset();
				body.accept(data);
				RDAttachments.pushSnapshot(mob);
				MobAnatomy.track(mob);
			}
		});
	}
}
