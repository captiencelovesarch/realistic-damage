package dev.captience.realisticdamage.client.gametest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ConsciousnessEffects;
import dev.captience.realisticdamage.client.VrLens;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * The VR lens, without a headset (1.7 alpha.10). In VR the screen effects are
 * recorded off the HUD and replayed onto each eye by the {@code vr_lens} chain;
 * this forces that same route through the flat-play render, where a screenshot
 * can see it, and holds it to the HUD draw it replaces. Agreement is judged on
 * the middle of the frame, where neither has a hotbar on top.
 *
 * <p>The Vivecraft-specific half — two eyes, the pass gating, the hand — is
 * {@link VivecraftClientGameTest}, which only runs with Vivecraft present.
 */
public class VrLensClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		context.runOnClient(client -> ClientEffectsConfig.load());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			singleplayer.getServer().runOnServer(server -> server.getCommands().performPrefixedCommand(
					server.createCommandSourceStack(), "time set 6000"));
			context.waitTicks(10);
			float pitch = context.computeOnClient(client -> client.player.getXRot());
			double healthy = centreLuma(context.takeScreenshot("rd_vrlens_00_healthy"));

			// Fading: vignettes, washout, afterimage. HUD first, then the lens.
			mutate(singleplayer, data -> {
				data.setBlood(35.0);
				data.drainConsciousness(70.0);
			});
			context.waitTicks(30);
			double hud = centreLuma(context.takeScreenshot("rd_vrlens_01_fading_hud"));
			long eyes = context.computeOnClient(client -> VrLens.eyesProcessed());
			context.runOnClient(client -> VrLens.forceInFlat(true));
			context.waitTicks(5);
			double lens = centreLuma(context.takeScreenshot("rd_vrlens_02_fading_lens"));
			int ops = context.computeOnClient(client -> VrLens.publishedOps());
			if (ops <= 0) {
				throw new AssertionError("fading, and the lens recorded nothing");
			}
			if (context.computeOnClient(client -> VrLens.eyesProcessed()) <= eyes) {
				throw new AssertionError("the lens recorded " + ops + " ops and never drew them");
			}
			if (Math.abs(hud - healthy) < 0.01) {
				throw new AssertionError("the fading HUD frame looks healthy (" + hud + " vs " + healthy
						+ "); this test would prove nothing");
			}
			// The lens must land where the HUD did, not merely somewhere darker.
			double shift = hud - healthy;
			double lensShift = lens - healthy;
			if (Math.signum(shift) != Math.signum(lensShift) || Math.abs(lensShift - shift) > Math.abs(shift) * 0.5 + 0.02) {
				throw new AssertionError("the lens and the HUD disagree: healthy " + healthy + ", HUD " + hud
						+ ", lens " + lens);
			}

			// Unconscious: the blackout, the one thing that must cover the eye.
			mutate(singleplayer, data -> data.drainConsciousness(100.0));
			context.waitTicks(40);
			double level = context.computeOnClient(client -> ConsciousnessEffects.lastBlackoutLevel());
			double dark = centreLuma(context.takeScreenshot("rd_vrlens_03_unconscious_lens"));
			if (level < 0.8 || dark > 0.12) {
				throw new AssertionError("unconscious through the lens: blackout " + level + ", centre luma " + dark);
			}

			// Recovered, still through the lens: nothing may be left on it. The
			// head sagged while unconscious (CameraDrift), so the view is levelled
			// again first — a frame full of grass is darker than one half sky.
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(60);
			context.runOnClient(client -> client.player.setXRot(pitch));
			context.waitTicks(5);
			if (context.computeOnClient(client -> VrLens.publishedOps()) != 0) {
				throw new AssertionError("recovered, and the lens still records "
						+ context.computeOnClient(client -> VrLens.publishedOps()) + " ops");
			}
			double recovered = centreLuma(context.takeScreenshot("rd_vrlens_04_recovered"));
			context.runOnClient(client -> VrLens.forceInFlat(false));
			if (Math.abs(recovered - healthy) > 0.08) {
				throw new AssertionError("the lens left something behind: healthy " + healthy
						+ ", recovered " + recovered);
			}
		} finally {
			context.runOnClient(client -> VrLens.forceInFlat(false));
		}
	}

	private static void mutate(TestSingleplayerContext singleplayer, Consumer<RealisticDamageData> change) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				change.accept(RDAttachments.get(player));
				RDAttachments.pushSnapshot(player);
			}
		});
	}

	/** Mean luma, 0 to 1, of the middle third of a screenshot. */
	static double centreLuma(Path screenshot) {
		try (NativeImage image = NativeImage.read(java.nio.file.Files.newInputStream(screenshot))) {
			return centreLuma(image);
		} catch (IOException e) {
			throw new AssertionError("could not read " + screenshot, e);
		}
	}

	/** Mean luma, 0 to 1, of the middle third of an image. */
	static double centreLuma(NativeImage image) {
		{
			int x0 = image.getWidth() / 3;
			int y0 = image.getHeight() / 3;
			double sum = 0.0;
			int count = 0;
			for (int y = y0; y < 2 * y0; y += 2) {
				for (int x = x0; x < 2 * x0; x += 2) {
					int argb = image.getPixel(x, y);
					sum += (0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF)
							+ 0.0722 * (argb & 0xFF)) / 255.0;
					count++;
				}
			}
			return sum / Math.max(1, count);
		}
	}
}
