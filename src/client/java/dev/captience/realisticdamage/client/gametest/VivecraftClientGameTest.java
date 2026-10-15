package dev.captience.realisticdamage.client.gametest;

import java.util.function.Consumer;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.Afterimage;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.VrLens;
import dev.captience.realisticdamage.client.compat.Vr;
import dev.captience.realisticdamage.systems.ImpairmentSystem;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Realistic Damage under Vivecraft, for real (1.7 alpha.10). Runs only when
 * Vivecraft is on the runtime classpath — {@code ./gradlew runClientGametest
 * -PwithVivecraft} — and is a no-op otherwise.
 *
 * <p>No headset needed: Vivecraft's NullVR provider renders both eyes exactly as
 * a headset run does, just to nowhere. Everything the user's crash report and
 * the compat audit turned up is driven here: entering VR and a world, the per-eye
 * lens and afterimage, integrators stepping once per frame and not once per
 * eye, the limp and shaking hand that used to unbalance the pose stack under
 * Vivecraft's own hand renderer, a hit and a landing (haptics), and a death
 * and respawn.
 */
public class VivecraftClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (!FabricLoader.getInstance().isModLoaded("vivecraft")) {
			return;
		}
		context.runOnClient(client -> ClientEffectsConfig.load());
		context.runOnClient(client -> NullVr.start());
		context.waitFor(client -> Vr.active(), 400);
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(40);
			if (!Vr.active()) {
				throw new AssertionError("VR stopped running on entering the world");
			}
			singleplayer.getServer().runOnServer(server -> server.getCommands().performPrefixedCommand(
					server.createCommandSourceStack(), "time set 6000"));
			context.waitTicks(10);
			double healthy = eye(context, "rd_vive_00_healthy");

			// Fading: the lens and the afterimage, on both eyes, once a frame.
			mutate(singleplayer, data -> {
				data.setBlood(35.0);
				data.drainConsciousness(70.0);
			});
			context.waitTicks(30);
			long frames = context.computeOnClient(client -> VrLens.frames());
			long eyes = context.computeOnClient(client -> VrLens.eyesProcessed());
			long ghosts = context.computeOnClient(client -> Afterimage.framesProcessed());
			context.waitTicks(40);
			long dFrames = context.computeOnClient(client -> VrLens.frames()) - frames;
			long dEyes = context.computeOnClient(client -> VrLens.eyesProcessed()) - eyes;
			long dGhosts = context.computeOnClient(client -> Afterimage.framesProcessed()) - ghosts;
			if (dFrames < 4) {
				throw new AssertionError("only " + dFrames + " world frames in two seconds of VR; too few to judge");
			}
			// Two eyes a frame, give or take the frame the window opened on.
			if (dEyes < dFrames * 2 - 2 || dEyes > dFrames * 2 + 2) {
				throw new AssertionError("the lens ran on " + dEyes + " eye passes over " + dFrames
						+ " frames; expected two a frame");
			}
			if (context.computeOnClient(client -> Afterimage.strength()) > 0.01
					&& (dGhosts < dFrames * 2 - 2 || dGhosts > dFrames * 2 + 2)) {
				throw new AssertionError("the afterimage ran " + dGhosts + " times over " + dFrames
						+ " frames; expected once per eye");
			}
			String state = context.computeOnClient(client -> "consciousness "
					+ ClientState.effectiveConsciousness() + ", vignette "
					+ dev.captience.realisticdamage.client.ConsciousnessEffects.lastVignetteStrength()
					+ ", washout " + dev.captience.realisticdamage.client.ConsciousnessEffects.lastExposureAlpha()
					+ ", ghost " + Afterimage.strength() + ", lens ops " + VrLens.publishedOps()
					+ ", frames " + VrLens.frames() + ", published at " + VrLens.publishedFrame()
					+ ", eyes " + VrLens.eyesProcessed() + ", ghosts " + Afterimage.framesProcessed()
					+ ", fps " + client.getFps());
			dev.captience.realisticdamage.RealisticDamage.LOGGER.info("VR fading state: {}", state);
			double fading = eye(context, "rd_vive_01_fading");
			if (Math.abs(fading - healthy) < 0.01) {
				throw new AssertionError("fading in VR and the eye looks healthy (" + fading + " vs " + healthy
						+ "); " + state);
			}

			// The limp arm and the shaking hands: under Vivecraft's own hand
			// renderer these pushed a pose and never popped it.
			mutate(singleplayer, RealisticDamageData::reset);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
				player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
				RealisticDamageData data = RDAttachments.get(player);
				data.part(ImpairmentSystem.armFor(player, false)).setCondition(0.0);
				data.part(BodyPart.LEFT_LEG).setCondition(2.0);
				player.getFoodData().setFoodLevel(6);
				player.setHealth(3.0F);
				RDAttachments.pushSnapshot(player);
			});
			context.waitTicks(60);
			eye(context, "rd_vive_02_limp_and_shaking");

			// A lost arm (1.7 alpha.11): Vivecraft drew the whole VR hand anyway.
			long skipped = context.computeOnClient(client -> dev.captience.realisticdamage.client.LostHands.vrHandsSkipped());
			long stumps = context.computeOnClient(client -> dev.captience.realisticdamage.client.FirstPersonBlood.stumpSubmits());
			mutate(singleplayer, data -> data.part(BodyPart.LEFT_ARM).setDestroyed(true));
			context.waitTicks(20);
			if (context.computeOnClient(client -> dev.captience.realisticdamage.client.LostHands.vrHandsSkipped()) <= skipped) {
				throw new AssertionError("lost the left arm in VR and the controller still held its item");
			}
			if (context.computeOnClient(client -> dev.captience.realisticdamage.client.FirstPersonBlood.stumpSubmits()) <= stumps) {
				throw new AssertionError("lost the left arm in VR and no stump was drawn on the controller");
			}
			eye(context, "rd_vive_02b_lost_left_arm");

			// Blood on the arm you can see: Vivecraft's hands never got it before.
			long painted = context.computeOnClient(client -> dev.captience.realisticdamage.client.FirstPersonBlood.bloodSubmits());
			mutate(singleplayer, data -> data.addWound(BodyPart.RIGHT_ARM, dev.captience.realisticdamage.body.WoundSeverity.CRITICAL));
			context.waitTicks(80);
			if (context.computeOnClient(client -> dev.captience.realisticdamage.client.FirstPersonBlood.bloodSubmits()) <= painted) {
				throw new AssertionError("a bleeding right arm in VR and no blood was painted on the controller's arm");
			}
			eye(context, "rd_vive_02c_bleeding_right_arm");

			// A hit and a landing: the view must not turn, the hands get it instead.
			float yawBefore = context.computeOnClient(client -> client.player.getYRot());
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.setHealth(player.getMaxHealth());
				player.hurtServer(player.level(), player.level().damageSources().generic(), 6.0F);
			});
			context.waitTicks(20);
			float yawAfter = context.computeOnClient(client -> client.player.getYRot());
			if (Math.abs(yawAfter - yawBefore) > 0.5F) {
				throw new AssertionError("a blow turned the view in VR: yaw " + yawBefore + " -> " + yawAfter);
			}

			// Dying in VR, and the way back out.
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.hurtServer(player.level(), player.level().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
				}
			});
			context.waitTicks(10);
			if (!context.computeOnClient(client -> ClientState.dying())) {
				throw new AssertionError("the player did not die in VR");
			}
			context.waitTicks(160);
			double dead = eye(context, "rd_vive_03_dead");
			if (dead > healthy - 0.1) {
				throw new AssertionError("dead in VR and the eye is barely darker: " + dead + " vs " + healthy);
			}
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			if (context.computeOnClient(client -> ClientState.dying())) {
				throw new AssertionError("respawned in VR and still dying");
			}
			eye(context, "rd_vive_04_respawned");
		} finally {
			context.runOnClient(client -> NullVr.stop());
			context.waitTicks(5);
		}
	}

	/**
	 * The left eye as the headset gets it, after Vivecraft's own post pass. Not
	 * {@code context.takeScreenshot}: that renders one extra frame of its own,
	 * outside Vivecraft's eye loop, so it never shows what an eye sees.
	 */
	private static double eye(ClientGameTestContext context, String name) {
		context.waitTicks(2);
		java.util.concurrent.CompletableFuture<Double> luma = context.computeOnClient(client -> NullVr.eyeLuma(name));
		while (!luma.isDone()) {
			context.waitTick();
		}
		return luma.join();
	}

	private static void mutate(TestSingleplayerContext singleplayer, Consumer<RealisticDamageData> change) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				change.accept(RDAttachments.get(player));
				RDAttachments.pushSnapshot(player);
			}
		});
	}

	/** Vivecraft's internals, reached only once it is known to be loaded. */
	private static final class NullVr {
		static void start() {
			var settings = org.vivecraft.client_vr.ClientDataHolderVR.getInstance().vrSettings;
			settings.stereoProviderPluginID = org.vivecraft.client_vr.settings.VRSettings.VRProvider.NULLVR;
			// The menu world is downloaded on first start; a test must not need the network.
			settings.menuWorldSelection = org.vivecraft.client_vr.settings.VRSettings.MenuWorld.NONE;
			settings.disableGarbageCollectorMessage = true;
			settings.vrEnabled = true;
			org.vivecraft.client_vr.VRState.VR_ENABLED = true;
		}

		static java.util.concurrent.CompletableFuture<Double> eyeLuma(String name) {
			var target = org.vivecraft.client_vr.ClientDataHolderVR.getInstance().vrRenderer.framebufferEye[0];
			var result = new java.util.concurrent.CompletableFuture<Double>();
			net.minecraft.client.Screenshot.takeScreenshot(target, image -> {
				try (image) {
					java.nio.file.Path dir = net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath()
							.resolve("screenshots");
					java.nio.file.Files.createDirectories(dir);
					image.writeToFile(dir.resolve(name + ".png"));
					result.complete(VrLensClientGameTest.centreLuma(image));
				} catch (Throwable e) {
					result.completeExceptionally(e);
				}
			});
			return result;
		}

		static void stop() {
			org.vivecraft.client_vr.ClientDataHolderVR.getInstance().vrSettings.vrEnabled = false;
			org.vivecraft.client_vr.VRState.VR_ENABLED = false;
		}
	}
}
