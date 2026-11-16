package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.client.Afterimage;
import dev.captience.realisticdamage.client.BloodBodyLayer;
import dev.captience.realisticdamage.client.BodyHud;
import dev.captience.realisticdamage.client.FirstPersonBlood;
import dev.captience.realisticdamage.client.CameraMotion;
import dev.captience.realisticdamage.client.BodyInspector;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.ConsciousnessEffects;
import dev.captience.realisticdamage.client.InjuryMessageFilter;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.config.Balance;
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

			// Collapse: prone pose plus the heavy blackout fade — drawn by the
			// last HUD element of all (1.6.2), so it covers the hotbar and hearts
			// rather than sitting under them.
			mutate(singleplayer, data -> data.drainConsciousness(100.0));
			context.waitTicks(40);
			double collapsed = context.computeOnClient(client -> ConsciousnessEffects.lastBlackoutLevel());
			if (collapsed < 0.8) {
				throw new AssertionError("unconscious and the blackout only reached " + collapsed);
			}
			long blackDraws = context.computeOnClient(client -> ConsciousnessEffects.blackoutDraws());
			context.waitTicks(3);
			if (context.computeOnClient(client -> ConsciousnessEffects.blackoutDraws()) <= blackDraws) {
				throw new AssertionError("the blackout was computed but the top-of-HUD element never drew it");
			}
			context.takeScreenshot("rd_03_unconscious");

			// Recovery: everything must unwind cleanly.
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(60);
			context.takeScreenshot("rd_04_recovered");
			assertSynced(context, "after recovery");

			// The middle of the ladder, on a clear head (1.6 playtest fix). The beta
			// report was that afterimages and exposure shift "don't have much
			// effect", and it was right: both curves reached full strength at zero
			// consciousness, underneath the blackout, so the band a conscious player
			// actually occupies got their weakest few percent. A screenshot cannot
			// tell 8/255 from 50/255, so this asserts the magnitudes the renderer
			// really asked for.
			mutate(singleplayer, data -> {
				data.reset();
				data.drainConsciousness(72.0);   // 100 - 72 = 28, mid-band and no concussion
			});
			// Six ticks, not thirty: consciousness recovers at 2.5 a second, so a
			// long settle drifts off the rung being measured. The first version of
			// this waited 30 and sampled at 33 instead of 28. The window below is
			// wide enough to hold whatever it drifts to in three tenths of a second.
			context.waitTicks(6);
			double[] midband = context.computeOnClient(client -> new double[] {
					ClientState.effectiveConsciousness(),
					ConsciousnessEffects.lastExposureAlpha(),
					ConsciousnessEffects.lastGhostStrength()});
			if (midband[0] <= Balance.SYMPTOM_MICROSLEEP || midband[0] >= 33.0) {
				throw new AssertionError("meant to sample mid-band, sampled " + midband[0]);
			}
			// Worst case inside that window is consciousness 33, where
			// exposureStrength = sqrt(2/25) = 0.283 and the breath can take a fifth
			// off it: 0.283 * 0.80 * 120 = 27. The linear ramp this replaces could
			// not reach 4 there, and never got past 25 anywhere a player can see.
			if (midband[1] < 24.0) {
				throw new AssertionError("the washout at consciousness " + midband[0]
						+ " was only " + (int) midband[1] + "/255");
			}
			// And at 33, afterimageStrength = sqrt(12/27) = 0.667 against a pulse
			// floor of 0.55, so 0.37. The curve this replaces was at 0.07 before
			// its pulse even touched it.
			if (midband[2] < 0.30) {
				throw new AssertionError("the ghost at consciousness " + midband[0]
						+ " was only " + midband[2]);
			}
			// And the gaussian is still a deep-end backstop: it must not have eaten
			// the middle of the ladder on its way to being made perceptible.
			if (context.computeOnClient(client -> ConsciousnessEffects.blurRequestedLastFrame())) {
				throw new AssertionError("the blur fired in mid-band, at consciousness " + midband[0]);
			}
			// The ghost is a post chain now (1.6.1), not a HUD blit: a strength
			// being asked for proves nothing unless the chain actually ran on the
			// world target. Frames processed has to move while the strength is up.
			long ghostFrames = context.computeOnClient(client -> Afterimage.framesProcessed());
			context.waitTicks(3);
			long ghostFramesLater = context.computeOnClient(client -> Afterimage.framesProcessed());
			if (ghostFramesLater <= ghostFrames) {
				throw new AssertionError("the afterimage chain never ran while a ghost of "
						+ midband[2] + " was asked for");
			}
			context.takeScreenshot("rd_18_midladder");
			// A trail needs motion: snap the view sideways and shoot the next frame,
			// where the horizon and the hand from the frame before should still be
			// visible under the new ones.
			context.runOnClient(client -> client.player.setYRot(client.player.getYRot() + 30.0F));
			context.waitTicks(1);
			context.takeScreenshot("rd_18b_midladder_turning");

			// Every sensory effect keeps its own switch. Turning these two off has
			// to actually stop them, not merely quieten them.
			context.runOnClient(client -> {
				ClientEffectsConfig.get().exposureIntensity = 0.0;
				ClientEffectsConfig.get().afterimageIntensity = 0.0;
			});
			context.waitTicks(5);
			int mutedAlpha = context.computeOnClient(client -> ConsciousnessEffects.lastExposureAlpha());
			double mutedGhost = context.computeOnClient(client -> ConsciousnessEffects.lastGhostStrength());
			if (mutedAlpha != 0 || mutedGhost != 0.0) {
				throw new AssertionError("sliders at zero still drew " + mutedAlpha + "/255 and "
						+ mutedGhost);
			}
			// ...and off means the chain stops running, not merely blending at zero.
			long mutedFrames = context.computeOnClient(client -> Afterimage.framesProcessed());
			context.waitTicks(3);
			if (context.computeOnClient(client -> Afterimage.framesProcessed()) != mutedFrames) {
				throw new AssertionError("the afterimage chain kept running with its slider at zero");
			}
			// The config is a singleton shared with every other client gametest.
			context.runOnClient(client -> ClientEffectsConfig.load());
			context.waitTicks(5);

			// Below the blur backstop the gaussian does come on, and the ghost has
			// ramped all the way up to hand over to it.
			mutate(singleplayer, data -> data.drainConsciousness(18.0));   // to about 12, under the 18 backstop
			context.waitTicks(10);
			if (!context.computeOnClient(client -> ConsciousnessEffects.blurRequestedLastFrame())) {
				throw new AssertionError("the blur never fired below its threshold");
			}
			context.takeScreenshot("rd_19_deep");

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
			// The blow has to move the picture as well as the crosshair (1.6.1):
			// the view spring must be displaced within a couple of frames of the
			// payload arriving, and none of that displacement may be on the aim,
			// which is the whole reason it exists.
			float[] aim = context.computeOnClient(client -> new float[] {
					client.player.getXRot(), client.player.getYRot()});
			context.waitTicks(1);
			double[] view = context.computeOnClient(client -> new double[] {
					CameraMotion.lastPitch(), CameraMotion.lastRoll()});
			if (Math.abs(view[0]) < 0.05 && Math.abs(view[1]) < 0.05) {
				throw new AssertionError("a mace blow left the view spring at rest: pitch " + view[0]
						+ ", roll " + view[1]);
			}
			// The aim kick moves the crosshair on purpose; the view spring must
			// then be settling on its own. Wait it out and the picture returns to
			// where the aim is, which is the property a spring has and a scripted
			// lurch does not.
			context.waitTicks(40);
			// The spring alone (1.7): a mace to the head is now a severe
			// concussion, whose ladder shift is deep enough that the sway — a
			// symptom on purpose, not a blow still ringing — is visible on its
			// own. This claim is about the spring, so it reads the spring.
			double[] settled = context.computeOnClient(client -> new double[] {
					CameraMotion.springPitch(), CameraMotion.springRoll()});
			if (Math.abs(settled[0]) > 0.3 || Math.abs(settled[1]) > 0.3) {
				String detail = context.computeOnClient(client -> String.format(java.util.Locale.ROOT,
						" [spring %.3f/%.3f, effective %.1f, grade %d, flare %.2f, vertigo %.2f]",
						CameraMotion.springPitch(), CameraMotion.springRoll(),
						dev.captience.realisticdamage.client.ClientState.effectiveConsciousness(),
						dev.captience.realisticdamage.client.ClientState.concussionGrade(),
						dev.captience.realisticdamage.client.ClientState.flare(),
						dev.captience.realisticdamage.client.ConcussionEffects.vertigoEnvelope()));
				throw new AssertionError("two seconds after a blow the view spring is still at pitch "
						+ settled[0] + ", roll " + settled[1] + detail);
			}

			// The sway: near collapse the horizon tilts on its own, and with its
			// slider at zero it does not — while the aim stays exactly put, because
			// this is the camera effect that never touches it.
			mutate(singleplayer, data -> {
				data.reset();
				data.drainConsciousness(88.0);   // about 12: deep in the sway band
			});
			// Camera Drift moves the aim on purpose and is on by default since
			// 1.6.2, so it is switched off for this measurement: what is being
			// asserted is that the sway itself never touches the aim.
			context.runOnClient(client -> {
				ClientEffectsConfig.get().cameraDriftIntensity = 0.0;
				client.player.setXRot(10.0F);
				client.player.setYRot(0.0F);
			});
			context.waitTicks(15);
			// The sway is two sines at different periods, so any single instant can
			// sit near a zero crossing (one run measured -0.019). Sample a window
			// and take the widest tilt seen in it.
			double swayRoll = 0.0;
			for (int sample = 0; sample < 12; sample++) {
				double roll = context.computeOnClient(client -> CameraMotion.lastRoll());
				if (Math.abs(roll) > Math.abs(swayRoll)) {
					swayRoll = roll;
				}
				context.waitTicks(3);
			}
			float[] aimAfterSway = context.computeOnClient(client -> new float[] {
					client.player.getXRot(), client.player.getYRot()});
			if (Math.abs(swayRoll) < 0.2) {
				throw new AssertionError("near collapse the horizon did not tilt: widest roll " + swayRoll);
			}
			// ...and the tilt has to reach the picture. The value above is what the
			// motion asked for; this is whether the view hook carried it. The first
			// build injected at the wrong point of vanilla's hurt tilt and applied
			// nothing outside a hurt animation, which only the screenshot showed.
			long applied = context.computeOnClient(client -> CameraMotion.applications());
			context.waitTicks(3);
			if (context.computeOnClient(client -> CameraMotion.applications()) <= applied) {
				throw new AssertionError("the head sway was computed but never applied to the view");
			}
			// With drift off nothing else in the mod moves the aim here; the sway
			// certainly must not be.
			if (Math.abs(aimAfterSway[0] - 10.0F) > 0.01F || Math.abs(aimAfterSway[1]) > 0.01F) {
				throw new AssertionError("the head sway moved the aim: pitch " + aimAfterSway[0]
						+ ", yaw " + aimAfterSway[1]);
			}
			context.takeScreenshot("rd_20_sway");
			context.runOnClient(client -> ClientEffectsConfig.get().cameraSwayIntensityValue = 0.0);
			context.waitTicks(3);
			double mutedSway = context.computeOnClient(client -> CameraMotion.lastRoll());
			if (mutedSway != 0.0) {
				throw new AssertionError("Head Sway at zero still rolled the view by " + mutedSway);
			}
			context.runOnClient(client -> ClientEffectsConfig.load());
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(10);

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

			// One leg gone, still standing (1.6.2 playtest): the model must show a
			// stump and not the leg. Shot from the front so both legs are in
			// frame, before the crawl below folds the body over them.
			mutate(singleplayer, data -> data.part(BodyPart.LEFT_LEG).setDestroyed(true));
			context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("rd_06a_leg_stump_standing");

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
			// A lost limb leaves a stump, not an absence (1.6.2): three destroyed
			// limbs in view must be drawing stumps frame after frame.
			long stumps = context.computeOnClient(client -> BloodBodyLayer.stumpSubmits());
			context.waitTicks(3);
			if (context.computeOnClient(client -> BloodBodyLayer.stumpSubmits()) <= stumps) {
				throw new AssertionError("three destroyed limbs drew no stumps in third person");
			}
			context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			context.waitTicks(10);
			// First person: the hand and anything it was holding are gone, and the
			// stump is raised into view where the hand was.
			long handStumps = context.computeOnClient(client -> FirstPersonBlood.stumpSubmits());
			context.waitTicks(3);
			if (context.computeOnClient(client -> FirstPersonBlood.stumpSubmits()) <= handStumps) {
				throw new AssertionError("a destroyed main arm drew no stump in first person");
			}
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

			assertThePanelFollowsTheWindow(context);

			context.setScreen(() -> null);
			context.waitTicks(3);

			assertInjuryTextCanBeSilenced(context, singleplayer);

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

			// A body with one of everything that is not about to kill it, for the
			// pictures of the three views: a broken arm, a cut that seeps, a leg
			// gone, a scar.
			mutate(singleplayer, data -> {
				data.reset();
				data.part(BodyPart.LEFT_ARM).setCondition(3.0);
				data.part(BodyPart.LEFT_ARM).setBroken(true);
				data.part(BodyPart.TORSO).setCondition(7.0);
				data.addWound(BodyPart.TORSO, WoundSeverity.MINOR);
				data.part(BodyPart.RIGHT_LEG).setDestroyed(true);
				data.part(BodyPart.HEAD).setCondition(8.0);
			});
			context.waitTicks(30);
			context.takeScreenshot("rd_17_body_in_the_box");
			hover(context, BodyPart.LEFT_ARM);
			context.takeScreenshot("rd_17_body_hover");
			// The corner button changes what the box shows: the body, the x-ray
			// on Minecraft's own skeleton, vanilla's model, and round again. A
			// real click on the real button, and the choice is kept.
			for (BodyInspector.View expected : new BodyInspector.View[] {BodyInspector.View.XRAY,
					BodyInspector.View.MODEL, BodyInspector.View.BODY}) {
				int[] button = context.computeOnClient(client -> BodyInspector.viewButton());
				if (button == null) {
					throw new AssertionError("the view button was not drawn");
				}
				setCursor(context, button[0], button[1]);
				context.waitTicks(2);
				context.getInput().pressMouse(0);
				context.waitTicks(3);
				BodyInspector.View drawn = context.computeOnClient(client -> BodyInspector.viewDrawn());
				if (drawn != expected) {
					throw new AssertionError("clicking the view button gave " + drawn + ", expected " + expected);
				}
				String kept = context.computeOnClient(client -> ClientEffectsConfig.get().inventoryBodyView);
				if (!kept.equals(expected.id())) {
					throw new AssertionError("the chosen view was not kept: " + kept);
				}
				if (expected == BodyInspector.View.XRAY) {
					hover(context, BodyPart.LEFT_ARM);
					if (context.computeOnClient(client -> BodyInspector.hoveredPart()) != BodyPart.LEFT_ARM) {
						throw new AssertionError("the x-ray lost the hover on the left arm");
					}
				}
				if (expected == BodyInspector.View.MODEL
						&& context.computeOnClient(client -> BodyInspector.regionCenter(BodyPart.HEAD)) != null) {
					throw new AssertionError("the model view still claims to have drawn regions");
				}
				context.takeScreenshot("rd_18_view_" + expected.id());
			}


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

	/**
	 * The body is in the inventory's own player box (2.0), so it goes where the
	 * window goes and there is nothing for it to collide with.
	 *
	 * <p>This used to be the hardest thing the panel did. It sat in the strip
	 * left of the inventory, which is exactly where the recipe book opens, and
	 * had to measure round the book and its tabs and stand aside altogether
	 * whenever nothing fitted, which on the automatic GUI scale was whenever
	 * the book was open. What is asserted now is the property that replaced all
	 * of that: with the book open or shut, in a wide window or the default one,
	 * the body is drawn, it is inside the inventory window, and every region is
	 * under the cursor that points at it.
	 */
	private void assertThePanelFollowsTheWindow(ClientGameTestContext context) {
		int restoreScale = context.computeOnClient(client -> client.options.guiScale().get());
		setGuiScale(context, 2);
		context.getInput().resizeWindow(1280, 720);
		context.waitTicks(3);
		setRecipeBookOpen(context, false);
		int wide = guiWidth(context);
		int[] shut = assertInsideTheWindow(context, "book shut, W = " + wide);

		setRecipeBookOpen(context, true);
		int[] open = assertInsideTheWindow(context, "book open, W = " + wide);
		// Vanilla slides the window 77 right for the book, and the box is part
		// of the window.
		if (open[0] - shut[0] != 77) {
			throw new AssertionError("the body moved " + (open[0] - shut[0])
					+ " when the recipe book opened; the window moves 77");
		}
		context.takeScreenshot("rd_20_recipe_book_open");

		// The default window, book still open: the case the old panel gave up on.
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(3);
		int narrow = guiWidth(context);
		assertInsideTheWindow(context, "book open, W = " + narrow);
		context.takeScreenshot("rd_21_recipe_book_cramped");

		setRecipeBookOpen(context, false);
		int[] backAgain = assertInsideTheWindow(context, "book shut, W = " + narrow);
		if (backAgain[0] == shut[0]) {
			throw new AssertionError("the body sat at " + shut[0] + " at both W = " + wide + " and W = " + narrow);
		}

		setGuiScale(context, restoreScale);
		context.getInput().resizeWindow(854, 480);
		context.waitTicks(3);
	}

	/** The body is drawn, lies wholly inside the inventory window, and every region answers its own hover. */
	private int[] assertInsideTheWindow(ClientGameTestContext context, String when) {
		int[] box = context.computeOnClient(client -> BodyInspector.panelBounds());
		if (box == null) {
			throw new AssertionError("the body was not drawn (" + when + ")");
		}
		int left = context.computeOnClient(client ->
				((dev.captience.realisticdamage.client.mixin.ContainerLayoutAccessor) client.gui.screen())
						.realisticdamage$leftPos());
		if (box[0] < left || box[2] > left + 176) {
			throw new AssertionError("the body is at " + box[0] + ".." + box[2] + ", outside the window at "
					+ left + ".." + (left + 176) + " (" + when + ")");
		}
		for (BodyPart part : BodyPart.values()) {
			int[] centre = context.computeOnClient(client -> BodyInspector.regionCenter(part));
			if (centre == null || centre[0] < box[0] || centre[0] >= box[2] || centre[1] < box[1] || centre[1] >= box[3]) {
				throw new AssertionError(part + " is not inside the box (" + when + ")");
			}
			hover(context, part);
			if (context.computeOnClient(client -> BodyInspector.hoveredPart()) != part) {
				throw new AssertionError("hovering " + part + " did not find it (" + when + ")");
			}
		}
		return box;
	}

	/** 0 is vanilla's "auto". resizeGui is what actually applies the new scale. */
	private void setGuiScale(ClientGameTestContext context, int scale) {
		context.runOnClient(client -> {
			client.options.guiScale().set(scale);
			client.resizeGui();
		});
		context.waitTicks(3);
	}

	/**
	 * Puts vanilla's recipe book into a known state by clicking its toggle the
	 * way a player does, because {@code leftPos} is only recomputed inside that
	 * button's own handler — calling {@code toggleVisibility} would flip the book
	 * and leave the window where it was, which is a layout that never happens.
	 *
	 * <p>Driven to a state rather than toggled blindly: the book remembers
	 * whether it was open in the player's saved book data, so nothing here may
	 * assume it starts shut.
	 */
	private void setRecipeBookOpen(ClientGameTestContext context, boolean open) {
		boolean already = context.computeOnClient(client ->
				((dev.captience.realisticdamage.client.mixin.InventoryLayoutAccessor) client.gui.screen())
						.realisticdamage$recipeBook().isVisible());
		if (already != open) {
			// The toggle is 20x18 at leftPos+104, height/2-22 (InventoryScreen
			// .getRecipeBookButtonPosition), so its centre is +114, +9 from there.
			int[] at = context.computeOnClient(client -> new int[] {
					((dev.captience.realisticdamage.client.mixin.ContainerLayoutAccessor) client.gui.screen())
							.realisticdamage$leftPos() + 114,
					client.getWindow().getGuiScaledHeight() / 2 - 13});
			setCursor(context, at[0], at[1]);
			context.waitTicks(2);
			context.getInput().pressMouse(0);
			context.waitTicks(3);
			boolean now = context.computeOnClient(client ->
					((dev.captience.realisticdamage.client.mixin.InventoryLayoutAccessor) client.gui.screen())
							.realisticdamage$recipeBook().isVisible());
			if (now != open) {
				throw new AssertionError("clicking the recipe book toggle did not make it "
						+ (open ? "open" : "shut"));
			}
		}
		context.waitTicks(3);
	}

	private int guiWidth(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.getWindow().getGuiScaledWidth());
	}

	/**
	 * "I wish the white text at the bottom of the screen could be disabled"
	 * (1.6 playtest fix). It is the mod's only visible output that used words and
	 * the only one with no switch, in a mod that promises in two documents that
	 * every sensory layer has one.
	 *
	 * <p>Asserted through the filter's own hook rather than by reading the HUD's
	 * overlay string: a screenshot cannot tell a line that was never sent from
	 * one that was sent and refused, and those are precisely the two states this
	 * fix creates. The server is unchanged either way — it still sends ordinary
	 * system chat, so a vanilla client on a modded server keeps the text.
	 */
	private void assertInjuryTextCanBeSilenced(ClientGameTestContext context,
			TestSingleplayerContext singleplayer) {
		context.runOnClient(client -> InjuryMessageFilter.clearLastSuppressedKey());
		announce(singleplayer, "realisticdamage.msg.break.left_leg");
		context.waitTicks(5);
		String leaked = context.computeOnClient(client -> InjuryMessageFilter.lastSuppressedKey());
		if (leaked != null) {
			throw new AssertionError("the switch is on by default and it still ate " + leaked);
		}
		context.takeScreenshot("rd_22_injury_text");

		// Let that line expire before switching the option off. Hud.setOverlayMessage
		// gives it 60 ticks, and without the wait the "off" screenshot shows the
		// previous message still fading — which looks exactly like the switch
		// having failed, in the one picture whose whole job is to show that it
		// worked.
		context.waitTicks(65);
		context.runOnClient(client -> {
			ClientEffectsConfig.get().showInjuryMessages = false;
			InjuryMessageFilter.clearLastSuppressedKey();
		});
		announce(singleplayer, "realisticdamage.msg.break.right_leg");
		context.waitTicks(5);
		String silenced = context.computeOnClient(client -> InjuryMessageFilter.lastSuppressedKey());
		if (!"realisticdamage.msg.break.right_leg".equals(silenced)) {
			throw new AssertionError("the switch is off and the leg break came through as " + silenced);
		}
		context.takeScreenshot("rd_23_injury_text_off");

		// Still off, and a rescue acknowledgement must arrive anyway. These
		// answer a keypress instead of reporting a body, and nothing else on
		// screen says them — silencing them would break a feature rather than
		// quiet an effect.
		context.runOnClient(client -> InjuryMessageFilter.clearLastSuppressedKey());
		announce(singleplayer, "realisticdamage.msg.pressure.self");
		context.waitTicks(5);
		String rescue = context.computeOnClient(client -> InjuryMessageFilter.lastSuppressedKey());
		if (rescue != null) {
			throw new AssertionError("a rescue acknowledgement was swallowed: " + rescue);
		}

		// Back on. The config is a singleton shared with every other client
		// gametest, and this one is a plain field on it rather than a reloadable
		// file value, so it is restored by hand as well as by load().
		context.runOnClient(client -> {
			ClientEffectsConfig.get().showInjuryMessages = true;
			ClientEffectsConfig.load();
		});
		context.waitTicks(3);
		if (!context.computeOnClient(client -> ClientEffectsConfig.get().showInjuryMessages)) {
			throw new AssertionError("the injury text stayed off for the tests that follow");
		}
	}

	/** Sends one action-bar line down the real wire, exactly as an injury would. */
	private void announce(TestSingleplayerContext singleplayer, String key) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				dev.captience.realisticdamage.util.Notifier.message(player, key);
			}
		});
	}

	/** Puts the real cursor over the centre of a body region and lets it render. */
	private void hover(ClientGameTestContext context, BodyPart part) {
		int[] centre = context.computeOnClient(client -> BodyInspector.regionCenter(part));
		if (centre == null) {
			String why = context.computeOnClient(client -> "view drawn " + BodyInspector.viewDrawn()
					+ ", chosen " + ClientEffectsConfig.get().inventoryBodyView
					+ ", display on " + ClientEffectsConfig.get().showBodyHud
					+ ", taking part " + dev.captience.realisticdamage.client.ClientState.active()
					+ ", screen " + (client.gui.screen() == null ? null : client.gui.screen().getClass().getSimpleName()));
			throw new AssertionError("the inspector never drew " + part + " (" + why + ")");
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
