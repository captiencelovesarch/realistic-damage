package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.client.Afterimage;
import dev.captience.realisticdamage.client.ClientState;
import dev.captience.realisticdamage.client.ConsciousnessEffects;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;

/**
 * The death sequence, run for real (1.5).
 *
 * <p>Death used to snap every effect off between one frame and the next —
 * that was the complaint — so the thing to prove is the opposite of the usual:
 * not that effects stop when the player stops being a valid subject, but that
 * for the seconds after death they deliberately keep going, and that the
 * respawn then gets the clean slate the death screen no longer provides.
 */
public class DeathClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		// Defensive: the effects config is shared across every test in the
		// session, and a predecessor may have left it panic-buttoned. This test
		// is about effects rendering, so it must not inherit a config that
		// renders nothing.
		context.runOnClient(client ->
				dev.captience.realisticdamage.client.ClientEffectsConfig.load());
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			context.waitTicks(20);

			if (context.computeOnClient(client -> ClientState.deathFade()) != 0.0) {
				throw new AssertionError("the death fade was running on a living player");
			}

			// Kill for real, through damage, so the ordinary death path runs.
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.hurtServer(player.level(),
							player.level().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
				}
			});
			context.waitTicks(10);

			if (!context.computeOnClient(client -> ClientState.dying())) {
				throw new AssertionError("the player did not die to a lethal blow");
			}
			double early = context.computeOnClient(client -> ClientState.deathFade());
			if (early <= 0.0) {
				throw new AssertionError("death arrived and the fade never started");
			}
			context.takeScreenshot("rd_death_00_moment");

			// The fade must advance — a value that starts and freezes is the old
			// snap with one extra frame in it.
			context.waitTicks(40);
			double later = context.computeOnClient(client -> ClientState.deathFade());
			if (later <= early) {
				throw new AssertionError("the death fade stalled: " + early + " then " + later);
			}
			context.takeScreenshot("rd_death_01_fading");

			// ...and it saturates rather than wrapping or overshooting.
			context.waitTicks(80);
			double at_end = context.computeOnClient(client -> ClientState.deathFade());
			if (at_end < 0.95 || at_end > 1.0) {
				throw new AssertionError("the fade should have completed, got " + at_end);
			}
			context.takeScreenshot("rd_death_02_settled");

			// The respawn gets a fresh body: no fade, no dying, a resting heart.
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			if (context.computeOnClient(client -> ClientState.dying())) {
				throw new AssertionError("respawned and still dying");
			}
			if (context.computeOnClient(client -> ClientState.deathFade()) != 0.0) {
				throw new AssertionError("the death fade leaked into the next life");
			}
			context.takeScreenshot("rd_death_03_respawned");

			// The same death again in the realistic style: a longer fade that
			// tunnels to true black, with the heart beating past the blackout.
			context.runOnClient(client ->
					dev.captience.realisticdamage.client.ClientEffectsConfig.get().realisticDeath = true);
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.hurtServer(player.level(),
							player.level().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
				}
			});
			context.waitTicks(10);
			if (!context.computeOnClient(client -> ClientState.dying())) {
				throw new AssertionError("the second death did not take");
			}
			context.takeScreenshot("rd_death_04_realistic_moment");

			// The realistic fade is longer by design; the cinematic timings would
			// have finished by now, so reaching this point below 0.6 is itself the
			// proof that the style switched.
			context.waitTicks(60);
			double slowFade = context.computeOnClient(client -> ClientState.deathFade());
			if (slowFade >= 0.6) {
				throw new AssertionError("the realistic fade is running at cinematic speed: "
						+ slowFade + " after 70 ticks");
			}
			context.takeScreenshot("rd_death_05_realistic_tunnel");

			// Past the blackout point: the screen is gone and the fade is still
			// running — the stretch where only the heart is left.
			context.waitTicks(60);
			double darkFade = context.computeOnClient(client -> ClientState.deathFade());
			if (darkFade <= dev.captience.realisticdamage.config.Balance.DEATH_BLACK_AT) {
				throw new AssertionError("expected to be past the blackout by now, at " + darkFade);
			}
			context.takeScreenshot("rd_death_06_realistic_dark");

			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			if (context.computeOnClient(client -> ClientState.deathFade()) != 0.0) {
				throw new AssertionError("the realistic fade leaked into the next life");
			}
			// Leave the config as the file on disk has it, for whoever runs next.
			context.runOnClient(client ->
					dev.captience.realisticdamage.client.ClientEffectsConfig.load());
			context.takeScreenshot("rd_death_07_realistic_respawned");

			// An overwhelming death. The two above died to the void, which the
			// classifier deliberately ignores, so their violence was 0 and the
			// fades ran slow — which is also why those passes still test what
			// they always tested. This one dies to a blow three times its whole
			// health bar: the server must score it, the payload must arrive, and
			// the fade must be a cut rather than a sequence.
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.hurtServer(player.level(),
							player.level().damageSources().generic(), 60.0F);
				}
			});
			context.waitTicks(6);
			if (!context.computeOnClient(client -> ClientState.dying())) {
				throw new AssertionError("the violent death did not take");
			}
			double violence = context.computeOnClient(client -> ClientState.deathViolence());
			if (violence < 0.99) {
				throw new AssertionError("sixty damage against twenty health should score a full cut, got "
						+ violence);
			}
			// Cinematic style compressed by full violence: three quarters of a
			// second. Twenty ticks in it must be nearly over — the calm pass was
			// barely a tenth through at this point.
			context.waitTicks(14);
			double cutFade = context.computeOnClient(client -> ClientState.deathFade());
			if (cutFade < 0.8) {
				throw new AssertionError("an obliterating death is fading at a stroll: "
						+ cutFade + " after 20 ticks");
			}
			context.takeScreenshot("rd_death_08_violent_cut");

			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			if (context.computeOnClient(client -> ClientState.deathViolence()) != 0.0) {
				throw new AssertionError("the violence leaked into the next life");
			}

			// Dying with the ladder already on screen (1.6.1). The report was that a
			// player at low consciousness saw the screen clear on the frame they
			// died and then dim again from nothing. So: drain to the bottom of the
			// ladder, let the living pass draw its vignette and washout, then kill
			// — and from that frame to the end of the fade nothing may be lighter
			// than the last living frame. Cinematic style, because its dim caps at
			// 110/255 and is therefore the ending most able to brighten.
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					dev.captience.realisticdamage.body.RealisticDamageData data =
							dev.captience.realisticdamage.RDAttachments.get(player);
					data.reset();
					data.drainConsciousness(92.0);   // about 8: vignette full, washout near full
					dev.captience.realisticdamage.RDAttachments.pushSnapshot(player);
				}
			});
			context.waitTicks(8);
			double[] living = readouts(context);
			if (living[0] < 0.9 || living[1] < 60.0) {
				throw new AssertionError("meant to die under a full ladder, but the living frame drew vignette "
						+ living[0] + " and washout " + living[1] + "/255");
			}
			// Deep in the band, the blur and the afterimage are both up; they are
			// the two things the playtest saw snap off on the death frame (1.6.2).
			double livingGhost = context.computeOnClient(client -> Afterimage.strength());
			if (livingGhost < 0.5 || !context.computeOnClient(client -> ConsciousnessEffects.blurRequestedLastFrame())) {
				throw new AssertionError("meant to die blurred and ghosting, but the living frame had ghost "
						+ livingGhost + " and blur " + context.computeOnClient(client -> ConsciousnessEffects.blurRequestedLastFrame()));
			}
			context.takeScreenshot("rd_death_09_low_before");
			singleplayer.getServer().runOnServer(server -> {
				for (ServerPlayer player : server.getPlayerList().getPlayers()) {
					player.hurtServer(player.level(),
							player.level().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
				}
			});
			// The death arrives a tick or two after the server call. Re-sample the
			// living frame every tick until it does, so the comparison is against
			// the frame death actually interrupted and not one from before the
			// kill: the deep swell and the washout's breath both move a little
			// every tick, and a sample taken eight ticks earlier fails on that
			// drift alone (it did, first run: 0.0385 against a frozen 0.0279).
			int waited = 0;
			while (!context.computeOnClient(client -> ClientState.dying())) {
				living = readouts(context);
				context.waitTicks(1);
				if (++waited > 20) {
					throw new AssertionError("the low-consciousness death did not take");
				}
			}
			// One rendered frame can still fall between the last sample and the
			// death, so allow exactly one tick of pulse drift: the swell moves at
			// most 0.35 x 0.2 x 2pi/70 ~ 0.006 a tick and the breath at most about
			// 0.9/255. A real regression drops the vignette from 1.0 to 0 and the
			// washout from ~117 to 0, orders of magnitude past either.
			final double[] last = living;
			for (int sample = 0; sample < 4; sample++) {
				double[] dying = readouts(context);
				if (dying[0] < last[0] - 1.0e-6 || dying[1] < last[1] - 1.5 || dying[2] < last[2] - 0.01) {
					throw new AssertionError("the screen got brighter for dying: living frame drew "
							+ last[0] + " / " + last[1] + " / " + last[2] + ", dying sample " + sample
							+ " drew " + dying[0] + " / " + dying[1] + " / " + dying[2]);
				}
				// ...and neither the ghost nor the blur may have let go.
				double dyingGhost = context.computeOnClient(client -> Afterimage.strength());
				if (dyingGhost < livingGhost - 1.0e-6) {
					throw new AssertionError("the afterimage let go on dying: " + livingGhost + " then " + dyingGhost);
				}
				if (!context.computeOnClient(client -> ConsciousnessEffects.blurRequestedLastFrame())) {
					throw new AssertionError("the blur snapped off on the death screen, dying sample " + sample);
				}
				long ghostFrames = context.computeOnClient(client -> Afterimage.framesProcessed());
				context.waitTicks(2);
				if (context.computeOnClient(client -> Afterimage.framesProcessed()) <= ghostFrames) {
					throw new AssertionError("the afterimage chain stopped running on the death screen, sample " + sample);
				}
				if (sample == 0) {
					context.takeScreenshot("rd_death_10_low_moment");
				}
				context.waitTicks(33);
			}
			context.takeScreenshot("rd_death_11_low_settled");
			context.clickScreenButton("deathScreen.respawn");
			context.waitTicks(20);
			// A fresh body: the floor must not leak into the next life either.
			double[] reborn = readouts(context);
			if (reborn[0] != 0.0 || reborn[2] != 0.0) {
				throw new AssertionError("the frozen ladder leaked into the respawn: vignette " + reborn[0]
						+ ", blackout " + reborn[2]);
			}
		}
	}

	/** Vignette strength, washout alpha and blackout level of the last rendered frame. */
	private static double[] readouts(ClientGameTestContext context) {
		return context.computeOnClient(client -> new double[] {
				ConsciousnessEffects.lastVignetteStrength(),
				ConsciousnessEffects.lastExposureAlpha(),
				ConsciousnessEffects.lastBlackoutLevel()});
	}
}
