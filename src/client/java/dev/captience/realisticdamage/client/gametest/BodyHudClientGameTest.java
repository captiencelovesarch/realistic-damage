package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.BodyHud;
import dev.captience.realisticdamage.client.BodyInspector;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
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
		assertUpgradeKeepsDisabledEffectsDisabled(context);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);
			context.takeScreenshot("rd_00_healthy");

			// Moderate injuries: hurt leg, bleeding arm, broken arm.
			mutate(singleplayer, data -> {
				data.part(BodyPart.LEFT_LEG).setCondition(7.0);
				data.part(BodyPart.RIGHT_ARM).setCondition(4.0);
				data.part(BodyPart.RIGHT_ARM).setBroken(true);
				data.addWound(BodyPart.RIGHT_ARM, WoundSeverity.MODERATE);
				// Every status at once, so the icon row and blood gauge are all
				// exercised in one frame.
				data.setConcussion(true);
				data.setBurned(true);
				data.part(BodyPart.LEFT_ARM).setDestroyed(true);
				data.setBlood(34.0);
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

			// Deep unconsciousness: the greyout chain should be live, so the world
			// genuinely loses its colour rather than being tinted over.
			mutate(singleplayer, data -> {
				data.setBlood(6.0);
				data.drainConsciousness(100.0);
				data.restoreConsciousness(12.0);
			});
			context.waitTicks(30);
			context.takeScreenshot("rd_10_greyout");

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

			// A heavy blow: the impact flash must be on screen the frame it lands.
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(10);
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					dev.captience.realisticdamage.damage.TraumaPipeline.handle(p,
							RDAttachments.get(p),
							p.level().damageSources().mace(p), 12, 12, 12, false);
				}
			});
			context.waitTicks(1);
			context.takeScreenshot("rd_11_impact");

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

			// Inventory inspector: open the inventory with a battered body and check
			// the panel draws.
			mutate(singleplayer, data -> {
				data.part(BodyPart.TORSO).setCondition(6.0);
				data.part(BodyPart.LEFT_ARM).setCondition(3.0);
				data.part(BodyPart.LEFT_ARM).setBroken(true);
				data.addWound(BodyPart.TORSO, WoundSeverity.SEVERE);
				data.setBlood(42.0);
			});
			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(
					net.minecraft.client.Minecraft.getInstance().player));
			context.waitTicks(5);
			context.takeScreenshot("rd_12_inventory");

			// Hover every region with a real cursor. This is the path that shipped
			// in 1.0 unverified, on the belief that the harness could not position
			// a pointer — it can.
			for (BodyPart part : BodyPart.values()) {
				hover(context, part);
				BodyPart seen = context.computeOnClient(client -> BodyInspector.hoveredPart());
				if (seen != part) {
					throw new AssertionError("hovering " + part + " resolved to " + seen);
				}
				context.takeScreenshot("rd_13_hover_" + part.id());
			}

			// Off the body entirely: no region, and therefore no detail box.
			setCursor(context, 4, 4);
			context.waitTicks(2);
			if (context.computeOnClient(client -> BodyInspector.hoveredPart()) != null) {
				throw new AssertionError("a region was hovered with the cursor off the panel");
			}

			// The same panel with numbers turned on: condition figures per region,
			// and the two vitals named by band rather than shown as figures.
			// Given room beside the inventory, switching numbers on must not move
			// the body: the first version sized the panel to the vitals text and
			// positioned from that, which shoved every region sideways and out
			// from under the cursor. A screenshot-only check sailed past it.
			context.getInput().resizeWindow(1280, 720);
			context.waitTicks(3);
			int[] before = context.computeOnClient(client -> BodyInspector.regionCenter(BodyPart.HEAD));
			context.runOnClient(client -> ClientEffectsConfig.get().showNumericStats = true);
			context.waitTicks(3);
			int[] after = context.computeOnClient(client -> BodyInspector.regionCenter(BodyPart.HEAD));
			if (before[0] != after[0] || before[1] != after[1]) {
				throw new AssertionError("the body moved when the numbers were switched on: "
						+ before[0] + "," + before[1] + " -> " + after[0] + "," + after[1]);
			}
			hover(context, BodyPart.LEFT_ARM);
			BodyPart seen = context.computeOnClient(client -> BodyInspector.hoveredPart());
			if (seen != BodyPart.LEFT_ARM) {
				throw new AssertionError("hover broke once numbers were shown: got " + seen);
			}
			context.takeScreenshot("rd_14_hover_numeric");

			// Squeezed into the default window there is genuinely not room for the
			// vitals text beside the inventory, so the panel shifts to stay on
			// screen. That is the right trade, but it must still be usable: on
			// screen, and every region still where the cursor thinks it is.
			context.getInput().resizeWindow(854, 480);
			context.waitTicks(3);
			for (BodyPart part : BodyPart.values()) {
				int[] centre = context.computeOnClient(client -> BodyInspector.regionCenter(part));
				if (centre == null || centre[0] < 0 || centre[1] < 0) {
					throw new AssertionError(part + " left the screen once numbers were shown");
				}
				hover(context, part);
				if (context.computeOnClient(client -> BodyInspector.hoveredPart()) != part) {
					throw new AssertionError("cramped layout broke the hover on " + part);
				}
			}
			context.takeScreenshot("rd_15_numeric_cramped");
			context.runOnClient(client -> ClientEffectsConfig.get().showNumericStats = false);

			context.setScreen(() -> null);
			context.waitTicks(3);

			// A permanent ceiling (1.6): the leg can never come back past three
			// quarters again, and right now it is sitting exactly on that line.
			// That is this player's baseline, not an alarm — so the HUD has to be
			// allowed to fade on it exactly as it does on an unhurt body. Only
			// hurt() can be asked that: a screenshot of a HUD that faded out and a
			// screenshot of one that never drew are the same picture.
			mutate(singleplayer, data -> {
				data.reset();
				data.part(BodyPart.LEFT_LEG).lowerCeilingTo(1);
			});
			context.waitTicks(20);
			int band = snapshot(context).ceilingBand(BodyPart.LEFT_LEG);
			if (band != 1) {
				throw new AssertionError("a ceiling lowered on the server arrived as band " + band);
			}
			if (BodyHud.hurt(snapshot(context))) {
				throw new AssertionError("a body resting at its ceilings kept the HUD pinned open");
			}

			// And the panel says so in words, once — by band. The wrong band would
			// be a quiet lie the screenshot could not catch, so both halves are
			// asserted: the line that must be there and the one that must not.
			context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(
					net.minecraft.client.Minecraft.getInstance().player));
			context.waitTicks(5);
			hover(context, BodyPart.LEFT_LEG);
			java.util.List<String> keys = context.computeOnClient(client -> BodyInspector.lastDetailKeys());
			if (!keys.contains("realisticdamage.detail.ceiling.hurt")
					|| keys.contains("realisticdamage.detail.ceiling.injured")) {
				throw new AssertionError("the hover box described a Hurt ceiling as " + keys);
			}
			context.takeScreenshot("rd_16_capped");

			// Below the ceiling: 8 of 16 under a 12 ceiling. This is the state that
			// still deserves the player's attention, and the fade must come back on
			// for it — otherwise "capped bodies do not raise the HUD" would have
			// quietly become "capped bodies never raise the HUD again".
			mutate(singleplayer, data -> data.part(BodyPart.LEFT_LEG).damage(4.0));
			context.waitTicks(20);
			if (!BodyHud.hurt(snapshot(context))) {
				throw new AssertionError("a capped leg hurt further left the HUD faded out");
			}
			context.takeScreenshot("rd_17_capped_and_hurt");
			context.setScreen(() -> null);
			context.waitTicks(3);

			// The accessibility screen must open and draw.
			context.setScreen(() -> new dev.captience.realisticdamage.client.AccessibilityScreen(null));
			context.waitTicks(5);
			context.takeScreenshot("rd_05_settings");
			context.setScreen(() -> null);
			context.waitTicks(5);
		}
	}

	/**
	 * Look Drift was split out of Look Slowdown in 1.1, so a config written by
	 * 1.0 has no key for it. Defaulting the new key to full strength would switch
	 * the melty camera back on for someone who had deliberately turned it off,
	 * which is the accessibility promise breaking on update.
	 */
	private void assertUpgradeKeepsDisabledEffectsDisabled(ClientGameTestContext context) {
		double migrated = context.computeOnClient(client -> {
			ClientEffectsConfig legacy = new com.google.gson.Gson().fromJson(
					"{\"sensitivityReductionIntensity\":0.0}", ClientEffectsConfig.class);
			legacy.migrate();
			return legacy.lookInertia();
		});
		if (migrated != 0.0) {
			throw new AssertionError("a 1.0 config with look slowdown off came back with drift at "
					+ migrated);
		}
		double fresh = context.computeOnClient(client -> {
			ClientEffectsConfig blank = new ClientEffectsConfig();
			blank.migrate();
			return blank.lookInertia();
		});
		if (fresh != 1.0) {
			throw new AssertionError("a fresh config should default to full drift, got " + fresh);
		}
	}

	/** Puts the real cursor over the centre of a body region and lets it render. */
	private void hover(ClientGameTestContext context, BodyPart part) {
		int[] centre = context.computeOnClient(client -> BodyInspector.regionCenter(part));
		if (centre == null) {
			throw new AssertionError("the inspector never drew " + part);
		}
		setCursor(context, centre[0], centre[1]);
		context.waitTicks(2);
	}

	/**
	 * Cursor positions are in physical window pixels, but the panel lays itself
	 * out in GUI-scaled ones, so the two have to be converted between or every
	 * hover lands in the wrong place on any scale but 1.
	 */
	private void setCursor(ClientGameTestContext context, int guiX, int guiY) {
		double[] raw = context.computeOnClient(client -> {
			var window = client.getWindow();
			double scaleX = (double) window.getScreenWidth() / window.getGuiScaledWidth();
			double scaleY = (double) window.getScreenHeight() / window.getGuiScaledHeight();
			return new double[] {(guiX + 0.5) * scaleX, (guiY + 0.5) * scaleY};
		});
		context.getInput().setCursorPos(raw[0], raw[1]);
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

	/**
	 * The snapshot the client is holding right now. Fetched rather than asserted
	 * against because the interesting questions in 1.6 are about what the client
	 * concluded from it — a band, and whether the HUD is entitled to fade — and
	 * both of those are answered by client code that a screenshot cannot reach.
	 */
	private BodySnapshot snapshot(ClientGameTestContext context) {
		BodySnapshot snapshot = context.computeOnClient(client ->
				client.player == null ? null : client.player.getAttached(RDAttachments.SNAPSHOT));
		if (snapshot == null) {
			throw new AssertionError("the client is holding no body snapshot");
		}
		return snapshot;
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
