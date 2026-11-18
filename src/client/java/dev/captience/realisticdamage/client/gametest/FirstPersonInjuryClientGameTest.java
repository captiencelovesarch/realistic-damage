package dev.captience.realisticdamage.client.gametest;

import java.util.function.Consumer;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.FirstPersonBrokenArm;
import dev.captience.realisticdamage.client.FirstPersonLimp;
import dev.captience.realisticdamage.systems.BrokenArmHold;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A broken arm and a broken leg from behind your own eyes (2.0), drawn for
 * real: the same four things held sound and then broken, a swing of each, the
 * off hand on its own arm, the limp while walking, and the switch that turns
 * all of it off.
 *
 * <p>Counted as well as photographed. The pictures are for the author to judge
 * how it looks; the counters are what prove the hook fired at all.
 */
public class FirstPersonInjuryClientGameTest implements FabricClientGameTest {
	private static final int RAISE = 30;
	private static final String[] NAMES = {"sword", "axe", "shield", "bread", "empty"};

	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> ClientEffectsConfig.load());
		try {
			run(context);
		} finally {
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}

	private void run(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server ->
					server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set noon"));
			context.runOnClient(client -> {
				client.options.setCameraType(CameraType.FIRST_PERSON);
				ClientEffectsConfig.get().firstPersonInjury = true;
				client.player.setYRot(0.0F);
				client.player.setXRot(10.0F);
			});
			mutate(singleplayer, RealisticDamageData::reset);
			// Made here, not in a static: an item stack cannot exist before a world does.
			final ItemStack[] ITEMS = {new ItemStack(Items.IRON_SWORD), new ItemStack(Items.DIAMOND_AXE),
					new ItemStack(Items.SHIELD), new ItemStack(Items.BREAD), ItemStack.EMPTY};

			// 1. Sound arms: nothing is posed, whatever is held.
			long before = applications(context);
			for (int i = 0; i < ITEMS.length; i++) {
				hold(singleplayer, InteractionHand.MAIN_HAND, ITEMS[i]);
				// Long enough for the slowest thing here (the axe, a full second)
				// to finish coming up: vanilla holds a hand low until its attack
				// is ready, and a picture taken sooner compares two half-raised hands.
				context.waitTicks(RAISE);
				context.takeScreenshot("rd_fp_arm_" + NAMES[i] + "_0_sound");
				swing(context);
				context.takeScreenshot("rd_fp_arm_" + NAMES[i] + "_1_sound_swing");
				context.waitTicks(RAISE);
			}
			if (applications(context) != before) {
				throw new AssertionError("a sound arm was given the broken hold");
			}

			// 2. The right arm broken (the bone only: its condition is left
			// alone so no other effect is in the picture). Every hold sags, and
			// the heavier the thing the further.
			mutate(singleplayer, data -> data.part(BodyPart.RIGHT_ARM).setBroken(true));
			context.waitTicks(10);
			float[] drops = new float[ITEMS.length];
			for (int i = 0; i < ITEMS.length; i++) {
				hold(singleplayer, InteractionHand.MAIN_HAND, ITEMS[i]);
				// Long enough for the slowest thing here (the axe, a full second)
				// to finish coming up: vanilla holds a hand low until its attack
				// is ready, and a picture taken sooner compares two half-raised hands.
				context.waitTicks(RAISE);
				long held = applications(context);
				context.waitTicks(2);
				if (applications(context) <= held) {
					throw new AssertionError("a broken arm holding " + NAMES[i] + " was not posed");
				}
				BrokenArmHold.Pose pose = context.computeOnClient(client -> FirstPersonBrokenArm.lastPose());
				drops[i] = pose.drop();
				if (pose.drop() <= 0.0F || pose.roll() <= 0.0F || pose.swing() != 0.0F) {
					throw new AssertionError("the resting hold of " + NAMES[i] + " is wrong: " + pose);
				}
				context.takeScreenshot("rd_fp_arm_" + NAMES[i] + "_2_broken");
				swing(context);
				BrokenArmHold.Pose swung = context.computeOnClient(client -> FirstPersonBrokenArm.lastPose());
				if (swung.swing() <= 0.0F || swung.swing() > 0.12F) {
					throw new AssertionError("the swing of " + NAMES[i] + " was not a laboured one: " + swung);
				}
				context.takeScreenshot("rd_fp_arm_" + NAMES[i] + "_3_broken_swing");
				context.waitTicks(3);
				context.takeScreenshot("rd_fp_arm_" + NAMES[i] + "_4_broken_swing_drop");
				context.waitTicks(RAISE);
			}
			// sword < axe, sword < shield, bread < sword, empty < bread
			if (!(drops[4] < drops[3] && drops[3] < drops[0] && drops[0] < drops[1] && drops[1] == drops[2])) {
				throw new AssertionError("the sag does not follow the weight: " + java.util.Arrays.toString(drops));
			}

			// 3. Each hand follows its own arm: a shield in the off hand of a
			// player whose LEFT arm is sound is not posed, and once that arm
			// breaks it is.
			hold(singleplayer, InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			mutate(singleplayer, RealisticDamageData::reset);
			hold(singleplayer, InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
			hold(singleplayer, InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
			context.waitTicks(RAISE);
			before = applications(context);
			context.waitTicks(5);
			if (applications(context) != before) {
				throw new AssertionError("healed arms were still posed");
			}
			context.takeScreenshot("rd_fp_arm_offhand_0_sound");
			mutate(singleplayer, data -> data.part(BodyPart.LEFT_ARM).setBroken(true));
			context.waitTicks(10);
			before = applications(context);
			context.waitTicks(5);
			if (applications(context) <= before) {
				throw new AssertionError("a broken off arm was not posed");
			}
			BrokenArmHold.Pose off = context.computeOnClient(client -> FirstPersonBrokenArm.lastPose());
			if (off.drop() != drops[2]) {
				throw new AssertionError("the last hand posed was not the off hand's shield: " + off);
			}
			context.takeScreenshot("rd_fp_arm_offhand_1_broken");

			// 4. The switch.
			context.runOnClient(client -> ClientEffectsConfig.get().firstPersonInjury = false);
			context.waitTicks(5);
			before = applications(context);
			context.waitTicks(5);
			if (applications(context) != before) {
				throw new AssertionError("firstPersonInjury = false left the hold running");
			}
			context.takeScreenshot("rd_fp_arm_offhand_2_switched_off");
			context.runOnClient(client -> ClientEffectsConfig.get().firstPersonInjury = true);

			// 5. The limp. Sound legs walking: the view is not touched.
			mutate(singleplayer, RealisticDamageData::reset);
			hold(singleplayer, InteractionHand.OFF_HAND, ItemStack.EMPTY);
			context.waitTicks(10);
			long limped = context.computeOnClient(client -> FirstPersonLimp.applications());
			walk(context, "rd_fp_leg_0_sound");
			if (context.computeOnClient(client -> FirstPersonLimp.applications()) != limped) {
				throw new AssertionError("sound legs limped");
			}
			// A broken left leg: the view dips, and leans left, on its steps.
			mutate(singleplayer, data -> data.part(BodyPart.LEFT_LEG).setBroken(true));
			context.waitTicks(10);
			float[] worst = walk(context, "rd_fp_leg_1_broken");
			if (context.computeOnClient(client -> FirstPersonLimp.applications()) <= limped) {
				throw new AssertionError("a broken leg did not limp");
			}
			if (worst[0] <= 0.02F || worst[1] >= -0.3F || worst[2] > 0.001F) {
				throw new AssertionError("the limp of a left leg should dip and lean left only: dip " + worst[0]
						+ ", furthest left " + worst[1] + ", furthest right " + worst[2]);
			}
			// Standing still again it is level.
			context.waitTicks(30);
			float rest = context.computeOnClient(client -> FirstPersonLimp.last().dip());
			if (rest > 0.005F) {
				throw new AssertionError("the view is still dipped standing still: " + rest);
			}
			// And the switch takes the limp too.
			context.runOnClient(client -> ClientEffectsConfig.get().firstPersonInjury = false);
			limped = context.computeOnClient(client -> FirstPersonLimp.applications());
			walk(context, "rd_fp_leg_2_switched_off");
			if (context.computeOnClient(client -> FirstPersonLimp.applications()) != limped) {
				throw new AssertionError("firstPersonInjury = false left the limp running");
			}
			// The panic button turns it off as well.
			context.runOnClient(client -> {
				ClientEffectsConfig.get().firstPersonInjury = true;
				ClientEffectsConfig.get().disableAllEffects();
			});
			if (context.computeOnClient(client -> ClientEffectsConfig.get().firstPersonInjury)) {
				throw new AssertionError("the panic button left the first-person injuries on");
			}
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(5);
		}
	}

	/** Walks forward for a second and a half; returns the deepest dip, furthest left and furthest right lean seen. */
	private static float[] walk(ClientGameTestContext context, String shot) {
		float[] worst = new float[3];
		context.getInput().holdKey(options -> options.keyUp);
		try {
			for (int tick = 0; tick < 30; tick++) {
				context.waitTicks(1);
				float dip = context.computeOnClient(client -> FirstPersonLimp.last().dip());
				float roll = context.computeOnClient(client -> FirstPersonLimp.last().roll());
				worst[0] = Math.max(worst[0], dip);
				worst[1] = Math.min(worst[1], roll);
				worst[2] = Math.max(worst[2], roll);
				if (tick == 14 || tick == 20) {
					context.takeScreenshot(shot + (tick == 14 ? "_a" : "_b"));
				}
			}
		} finally {
			context.getInput().releaseKey(options -> options.keyUp);
		}
		context.waitTicks(10);
		return worst;
	}

	/** Starts a main-hand swing and waits until it is a third of the way through. */
	private static void swing(ClientGameTestContext context) {
		context.runOnClient(client -> client.player.swing(InteractionHand.MAIN_HAND));
		context.waitTicks(2);
	}

	private static long applications(ClientGameTestContext context) {
		return context.computeOnClient(client -> FirstPersonBrokenArm.applications());
	}

	private static void hold(TestSingleplayerContext singleplayer, InteractionHand hand, ItemStack stack) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				player.getInventory().setSelectedSlot(0);
				player.setItemInHand(hand, stack.copy());
			}
		});
	}

	private static void mutate(TestSingleplayerContext singleplayer, Consumer<RealisticDamageData> action) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				action.accept(RDAttachments.get(player));
				RDAttachments.pushSnapshot(player);
			}
		});
	}
}
