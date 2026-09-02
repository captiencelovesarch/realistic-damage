package dev.captience.realisticdamage;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.DeathMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The violence scoring (1.5): what separates a death that fades from a death
 * that cuts. The property that matters throughout is continuity — worse deaths
 * end faster, with no cliff where two nearly identical deaths end differently.
 */
class DeathMathTest {
	/** The scenario that motivated the feature. */
	@Test
	void aHugeFallIsInstantAndAShortLethalOneIsNot() {
		// A 50-block drop (47 over safe) must max the scale.
		assertEquals(1.0, DeathMath.violenceFromFall(47.0), 1.0e-9);
		// A drop at the knockdown-full height that kills an already-wounded
		// player: quicker than a bleed-out, nowhere near a cut.
		double marginal = DeathMath.violenceFromFall(Balance.KNOCKDOWN_FULL_HEIGHT);
		assertTrue(marginal > 0.2 && marginal < 0.8,
				"a marginal lethal fall should sit between the extremes, got " + marginal);
		assertEquals(0.0, DeathMath.violenceFromFall(0.0));
	}

	@Test
	void aMassiveBlowIsInstantAndAFinishingPokeIsNot() {
		// A charged-creeper-sized hit against 20 max health maxes the scale...
		assertEquals(1.0, DeathMath.violenceFromBlow(40.0, 20.0), 1.0e-9);
		// ...while the weak hit that finishes a nearly-dead player barely moves
		// it — measured against max health, not remaining, on purpose: being
		// finished off while dying is a quiet end, not an execution.
		assertTrue(DeathMath.violenceFromBlow(2.0, 20.0) < 0.1);
		// The bleed tick that ends a bleed-out never even reaches this scoring
		// (internal damage skips the pipeline), but the value it would get is
		// the same answer.
		assertTrue(DeathMath.violenceFromBlow(1.0, 20.0) < 0.05);
	}

	@Test
	void violenceIsMonotonicAndClamped() {
		double previousFall = -1.0;
		double previousBlow = -1.0;
		for (double x = 0.0; x <= 120.0; x += 0.5) {
			double fall = DeathMath.violenceFromFall(x);
			double blow = DeathMath.violenceFromBlow(x, 20.0);
			assertTrue(fall >= previousFall, "fall violence dipped at " + x);
			assertTrue(blow >= previousBlow, "blow violence dipped at " + x);
			assertTrue(fall >= 0.0 && fall <= 1.0);
			assertTrue(blow >= 0.0 && blow <= 1.0);
			previousFall = fall;
			previousBlow = blow;
		}
	}

	@Test
	void degenerateInputsScoreZeroRatherThanExploding() {
		assertEquals(0.0, DeathMath.violenceFromFall(-5.0));
		assertEquals(0.0, DeathMath.violenceFromBlow(-1.0, 20.0));
		assertEquals(0.0, DeathMath.violenceFromBlow(10.0, 0.0),
				"a zero max health must not divide by itself into a violent death");
	}

	@Test
	void violenceCompressesTheFadeAndNeverEliminatesIt() {
		double base = Balance.DEATH_FADE_SECONDS_REALISTIC;
		assertEquals(base, DeathMath.fadeSeconds(base, 0.0), 1.0e-9,
				"a calm death keeps its style's full length");
		assertEquals(base * Balance.DEATH_INSTANT_FACTOR, DeathMath.fadeSeconds(base, 1.0), 1.0e-9);
		double previous = Double.MAX_VALUE;
		for (double v = 0.0; v <= 1.0; v += 0.01) {
			double seconds = DeathMath.fadeSeconds(base, v);
			assertTrue(seconds < previous + 1.0e-12, "the fade lengthened as violence rose, at " + v);
			assertTrue(seconds > 0.0, "the fade must never be a literal zero-frame cut");
			previous = seconds;
		}
	}

	/**
	 * The overwhelming case, end to end through the heart: a death at full
	 * violence pre-ages the run-down past the silent point, so the dying heart
	 * never lands a single audible beat. Gone before it was.
	 */
	@Test
	void anObliteratingDeathHasNoHeartbeatsInIt() {
		var heart = new dev.captience.realisticdamage.systems.Cardio();
		double fadePerTick = 1.0
				/ (DeathMath.fadeSeconds(Balance.DEATH_FADE_SECONDS_REALISTIC, 1.0) * 20.0);
		double fade = 0.0;
		for (int tick = 0; tick < 200; tick++) {
			fade = Math.min(1.0, fade + fadePerTick);
			heart.setMoribund(Math.max(fade, 1.0), Balance.DEATH_HEART_SILENT_REALISTIC);
			heart.tick(0.0, 0.0, 1.0, 0.5);
			assertTrue(!heart.beatJustFired(),
					"an obliterating death let a beat through at tick " + tick);
		}
	}
}
