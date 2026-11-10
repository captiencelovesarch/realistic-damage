package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.damage.DeathKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Deaths on their feet (stumbling deaths, IK phase 1) against a fake world:
 * Grounded's rules. Arcade's are in {@link ArcadeStumbleTest}, on the same
 * deaths.
 */
class StumbleTest {
	static final double DT = 0.05;
	static final double UNIT = 0.9375 / 16.0;
	/** How fast a player walks and sprints, blocks a second. */
	static final double WALK = 4.3;
	static final double SPRINT = 5.6;

	/**
	 * One death, as the client would start it: a player-sized body at (0.5, 0,
	 * 0.5) facing +Z, standing or mid-stride, killed.
	 */
	static final class Death {
		StumbleStyle style = StumbleStyle.grounded();
		DeathKind kind = DeathKind.PROJECTILE;
		double violence = 0.4;
		/** Where it struck, a ragdoll part, or -1. */
		int hit = HumanoidRagdoll.TORSO;
		/** How fast it was going, the way it faces, and where in its stride it was. */
		double speed;
		double phase;
		/** Where the blow came from: front (it is knocked back), behind, left, right. */
		String from = "front";
		/** The knockback the server gives a body: along the ground and up, blocks a second. */
		double knock = 8.0;
		double lift = 8.0;
		boolean[] broken = new boolean[HumanoidRagdoll.PARTS];
		boolean[] limp = new boolean[HumanoidRagdoll.PARTS];
		double consciousness = 100.0;
		boolean surge;
		boolean crash;
		int seed = 7;
		/** How far off the ground its feet were, and how fast it was rising, blocks and blocks a second. */
		double height;
		double rise;

		/** The way the blow went, along the ground. */
		Vector3d way() {
			return switch (from) {
				case "behind" -> new Vector3d(0.0, 0.0, 1.0);
				case "left" -> new Vector3d(-1.0, 0.0, 0.0);
				case "right" -> new Vector3d(1.0, 0.0, 0.0);
				default -> new Vector3d(0.0, 0.0, -1.0);
			};
		}

		Muscles muscles() {
			return Muscles.forDeath(new Muscles.Input(kind, violence, hit, consciousness, false, broken, limp, 0, surge,
					crash));
		}

		/** The body, launched: with a {@link Stumble} for its controller if this death gets one. */
		HumanoidRagdoll start() {
			HumanoidRagdoll.Part[] parts = HumanoidRagdoll.playerParts(false);
			double stride = Math.min(1.0, speed * DT * 4.0);
			float[] now = walkPose(phase, stride);
			float[] before = walkPose(phase - stride, stride);
			HumanoidRagdoll r = new HumanoidRagdoll(parts, new Vector3d(0.5, height, 0.5), 0.0F, UNIT, now);
			Vector3d seen = new Vector3d(0.0, rise, speed);
			r.inheritMotion(parts, before, seen, 0.35, DT);
			Muscles muscles = muscles();
			Vector3d way = way();
			boolean struck = kind == DeathKind.BLOW || kind == DeathKind.PROJECTILE;
			// The game's knockback: half of what it was doing, and the blow's own.
			Vector3d sent = struck ? new Vector3d(0.0, 0.0, speed * 0.5).add(way.x * knock, lift, way.z * knock)
					: new Vector3d(seen);
			double kick = !struck ? 0.0 : kind == DeathKind.PROJECTILE ? (0.15 + 0.20 * violence) * 20.0
					: (0.10 + 0.20 * violence) * 20.0;
			Behaviour.Input in = new Behaviour.Input(kind, violence, struck ? hit : -1, struck ? hit : HumanoidRagdoll.TORSO,
					muscles, new Vector3d(way), seed);
			// The fall this death has without a stumble, near enough.
			Runnable fall = () -> {
				r.setMuscles(muscles);
				r.setBehaviour(Behaviour.choose(in));
				if (sent.lengthSquared() > seen.lengthSquared()) {
					r.push(sent.x - seen.x, sent.y - seen.y, sent.z - seen.z);
				}
				r.spin(new Vector3d(way.z, 0.0, -way.x).mul(0.6), new Vector3d(0.5, 0.0, 0.5));
			};
			if (Stumble.begin(style, r, in, sent, seen, struck ? way : null, kick, fall) == null) {
				fall.run();
			}
			return r;
		}

		@Override
		public String toString() {
			return String.format(Locale.ROOT, "%s hit %d from %s at %.1f (phase %.1f, violence %.1f, seed %d%s)", kind, hit,
					from, speed, phase, violence, seed,
					height > 0.0 || rise != 0.0 ? String.format(Locale.ROOT, ", %.1f up rising %.1f", height, rise) : "");
		}
	}

	/** How a death went. */
	static final class Outcome {
		Stumble stumble;
		HumanoidRagdoll body;
		/** How far its centre of mass got, blocks. */
		Vector3d went;
		/** The deepest any part sat in a block, and the furthest any joint came apart, blocks. */
		double pen;
		double sep;
		/** The same while it was on its feet, and at the end. */
		double penHeld;
		double sepHeld;
		double penEnd;
		double sepEnd;
		/** Seconds until it was last seen on its feet: leaning less than {@link #STANDING} with its head up. */
		double lastUp;
		/** Whether it was still on its feet at the end. */
		boolean up;
		boolean finite = true;

		int steps() {
			return stumble == null ? 0 : stumble.stepper.steps();
		}

		/** Steps, and the stride it went over in the middle of. */
		int strides() {
			return stumble == null ? 0 : stumble.stepper.strides();
		}

		/** -1 face down, +1 on its back. */
		double face() {
			return body.facing().y;
		}
	}

	/**
	 * Leaning less than this, degrees, with its head this far above its lowest
	 * part and its chest this far off the ground under it, a body is still up.
	 * (The chest, because a body lying up a flight of stairs has its head well
	 * above its feet too.)
	 */
	static final double STANDING = 40.0;
	static final double HEAD_UP = 1.3;
	static final double CHEST_UP = 0.8;

	static boolean up(HumanoidRagdoll r, HumanoidRagdollTest.FakeWorld world) {
		double low = Double.MAX_VALUE;
		for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
			if (r.present(i)) {
				low = Math.min(low, Math.min(r.body(i).x.y, r.tip(i).y));
			}
		}
		if (r.lean() >= STANDING || r.body(HumanoidRagdoll.HEAD).x.y - low <= HEAD_UP) {
			return false;
		}
		Vector3d chest = r.body(HumanoidRagdoll.TORSO).x;
		double ground = Double.NEGATIVE_INFINITY;
		for (double[] b : world.boxes) {
			if (chest.x >= b[0] && chest.x < b[3] && chest.z >= b[2] && chest.z < b[5] && b[4] <= chest.y) {
				ground = Math.max(ground, b[4]);
			}
		}
		return chest.y - ground > CHEST_UP;
	}

	static Outcome run(Death death, HumanoidRagdollTest.FakeWorld world, int ticks) {
		Outcome o = new Outcome();
		HumanoidRagdoll r = death.start();
		o.body = r;
		o.stumble = r.controller() instanceof Stumble s ? s : null;
		Vector3d start = Balance.centreOfMass(r, new Vector3d());
		for (int t = 0; t < ticks; t++) {
			r.tick(world, DT);
			double pen = world.maxPenetration(r);
			double sep = r.worstSeparation();
			o.pen = Math.max(o.pen, pen);
			o.sep = Math.max(o.sep, sep);
			if (o.stumble != null && o.stumble.holding()) {
				o.penHeld = Math.max(o.penHeld, pen);
				o.sepHeld = Math.max(o.sepHeld, sep);
			}
			o.penEnd = pen;
			o.sepEnd = sep;
			o.up = up(r, world);
			if (o.up) {
				o.lastUp = (t + 1) * DT;
			}
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				o.finite &= r.body(i).x.isFinite() && r.body(i).q.isFinite();
			}
		}
		o.went = Balance.centreOfMass(r, new Vector3d()).sub(start);
		return o;
	}

	static HumanoidRagdollTest.FakeWorld flat() {
		return StepperTest.scene("flat");
	}

	/** Every death that could stumble: both kinds of hit on every part from every side, and bleed-outs. */
	static List<Death> everyDeath() {
		List<Death> all = new ArrayList<>();
		double[] speeds = {0.0, 2.0, WALK, SPRINT};
		double[] phases = {0.0, 0.8, 1.6, 2.4, 3.2, 4.0};
		double[] violences = {0.2, 0.6, 1.0};
		for (double speed : speeds) {
			for (double phase : phases) {
				for (double violence : violences) {
					for (DeathKind kind : new DeathKind[] {DeathKind.PROJECTILE, DeathKind.BLOW}) {
						for (int hit = 0; hit < HumanoidRagdoll.PARTS; hit++) {
							for (String from : new String[] {"front", "behind", "left", "right"}) {
								Death d = new Death();
								d.kind = kind;
								d.hit = hit;
								d.from = from;
								d.speed = speed;
								d.phase = phase;
								d.violence = violence;
								d.seed = all.size();
								all.add(d);
							}
						}
					}
					for (int seed = 0; seed < 4; seed++) {
						Death d = new Death();
						d.kind = DeathKind.FADE;
						d.speed = speed;
						d.phase = phase;
						d.violence = violence;
						d.seed = 31 * seed + all.size();
						all.add(d);
					}
				}
			}
		}
		return all;
	}

	static void fail(List<String> failures, Death d, Outcome o, String what) {
		failures.add(String.format(Locale.ROOT, "%s: %s [%s, %d steps, down at %.2f (%s), went %.2f %.2f, lean %.0f, face %.2f, pen %.3f, sep %.3f, up until %.2f]",
				what, d, o.stumble == null ? "no stumble" : String.format(Locale.ROOT, "%.2f s on its feet", o.stumble.onFeet),
				o.steps(), o.stumble == null ? 0.0 : o.stumble.letGoAt(), o.stumble == null ? "-" : o.stumble.why(),
				o.went.z, o.went.x, o.body.lean(), o.face(), o.pen, o.sep, o.lastUp));
	}

	static void report(List<String> failures, int of) {
		assertTrue(failures.isEmpty(), () -> failures.size() + " of " + of + " deaths:\n  "
				+ String.join("\n  ", failures.subList(0, Math.min(40, failures.size()))));
	}

	// --- Every death ends on the ground ---------------------------------------------------

	/** A death and how it went. */
	record Ran(Death death, Outcome outcome) {
	}

	private static List<Ran> everyOutcome;

	/**
	 * Every death of {@link #everyDeath} on flat ground, eight seconds of each:
	 * run once, for all the tests that read them. With RD_STATS set each is
	 * printed as a line, for the tuning scripts.
	 */
	static synchronized List<Ran> everyOutcome() {
		if (everyOutcome == null) {
			everyOutcome = everyDeath().parallelStream().map(d -> {
				tune(d.style);
				Outcome o = run(d, flat(), 160);
				if (System.getenv("RD_STATS") != null) {
					System.out.printf(Locale.ROOT, "ST %s %s %d %s %.1f %.1f %.1f | %d %d %.2f %s | %.3f %.3f %.3f %.3f %.3f %.3f | %.1f %.2f %.2f %.2f %.2f %.3f %.2f%n",
							d.kind, d.hit, d.seed, d.from, d.speed, d.phase, d.violence, o.stumble == null ? 0 : 1, o.steps(),
							o.stumble == null ? 0.0 : o.stumble.letGoAt(), o.stumble == null ? "-" : String.valueOf(o.stumble.why()).replace(' ', '_'),
							o.pen, o.sep, o.penHeld, o.sepHeld, o.penEnd, o.sepEnd, o.body.lean(), o.face(), o.went.z, o.went.x, o.lastUp,
							o.body.sim.maxMotion(), o.body.tip(HumanoidRagdoll.RIGHT_LEG).distance(o.body.tip(HumanoidRagdoll.LEFT_LEG)));
				}
				return new Ran(d, o);
			}).toList();
		}
		return everyOutcome;
	}

	/**
	 * The deepest a part may sit in a block, and the furthest a joint may come
	 * apart, blocks: once the body lies there, while it is on its feet, and for
	 * a tick on the way down (a body landing is caught a little late, and a
	 * limb caught under it is pulled on until it comes free).
	 */
	static final double PEN_END = 0.08;
	static final double SEP_END = 0.10;
	static final double PEN_HELD = 0.08;
	static final double SEP_HELD = 0.12;
	static final double PEN_EVER = 0.2;
	static final double SEP_EVER = 0.45;

	/** What holds for any death anywhere: it is let go of in time, ends down and in one piece, and in no block. */
	static void sound(List<String> failures, Death d, Outcome o) {
		sound(failures, d, o, false);
	}

	/**
	 * {@code confined}: it ended somewhere it cannot lie down in (a hole one
	 * block wide), so it is not asked to.
	 */
	static void sound(List<String> failures, Death d, Outcome o, boolean confined) {
		if (!o.finite) {
			fail(failures, d, o, "blew up");
			return;
		}
		if (o.stumble != null) {
			// The cap: no body is held up past the style's longest time on its feet.
			if (o.stumble.holding() || o.stumble.letGoAt() > d.style.feetMax + DT + 1.0e-9) {
				fail(failures, d, o, "still held up");
			}
			// And none is left standing once it has been let go of.
			if (!confined && o.lastUp > d.style.feetMax + 1.5) {
				fail(failures, d, o, "still on its feet");
			}
			if (o.penHeld > PEN_HELD) {
				fail(failures, d, o, "in a block on its feet");
			}
			if (o.sepHeld > SEP_HELD) {
				fail(failures, d, o, "came apart on its feet");
			}
		}
		if (o.up && !confined) {
			fail(failures, d, o, "left standing");
		}
		if (o.penEnd > PEN_END || o.pen > PEN_EVER) {
			fail(failures, d, o, "in a block");
		}
		if (o.sepEnd > SEP_END || o.sep > SEP_EVER) {
			fail(failures, d, o, "came apart");
		}
	}

	@Test
	void thousandsOfDeathsAllEndLyingDown() {
		List<Ran> all = everyOutcome();
		assertTrue(all.size() > 3000, "thousands: " + all.size());
		List<String> failures = new ArrayList<>();
		int stumbled = 0;
		for (Ran ran : all) {
			sound(failures, ran.death, ran.outcome);
			if (ran.outcome.stumble != null) {
				stumbled++;
			}
			if (ran.outcome.finite && ran.outcome.body.lean() < 45.0) {
				fail(failures, ran.death, ran.outcome, "not lying down");
			}
		}
		assertTrue(stumbled > all.size() / 3, "deaths that stumbled: " + stumbled + " of " + all.size());
		report(failures, all.size());
	}

	@Test
	void theSameDeathTwiceIsTheSameDeath() {
		List<Death> deaths = new ArrayList<>();
		for (int twice = 0; twice < 2; twice++) {
			Death arrow = new Death();
			deaths.add(arrow);
			Death running = new Death();
			running.speed = SPRINT;
			running.phase = 1.6;
			running.from = "behind";
			deaths.add(running);
			Death leg = new Death();
			leg.kind = DeathKind.BLOW;
			leg.hit = HumanoidRagdoll.LEFT_LEG;
			leg.speed = WALK;
			leg.from = "left";
			deaths.add(leg);
			Death fade = new Death();
			fade.kind = DeathKind.FADE;
			fade.seed = 1234;
			deaths.add(fade);
		}
		int each = deaths.size() / 2;
		for (String scene : new String[] {"flat", "stairs_down"}) {
			for (int i = 0; i < each; i++) {
				Outcome a = run(deaths.get(i), StepperTest.scene(scene), 120);
				Outcome b = run(deaths.get(i + each), StepperTest.scene(scene), 120);
				assertNotNull(a.stumble, deaths.get(i) + " stumbles");
				assertEquals(a.stumble.letGoAt(), b.stumble.letGoAt(), 0.0, deaths.get(i) + " on " + scene);
				assertEquals(a.steps(), b.steps(), deaths.get(i) + " on " + scene);
				for (int part = 0; part < HumanoidRagdoll.PARTS; part++) {
					assertEquals(a.body.body(part).x, b.body.body(part).x, deaths.get(i) + " on " + scene + ", part " + part);
					assertEquals(a.body.body(part).q, b.body.body(part).q, deaths.get(i) + " on " + scene + ", part " + part);
				}
			}
		}
	}

	// --- How far it gets ------------------------------------------------------------------------

	static double share(List<Ran> of, java.util.function.Predicate<Ran> which) {
		return of.isEmpty() ? 0.0 : of.stream().filter(which).count() / (double) of.size();
	}

	static double median(List<Ran> of, java.util.function.ToDoubleFunction<Ran> what) {
		double[] v = of.stream().mapToDouble(what).sorted().toArray();
		return v[v.length / 2];
	}

	/** The deaths that stumbled at a speed: those struck in a leg, or those struck anywhere else (and the bleed-outs). */
	private static List<Ran> stumbledAt(double speed, boolean inALeg) {
		return everyOutcome().stream().filter(ran -> ran.outcome.stumble != null && ran.death.speed == speed
				&& (ran.death.hit >= HumanoidRagdoll.RIGHT_LEG && ran.death.kind != DeathKind.FADE) == inALeg).toList();
	}

	@Test
	void theFasterItWasGoingTheMoreStepsItTakesAndTheFurtherItGets() {
		List<Ran> standing = stumbledAt(0.0, false);
		List<Ran> slow = stumbledAt(2.0, false);
		List<Ran> walking = stumbledAt(WALK, false);
		List<Ran> sprinting = stumbledAt(SPRINT, false);
		assertTrue(standing.size() > 200 && walking.size() > 200 && sprinting.size() > 200,
				standing.size() + " " + walking.size() + " " + sprinting.size());
		// Nothing takes more than a few steps: it is dying, not walking it off.
		for (Ran ran : everyOutcome()) {
			assertTrue(ran.outcome.steps() <= 4, () -> ran.outcome.steps() + " steps: " + ran.death);
		}
		// Standing still it has nothing to catch but its own sway: a step or two, and it lies where it stood.
		for (Ran ran : standing) {
			assertTrue(ran.outcome.steps() <= 2, () -> ran.outcome.steps() + " steps standing: " + ran.death);
			assertTrue(Math.hypot(ran.outcome.went.x, ran.outcome.went.z) < 1.6,
					() -> "went " + ran.outcome.went + " standing: " + ran.death);
		}
		assertTrue(share(standing, ran -> ran.outcome.steps() <= 1) > 0.8, "standing, a step at most: "
				+ share(standing, ran -> ran.outcome.steps() <= 1));
		// Running, it runs on: two strides or more, well past where it died, and down on its face.
		for (List<Ran> running : List.of(walking, sprinting)) {
			double speed = running.get(0).death.speed;
			double strides = share(running, ran -> ran.outcome.strides() >= 2);
			double far = share(running, ran -> ran.outcome.went.z >= 1.4);
			double faceFirst = share(running, ran -> ran.outcome.face() < -0.3);
			assertTrue(strides >= 0.95, "at " + speed + ", two strides or more: " + strides);
			assertTrue(far >= 0.95, "at " + speed + ", 1.4 blocks or more: " + far);
			assertTrue(faceFirst >= 0.92, "at " + speed + ", face first: " + faceFirst);
		}
		double[] medians = {median(standing, ran -> ran.outcome.went.z), median(slow, ran -> ran.outcome.went.z),
				median(walking, ran -> ran.outcome.went.z), median(sprinting, ran -> ran.outcome.went.z)};
		for (int i = 1; i < medians.length; i++) {
			assertTrue(medians[i] > medians[i - 1] + 0.3, "further the faster: " + java.util.Arrays.toString(medians));
		}
		// And it does not carry on forever: a sprinter is down within a few blocks.
		for (Ran ran : sprinting) {
			assertTrue(ran.outcome.went.z < 5.5, () -> "went " + ran.outcome.went.z + ": " + ran.death);
		}
	}

	/**
	 * The stride it died in decides how its first step goes, and the sweep
	 * above samples only six points of one. Here: a body struck in the torso,
	 * walking or sprinting, from every side, at every tenth of the way round
	 * its stride. Most take two strides; one that died with its feet together
	 * puts a foot down short, is over it before the next arrives, and goes down
	 * on its face after a single step (walking into the blow most of all).
	 * Nearly every one of them ends up lying well clear of where it died; the
	 * rare one comes down low on a leg stuck out in front of it, is stopped dead
	 * by it and topples where it stands (one of these 768).
	 */
	@Test
	void whereverInItsStrideItDied() {
		List<Death> deaths = new ArrayList<>();
		for (double speed : new double[] {WALK, SPRINT}) {
			for (int i = 0; i < 48; i++) {
				for (DeathKind kind : new DeathKind[] {DeathKind.PROJECTILE, DeathKind.BLOW}) {
					for (String from : new String[] {"front", "behind", "left", "right"}) {
						Death d = new Death();
						d.kind = kind;
						d.from = from;
						d.speed = speed;
						// One whole stride, both feet: what the game picks from.
						d.phase = i / 48.0 * 2.0 * Math.PI / 0.6662;
						d.violence = 0.7;
						d.seed = deaths.size();
						deaths.add(d);
					}
				}
			}
		}
		List<Ran> all = deaths.parallelStream().map(d -> {
			tune(d.style);
			return new Ran(d, run(d, flat(), 160));
		}).toList();
		List<String> failures = new ArrayList<>();
		for (Ran ran : all) {
			sound(failures, ran.death, ran.outcome);
			if (ran.outcome.stumble == null) {
				fail(failures, ran.death, ran.outcome, "did not stumble");
			} else if (ran.outcome.steps() < 1) {
				fail(failures, ran.death, ran.outcome, "never put a foot down");
			}
		}
		report(failures, all.size());
		double clear = share(all, ran -> ran.outcome.went.z >= 0.8);
		assertTrue(clear >= 0.99, "clear of where it died: " + clear);
		for (double speed : new double[] {WALK, SPRINT}) {
			List<Ran> running = all.stream().filter(ran -> ran.death.speed == speed).toList();
			double steps = share(running, ran -> ran.outcome.steps() >= 2);
			double strides = share(running, ran -> ran.outcome.strides() >= 2);
			double far = share(running, ran -> ran.outcome.went.z >= 1.4);
			double faceFirst = share(running, ran -> ran.outcome.face() < -0.3);
			if (System.getenv("RD_STATS") != null) {
				System.out.printf(Locale.ROOT, "STRIDE at %.1f: two steps %.3f, two strides %.3f, 1.4 blocks %.3f, face first %.3f, of %d%n",
						speed, steps, strides, far, faceFirst, running.size());
				for (String from : new String[] {"front", "behind", "left", "right"}) {
					List<Ran> side = running.stream().filter(ran -> ran.death.from.equals(from)).toList();
					System.out.printf(Locale.ROOT, "STRIDE at %.1f from %s: two steps %.3f, two strides %.3f, 1.4 blocks %.3f, face first %.3f, least went %.2f%n",
							speed, from, share(side, ran -> ran.outcome.steps() >= 2), share(side, ran -> ran.outcome.strides() >= 2),
							share(side, ran -> ran.outcome.went.z >= 1.4), share(side, ran -> ran.outcome.face() < -0.3),
							side.stream().mapToDouble(ran -> ran.outcome.went.z).min().orElse(0.0));
				}
			}
			assertTrue(steps >= 0.9, "at " + speed + ", two steps put down: " + steps);
			assertTrue(strides >= 0.92, "at " + speed + ", two strides or more: " + strides);
			assertTrue(far >= 0.95, "at " + speed + ", 1.4 blocks or more: " + far);
			assertTrue(faceFirst >= 0.95, "at " + speed + ", face first: " + faceFirst);
		}
	}

	// --- A wounded leg ---------------------------------------------------------------------------

	@Test
	void aLegThatWasStruckGivesOnItsFirstStep() {
		List<Ran> struck = everyOutcome().stream().filter(ran -> ran.outcome.stumble != null
				&& ran.death.kind != DeathKind.FADE && ran.death.hit >= HumanoidRagdoll.RIGHT_LEG).toList();
		assertTrue(struck.size() > 500, "leg hits that stumble: " + struck.size());
		for (Ran ran : struck) {
			assertEquals(ran.death.hit - HumanoidRagdoll.RIGHT_LEG, ran.outcome.stumble.weakLeg, "the weak leg: " + ran.death);
			assertTrue(ran.outcome.steps() <= 2, () -> ran.outcome.steps() + " steps on a wounded leg: " + ran.death);
		}
		double gave = share(struck, ran -> "its leg gave".equals(ran.outcome.stumble.why()));
		assertTrue(gave >= 0.85, "its leg gave: " + gave);
		// Sooner than the same body struck anywhere else.
		for (double speed : new double[] {0.0, WALK, SPRINT}) {
			double leg = median(stumbledAt(speed, true), ran -> ran.outcome.stumble.letGoAt());
			double body = median(stumbledAt(speed, false), ran -> ran.outcome.stumble.letGoAt());
			assertTrue(leg < body - 0.3, "at " + speed + ", down at " + leg + " on a wounded leg and at " + body + " otherwise");
		}
	}

	@Test
	void aBrokenOrLimpLegIsAWeakLegAndTwoAreNothingToStandOn() {
		Death sound = new Death();
		Outcome whole = run(sound, flat(), 100);
		assertNotNull(whole.stumble, "an arrow in the chest stumbles");
		assertEquals(-1, whole.stumble.weakLeg);
		for (int leg = HumanoidRagdoll.RIGHT_LEG; leg <= HumanoidRagdoll.LEFT_LEG; leg++) {
			for (boolean limp : new boolean[] {false, true}) {
				Death d = new Death();
				(limp ? d.limp : d.broken)[leg] = true;
				d.speed = WALK;
				Outcome o = run(d, flat(), 100);
				String what = (limp ? "a limp" : "a broken") + " leg " + leg;
				assertNotNull(o.stumble, what + " still stumbles");
				assertEquals(leg - HumanoidRagdoll.RIGHT_LEG, o.stumble.weakLeg, what);
				assertTrue(o.stumble.onFeet < whole.stumble.onFeet, what + ": " + o.stumble.onFeet + " s on its feet, "
						+ whole.stumble.onFeet + " sound");
				assertTrue(o.steps() <= 2, what + ": " + o.steps() + " steps");
				assertTrue(!o.up && o.body.lean() > 45.0, what + " ends down");
			}
		}
		Death both = new Death();
		both.broken[HumanoidRagdoll.RIGHT_LEG] = true;
		both.limp[HumanoidRagdoll.LEFT_LEG] = true;
		assertNull(run(both, flat(), 40).stumble, "no leg to stand on");
		Death other = new Death();
		other.broken[HumanoidRagdoll.RIGHT_LEG] = true;
		other.hit = HumanoidRagdoll.LEFT_LEG;
		assertNull(run(other, flat(), 40).stumble, "one broken, the other struck");
	}

	// --- Who does not stumble --------------------------------------------------------------------

	@Test
	void onlyBlowsArrowsAndBleedOutsStumble() {
		for (DeathKind kind : DeathKind.values()) {
			Death d = new Death();
			d.kind = kind;
			boolean stumbles = kind == DeathKind.BLOW || kind == DeathKind.PROJECTILE || kind == DeathKind.FADE;
			Outcome o = run(d, flat(), 40);
			assertEquals(stumbles, o.stumble != null, kind + " stumbles");
		}
	}

	@Test
	void aBodyWithItsStringsCutDropsOnTheSpot() {
		// A headshot, and an overkill.
		for (double violence : new double[] {0.2, 0.6, 1.0}) {
			Death head = new Death();
			head.hit = HumanoidRagdoll.HEAD;
			head.violence = violence;
			assertNull(run(head, flat(), 40).stumble, "a headshot, violence " + violence);
		}
		Death overkill = new Death();
		overkill.violence = 1.0;
		overkill.speed = SPRINT;
		assertNull(run(overkill, flat(), 40).stumble, "an overkill");
		// One that was hardly there.
		Death faint = new Death();
		faint.consciousness = 20.0;
		assertNull(run(faint, flat(), 40).stumble, "barely conscious");
	}

	@Test
	void aBodyThatWasThrownIsNotOnItsFeet() {
		Death knockback = new Death();
		knockback.knock = 24.0;
		assertNull(run(knockback, flat(), 40).stumble, "Knockback II");
		Death launched = new Death();
		launched.lift = 13.0;
		assertNull(run(launched, flat(), 40).stumble, "launched upward");
		// A plain hit, and a sprinting one, are not that.
		Death plain = new Death();
		assertNotNull(run(plain, flat(), 40).stumble, "a plain hit");
		Death sprintHit = new Death();
		sprintHit.kind = DeathKind.BLOW;
		sprintHit.knock = 14.0;
		assertNotNull(run(sprintHit, flat(), 40).stumble, "a sprinting hit");
	}

	@Test
	void withTheSettingOffNothingStumbles() {
		for (Death d : everyDeath().subList(0, 400)) {
			d.style = null;
			HumanoidRagdoll r = d.start();
			assertNull(r.controller(), "off: " + d);
		}
		assertNull(StumbleStyle.byName("off"));
		assertNull(StumbleStyle.byName(null));
		assertNotNull(StumbleStyle.byName("Grounded"));
	}

	// --- How long ------------------------------------------------------------------------------------

	@Test
	void itsTimeOnItsFeetComesFromItsMuscles() {
		double last = Double.MAX_VALUE;
		for (double violence : new double[] {0.1, 0.3, 0.5, 0.7, 0.9}) {
			Death d = new Death();
			d.violence = violence;
			Outcome o = run(d, flat(), 4);
			if (o.stumble == null) {
				last = 0.0;
				continue;
			}
			assertTrue(o.stumble.onFeet >= d.style.feetMin && o.stumble.onFeet <= d.style.feetMax, "seconds: " + o.stumble.onFeet);
			assertTrue(o.stumble.onFeet <= last, "the harder the hit the shorter: " + o.stumble.onFeet + " after " + last);
			last = o.stumble.onFeet;
		}
		Death plain = new Death();
		Death surge = new Death();
		surge.surge = true;
		Death crash = new Death();
		crash.crash = true;
		double usual = run(plain, flat(), 4).stumble.onFeet;
		assertTrue(run(surge, flat(), 4).stumble.onFeet >= usual, "adrenaline keeps it up");
		Outcome crashed = run(crash, flat(), 4);
		assertTrue(crashed.stumble == null || crashed.stumble.onFeet <= usual, "a crash does not");
		// Every stumble there is: within the style's limits, and down by the end of it.
		for (Ran ran : everyOutcome()) {
			Stumble s = ran.outcome.stumble;
			if (s != null) {
				assertTrue(s.onFeet >= ran.death.style.feetMin - 1.0e-9 && s.onFeet <= ran.death.style.feetMax + 1.0e-9,
						() -> s.onFeet + " s: " + ran.death);
				assertTrue(s.letGoAt() <= s.onFeet + DT + 1.0e-9, () -> "down at " + s.letGoAt() + " of " + s.onFeet + ": " + ran.death);
			}
		}
	}

	// --- Not on flat ground ------------------------------------------------------------------------

	/** Deaths to try a place with: standing, walking and sprinting into it, struck from either side, and fading. */
	static List<Death> deathsInto() {
		List<Death> all = new ArrayList<>();
		for (double speed : new double[] {0.0, WALK, SPRINT}) {
			for (double phase : new double[] {0.0, 1.2, 2.4, 3.6}) {
				for (double violence : new double[] {0.2, 0.6}) {
					for (String from : new String[] {"front", "behind", "left"}) {
						Death arrow = new Death();
						arrow.from = from;
						arrow.speed = speed;
						arrow.phase = phase;
						arrow.violence = violence;
						arrow.seed = all.size();
						all.add(arrow);
						Death blow = new Death();
						blow.kind = DeathKind.BLOW;
						blow.hit = all.size() % 2 == 0 ? HumanoidRagdoll.RIGHT_ARM : HumanoidRagdoll.LEFT_LEG;
						blow.from = from;
						blow.speed = speed;
						blow.phase = phase;
						blow.violence = violence;
						blow.seed = all.size();
						all.add(blow);
					}
					Death fade = new Death();
					fade.kind = DeathKind.FADE;
					fade.speed = speed;
					fade.phase = phase;
					fade.violence = violence;
					fade.seed = 17 * all.size();
					all.add(fade);
				}
			}
		}
		return all;
	}

	private static List<Ran> into(String scene) {
		return into(scene, null);
	}

	/** {@link #deathsInto} a place, each with a style of its own ({@code style} makes it), or the default one for null. */
	static List<Ran> into(String scene, java.util.function.Supplier<StumbleStyle> style) {
		return deathsInto().parallelStream().map(d -> {
			if (style != null) {
				d.style = style.get();
			}
			Outcome o = run(d, StepperTest.scene(scene), 240);
			if (System.getenv("RD_STATS") != null) {
				Vector3d c = Balance.centreOfMass(o.body, new Vector3d());
				System.out.printf(Locale.ROOT, "SC %s %s %s %d from %s v %.1f | st %d steps %d down %.2f %s | pen %.3f %.3f %.3f sep %.3f %.3f %.3f | lean %.1f face %.2f at %.2f %.2f %.2f up %.2f%n",
						scene, d.kind, d.hit, d.seed, d.from, d.speed, o.stumble == null ? 0 : 1, o.steps(),
						o.stumble == null ? 0.0 : o.stumble.letGoAt(), o.stumble == null ? "-" : String.valueOf(o.stumble.why()).replace(' ', '_'),
						o.pen, o.penHeld, o.penEnd, o.sep, o.sepHeld, o.sepEnd, o.body.lean(), o.face(), c.x, c.y, c.z, o.lastUp);
			}
			return new Ran(d, o);
		}).toList();
	}

	@Test
	void onStairsItEndsLyingOnThem() {
		List<String> failures = new ArrayList<>();
		int total = 0;
		for (String scene : new String[] {"stairs_up", "stairs_down"}) {
			List<Ran> all = into(scene);
			List<String> here = new ArrayList<>();
			for (Ran ran : all) {
				sound(here, ran.death, ran.outcome);
			}
			for (String failure : here) {
				failures.add(scene + ": " + failure);
			}
			total += all.size();
		}
		report(failures, total);
	}

	@Test
	void atALedgeItStepsOffAndLiesAtTheBottom() {
		List<Ran> all = into("ledge");
		List<String> failures = new ArrayList<>();
		for (Ran ran : all) {
			sound(failures, ran.death, ran.outcome);
		}
		report(failures, all.size());
		// A running body does not stop for the edge.
		List<Ran> running = all.stream().filter(ran -> ran.outcome.stumble != null && ran.death.speed >= WALK
				&& ran.death.hit < HumanoidRagdoll.RIGHT_LEG).toList();
		double fell = share(running, ran -> Balance.centreOfMass(ran.outcome.body, new Vector3d()).y < -2.0);
		assertTrue(fell >= 0.9, "running bodies that went over the edge: " + fell);
	}

	@Test
	void atAWallItGoesDownAgainstIt() {
		List<Ran> all = into("wall");
		List<String> failures = new ArrayList<>();
		for (Ran ran : all) {
			sound(failures, ran.death, ran.outcome);
			// It does not walk on the spot against it.
			if (ran.outcome.steps() > 4) {
				fail(failures, ran.death, ran.outcome, "kept walking into the wall");
			}
		}
		report(failures, all.size());
	}

	@Test
	void atAPitItGoesIn() {
		List<Ran> all = into("pit");
		List<String> failures = new ArrayList<>();
		int in = 0;
		for (Ran ran : all) {
			// The hole is one block square and three deep: a body in it, hanging
			// by its arms or standing on the bottom, has no room to lie down.
			HumanoidRagdoll r = ran.outcome.body;
			Vector3d chest = r.body(HumanoidRagdoll.TORSO).x;
			double feet = Math.min(r.tip(HumanoidRagdoll.RIGHT_LEG).y, r.tip(HumanoidRagdoll.LEFT_LEG).y);
			boolean inTheHole = ran.outcome.finite && chest.x > -0.1 && chest.x < 1.1 && chest.z > 1.4 && chest.z < 2.6
					&& (chest.y < 0.0 || feet < -0.3);
			if (inTheHole) {
				in++;
			}
			sound(failures, ran.death, ran.outcome, inTheHole);
		}
		report(failures, all.size());
		assertTrue(in >= all.size() / 5, "bodies that ended in the hole: " + in + " of " + all.size());
	}

	@Test
	void killedOffTheGroundItComesDown() {
		List<Death> all = new ArrayList<>();
		for (double height : new double[] {0.3, 0.7}) {
			for (double rise : new double[] {6.0, 0.0, -8.0}) {
				for (double speed : new double[] {0.0, SPRINT}) {
					for (String from : new String[] {"front", "behind"}) {
						Death d = new Death();
						d.height = height;
						d.rise = rise;
						d.speed = speed;
						d.from = from;
						d.seed = all.size();
						all.add(d);
					}
				}
			}
		}
		List<String> failures = new ArrayList<>();
		for (Death d : all) {
			Outcome o = run(d, flat(), 200);
			sound(failures, d, o);
			if (o.finite && o.body.lean() < 45.0) {
				fail(failures, d, o, "not lying down");
			}
		}
		report(failures, all.size());
	}

	/** For tuning by hand: RD_SET=name=value,name=value changes the style every death in a test has. */
	static void tune(StumbleStyle style) {
		String set = System.getenv("RD_SET");
		if (set == null || set.isBlank()) {
			return;
		}
		for (String pair : set.split(",")) {
			String[] kv = pair.split("=");
			try {
				StumbleStyle.class.getField(kv[0].trim()).setDouble(style, Double.parseDouble(kv[1].trim()));
			} catch (ReflectiveOperationException e) {
				throw new IllegalArgumentException("no such style number: " + kv[0], e);
			}
		}
	}

	/** The pose a walking player model is in (HumanoidModel's walk cycle), as the corpse takes it. */
	static float[] walkPose(double pos, double stride) {
		float[] r = new float[HumanoidRagdoll.PARTS * 3];
		double s = Math.max(0.0, Math.min(1.0, stride));
		r[HumanoidRagdoll.RIGHT_ARM * 3] = (float) (Math.cos(pos * 0.6662 + Math.PI) * s);
		r[HumanoidRagdoll.LEFT_ARM * 3] = (float) (Math.cos(pos * 0.6662) * s);
		r[HumanoidRagdoll.RIGHT_ARM * 3 + 2] = 0.06F;
		r[HumanoidRagdoll.LEFT_ARM * 3 + 2] = -0.06F;
		r[HumanoidRagdoll.RIGHT_LEG * 3] = (float) (Math.cos(pos * 0.6662) * 1.4 * s);
		r[HumanoidRagdoll.LEFT_LEG * 3] = (float) (Math.cos(pos * 0.6662 + Math.PI) * 1.4 * s);
		r[HumanoidRagdoll.RIGHT_LEG * 3 + 2] = 0.005F;
		r[HumanoidRagdoll.LEFT_LEG * 3 + 2] = -0.005F;
		return r;
	}
}
