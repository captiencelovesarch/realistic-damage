package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.client.BloodBody;
import dev.captience.realisticdamage.client.BloodTint;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.GenericLimbs;
import dev.captience.realisticdamage.client.ModelBloodLayer;
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
import net.minecraft.world.entity.Mob;

/**
 * Every other mob, on screen (1.7 slice 3): a cow, a spider, a chicken, a
 * slime, an enderman and an iron golem, each wounded and each missing a limb
 * where it has one. Their blood draws on their own models in their own
 * colours, the lost parts are hidden, and the shared model is whole again for
 * the next body through it.
 */
public class MobBodiesClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> ClientEffectsConfig.load());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon");
			});
			context.runOnClient(client -> {
				client.player.setYRot(0.0F);
				client.player.setXRot(18.0F);
			});

			// Six bodies across the view, nearest in the middle.
			spawn(singleplayer, EntityTypes.COW, 2.6, 5.0, data -> {
				data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
				data.part(BodyPart.LEFT_ARM).setDestroyed(true);
			});
			spawn(singleplayer, EntityTypes.SPIDER, -0.2, 5.5, data -> {
				data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
				data.addWound(BodyPart.HEAD, WoundSeverity.SEVERE);
				data.part(BodyPart.RIGHT_ARM).setDestroyed(true);
			});
			spawn(singleplayer, EntityTypes.CHICKEN, -2.4, 4.0, data -> {
				data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
				data.part(BodyPart.LEFT_ARM).setDestroyed(true);
			});
			spawn(singleplayer, EntityTypes.SLIME, 1.2, 3.2, data -> data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL));
			spawn(singleplayer, EntityTypes.ENDERMAN, -4.2, 6.5, data -> {
				data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
				data.addWound(BodyPart.LEFT_ARM, WoundSeverity.CRITICAL);
			});
			spawn(singleplayer, EntityTypes.IRON_GOLEM, 5.2, 7.0, data -> data.part(BodyPart.LEFT_ARM).setDestroyed(true));

			// Long enough for the wounds to soak into the bodies.
			context.waitTicks(100);

			int cowId = idOf(context, EntityTypes.COW);
			int spiderId = idOf(context, EntityTypes.SPIDER);
			int slimeId = idOf(context, EntityTypes.SLIME);
			int endermanId = idOf(context, EntityTypes.ENDERMAN);
			for (int id : new int[] {cowId, spiderId, slimeId, endermanId}) {
				double stain = context.computeOnClient(client -> BloodBody.stain(id, BodyPart.TORSO));
				if (stain <= 0.0) {
					throw new AssertionError("entity " + id + " gathered no blood on its body");
				}
			}

			// The colours are the tags' (client-side lookups, synced tags).
			context.runOnClient(client -> {
				expectTint(EntityTypes.SPIDER, BloodTint.BLUE);
				expectTint(EntityTypes.SLIME, BloodTint.GREEN);
				expectTint(EntityTypes.ENDERMAN, BloodTint.PURPLE);
				expectTint(EntityTypes.ENDER_DRAGON, BloodTint.PURPLE);
				expectTint(EntityTypes.COW, BloodTint.RED);
				if (MobAnatomy.bleeds(EntityTypes.IRON_GOLEM)) {
					throw new AssertionError("the client thinks iron bleeds");
				}
			});

			long blood = context.computeOnClient(client -> ModelBloodLayer.submits());
			long regions = context.computeOnClient(client -> GenericLimbs.regionDraws());
			long hidden = context.computeOnClient(client -> GenericLimbs.hiddenDraws());
			context.waitTicks(5);
			if (context.computeOnClient(client -> ModelBloodLayer.submits()) <= blood) {
				throw new AssertionError("no blood was submitted onto any non-humanoid model");
			}
			if (context.computeOnClient(client -> GenericLimbs.regionDraws()) <= regions) {
				throw new AssertionError("blood was submitted but no region was ever drawn");
			}
			if (context.computeOnClient(client -> GenericLimbs.hiddenDraws()) <= hidden) {
				throw new AssertionError("no lost limb was hidden on any model");
			}
			context.takeScreenshot("rd_mobs_00_lineup");

			// Between frames the shared cow model must be whole: the hidden leg is
			// put back inside the same draw, or every cow drawn after this one
			// would be three-legged.
			context.runOnClient(client -> {
				Entity cow = client.level.getEntity(cowId);
				var renderer = (net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?, ?>)
						client.getEntityRenderDispatcher().getRenderer(cow);
				var leg = renderer.getModel().root().getChild("left_front_leg");
				if (!leg.visible) {
					throw new AssertionError("the cow model's leg stayed hidden after the draw");
				}
				var body = renderer.getModel().root().getChild("body");
				if (!body.visible || body.skipDraw) {
					throw new AssertionError("a region draw left the cow's body switched off");
				}
			});

			// Closer: the cow and the spider, side on.
			context.runOnClient(client -> {
				client.player.setPos(client.player.getX() + 1.2, client.player.getY(), client.player.getZ() + 2.0);
				client.player.setYRot(0.0F);
				client.player.setXRot(28.0F);
			});
			context.waitTicks(10);
			context.takeScreenshot("rd_mobs_01_cow_spider_close");

			context.runOnClient(client -> {
				client.player.setPos(client.player.getX() - 3.2, client.player.getY(), client.player.getZ() + 0.5);
				client.player.setYRot(-20.0F);
				client.player.setXRot(20.0F);
			});
			context.waitTicks(10);
			context.takeScreenshot("rd_mobs_02_chicken_enderman");
		}
	}

	private static void expectTint(EntityType<?> type, BloodTint expected) {
		if (BloodTint.ofType(type) != expected) {
			throw new AssertionError(type + " bleeds the wrong colour");
		}
	}

	private static int idOf(ClientGameTestContext context, EntityType<?> type) {
		return context.computeOnClient(client -> {
			for (Entity entity : client.level.entitiesForRendering()) {
				if (entity.getType() == type) {
					var snapshot = entity.getAttached(RDAttachments.SNAPSHOT);
					if (snapshot == null) {
						throw new AssertionError("the " + type + "'s body never reached the client");
					}
					return entity.getId();
				}
			}
			throw new AssertionError("no " + type + " on the client");
		});
	}

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
				// Side on, so legs and wings read; a body facing the camera hides them.
				mob.setYRot(90.0F);
				mob.setYHeadRot(90.0F);
				mob.yBodyRot = 90.0F;
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
