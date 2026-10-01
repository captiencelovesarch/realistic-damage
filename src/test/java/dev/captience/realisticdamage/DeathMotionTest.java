package dev.captience.realisticdamage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.systems.DeathMotion;
import dev.captience.realisticdamage.systems.DeathMotion.Profile;
import org.junit.jupiter.api.Test;

/**
 * The death camera's motion by kind of death (1.7). The playtest asked for
 * deaths to move differently and for the view not to freeze; these pin the
 * shapes that make that true without a running game.
 */
class DeathMotionTest {
	@Test
	void everyKindHasASaneProfile() {
		for (DeathKind kind : DeathKind.values()) {
			for (double v : new double[] {0.0, 0.5, 1.0}) {
				Profile p = DeathMotion.profile(kind, v);
				assertTrue(p.collapseTicks() > 0.0, kind + ": collapse must take time");
				assertTrue(p.eye() > 0.0 && p.eye() < 1.62, kind + ": the eye ends somewhere on the body");
				assertTrue(Math.abs(p.roll()) < 90.0, kind + ": rolled past lying on your side");
				assertTrue(Math.abs(p.pitch()) < 60.0, kind + ": pitched past the floor");
				assertTrue(p.twitches() <= DeathMotion.TWITCH_TICKS.length, kind + ": more twitches than times");
				assertEquals(1.0, DeathMotion.collapse(p, p.collapseTicks()), 1.0e-9, kind + ": collapse ends at 1");
				assertEquals(0.0, DeathMotion.collapse(p, 0.0), 1.0e-9, kind + ": collapse starts at 0");
			}
		}
	}

	/** Identical deaths move identically: nothing here may draw a random number. */
	@Test
	void deterministic() {
		for (DeathKind kind : DeathKind.values()) {
			assertEquals(DeathMotion.profile(kind, 0.42), DeathMotion.profile(kind, 0.42));
			Profile p = DeathMotion.profile(kind, 0.42);
			assertEquals(DeathMotion.writheRoll(p, 7.3), DeathMotion.writheRoll(p, 7.3));
		}
	}

	/** A fifty-block drop is on the floor at once; a faint holds and then folds. */
	@Test
	void aFallSlamsAndAFaintFolds() {
		Profile fall = DeathMotion.profile(DeathKind.FALL, 1.0);
		Profile fade = DeathMotion.profile(DeathKind.FADE, 0.0);
		assertTrue(DeathMotion.collapse(fall, 2.0) > 0.5, "a fall should be most of the way down in two ticks");
		assertTrue(DeathMotion.collapse(fade, 8.0) < 0.05, "a faint should still be standing at eight ticks");
		assertTrue(fall.impactAtDeath(), "a fall's landing is the death itself");
		assertTrue(!fade.impactAtDeath());
	}

	/** Harder deaths throw you further over, spin you faster and land you harder. */
	@Test
	void violenceScalesWhatAHarderDeathWouldMakeBigger() {
		assertTrue(DeathMotion.profile(DeathKind.BLOW, 1.0).roll() > DeathMotion.profile(DeathKind.BLOW, 0.0).roll());
		assertTrue(DeathMotion.profile(DeathKind.EXPLOSION, 1.0).tumble()
				> DeathMotion.profile(DeathKind.EXPLOSION, 0.0).tumble());
		assertTrue(DeathMotion.profile(DeathKind.FALL, 1.0).thud() > DeathMotion.profile(DeathKind.FALL, 0.0).thud());
		// ...and a drowning is not more violent for having been quick.
		assertEquals(DeathMotion.profile(DeathKind.DROWNING, 0.0), DeathMotion.profile(DeathKind.DROWNING, 1.0));
	}

	/** Only a blast or the void spins you; only water and lava take the landing. */
	@Test
	void theKindsThatSpinAndTheKindsThatFloat() {
		for (DeathKind kind : DeathKind.values()) {
			Profile p = DeathMotion.profile(kind, 1.0);
			boolean spins = kind == DeathKind.EXPLOSION || kind == DeathKind.VOID;
			assertEquals(spins, p.tumble() > 0.0, kind + " spin");
			boolean floats = kind == DeathKind.DROWNING || kind == DeathKind.LAVA || kind == DeathKind.VOID;
			assertEquals(floats, p.thud() <= 0.0, kind + " landing");
		}
		assertTrue(DeathMotion.profile(DeathKind.LAVA, 0.0).pitch() < 0.0, "sinking in lava, the face turns up");
		assertTrue(DeathMotion.profile(DeathKind.LAVA, 0.0).eye() < DeathMotion.LYING_EYE,
				"...and the eye goes under the surface");
		assertTrue(DeathMotion.profile(DeathKind.PROJECTILE, 0.5).jerk() < 0.0, "a shot snaps the head back");
		assertTrue(DeathMotion.profile(DeathKind.PROJECTILE, 0.5).pitch() > 30.0, "then folds you onto your face");
	}

	/** Thrash and convulsion die away, and a death without one has none. */
	@Test
	void writheDecaysToNothing() {
		Profile fire = DeathMotion.profile(DeathKind.FIRE, 0.5);
		double early = 0.0;
		for (double t = 0.0; t < 10.0; t += 0.25) {
			early = Math.max(early, Math.abs(DeathMotion.writheRoll(fire, t)));
		}
		assertTrue(early > fire.writhe() * 0.4, "burning should thrash early, peak " + early);
		assertEquals(0.0, DeathMotion.writheRoll(fire, fire.writheTicks() + 1.0));
		assertEquals(0.0, DeathMotion.writhePitch(fire, fire.writheTicks() + 1.0));
		Profile blow = DeathMotion.profile(DeathKind.BLOW, 0.5);
		for (double t = 0.0; t < 40.0; t += 0.5) {
			assertEquals(0.0, DeathMotion.writheRoll(blow, t), "a blow does not thrash");
		}
	}

	/**
	 * The freeze the playtest saw was the frame going static the moment the
	 * curve ran out. After landing the head keeps settling for a few seconds:
	 * most of the way at once, then creeping, never jumping.
	 */
	@Test
	void theHeadKeepsSettlingAfterLanding() {
		for (DeathKind kind : DeathKind.values()) {
			Profile p = DeathMotion.profile(kind, 0.5);
			assertEquals(0.0, DeathMotion.loll(p, 0.0), 1.0e-9);
			double previous = 0.0;
			for (int t = 1; t <= p.lollTicks(); t++) {
				double l = DeathMotion.loll(p, t);
				assertTrue(l >= previous, kind + ": the settle went backwards");
				previous = l;
			}
			assertEquals(1.0, DeathMotion.loll(p, p.lollTicks() + 5.0), 1.0e-9);
		}
		Profile blow = DeathMotion.profile(DeathKind.BLOW, 0.5);
		assertTrue(DeathMotion.loll(blow, 20.0) < 1.0 && blow.loll() > 0.0,
				"a second after landing the head is still going");
	}

	/** A spin comes to rest on the nearest whole turn, never unwinding through all of it. */
	@Test
	void aTumbleRestsOnTheNearestTurn() {
		assertEquals(0.0, DeathMotion.restingTumble(170.0));
		assertEquals(360.0, DeathMotion.restingTumble(190.0));
		assertEquals(-360.0, DeathMotion.restingTumble(-200.0));
		assertEquals(720.0, DeathMotion.restingTumble(700.0));
	}

	/** The head snap is there at once and gone within half a second. */
	@Test
	void theJerkIsAMoment() {
		Profile shot = DeathMotion.profile(DeathKind.PROJECTILE, 1.0);
		assertTrue(Math.abs(DeathMotion.jerk(shot, 1.0)) > Math.abs(shot.jerk()) * 0.5);
		assertTrue(Math.abs(DeathMotion.jerk(shot, 12.0)) < Math.abs(shot.jerk()) * 0.05);
	}
}
