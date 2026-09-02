package dev.captience.realisticdamage.client.gametest;

import dev.captience.realisticdamage.client.ClientState;
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
		}
	}
}
