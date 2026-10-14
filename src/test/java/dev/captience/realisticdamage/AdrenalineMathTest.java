package dev.captience.realisticdamage;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.AdrenalineMath;
import dev.captience.realisticdamage.systems.AdrenalineMath.Phase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The last two hearts (1.7): surge, crash, and no chain of surges. */
class AdrenalineMathTest {
	private static void run(AdrenalineMath.State state, double health, int ticks, boolean sprinting) {
		for (int i = 0; i < ticks; i++) {
			AdrenalineMath.tick(state, health, sprinting);
		}
	}

	@Test
	void droppingToTwoHeartsSurgesThenCrashes() {
		AdrenalineMath.State state = new AdrenalineMath.State();
		assertFalse(AdrenalineMath.tick(state, 0.5, false));
		assertEquals(Phase.NONE, state.phase);
		assertTrue(AdrenalineMath.tick(state, 0.15, false), "the crossing fires the surge");
		assertEquals(Phase.SURGE, state.phase);
		run(state, 0.15, Balance.ADRENALINE_SURGE_TICKS, false);
		assertEquals(Phase.CRASH, state.phase);
	}

	@Test
	void theCrashLastsWhileHealthStaysLowAndAtLeastItsMinimum() {
		AdrenalineMath.State state = new AdrenalineMath.State();
		AdrenalineMath.tick(state, 0.15, false);
		run(state, 0.15, Balance.ADRENALINE_SURGE_TICKS, false);
		run(state, 0.9, Balance.ADRENALINE_CRASH_TICKS - 10, false);
		assertEquals(Phase.CRASH, state.phase, "healing straight away does not skip the crash");
		run(state, 0.15, 400, false);
		assertEquals(Phase.CRASH, state.phase, "still at two hearts: still crashing");
		run(state, 0.5, 1, false);
		assertEquals(Phase.NONE, state.phase);
	}

	@Test
	void noSecondSurgeUntilRefractoryAndRecovery() {
		AdrenalineMath.State state = new AdrenalineMath.State();
		AdrenalineMath.tick(state, 0.15, false);
		run(state, 0.15, Balance.ADRENALINE_SURGE_TICKS + Balance.ADRENALINE_CRASH_TICKS, false);
		run(state, 0.3, 1, false);   // above two hearts, below the heartbeat line
		assertEquals(Phase.NONE, state.phase);
		run(state, 0.15, Balance.ADRENALINE_REFRACTORY_TICKS, false);
		// Back in the crash because health is low again, but never a second surge:
		// health never climbed past the heartbeat line.
		assertTrue(state.phase != Phase.SURGE);
		run(state, 0.8, Balance.ADRENALINE_CRASH_TICKS + 5, false);
		assertEquals(Phase.NONE, state.phase);
		assertTrue(AdrenalineMath.tick(state, 0.1, false), "recovered and rested: it can surge again");
	}

	@Test
	void sprintingInTheCrashRunsOutOfBreath() {
		AdrenalineMath.State state = new AdrenalineMath.State();
		AdrenalineMath.tick(state, 0.15, false);
		run(state, 0.15, Balance.ADRENALINE_SURGE_TICKS, false);
		int ticks = 0;
		while (!state.winded && ticks < 1000) {
			AdrenalineMath.tick(state, 0.15, true);
			ticks++;
		}
		assertTrue(state.winded);
		assertTrue(ticks > 40 && ticks < 100, "winded after " + ticks + " ticks of sprinting");
		run(state, 0.15, (int) (Balance.CRASH_BREATH_MAX / Balance.CRASH_BREATH_RECOVER) + 1, false);
		assertFalse(state.winded, "catching your breath gives the sprint back");
	}

	@Test
	void theSurgeDoesNotWindYou() {
		AdrenalineMath.State state = new AdrenalineMath.State();
		AdrenalineMath.tick(state, 0.15, true);
		run(state, 0.15, Balance.ADRENALINE_SURGE_TICKS - 5, true);
		assertEquals(Phase.SURGE, state.phase);
		assertFalse(state.winded);
	}
}
