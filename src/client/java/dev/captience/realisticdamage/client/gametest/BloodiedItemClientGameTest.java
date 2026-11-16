package dev.captience.realisticdamage.client.gametest;

import java.util.List;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.client.BloodLens;
import dev.captience.realisticdamage.client.BloodiedDrips;
import dev.captience.realisticdamage.client.BloodiedItems;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.damage.BloodiedItem;
import dev.captience.realisticdamage.systems.BloodKind;
import dev.captience.realisticdamage.systems.BloodLayer;
import dev.captience.realisticdamage.systems.BloodiedMath;
import dev.captience.realisticdamage.systems.MobAnatomy;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Blood on a held item, drawn for real (2.0): in the hand, on the body seen
 * from outside, and in the inventory; drying; dripping; and put there by an
 * actual blow, whose spray is also what reaches the camera.
 *
 * <p>Asserted on what was drawn (pictures painted, quads submitted, drops
 * shed, droplets on the lens) as well as photographed, for the usual reason:
 * a screenshot of an overlay that silently drew nothing is a perfectly good
 * screenshot of a sword.
 */
public class BloodiedItemClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> ClientEffectsConfig.load());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server ->
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon"));
			context.runOnClient(client -> {
				client.player.setYRot(0.0F);
				client.player.setXRot(10.0F);
			});

			// A clean sword: nothing painted, nothing drawn over it.
			hold(singleplayer, new ItemStack(Items.IRON_SWORD));
			context.waitTicks(10);
			long cleanQuads = context.computeOnClient(client -> BloodiedItems.quadsDrawn());
			context.waitTicks(10);
			long stillClean = context.computeOnClient(client -> BloodiedItems.quadsDrawn());
			if (stillClean != cleanQuads) {
				throw new AssertionError("blood was drawn on a clean sword");
			}
			context.takeScreenshot("rd_bloodied_00_clean");

			// Three bloods on one blade, fresh: a zombie's streak, an enderman's
			// tip and a slime's splat.
			mark(singleplayer, 0, new Object[][] {
					{BloodKind.STREAK, "dark", 0.6F, 101},
					{BloodKind.TIP, "purple", 0.45F, 102},
					{BloodKind.SPLAT, "green", 0.4F, 103}});
			context.waitTicks(10);
			long made = context.computeOnClient(client -> BloodiedItems.picturesMade());
			long quads = context.computeOnClient(client -> BloodiedItems.quadsDrawn());
			if (made <= 0 || quads <= stillClean) {
				throw new AssertionError("a bloodied sword in the hand drew no blood: " + made + " pictures, "
						+ (quads - stillClean) + " quads");
			}
			// The marks the client holds are the marks the server wrote.
			int marks = context.computeOnClient(client -> BloodiedItem.read(client.player.getMainHandItem()).size());
			if (marks != 3) {
				throw new AssertionError("the client's sword has " + marks + " marks, the server wrote 3");
			}
			context.takeScreenshot("rd_bloodied_01_hand_fresh");

			// A heavy wet mark drips.
			long dripped = context.computeOnClient(client -> BloodiedDrips.emitted());
			context.waitTicks(60);
			long drippedAfter = context.computeOnClient(client -> BloodiedDrips.emitted());
			if (drippedAfter <= dripped) {
				throw new AssertionError("a fresh, heavily bloodied sword shed no drops in three seconds");
			}

			// From outside: the same blood on the sword in the model's hand.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("rd_bloodied_02_third_person");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			// In the inventory: the slot's picture of the sword is the bloodied
			// one, beside a clean sword that must stay clean.
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.getInventory().setItem(1, new ItemStack(Items.IRON_SWORD));
					player.getInventory().setItem(2, new ItemStack(Items.DIAMOND_AXE));
				}
			});
			context.waitTicks(5);
			context.runOnClient(client -> client.gui.setScreen(new InventoryScreen(client.player)));
			context.waitTicks(10);
			context.takeScreenshot("rd_bloodied_03_inventory");
			context.runOnClient(client -> client.gui.setScreen(null));

			// Dried: the same marks half a minute on. A different look, so new
			// pictures; and nothing wet enough to drip.
			long beforeDry = context.computeOnClient(client -> BloodiedItems.picturesMade());
			mark(singleplayer, BloodiedMath.DRY_TICKS * 2L, new Object[][] {
					{BloodKind.STREAK, "dark", 0.6F, 101},
					{BloodKind.TIP, "purple", 0.45F, 102},
					{BloodKind.SPLAT, "green", 0.4F, 103}});
			context.waitTicks(10);
			long afterDry = context.computeOnClient(client -> BloodiedItems.picturesMade());
			if (afterDry <= beforeDry) {
				throw new AssertionError("dried blood was drawn with the wet picture");
			}
			long dryDrips = context.computeOnClient(client -> BloodiedDrips.emitted());
			context.waitTicks(40);
			if (context.computeOnClient(client -> BloodiedDrips.emitted()) != dryDrips) {
				throw new AssertionError("dried blood dripped");
			}
			context.takeScreenshot("rd_bloodied_04_hand_dried");

			// Blood switched off: the marks stay on the item and are not drawn.
			context.runOnClient(client -> ClientEffectsConfig.get().disableAllEffects());
			context.waitTicks(5);
			long off = context.computeOnClient(client -> BloodiedItems.quadsDrawn());
			context.waitTicks(10);
			if (context.computeOnClient(client -> BloodiedItems.quadsDrawn()) != off) {
				throw new AssertionError("blood on items was drawn with blood switched off");
			}
			context.runOnClient(client -> ClientEffectsConfig.load());

			// The real thing. A zombie a block and a half ahead, a clean sword,
			// one hard blow through the real damage path: the sword comes away
			// bloody, and the spray the wound throws back along the blow lands
			// on the one who dealt it, some of it on the lens.
			hold(singleplayer, new ItemStack(Items.IRON_SWORD));
			context.runOnClient(client -> BloodLens.reset());
			long splashedBefore = context.computeOnClient(client -> BloodLens.splashedByOthers());
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					ServerLevel level = player.level();
					Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
					zombie.setPos(player.getX(), player.getY(), player.getZ() + 1.5);
					zombie.setYRot(180.0F);
					zombie.setYHeadRot(180.0F);
					zombie.yBodyRot = 180.0F;
					zombie.setNoAi(true);
					zombie.setPersistenceRequired();
					zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
					level.addFreshEntity(zombie);
					RDAttachments.get(zombie).reset();
					RDAttachments.pushSnapshot(zombie);
					MobAnatomy.track(zombie);
				}
			});
			context.waitTicks(10);
			for (int blow = 0; blow < 3; blow++) {
				singleplayer.getServer().runOnServer(server -> {
					for (ServerPlayer player : server.getPlayerList().getPlayers()) {
						for (Zombie zombie : player.level().getEntitiesOfClass(Zombie.class,
								player.getBoundingBox().inflate(6.0))) {
							zombie.invulnerableTime = 0;
							zombie.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 5.0F);
						}
					}
				});
				context.waitTicks(15);
			}
			List<BloodLayer> struck = context.computeOnClient(client -> BloodiedItem.read(client.player.getMainHandItem()));
			if (struck.isEmpty() || !struck.get(0).tint().equals("dark")) {
				throw new AssertionError("three blows into a zombie left the client's sword looking like " + struck);
			}
			long splashed = context.computeOnClient(client -> BloodLens.splashedByOthers()) - splashedBefore;
			int onLens = context.computeOnClient(client -> BloodLens.count());
			long thrown = context.computeOnClient(client -> dev.captience.realisticdamage.client.BloodEmitter.struckDrops());
			System.out.println("[rd-bloodied] lens droplets from the zombie's blood: " + splashed + ", on lens now " + onLens
					+ ", drops thrown by blows into open wounds: " + thrown);
			if (thrown <= 0) {
				throw new AssertionError("blows into a bleeding zombie threw no blood");
			}
			if (splashed <= 0) {
				throw new AssertionError("cut a zombie open at arm's length three times and none of it reached the camera");
			}
			context.takeScreenshot("rd_bloodied_05_struck_with_lens");

			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					for (Zombie zombie : player.level().getEntitiesOfClass(Zombie.class,
							player.getBoundingBox().inflate(16.0))) {
						zombie.discard();
					}
				}
			});
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}

	private static void hold(TestSingleplayerContext singleplayer, ItemStack stack) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.getInventory().setSelectedSlot(0);
				player.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
			}
		});
	}

	/** Writes these marks onto the held item, wetted {@code age} ticks ago. Each row: kind, tint, amount, seed. */
	private static void mark(TestSingleplayerContext singleplayer, long age, Object[][] rows) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				long wetAt = player.level().getGameTime() - age;
				List<BloodLayer> layers = new java.util.ArrayList<>();
				for (Object[] row : rows) {
					layers.add(new BloodLayer((BloodKind) row[0], (String) row[1], (Float) row[2], (Integer) row[3], wetAt));
				}
				BloodiedItem.write(player.getMainHandItem(), layers);
			}
		});
	}
}
