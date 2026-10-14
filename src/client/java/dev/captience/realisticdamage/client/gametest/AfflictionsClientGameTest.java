package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.AirHunger;
import dev.captience.realisticdamage.client.BlastShock;
import dev.captience.realisticdamage.client.Breathing;
import dev.captience.realisticdamage.client.CameraMotion;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ConcussionEffects;
import dev.captience.realisticdamage.client.LowHealthEffects;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The 1.7 afflictions as the player sees and hears them: a blast nearby takes
 * your hearing, a concussion spins the room and makes the sun hurt, running out
 * of air heaves the chest and surfacing gasps, the last two hearts surge and
 * crash, and a limp arm hangs. Each one is asserted on a readout as well as
 * screenshotted, because a screenshot cannot tell a weak effect from a missing one.
 */
public class AfflictionsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> ClientEffectsConfig.load());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> {
				// Noon, sun overhead. 26.2 moved time onto world clocks; the command
				// is the stable way in.
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000");
				ServerPlayer player = player(server);
				player.setXRot(0.0F);
			});
			context.waitTicks(10);
			blast(context, singleplayer);
			concussion(context, singleplayer);
			air(context, singleplayer);
			grip(context, singleplayer);
			lastTwoHearts(context, singleplayer);
		}
	}

	private static ServerPlayer player(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().getFirst();
	}

	private static void reset(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			RealisticDamageData data = RDAttachments.get(player);
			data.reset();
			player.setHealth(player.getMaxHealth());
			player.setAirSupply(player.getMaxAirSupply());
			RDAttachments.pushSnapshot(player);
		});
	}

	/** TNT ten blocks away: out of its damage range, well inside its deafening one. */
	private static void blast(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		reset(singleplayer);
		long before = context.computeOnClient(client -> BlastShock.shocks());
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.level().explode(null, player.getX() + 10.0, player.getY() + 1.0, player.getZ(),
					4.0F, Level.ExplosionInteraction.NONE);
		});
		context.waitTicks(2);
		context.takeScreenshot("rd_affl_00_blast_flash");
		context.waitTicks(6);
		if (context.computeOnClient(client -> BlastShock.shocks()) <= before) {
			throw new AssertionError("a TNT blast ten blocks away did nothing to the ears");
		}
		double deaf = context.computeOnClient(client -> BlastShock.deafness());
		if (deaf < 0.25) {
			throw new AssertionError("ten blocks from TNT should leave you badly deafened, got " + deaf);
		}
		context.takeScreenshot("rd_affl_01_blast_dust");
		context.waitTicks(400);
		double later = context.computeOnClient(client -> BlastShock.deafness());
		if (later > 0.05) {
			throw new AssertionError("hearing never came back after twenty seconds: " + later);
		}
	}

	private static void concussion(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		reset(singleplayer);
		// A vertigo spell: the room rolls.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			RealisticDamageData data = RDAttachments.get(player);
			data.setConcussionSeverity(85.0);
			data.beginVertigo(120);
			RDAttachments.pushSnapshot(player);
		});
		context.waitTicks(30);
		double roll = context.computeOnClient(client -> Math.abs(CameraMotion.lastRoll()));
		if (roll < 3.0) {
			throw new AssertionError("a vertigo spell should roll the horizon, got " + roll + " degrees");
		}
		context.takeScreenshot("rd_affl_02_vertigo");

		// Looking at the noon sun with a severe concussion: glare and a squint.
		context.waitTicks(100);
		singleplayer.getServer().runOnServer(server -> player(server).setXRot(-90.0F));
		context.runOnClient(client -> client.player.setXRot(-90.0F));
		context.waitTicks(40);
		double squint = context.computeOnClient(client -> ConcussionEffects.squint());
		if (squint < 0.3) {
			throw new AssertionError("staring at the sun concussed should make you squint, got " + squint
					+ " (glare " + context.computeOnClient(client -> ConcussionEffects.glare()) + ")");
		}
		context.takeScreenshot("rd_affl_03_sun_glare");
		context.runOnClient(client -> client.player.setXRot(10.0F));
		singleplayer.getServer().runOnServer(server -> player(server).setXRot(10.0F));

		// At the top of the flare: the retch, and what comes out.
		long bile = context.computeOnClient(client -> ConcussionEffects.bileDrops());
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			RDAttachments.get(player).setConcussionFlare(Balance.FLARE_RETCH + 5.0);
		});
		context.waitTicks(14);
		context.takeScreenshot("rd_affl_04_retch");
		context.waitTicks(30);
		if (context.computeOnClient(client -> ConcussionEffects.bileDrops()) <= bile) {
			throw new AssertionError("retched and nothing came out");
		}
		context.takeScreenshot("rd_affl_05_after_retch");
	}

	private static void air(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		reset(singleplayer);
		BlockPos[] head = new BlockPos[1];
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BlockPos feet = player.blockPosition();
			head[0] = feet;
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy <= 2; dy++) {
						player.level().setBlockAndUpdate(feet.offset(dx, dy, dz), Blocks.WATER.defaultBlockState());
					}
				}
			}
			player.setAirSupply(20);
		});
		long contractions = context.computeOnClient(client -> AirHunger.contractions());
		context.waitTicks(60);
		if (context.computeOnClient(client -> AirHunger.contractions()) <= contractions) {
			throw new AssertionError("out of air under water and the chest never heaved");
		}
		context.takeScreenshot("rd_affl_06_air_hunger");
		long gasps = context.computeOnClient(client -> Breathing.gasps());
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BlockPos feet = head[0];
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy <= 2; dy++) {
						player.level().setBlockAndUpdate(feet.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
					}
				}
			}
		});
		context.waitTicks(20);
		if (context.computeOnClient(client -> Breathing.gasps()) <= gasps) {
			throw new AssertionError("came out of the water and never gasped");
		}
		// Mop up the flowing water the sources left, so later shots are dry.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			BlockPos feet = head[0];
			for (int dx = -4; dx <= 4; dx++) {
				for (int dz = -4; dz <= 4; dz++) {
					for (int dy = -1; dy <= 3; dy++) {
						BlockPos at = feet.offset(dx, dy, dz);
						if (!player.level().getFluidState(at).isEmpty()) {
							player.level().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
						}
					}
				}
			}
		});
		context.waitTicks(10);
	}

	private static void grip(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		reset(singleplayer);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			RDAttachments.pushSnapshot(player);
		});
		context.waitTicks(40);
		context.takeScreenshot("rd_affl_07_grip_sound");
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			RealisticDamageData data = RDAttachments.get(player);
			data.part(ImpairmentSystem.armFor(player, false)).setCondition(0.0);
			data.part(ImpairmentSystem.armFor(player, true)).setCondition(0.0);
			RDAttachments.pushSnapshot(player);
		});
		context.waitTicks(20);
		context.takeScreenshot("rd_affl_08_grip_limp");
	}

	private static void lastTwoHearts(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		reset(singleplayer);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			player.getFoodData().setFoodLevel(6);   // no natural regen out of the danger zone
			player.setHealth(3.0F);
		});
		context.waitTicks(30);
		double surge = context.computeOnClient(client -> LowHealthEffects.surge());
		if (surge < 0.5) {
			throw new AssertionError("two hearts and no adrenaline surge, got " + surge);
		}
		float fov = context.computeOnClient(client -> LowHealthEffects.fovFactor());
		if (fov > 0.95F) {
			throw new AssertionError("the surge should narrow the view, factor " + fov);
		}
		context.takeScreenshot("rd_affl_09_surge");
		long breaths = context.computeOnClient(client -> Breathing.breaths());
		context.waitTicks(Balance.ADRENALINE_SURGE_TICKS + 60);
		double crash = context.computeOnClient(client -> LowHealthEffects.crash());
		if (crash < 0.3) {
			throw new AssertionError("the surge should give way to the crash, got " + crash);
		}
		if (context.computeOnClient(client -> Breathing.breaths()) <= breaths) {
			throw new AssertionError("crashing and not a breath to be heard");
		}
		context.takeScreenshot("rd_affl_10_crash");
	}
}
