package dev.captience.realisticdamage.client.gametest;

import java.util.function.Consumer;

import dev.captience.realisticdamage.RDAttachments;
import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.body.RealisticDamageData;
import dev.captience.realisticdamage.body.WoundSeverity;
import dev.captience.realisticdamage.client.BloodBody;
import dev.captience.realisticdamage.client.BloodLens;
import dev.captience.realisticdamage.client.BloodStains;
import dev.captience.realisticdamage.client.ClientEffectsConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

/**
 * The blood system, running for real (1.5).
 *
 * <p>Almost all of this rework lives on the client — the emitter, the physics
 * drop, the stain field, the body staining and the lens — so almost none of it
 * is reachable from a server gametest and the arithmetic alone
 * ({@code BloodFlowTest}) proves only that the numbers are right. This boots a
 * world, opens real wounds through the server-authoritative state, and asserts
 * that blood actually appears: particles in the level, stains accumulating on
 * the body, and nothing thrown while drawing any of it.
 *
 * <p>Screenshots are taken as well, but they are the weaker half. A screenshot
 * of a blood effect that silently did nothing looks exactly like a screenshot of
 * one that worked, which is why every stage below asserts on state too.
 */
public class BloodClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// The config is a singleton shared by every test in this client session, and
		// this one asserts on what the effects do. Reloading from disk first means a
		// panic-buttoned config leaked by whichever class ran before us fails here as
		// nothing rendering, rather than as a puzzle — the restore below is the same
		// courtesy in the other direction, and neither can rely on run order.
		context.runOnClient(client -> ClientEffectsConfig.load());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			// Nothing wrong: no blood anywhere. Establishes that what follows is
			// caused by the wounds and not simply always on. Particles are compared
			// against this baseline rather than against zero, because the level is
			// entitled to its own ambient ones and none of them are our business.
			int clean = countParticles(context);
			assertNoStain(context, "an uninjured player");
			context.takeScreenshot("rd_blood_00_clean");

			// A critical wound: this is the arterial case, so it should be
			// emitting hard and throwing on the beat.
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
				data.addWound(BodyPart.TORSO, WoundSeverity.CRITICAL);
			});
			context.waitTicks(40);
			int bleeding = countParticles(context);
			if (bleeding <= clean) {
				throw new AssertionError("a critical wound produced no particles: "
						+ clean + " before, " + bleeding + " after");
			}
			assertStain(context, BodyPart.TORSO, "a critical torso wound");
			context.takeScreenshot("rd_blood_01_critical");

			// Third person, so the body staining is actually on screen and the
			// render layer gets exercised rather than merely ticked.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(40);
			context.takeScreenshot("rd_blood_02_stained_body");

			// Staining has to be regional. A torso wound must not make the legs
			// bloody — that resolution is the entire reason the layer draws per
			// model part instead of tinting the whole model at once.
			assertNoStain(context, BodyPart.LEFT_LEG, "a wound on the torso only");

			// ...and wounding a leg must stain that leg.
			mutate(singleplayer, data -> data.addWound(BodyPart.LEFT_LEG, WoundSeverity.SEVERE));
			context.waitTicks(40);
			assertStain(context, BodyPart.LEFT_LEG, "a severe left leg wound");
			context.takeScreenshot("rd_blood_03_leg");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			// Staining has to keep accumulating while the wound is open. This is
			// the assertion that the whole client tick chain is actually running
			// rather than having painted one frame and stopped — the failure mode
			// a screenshot could not tell apart from success.
			double before = stainOf(context, BodyPart.TORSO);
			context.waitTicks(40);
			double after = stainOf(context, BodyPart.TORSO);
			if (after <= before) {
				throw new AssertionError("staining stopped growing over an open wound: "
						+ before + " then " + after);
			}
			// Blood on the camera. The layer that exists precisely because
			// everything else happens where a first-person player is not looking,
			// so if this one silently does nothing the rework has not fixed the
			// complaint it was built for.
			int droplets = context.computeOnClient(client -> BloodLens.count());
			if (droplets <= 0) {
				throw new AssertionError("bled for seconds and nothing reached the camera");
			}
			context.takeScreenshot("rd_blood_04_accumulating");

			// Drops have to actually arrive somewhere. This is the headline of the
			// rework — the old particles hung in the air and never landed — and it
			// is invisible from the default view, because blood falls straight down
			// and lands under your own feet where the camera never looks. Point the
			// camera at the floor, and assert on the count rather than trusting the
			// screenshot to show it.
			context.runOnClient(client -> {
				if (client.player != null) {
					client.player.setXRot(85.0F);
				}
			});
			context.waitTicks(60);
			int marks = context.computeOnClient(client -> BloodStains.count());
			if (marks <= 0) {
				throw new AssertionError("bled for seconds over solid ground and left no mark on it");
			}
			context.takeScreenshot("rd_blood_07_ground");

			// Healing closes the wounds; the blood must then dry off rather than
			// staying forever. Slowly — blood dries on, it does not rinse — so
			// this only has to fall, not vanish.
			double whileBleeding = stainOf(context, BodyPart.TORSO);
			mutate(singleplayer, RealisticDamageData::reset);
			context.waitTicks(80);
			double whileHealed = stainOf(context, BodyPart.TORSO);
			if (whileHealed >= whileBleeding) {
				throw new AssertionError("blood never dried off a closed wound: "
						+ whileBleeding + " then " + whileHealed);
			}
			context.takeScreenshot("rd_blood_05_healed");

			// The panic button has to cover blood too. This is the promise
			// disableAllEffects makes, and a new effect that forgot to join it
			// would break that promise silently.
			// Everything is cleared first so the assertions below are about the
			// switch suppressing new blood, not about old blood happening to have
			// aged out inside the window — which is what they would have measured
			// otherwise, and would have passed or failed on timing.
			context.runOnClient(client -> {
				ClientEffectsConfig.get().disableAllEffects();
				BloodBody.reset();
				BloodLens.reset();
				BloodStains.reset();
			});
			mutate(singleplayer, data -> {
				data.reset();
				data.clearWounds();
				data.addWound(BodyPart.HEAD, WoundSeverity.CRITICAL);
			});
			context.waitTicks(40);
			assertNoStain(context, "blood with every effect disabled");
			int lensWhenOff = context.computeOnClient(client -> BloodLens.count());
			if (lensWhenOff > 0) {
				throw new AssertionError("the panic button left " + lensWhenOff + " droplets on the camera");
			}
			int marksWhenOff = context.computeOnClient(client -> BloodStains.count());
			if (marksWhenOff > 0) {
				throw new AssertionError("the panic button left " + marksWhenOff + " marks on the world");
			}
			context.takeScreenshot("rd_blood_06_disabled");

			// The config is a singleton shared by every test in this client
			// session, and the line above just zeroed all of it. Reloading from
			// disk restores the defaults; without this, whichever test runs next
			// inherits a panic-buttoned config and renders nothing — which is
			// exactly how the death test shipped its first screenshots of a
			// completely vanilla death screen.
			context.runOnClient(client -> ClientEffectsConfig.load());
		}
	}

	/**
	 * Particles alive in the level right now.
	 *
	 * <p>{@code countParticles} returns a formatted per-group breakdown rather
	 * than a bare total, so the counts are summed out of it. The first version of
	 * this helper parsed the whole string as one integer, caught the
	 * NumberFormatException and returned a sentinel that made every particle
	 * assertion below skip itself — which passed, and proved nothing. There is no
	 * escape hatch now: a format change should break the test loudly.
	 */
	private static int countParticles(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			int total = 0;
			for (String token : client.particleEngine.countParticles().split("\\D+")) {
				if (!token.isEmpty()) {
					total += Integer.parseInt(token);
				}
			}
			return total;
		});
	}

	private static void assertStain(ClientGameTestContext context, BodyPart part, String when) {
		double level = stainOf(context, part);
		if (level <= 0.0) {
			throw new AssertionError("the " + part + " should be bloody with " + when + ", but is clean");
		}
	}

	private static void assertNoStain(ClientGameTestContext context, BodyPart part, String when) {
		double level = stainOf(context, part);
		if (level > 0.0) {
			throw new AssertionError("the " + part + " is bloody with " + when + " (" + level + ")");
		}
	}

	private static void assertNoStain(ClientGameTestContext context, String when) {
		for (BodyPart part : BodyPart.values()) {
			assertNoStain(context, part, when);
		}
	}

	private static double stainOf(ClientGameTestContext context, BodyPart part) {
		return context.computeOnClient(client -> {
			Minecraft mc = Minecraft.getInstance();
			return mc.player == null ? 0.0 : BloodBody.stain(mc.player.getId(), part);
		});
	}

	private static void mutate(TestSingleplayerContext singleplayer, Consumer<RealisticDamageData> change) {
		singleplayer.getServer().runOnServer(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				RealisticDamageData data = RDAttachments.get(player);
				change.accept(data);
				RDAttachments.pushSnapshot(player);
			}
		});
	}
}
