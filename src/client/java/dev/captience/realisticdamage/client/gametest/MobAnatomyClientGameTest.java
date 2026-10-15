package dev.captience.realisticdamage.client.gametest;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.client.BloodBody;
import dev.captience.realisticdamage.client.BloodBodyLayer;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.DownPose;
import dev.captience.realisticdamage.client.FigureBloodLayer;
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

			// A pillager, a body that is not a HumanoidModel (1.7 slice two): its
			// bleeding arm and missing leg go through the figure layer.
			spawn(singleplayer, EntityTypes.PILLAGER, -2.0, 4.0, data -> {
				data.addWound(BodyPart.RIGHT_ARM, WoundSeverity.CRITICAL);
				data.part(BodyPart.LEFT_LEG).setDestroyed(true);
			});
			context.waitTicks(40);
			long figure = context.computeOnClient(client -> FigureBloodLayer.submits());
			context.waitTicks(5);
			if (context.computeOnClient(client -> FigureBloodLayer.submits()) <= figure) {
				throw new AssertionError("the pillager's wound and stump drew nothing");
			}
			context.takeScreenshot("rd_mob_03_pillager");

			// Collapse lays a mob out: consciousness gone, the zombie lies where it
			// stood, and the client's pose hook has to have fired to put it there.
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					for (Zombie zombie : player.level().getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(16.0))) {
						RealisticDamageData data = RDAttachments.get(zombie);
						data.drainConsciousness(dev.captience.realisticdamage.config.Balance.CONSCIOUSNESS_MAX);
						MobAnatomy.tickBody(zombie, data);
					}
				}
			});
			context.waitTicks(10);
			long down = context.computeOnClient(client -> DownPose.applications());
			context.waitTicks(5);
			if (context.computeOnClient(client -> DownPose.applications()) <= down) {
				throw new AssertionError("a collapsed zombie was never laid out");
			}
			context.takeScreenshot("rd_mob_04_zombie_collapsed");

			// The shared-model trap (alpha.3): one ZombieModel draws every zombie,
			// and a leg hidden for one must not stay hidden for the next. Pose the
			// renderer's own model with a missing-leg body and then a whole one.
			context.runOnClient(client -> {
				Zombie zombie = client.level.getEntitiesOfClass(Zombie.class,
						client.player.getBoundingBox().inflate(16.0)).getFirst();
				@SuppressWarnings("unchecked")
				var renderer = (net.minecraft.client.renderer.entity.LivingEntityRenderer<Zombie,
						net.minecraft.client.renderer.entity.state.ZombieRenderState,
						net.minecraft.client.model.monster.zombie.ZombieModel<net.minecraft.client.renderer.entity.state.ZombieRenderState>>)
						client.getEntityRenderDispatcher().getRenderer(zombie);
				var model = renderer.getModel();
				var hurt = renderer.createRenderState();
				((dev.captience.realisticdamage.client.LimbStateHolder) hurt)
						.realisticdamage$setDestroyedMask(1 << BodyPart.LEFT_LEG.ordinal());
				var whole = renderer.createRenderState();
				model.setupAnim(hurt);
				if (model.leftLeg.visible) {
					throw new AssertionError("a body missing its left leg was drawn with one");
				}
				model.setupAnim(whole);
				if (!model.leftLeg.visible) {
					throw new AssertionError("the next zombie through the same model lost its leg too");
				}
			});
			// And the illager model, whose legs vanilla never re-shows either.
			context.runOnClient(client -> {
				var pillager = client.level.getEntitiesOfClass(net.minecraft.world.entity.monster.illager.Pillager.class,
						client.player.getBoundingBox().inflate(16.0)).getFirst();
				@SuppressWarnings("unchecked")
				var renderer = (net.minecraft.client.renderer.entity.LivingEntityRenderer<net.minecraft.world.entity.monster.illager.Pillager,
						net.minecraft.client.renderer.entity.state.IllagerRenderState,
						net.minecraft.client.model.monster.illager.IllagerModel<net.minecraft.client.renderer.entity.state.IllagerRenderState>>)
						client.getEntityRenderDispatcher().getRenderer(pillager);
				var model = renderer.getModel();
				var leg = model.root().getChild("right_leg");
				var hurt = renderer.createRenderState();
				((dev.captience.realisticdamage.client.LimbStateHolder) hurt)
						.realisticdamage$setDestroyedMask(1 << BodyPart.RIGHT_LEG.ordinal());
				var whole = renderer.createRenderState();
				model.setupAnim(hurt);
				if (leg.visible) {
					throw new AssertionError("a pillager missing its right leg was drawn with one");
				}
				model.setupAnim(whole);
				if (!leg.visible) {
					throw new AssertionError("the next pillager through the same model lost its leg too");
				}
			});

			firstWoundBleedsItsOwnColour(context, singleplayer);
		}
	}

	/**
	 * A mob's first wound sprays its own blood (1.7 alpha.11). The playtest
	 * report: green-blooded mobs threw a red first spurt, then bled green. The
	 * creeper here has never had a body snapshot sent, exactly as a wild one
	 * has not before anyone hits it.
	 */
	private static void firstWoundBleedsItsOwnColour(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
						new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
				var creeper = EntityTypes.CREEPER.create(player.level(), EntitySpawnReason.COMMAND);
				if (creeper == null) {
					throw new AssertionError("could not create a creeper");
				}
				creeper.setPos(player.getX() - 2.0, player.getY(), player.getZ() + 2.5);
				creeper.setNoAi(true);
				creeper.setPersistenceRequired();
				player.level().addFreshEntity(creeper);
			}
		});
		context.waitTicks(20);
		long before = context.computeOnClient(client -> dev.captience.realisticdamage.client.BloodDrop.Provider.thrown());
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				var level = player.level();
				for (var creeper : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class,
						player.getBoundingBox().inflate(8.0))) {
					creeper.hurtServer(level, level.damageSources().playerAttack(player), 6.0F);
				}
			}
		});
		context.waitTicks(5);
		long after = context.computeOnClient(client -> dev.captience.realisticdamage.client.BloodDrop.Provider.thrown());
		if (after <= before) {
			throw new AssertionError("a sword cut on a creeper threw no spray at all");
		}
		var tint = context.computeOnClient(client -> dev.captience.realisticdamage.client.BloodDrop.Provider.lastTint());
		if (tint != dev.captience.realisticdamage.client.BloodTint.GREEN) {
			throw new AssertionError("a creeper's first wound sprayed " + (tint == dev.captience.realisticdamage.client.BloodTint.RED
					? "red" : "the wrong colour") + "; creepers bleed green");
		}
		context.takeScreenshot("rd_mob_first_wound_green");
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
