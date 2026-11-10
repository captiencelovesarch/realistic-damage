package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.StumbleTest.Death;
import dev.captience.realisticdamage.ragdoll.StumbleTest.Outcome;
import dev.captience.realisticdamage.ragdoll.StumbleTest.Ran;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Arcade (stumbling deaths, IK phase 2) against the fake world: the shove
 * and the turn, the planned steps, the finishers, the headshot hang. The
 * deaths are {@link StumbleTest}'s, with the style swapped.
 */
class ArcadeStumbleTest {
	private static final double DT = StumbleTest.DT;

	static Death arcade() {
		Death d = new Death();
		d.style = StumbleStyle.arcade();
		StumbleTest.tune(d.style);
		return d;
	}

	private static List<Ran> every;

	/** {@link StumbleTest#everyDeath} in Arcade on flat ground, eight seconds of each, run once. */
	static synchronized List<Ran> everyOutcome() {
		if (every == null) {
			every = StumbleTest.everyDeath().parallelStream().map(d -> {
				d.style = StumbleStyle.arcade();
				StumbleTest.tune(d.style);
				Outcome o = StumbleTest.run(d, StumbleTest.flat(), 160);
				if (System.getenv("RD_STATS") != null) {
					Stumble s = o.stumble;
					System.out.printf(Locale.ROOT, "AT %s %s %d %s %.1f %.1f %.1f | %s %d %d %.2f %s %s | %.3f %.3f %.3f %.3f | %.1f %.2f %.2f %.2f %.2f%n",
							d.kind, d.hit, d.seed, d.from, d.speed, d.phase, d.violence,
							s == null ? "none" : s.hang() ? "hang" : "stumble", s == null ? 0 : s.plannedSteps(),
							s == null || s.staggered() < 0 ? o.steps() : s.staggered(),
							s == null ? 0.0 : s.letGoAt(), s == null || s.planned() == null ? "-" : s.planned().name(),
							s == null ? "-" : String.valueOf(s.why()).replace(' ', '_') + (s.early() ? "_early" : ""),
							o.pen, o.penHeld, o.sep, o.sepHeld,
							o.body.lean(), o.face(), o.went.z, o.went.x, o.lastUp);
				}
				return new Ran(d, o);
			}).toList();
		}
		return every;
	}

	/** Struck by a blow or an arrow, awake, its strings not cut and its legs sound: a body Arcade keeps on its feet. */
	private static boolean plain(Death d) {
		return (d.kind == DeathKind.BLOW || d.kind == DeathKind.PROJECTILE) && d.hit != HumanoidRagdoll.HEAD
				&& d.hit != HumanoidRagdoll.RIGHT_LEG && d.hit != HumanoidRagdoll.LEFT_LEG && d.violence < 0.95;
	}

	private static List<Ran> finished() {
		return everyOutcome().stream().filter(ran -> ran.outcome().stumble != null
				&& ran.outcome().stumble.finisher() != null).toList();
	}

	// --- Every death ends on the ground ------------------------------------------------------------

	@Test
	void thousandsOfArcadeDeathsAllEndLyingDown() {
		List<Ran> all = everyOutcome();
		List<String> failures = new ArrayList<>();
		for (Ran ran : all) {
			StumbleTest.sound(failures, ran.death(), ran.outcome());
			if (ran.outcome().finite && ran.outcome().body.lean() < 45.0) {
				StumbleTest.fail(failures, ran.death(), ran.outcome(), "not lying down");
			}
		}
		StumbleTest.report(failures, all.size());
	}

	@Test
	void nearlyEveryConsciousBlowOrArrowStumblesEvenStandingStill() {
		List<Ran> plain = everyOutcome().stream().filter(ran -> plain(ran.death())).toList();
		List<String> failures = new ArrayList<>();
		for (Ran ran : plain) {
			Stumble s = ran.outcome().stumble;
			if (s == null || s.hang() || s.plannedSteps() <= 0) {
				StumbleTest.fail(failures, ran.death(), ran.outcome(), "did not stumble");
			}
		}
		StumbleTest.report(failures, plain.size());
		assertTrue(plain.stream().anyMatch(ran -> ran.death().speed == 0.0), "standing deaths among them");
	}

	@Test
	void mostGoDownWithTheirFinisher() {
		List<Ran> plain = everyOutcome().stream().filter(ran -> plain(ran.death())).toList();
		double finished = StumbleTest.share(plain, ran -> ran.outcome().stumble.finisher() != null);
		assertTrue(finished >= 0.9, "went down with their finisher: " + finished);
		for (Ran ran : finished()) {
			assertEquals(ran.outcome().stumble.planned(), ran.outcome().stumble.finisher(), () -> "the planned one: " + ran.death());
		}
	}

	// --- Planned: steps and time -----------------------------------------------------------------------

	@Test
	void itsTimeOnItsFeetIsOneToTwoSeconds() {
		StumbleStyle style = StumbleStyle.arcade();
		List<String> failures = new ArrayList<>();
		for (Ran ran : finished()) {
			Stumble s = ran.outcome().stumble;
			double at = s.letGoAt();
			double look = s.finisher() == Stumble.Finisher.LAST_LOOK ? style.lookFor : 0.0;
			// One that lost its footing first goes early.
			double least = s.early() ? 0.5 : style.feetMin;
			if (at < least - 1.0e-9 || at > 2.2 + look + DT + 1.0e-9) {
				StumbleTest.fail(failures, ran.death(), ran.outcome(), "on its feet " + at + " s");
			}
		}
		StumbleTest.report(failures, finished().size());
		double onTime = StumbleTest.share(finished(), ran -> !ran.outcome().stumble.early());
		assertTrue(onTime >= 0.85, "finishers on time: " + onTime);
	}

	@Test
	void itTakesTwoToFourSteps() {
		List<Ran> all = finished();
		List<String> failures = new ArrayList<>();
		for (Ran ran : all) {
			int steps = ran.outcome().stumble.staggered();
			// A body that died walking first puts down the foot it had in the air
			// (not a stagger step), and now and then that is all it gets to do.
			int least = ran.death().speed > 3.0 ? 0 : ran.death().speed > 0.0 ? 1 : 2;
			if (steps < least || steps > 5) {
				StumbleTest.fail(failures, ran.death(), ran.outcome(), steps + " steps");
			}
		}
		StumbleTest.report(failures, all.size());
		double twoToFour = StumbleTest.share(all, ran -> ran.outcome().stumble.staggered() >= 2
				&& ran.outcome().stumble.staggered() <= 4);
		assertTrue(twoToFour >= 0.9, "two to four steps: " + twoToFour);
	}

	@Test
	void theHeavierTheBlowTheFewerTheStepsAndTheSoonerItGoes() {
		for (double speed : new double[] {0.0, StumbleTest.WALK}) {
			for (String from : new String[] {"front", "behind", "left"}) {
				int lastSteps = Integer.MAX_VALUE;
				double lastFeet = Double.POSITIVE_INFINITY;
				for (double violence : new double[] {0.0, 0.5, 0.9}) {
					Death d = arcade();
					d.speed = speed;
					d.from = from;
					d.violence = violence;
					Outcome o = StumbleTest.run(d, StumbleTest.flat(), 100);
					assertNotNull(o.stumble, d.toString());
					assertTrue(o.stumble.plannedSteps() <= lastSteps, () -> d + ": " + o.stumble.plannedSteps() + " planned steps");
					assertTrue(o.stumble.onFeet <= lastFeet, () -> d + ": " + o.stumble.onFeet + " s");
					lastSteps = o.stumble.plannedSteps();
					lastFeet = o.stumble.onFeet;
				}
			}
		}
		// Over every death: the light ones last longer than the heavy ones.
		List<Ran> all = finished();
		double light = StumbleTest.median(all.stream().filter(r -> r.death().violence < 0.3).toList(),
				r -> r.outcome().stumble.letGoAt());
		double heavy = StumbleTest.median(all.stream().filter(r -> r.death().violence > 0.5).toList(),
				r -> r.outcome().stumble.letGoAt());
		assertTrue(light > heavy + 0.2, "light blows " + light + " s, heavy " + heavy + " s");
	}

	// --- The hit -------------------------------------------------------------------------------------

	@Test
	void itIsShovedBackAlongTheShot() {
		// The body faces +Z; "front" is struck from ahead, so knocked toward -Z.
		String[] froms = {"front", "behind", "left", "right"};
		Vector3d[] ways = {new Vector3d(0, 0, -1), new Vector3d(0, 0, 1), new Vector3d(-1, 0, 0), new Vector3d(1, 0, 0)};
		for (int i = 0; i < froms.length; i++) {
			Death d = arcade();
			d.from = froms[i];
			d.violence = 0.5;
			Outcome o = StumbleTest.run(d, StumbleTest.flat(), 120);
			Vector3d way = ways[i];
			double along = o.went.x * way.x + o.went.z * way.z;
			assertTrue(along > 0.6, () -> d + ": went " + o.went + ", along the shot " + along);
		}
	}

	@Test
	void itTurnsTowardWhoeverShotIt() {
		// Shot from its left (+X, the body facing +Z) it turns left, toward +X; from the right, toward -X.
		for (String from : new String[] {"left", "right"}) {
			for (double violence : new double[] {0.2, 0.6}) {
				Death d = arcade();
				d.from = from;
				d.violence = violence;
				HumanoidRagdoll r = d.start();
				Stumble s = (Stumble) r.controller();
				double sign = from.equals("left") ? 1.0 : -1.0;
				assertEquals(sign * Math.toRadians(60.0), s.turnTo(), 1.0e-6, d.toString());
				double yawAtEnd = Double.NaN;
				for (int t = 0; t < 60 && s.holding(); t++) {
					r.tick(StumbleTest.flat(), DT);
					Vector3d f = r.facing();
					yawAtEnd = Math.toDegrees(Math.atan2(f.x, f.z));
				}
				double turned = sign * yawAtEnd;
				assertTrue(turned > 35.0 && turned < 80.0, d + ": turned " + turned + " degrees");
			}
		}
		// From dead ahead there is nothing to turn to; from dead behind it does not turn round.
		for (String from : new String[] {"front", "behind"}) {
			Death d = arcade();
			d.from = from;
			Stumble s = (Stumble) d.start().controller();
			assertEquals(0.0, s.turnTo(), 1.0e-6, from);
		}
	}

	// --- The finishers ---------------------------------------------------------------------------------

	@Test
	void theFinisherIsPickedFromHowItWasHit() {
		for (int seed = 0; seed < 8; seed++) {
			for (String from : new String[] {"front", "behind", "left", "right"}) {
				Death d = arcade();
				d.from = from;
				d.seed = seed;
				Stumble s = (Stumble) d.start().controller();
				Set<Stumble.Finisher> fits = switch (from) {
					case "front" -> EnumSet.of(Stumble.Finisher.SIT_DOWN, Stumble.Finisher.LAST_LOOK);
					case "behind" -> EnumSet.of(Stumble.Finisher.FACE_PLANT);
					default -> EnumSet.of(Stumble.Finisher.SPIN_DROP);
				};
				assertTrue(fits.contains(s.planned()), d + ": " + s.planned());
			}
		}
		// Going forward when it was hit (it died running, shot from behind), it falls on its face.
		Death running = arcade();
		running.speed = StumbleTest.SPRINT;
		running.from = "left";
		assertEquals(Stumble.Finisher.FACE_PLANT, ((Stumble) running.start().controller()).planned());
	}

	@Test
	void whereTwoFitTheBodysOwnSeedPicksAndAlwaysTheSame() {
		Set<Stumble.Finisher> seen = EnumSet.noneOf(Stumble.Finisher.class);
		for (int seed = 0; seed < 16; seed++) {
			Death a = arcade();
			a.seed = seed;
			Death b = arcade();
			b.seed = seed;
			Stumble.Finisher fa = ((Stumble) a.start().controller()).planned();
			assertEquals(fa, ((Stumble) b.start().controller()).planned(), "seed " + seed);
			seen.add(fa);
		}
		assertEquals(EnumSet.of(Stumble.Finisher.SIT_DOWN, Stumble.Finisher.LAST_LOOK), seen);
	}

	@Test
	void eachFinisherEndsTheWayItShould() {
		List<Ran> all = finished();
		List<Ran> facePlants = all.stream().filter(r -> r.outcome().stumble.finisher() == Stumble.Finisher.FACE_PLANT).toList();
		List<Ran> sitDowns = all.stream().filter(r -> r.outcome().stumble.finisher() == Stumble.Finisher.SIT_DOWN).toList();
		List<Ran> spins = all.stream().filter(r -> r.outcome().stumble.finisher() == Stumble.Finisher.SPIN_DROP).toList();
		List<Ran> looks = all.stream().filter(r -> r.outcome().stumble.finisher() == Stumble.Finisher.LAST_LOOK).toList();
		assertTrue(facePlants.size() > 100 && sitDowns.size() > 40 && spins.size() > 40 && looks.size() > 40,
				"face-plants " + facePlants.size() + ", sit-downs " + sitDowns.size() + ", spin-drops " + spins.size()
						+ ", last looks " + looks.size());
		double down = StumbleTest.share(facePlants, r -> r.outcome().face() < -0.3);
		assertTrue(down >= 0.85, "face-plants that ended face down: " + down);
		double back = StumbleTest.share(sitDowns, r -> r.outcome().face() > 0.3);
		assertTrue(back >= 0.85, "sit-downs that ended on their backs: " + back);
	}

	@Test
	void theSitDownSitsAndThenTipsBack() {
		Death d = arcade();
		d.from = "front";
		d.seed = seedFor(Stumble.Finisher.SIT_DOWN);
		HumanoidRagdoll r = d.start();
		Stumble s = (Stumble) r.controller();
		HumanoidRagdollTest.FakeWorld world = StumbleTest.flat();
		boolean sat = false;
		for (int t = 0; t < 120; t++) {
			r.tick(world, DT);
			double hips = r.joint(HumanoidRagdoll.RIGHT_LEG, new Vector3d()).y;
			if (!s.holding() && hips < 0.3 && r.lean() < 35.0) {
				// On its seat, its back up.
				sat = true;
			}
		}
		assertEquals(Stumble.Finisher.SIT_DOWN, s.finisher(), d.toString());
		assertTrue(sat, "it sat");
		assertTrue(r.facing().y > 0.5, "then on its back: " + r.facing().y);
	}

	@Test
	void theLastLookHoldsABeatAndThenDrops() {
		Death d = arcade();
		d.from = "front";
		d.seed = seedFor(Stumble.Finisher.LAST_LOOK);
		HumanoidRagdoll r = d.start();
		Stumble s = (Stumble) r.controller();
		HumanoidRagdollTest.FakeWorld world = StumbleTest.flat();
		double looked = 0.0;
		double worstLean = 0.0;
		for (int t = 0; t < 120; t++) {
			r.tick(world, DT);
			if (s.looking() && s.holding()) {
				looked += DT;
				worstLean = Math.max(worstLean, r.lean());
			}
		}
		assertEquals(Stumble.Finisher.LAST_LOOK, s.finisher(), d.toString());
		assertTrue(looked >= StumbleStyle.arcade().lookFor - DT - 1.0e-9, "looked for " + looked + " s");
		assertTrue(worstLean < 30.0, "standing while it looked: lean " + worstLean);
		assertTrue(r.lean() > 45.0, "then down: lean " + r.lean());
	}

	/** A seed that gives a body shot from the front this finisher. */
	private static int seedFor(Stumble.Finisher f) {
		for (int seed = 0; seed < 64; seed++) {
			Death d = arcade();
			d.from = "front";
			d.seed = seed;
			if (((Stumble) d.start().controller()).planned() == f) {
				return seed;
			}
		}
		throw new AssertionError("no seed gives " + f);
	}

	// --- The headshot hang -----------------------------------------------------------------------------

	@Test
	void aHeadshotACrushOrAnOverkillHangsABeatAndThenDrops() {
		List<Death> all = new ArrayList<>();
		for (double speed : new double[] {0.0, StumbleTest.WALK}) {
			Death head = arcade();
			head.hit = HumanoidRagdoll.HEAD;
			head.speed = speed;
			all.add(head);
			Death crush = arcade();
			crush.kind = DeathKind.CRUSH;
			crush.speed = speed;
			all.add(crush);
			Death overkill = arcade();
			overkill.kind = DeathKind.BLOW;
			overkill.violence = 1.0;
			overkill.speed = speed;
			all.add(overkill);
		}
		List<String> failures = new ArrayList<>();
		for (Death d : all) {
			HumanoidRagdoll r = d.start();
			Stumble s = r.controller() instanceof Stumble h ? h : null;
			assertNotNull(s, d.toString());
			assertTrue(s.hang(), d.toString());
			double headAt = r.body(HumanoidRagdoll.TORSO).x.y;
			double sank = 0.0;
			HumanoidRagdollTest.FakeWorld world = StumbleTest.flat();
			for (int t = 0; t < 3; t++) {
				r.tick(world, DT);
				sank = Math.max(sank, headAt - r.body(HumanoidRagdoll.TORSO).x.y);
			}
			assertTrue(sank < 0.05, d + ": sank " + sank + " while it hung");
			Outcome o = StumbleTest.run(d, world, 160);
			assertEquals(StumbleStyle.arcade().hangFor, o.stumble.letGoAt(), DT + 1.0e-9, d.toString());
			assertEquals("hang", o.stumble.why(), d.toString());
			StumbleTest.sound(failures, d, o);
			if (o.body.lean() < 45.0) {
				StumbleTest.fail(failures, d, o, "not lying down");
			}
		}
		StumbleTest.report(failures, all.size());
	}

	@Test
	void anUnconsciousBodyDoesNotHang() {
		Death d = arcade();
		d.hit = HumanoidRagdoll.HEAD;
		d.consciousness = 0.0;
		HumanoidRagdoll r = d.start();
		assertNull(r.controller(), "it just drops");
	}

	// --- A wounded leg ---------------------------------------------------------------------------------

	@Test
	void aWoundedLegLimpsACoupleOfStepsAndThenGoes() {
		List<Ran> legs = everyOutcome().stream().filter(ran -> ran.outcome().stumble != null && !ran.outcome().stumble.hang()
				&& ran.outcome().stumble.weakLeg >= 0).toList();
		assertTrue(legs.size() > 100, "wounded legs: " + legs.size());
		List<String> failures = new ArrayList<>();
		for (Ran ran : legs) {
			if ("its leg gave".equals(ran.outcome().stumble.why()) && ran.outcome().steps() < 2) {
				StumbleTest.fail(failures, ran.death(), ran.outcome(), "gave before it had limped");
			}
		}
		StumbleTest.report(failures, legs.size());
		double gave = StumbleTest.share(legs, ran -> "its leg gave".equals(ran.outcome().stumble.why()));
		assertTrue(gave >= 0.3, "went down over the leg: " + gave);
	}

	// --- Grounded has none of it -------------------------------------------------------------------------

	@Test
	void groundedPlansNothing() {
		Death d = new Death();
		Stumble s = (Stumble) d.start().controller();
		assertEquals(0, s.plannedSteps());
		assertNull(s.planned());
		assertEquals(0.0, s.turnTo());
		assertTrue(!s.hang());
		assertEquals("arcade", StumbleStyle.byName("ARCADE").name);
		assertEquals("grounded", StumbleStyle.byName("grounded").name);
		assertNull(StumbleStyle.byName("off"));
	}

	// --- Not on flat ground --------------------------------------------------------------------------------

	@Test
	void onStairsAtALedgeAtAWallAndInAPitItEndsDown() {
		List<String> failures = new ArrayList<>();
		int total = 0;
		for (String scene : new String[] {"stairs_up", "stairs_down", "ledge", "wall", "pit"}) {
			List<Ran> all = StumbleTest.into(scene, StumbleStyle::arcade);
			for (Ran ran : all) {
				boolean confined = false;
				if (scene.equals("pit")) {
					HumanoidRagdoll r = ran.outcome().body;
					Vector3d chest = r.body(HumanoidRagdoll.TORSO).x;
					double feet = Math.min(r.tip(HumanoidRagdoll.RIGHT_LEG).y, r.tip(HumanoidRagdoll.LEFT_LEG).y);
					confined = ran.outcome().finite && chest.x > -0.1 && chest.x < 1.1 && chest.z > 1.4 && chest.z < 2.6
							&& (chest.y < 0.0 || feet < -0.3);
				}
				List<String> here = new ArrayList<>();
				StumbleTest.sound(here, ran.death(), ran.outcome(), confined);
				for (String f : here) {
					// A body that came to rest up a flight of stairs and later slid
					// down it can sit up at the bottom for a moment: that is the
					// corpse sliding, not a body held up, so long as it ends lying.
					if (scene.startsWith("stairs") && f.startsWith("still on its feet") && !ran.outcome().up
							&& ran.outcome().body.lean() >= 45.0) {
						continue;
					}
					failures.add(scene + ": " + f);
				}
			}
			total += all.size();
		}
		StumbleTest.report(failures, total);
	}

	@Test
	void theSameArcadeDeathTwiceIsTheSameDeath() {
		for (String from : new String[] {"front", "behind", "left"}) {
			Death a = arcade();
			a.from = from;
			a.speed = StumbleTest.WALK;
			a.phase = 1.6;
			Death b = arcade();
			b.from = from;
			b.speed = StumbleTest.WALK;
			b.phase = 1.6;
			Outcome oa = StumbleTest.run(a, StumbleTest.flat(), 120);
			Outcome ob = StumbleTest.run(b, StumbleTest.flat(), 120);
			assertEquals(oa.steps(), ob.steps(), from);
			assertEquals(oa.stumble.letGoAt(), ob.stumble.letGoAt(), 1.0e-12, from);
			assertEquals(0.0, oa.went.distance(ob.went), 1.0e-12, from);
		}
	}
}
