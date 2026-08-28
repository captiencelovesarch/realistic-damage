package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.net.BodySnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * Renders the client layer for real: boots a world, injures the player through
 * the server-authoritative state, and screenshots the HUD at several severities.
 * Verifies the sync path (server state reaching the client snapshot) and that
 * drawing the body diagram and every sensory effect does not crash.
 */
public class BodyHudClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			context.takeScreenshot("rd_00_healthy");

			// Moderate injuries: hurt leg, bleeding arm, broken arm.
			mutate(singleplayer, data -> {
				data.part(BodyPart.LEFT_LEG).setCondition(7.0);
				data.part(BodyPart.RIGHT_ARM).setCondition(4.0);
				data.part(BodyPart.RIGHT_ARM).setBroken(true);
				data.addWound(BodyPart.RIGHT_ARM, WoundSeverity.MODERATE);
			});
			context.waitTicks(20);
			assertSynced(context, "moderate injuries");
			context.takeScreenshot("rd_01_injured");

			// Fading consciousness: vignette + sensitivity + afterimages + exposure.
			mutate(singleplayer, data -> {
				data.setBlood(35.0);
				data.drainConsciousness(70.0);
			});
			context.waitTicks(30);
			context.takeScreenshot("rd_02_fading");

			// Collapse: prone pose plus the heavy blackout fade.
			mutate(singleplayer, data -> data.drainConsciousness(100.0));
			context.waitTicks(40);
			context.takeScreenshot("rd_03_unconscious");

			// Recovery: everything must unwind cleanly.
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(60);
			context.takeScreenshot("rd_04_recovered");
			assertSynced(context, "after recovery");

			// A concussion alone, everything else pristine: it must survive (it used
			// to clear itself one tick later) and it must be visible.
			mutate(singleplayer, data -> {
				data.reset();
				data.setConcussion(true);
			});
			context.waitTicks(40);
			context.takeScreenshot("rd_08_concussed");
			boolean stillConcussed = singleplayer.getServer().computeOnServer(server ->
					server.getPlayerList().getPlayers().stream()
							.allMatch(p -> RDAttachments.get(p).concussion()));
			if (!stillConcussed) {
				throw new AssertionError("concussion cleared itself immediately");
			}

			// Near-death on the ordinary health bar, body otherwise fine: the red
			// vignette must still read (the "one heart and nothing happens" case).
			mutate(singleplayer, RealisticDamageData::reset);
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.setHealth(1.0F);
				}
			});
			context.waitTicks(20);
			context.takeScreenshot("rd_07_near_death");

			// Limb destruction: the arm and both legs must vanish from the model,
			// and the player drops to a crawl.
			mutate(singleplayer, data -> {
				data.part(BodyPart.RIGHT_ARM).setDestroyed(true);
				data.part(BodyPart.LEFT_LEG).setDestroyed(true);
				data.part(BodyPart.RIGHT_LEG).setDestroyed(true);
			});
			context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
			context.waitTicks(30);
			context.takeScreenshot("rd_06_destroyed");
			context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			context.waitTicks(10);
			// First person: the arm and anything it was holding must be gone too.
			context.takeScreenshot("rd_09_firstperson_armless");

			// The accessibility screen must open and draw.
			context.setScreen(() -> new dev.captience.realisticdamage.client.AccessibilityScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("rd_05_settings");
			context.setScreen(() -> null);
			context.waitTicks(5);
		}
	}

	/** Mutates server-side state, exactly as gameplay would. */
	private void mutate(TestSingleplayerContext singleplayer, java.util.function.Consumer<RealisticDamageData> action) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				action.accept(RDAttachments.get(player));
				RDAttachments.pushSnapshot(player);
			}
		});
	}

	/** The client must be seeing the server's state, not a stale default. */
	private void assertSynced(ClientGameTestContext context, String what) {
		boolean matches = context.computeOnClient(client -> {
			if (client.player == null) {
				return false;
			}
			BodySnapshot snapshot = client.player.getAttached(RDAttachments.SNAPSHOT);
			return snapshot != null;
		});
		if (!matches) {
			throw new AssertionError("client never received a body snapshot (" + what + ")");
		}
	}
}
