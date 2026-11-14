package dev.captience.realisticdamage.fieldkit.client.gametest;

import java.util.ArrayList;
import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.config.ServerConfig;
import dev.captience.realisticdamage.fieldkit.client.GearLayer;
import dev.captience.realisticdamage.fieldkit.client.SlungArm;
import dev.captience.realisticdamage.fieldkit.medicine.Dressings;
import dev.captience.realisticdamage.fieldkit.medicine.Medicine;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The field kit's gear, worn (items.7): on you in third person from the front
 * and the back, the stethoscope in your ears while you listen, a slung arm
 * from behind your own eyes, and the same gear on a zombie, a skeleton and a
 * villager. The screenshots are the preview a person judges the models by.
 */
public class GearClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		boolean medicine = context.computeOnClient(client -> ServerConfig.get().enableMedicine);
		List<String> failures = new ArrayList<>();
		context.runOnClient(client -> {
			ServerConfig.get().enableMedicine = true;
			ServerConfig.save();
		});
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				var source = server.createCommandSourceStack();
				server.getCommands().performPrefixedCommand(source, "time set noon");
				server.getCommands().performPrefixedCommand(source, "gamerule doMobSpawning false");
				server.getCommands().performPrefixedCommand(source, "gamerule doDaylightCycle false");
				server.getCommands().performPrefixedCommand(source, "gamemode survival @a");
			});
			context.waitTicks(5);
			context.getInput().resizeWindow(1280, 720);
			context.runOnClient(client -> {
				client.player.setYRot(0.0F);
				client.player.setXRot(0.0F);
				// A clean, close picture: no HUD, a narrow lens on the third-person shots.
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
				client.options.fov().set(45);
			});

			// You: goggles, the right arm in its sling, a splinted left leg, a bandaged left arm.
			// Each on something it is for: a sling and a splint stay only on a broken
			// bone, a bandage only on a wound.
			onServer(singleplayer, player -> {
				player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Medicine.TINTED_GOGGLES));
				broken(player, BodyPart.RIGHT_ARM);
				Dressings.sling(player, BodyPart.RIGHT_ARM);
				broken(player, BodyPart.LEFT_LEG);
				Dressings.splint(player, BodyPart.LEFT_LEG);
				wounded(player, BodyPart.LEFT_ARM, WoundSeverity.MINOR);
				Dressings.bandage(player, BodyPart.LEFT_ARM);
			});
			context.waitTicks(10);
			long before = context.computeOnClient(client -> GearLayer.submits());
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("rd_gear_01_front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("rd_gear_02_back");
			if (context.computeOnClient(client -> GearLayer.submits()) <= before) {
				failures.add("nothing of the gear was drawn on the player");
			}

			// First person, the arm in the sling: low across the view, holding nothing.
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				client.options.fov().set(70);
				client.player.setXRot(30.0F);
				// The hands are drawn only with the HUD on (as F1 hides them both).
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			long drawn = context.computeOnClient(client -> SlungArm.firstPersonDraws());
			context.waitTicks(5);
			context.takeScreenshot("rd_gear_03_first_person_sling");
			onServer(singleplayer, player -> player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)));
			context.waitTicks(5);
			context.takeScreenshot("rd_gear_04_first_person_sling_holding_a_sword");
			if (context.computeOnClient(client -> SlungArm.firstPersonDraws()) <= drawn) {
				failures.add("the slung arm was never drawn in first person");
			}

			// You again: the sling and splint off; a tourniquet, the head and chest
			// bandaged, and the stethoscope in your ears, listening to yourself.
			onServer(singleplayer, player -> {
				Dressings.unsling(player, BodyPart.RIGHT_ARM);
				wounded(player, BodyPart.HEAD, WoundSeverity.MINOR);
				Dressings.bandage(player, BodyPart.HEAD);
				wounded(player, BodyPart.TORSO, WoundSeverity.MODERATE);
				Dressings.bandage(player, BodyPart.TORSO);
				// Worn long enough to have soaked through, as a moderate wound's does.
				Dressings.peek(player).bandages.get(BodyPart.TORSO).ticks = dev.captience.realisticdamage.config.Balance.BANDAGE_SOAK_TICKS;
				Dressings.tourniquet(player, BodyPart.RIGHT_LEG);
				Dressings.splint(player, BodyPart.RIGHT_ARM);
				player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
				player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Medicine.STETHOSCOPE));
			});
			context.waitTicks(5);
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
				client.options.fov().set(45);
				client.player.setXRot(0.0F);
				if (!client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
			});
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(15);
			context.takeScreenshot("rd_gear_05_front_listening");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(3);
			context.takeScreenshot("rd_gear_06_back_listening");
			context.getInput().releaseKey(options -> options.keyUse);

			// The same gear on others: a zombie in the goggles with a splint and a
			// sling, a skeleton with a tourniquet and a bandage, a villager with
			// its head bandaged and a leg in a splint.
			double px = context.computeOnClient(client -> client.player.getX());
			double py = context.computeOnClient(client -> client.player.getY());
			double pz = context.computeOnClient(client -> client.player.getZ());
			singleplayer.getServer().runOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				LivingEntity zombie = standing(level, EntityTypes.ZOMBIE, px - 1.6, py, pz + 3.5);
				zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Medicine.TINTED_GOGGLES));
				broken(zombie, BodyPart.RIGHT_LEG);
				Dressings.splint(zombie, BodyPart.RIGHT_LEG);
				broken(zombie, BodyPart.LEFT_ARM);
				Dressings.sling(zombie, BodyPart.LEFT_ARM);
				LivingEntity skeleton = standing(level, EntityTypes.SKELETON, px, py, pz + 3.5);
				skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
				Dressings.tourniquet(skeleton, BodyPart.LEFT_LEG);
				wounded(skeleton, BodyPart.RIGHT_ARM, WoundSeverity.MINOR);
				Dressings.bandage(skeleton, BodyPart.RIGHT_ARM);
				LivingEntity villager = standing(level, EntityTypes.VILLAGER, px + 1.6, py, pz + 3.5);
				wounded(villager, BodyPart.HEAD, WoundSeverity.MINOR);
				Dressings.bandage(villager, BodyPart.HEAD);
				broken(villager, BodyPart.LEFT_LEG);
				Dressings.splint(villager, BodyPart.LEFT_LEG);
				wounded(villager, BodyPart.TORSO, WoundSeverity.MINOR);
				Dressings.bandage(villager, BodyPart.TORSO);
			});
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				client.options.fov().set(50);
				client.player.setYRot(0.0F);
				client.player.setXRot(12.0F);
			});
			onServer(singleplayer, player -> {
				player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				Dressings.unbandage(player, BodyPart.HEAD);
			});
			context.waitTicks(20);
			context.takeScreenshot("rd_gear_07_mobs");
			System.out.println("[rd] gear: " + context.computeOnClient(client -> GearLayer.submits())
					+ " pieces drawn, slung arm in first person " + context.computeOnClient(client -> SlungArm.firstPersonDraws())
					+ " times");
			if (!failures.isEmpty()) {
				throw new AssertionError(String.join("; ", failures));
			}
		} finally {
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				if (client.gui.hud.isHidden()) {
					client.gui.hud.toggle();
				}
				client.options.fov().set(70);
				ServerConfig.get().enableMedicine = medicine;
				ServerConfig.save();
			});
		}
	}

	private static void broken(LivingEntity body, BodyPart part) {
		RDAttachments.get(body).part(part).setBroken(true);
		RDAttachments.pushSnapshot(body);
	}

	private static void wounded(LivingEntity body, BodyPart part, WoundSeverity severity) {
		RDAttachments.get(body).addWound(part, severity);
		dev.captience.realisticdamage.systems.MobAnatomy.track(body);
		RDAttachments.pushSnapshot(body);
	}

	private static void onServer(TestSingleplayerContext singleplayer, java.util.function.Consumer<ServerPlayer> action) {
		singleplayer.getServer().runOnServer(server -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}

	/** A body standing still, facing the camera (north, toward the player). */
	private static LivingEntity standing(ServerLevel level, EntityType<? extends LivingEntity> type, double x, double y,
			double z) {
		LivingEntity body = type.create(level, EntitySpawnReason.COMMAND);
		body.snapTo(x, y, z, 180.0F, 0.0F);
		body.setYHeadRot(180.0F);
		body.yBodyRot = 180.0F;
		((Mob) body).setNoAi(true);
		((Mob) body).setPersistenceRequired();
		level.addFreshEntity(body);
		return body;
	}
}
