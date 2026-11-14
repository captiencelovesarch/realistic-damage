package dev.captience.realisticdamage.ragdoll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.CorpseImpactMath;
import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.net.CorpsePayload;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Can the server follow a ragdoll it does not draw? (2.0, dead bodies that
 * keep taking damage.)
 *
 * <p>A limb can only come off a falling corpse for everyone if the server
 * decides it, and the server can only decide what it has followed. Its copy of
 * a death ({@link CorpseLaunch#serverBody}) is started by the same code from
 * the same packet as each client's, but it is not the same body: the server has
 * no renderer, so it does not know the pose the body was drawn in, a slim skin
 * from a wide one, or exactly where and which way the watching client had the
 * body when the news arrived (a client's copy of someone else is a few ticks
 * of travel behind and turned a little differently).
 *
 * <p>This measures what that costs: one death, the server's copy, and a
 * spread of client copies each wrong in the ways a client can be, and then
 * how far apart they are on the only two things the server is asked: <em>when</em>
 * does the body first hit something hard, and <em>how hard</em>. Set
 * {@code RD_TRACE} to print the whole table.
 */
class CorpseAgreementTest {
	private static final double DT = CorpseLaunch.DT;
	private static final int TICKS = 100;
	/** A hit at least this fast, blocks a second, is "hard": a body lying down never reaches it. */
	private static final double HARD = 10.0;
	private static final String[] NAMES = {"head", "torso", "rArm", "lArm", "rLeg", "lLeg"};

	/** One way a client's copy of the body is not the server's. */
	record Variant(String name, boolean slim, int lagTicks, float yawOff, float walkPhase, float armsOut,
			double unitScale) {}

	static final Variant[] CLIENTS = {
			new Variant("exact", false, 0, 0.0F, 0.0F, 0.0F, 1.0),
			new Variant("lag1", false, 1, 0.0F, 0.0F, 0.0F, 1.0),
			new Variant("lag2", false, 2, 0.0F, 0.0F, 0.0F, 1.0),
			new Variant("lag3", false, 3, 0.0F, 0.0F, 0.0F, 1.0),
			new Variant("slim", true, 1, 0.0F, 0.0F, 0.0F, 1.0),
			new Variant("turned+12", false, 1, 12.0F, 0.0F, 0.0F, 1.0),
			new Variant("turned-20", false, 2, -20.0F, 0.0F, 0.0F, 1.0),
			new Variant("midstride", false, 1, 5.0F, 1.9F, 0.0F, 1.0),
			new Variant("armsOut", false, 2, -8.0F, 0.7F, 1.4F, 1.0),
			new Variant("zombie", false, 1, 4.0F, 0.4F, 1.5F, 1.0 / 0.9375),
	};

	/** The kinds of death the rule has to tell apart. */
	enum Kind {
		/** Came down on the ground, dead already or killed by it: the case the rule is for. */
		FALL,
		/** Thrown sideways at a wall: through the air, or skidding along the ground first. */
		THROWN,
		/** A lethal blast, as hard as they come. */
		BLAST,
		/** Died where it stood. */
		QUIET
	}

	/**
	 * What one copy of the body did: per part, the tick of its first hard strike
	 * (-1 for none) and its hardest; when it came to rest (-1: not inside the
	 * window); and what the rule made of it, on ordinary ground with no armour.
	 */
	record Run(int[] firstHard, double[] peak, int rest, int lost, int broke) {
		int firstAny() {
			int first = -1;
			for (int t : firstHard) {
				if (t >= 0 && (first < 0 || t < first)) {
					first = t;
				}
			}
			return first;
		}

		double peakAny() {
			double best = 0.0;
			for (double p : peak) {
				best = Math.max(best, p);
			}
			return best;
		}
	}

	/** One death: where, what the body was doing (blocks a second), the packet, the world. */
	record Scene(String name, Kind kind, Vector3d feet, Vector3d motion, CorpsePayload payload, PhysicsWorld world) {}

	static CorpsePayload payload(DeathKind kind, double violence, Vector3d launchPerSecond) {
		return new CorpsePayload(7, kind, (float) violence, (float) (launchPerSecond.x / 20.0),
				(float) (launchPerSecond.y / 20.0), (float) (launchPerSecond.z / 20.0));
	}

	/** Runs one copy through the hold window, judging it the way the server does. */
	static Run follow(HumanoidRagdoll r, PhysicsWorld world) {
		int[] first = {-1, -1, -1, -1, -1, -1};
		double[] peak = new double[HumanoidRagdoll.PARTS];
		double[] strikes = new double[HumanoidRagdoll.PARTS];
		boolean[] broken = new boolean[HumanoidRagdoll.PARTS];
		// The tick each limb the rule has taken leaves the body: the server waits
		// CORPSE_IMPACT_DELAY_TICKS before it applies what it saw.
		int[] leaves = {-1, -1, -1, -1, -1, -1};
		int rest = -1;
		int lost = 0;
		int broke = 0;
		for (int t = 0; t < TICKS; t++) {
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				if (leaves[i] == t) {
					r.detach(i);
				}
			}
			r.tick(world, DT);
			for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
				strikes[i] = leaves[i] >= 0 ? 0.0 : r.strike(i);
				if (strikes[i] >= HARD && first[i] < 0) {
					first[i] = t;
				}
				peak[i] = Math.max(peak[i], strikes[i]);
			}
			for (int part : CorpseImpactMath.hardestFirst(strikes)) {
				double trauma = CorpseImpactMath.trauma(strikes[part], Balance.LANDING_SURFACE_NEUTRAL, 0.0, 1.0);
				if (CorpseImpactMath.severs(CorpseLaunch.TO_BODY_PART[part], trauma, lost)) {
					leaves[part] = t + Balance.CORPSE_IMPACT_DELAY_TICKS;
					lost++;
				} else if (!broken[part] && CorpseImpactMath.breaks(CorpseLaunch.TO_BODY_PART[part], trauma)) {
					r.breakLimb(part);
					broken[part] = true;
					broke++;
				}
			}
			if (rest < 0 && r.asleep()) {
				rest = t;
			}
		}
		return new Run(first, peak, rest, lost, broke);
	}

	static HumanoidRagdoll server(Scene s) {
		return CorpseLaunch.serverBody(new Vector3d(s.feet), 0.0F, 1.8, s.payload, null, new Vector3d(s.motion), 7);
	}

	/** A client's copy, built the way {@code RagdollCorpse.create} builds one. */
	static HumanoidRagdoll client(Scene s, Variant v) {
		HumanoidRagdoll.Part[] parts = HumanoidRagdoll.playerParts(v.slim);
		double unit = 0.9375 / 16.0 * v.unitScale;
		// A watcher's copy of someone else is still gliding to where the server
		// last said they were: it is behind by that much travel.
		Vector3d feet = new Vector3d(s.feet).fma(-DT * v.lagTicks, s.motion);
		Vector3d seen = CorpseLaunch.inherited(new Vector3d(s.motion));
		float stride = (float) Math.min(1.0, Math.hypot(seen.x, seen.z) * DT * 4.0);
		float[] now = pose(v.walkPhase, Math.max(stride, v.walkPhase > 0.0F ? 0.5F : 0.0F), v.armsOut);
		float[] before = pose(v.walkPhase - 0.4F, Math.max(stride, v.walkPhase > 0.0F ? 0.5F : 0.0F), v.armsOut);
		float yaw = v.yawOff;
		HumanoidRagdoll r = new HumanoidRagdoll(parts, feet, yaw, unit, now);
		r.inheritMotion(parts, before, seen, 0.35, DT);
		Muscles muscles = CorpseLaunch.deathMuscles(s.payload, null);
		r.setMuscles(muscles);
		r.setBehaviour(Behaviour.choose(CorpseLaunch.behaviourInput(7, s.payload, null, muscles)));
		CorpseLaunch.launch(r, yaw, s.payload, feet, seen, muscles);
		return r;
	}

	/** Vanilla's walk cycle, and arms held out in front (a zombie's reach) by {@code armsOut} radians. */
	static float[] pose(float pos, float speed, float armsOut) {
		float[] r = new float[18];
		r[HumanoidRagdoll.RIGHT_ARM * 3] = (float) Math.cos(pos * 0.6662F + Math.PI) * speed - armsOut;
		r[HumanoidRagdoll.LEFT_ARM * 3] = (float) Math.cos(pos * 0.6662F) * speed - armsOut;
		r[HumanoidRagdoll.RIGHT_ARM * 3 + 2] = 0.06F;
		r[HumanoidRagdoll.LEFT_ARM * 3 + 2] = -0.06F;
		r[HumanoidRagdoll.RIGHT_LEG * 3] = (float) Math.cos(pos * 0.6662F) * 1.4F * speed;
		r[HumanoidRagdoll.LEFT_LEG * 3] = (float) Math.cos(pos * 0.6662F + Math.PI) * 1.4F * speed;
		return r;
	}

	static HumanoidRagdollTest.FakeWorld floor() {
		return new HumanoidRagdollTest.FakeWorld().blockFloor(14, 0.0);
	}

	static HumanoidRagdollTest.FakeWorld walled() {
		HumanoidRagdollTest.FakeWorld walled = floor();
		for (int y = 0; y < 6; y++) {
			for (int z = -4; z < 5; z++) {
				walled.box(5.0, y, z, 6.0, y + 1.0, z + 1.0);
			}
		}
		return walled;
	}

	/** The hardest lethal blast there is: the launch and the kick {@code LethalBlast} and {@code Corpses} send at violence 1. */
	static CorpsePayload hardestBlast() {
		return new CorpsePayload(7, DeathKind.EXPLOSION, 1.0F, (float) Balance.CORPSE_BLAST_SPEED_MAX,
				(float) Balance.CORPSE_BLAST_LIFT_MAX, 0.0F, 4, 0.8F, 0.6F, 0.0F, (float) Balance.CORPSE_KICK_BLAST_MAX);
	}

	/** The deaths that matter to the rule: the ones with real energy, and the ones without. */
	static List<Scene> scenes() {
		List<Scene> all = new ArrayList<>();
		// Killed by the landing itself: already on the ground, still going down.
		for (double v : new double[] {20.0, 30.0, 40.0}) {
			Vector3d m = new Vector3d(0.6, -v, 0.3);
			all.add(new Scene("lands dead at " + (int) v, Kind.FALL, new Vector3d(0.5, 0.0, 0.5), m,
					payload(DeathKind.FALL, 0.8, m), floor()));
		}
		// Killed in the air on the way down, with the ground still to come.
		for (double[] hv : new double[][] {{4, 12}, {6, 25}, {12, 20}, {20, 35}, {30, 10}, {8, 40}}) {
			Vector3d m = new Vector3d(1.0, -hv[1], -0.5);
			all.add(new Scene("dies " + (int) hv[0] + " up at " + (int) hv[1], Kind.FALL, new Vector3d(0.5, hv[0], 0.5), m,
					payload(DeathKind.PROJECTILE, 0.5, m), floor()));
		}
		Vector3d thrown = new Vector3d(30.0, 4.0, 0.0);
		all.add(new Scene("thrown through the air at a wall at 30", Kind.THROWN, new Vector3d(0.5, 3.0, 0.5), thrown,
				payload(DeathKind.BLOW, 0.9, thrown), walled()));
		all.add(new Scene("thrown along the ground at a wall at 30", Kind.THROWN, new Vector3d(0.5, 0.0, 0.5), thrown,
				payload(DeathKind.BLOW, 0.9, thrown), walled()));
		all.add(new Scene("blast, flat ground", Kind.BLAST, new Vector3d(0.5, 0.0, 0.5), new Vector3d(), hardestBlast(), floor()));
		all.add(new Scene("blast, into a wall", Kind.BLAST, new Vector3d(0.5, 0.0, 0.5), new Vector3d(), hardestBlast(), walled()));
		all.add(new Scene("struck down standing", Kind.QUIET, new Vector3d(0.5, 0.0, 0.5), new Vector3d(),
				new CorpsePayload(7, DeathKind.BLOW, 0.5F, 0.08F, 0.0F, 0.02F, 1, 1.0F, 0.0F, 0.0F, 0.2F), floor()));
		all.add(new Scene("running, struck down", Kind.QUIET, new Vector3d(0.5, 0.0, 0.5), new Vector3d(5.6, 0.0, 0.0),
				new CorpsePayload(7, DeathKind.BLOW, 0.7F, 0.3F, 0.1F, 0.0F, 0, 1.0F, 0.0F, 0.0F, 0.3F), floor()));
		all.add(new Scene("bled out standing", Kind.QUIET, new Vector3d(0.5, 0.0, 0.5), new Vector3d(),
				new CorpsePayload(7, DeathKind.FADE, 0.1F, 0.0F, 0.0F, 0.0F), floor()));
		return all;
	}

	/**
	 * The measurement, and the design's licence. On a fall the server's copy and
	 * every client's agree on the landing to within two ticks and an eighth of
	 * its speed, and so on what the rule takes. On a body thrown along the
	 * ground they agree only that it hit something hard. A blast and a death
	 * where the body stood never reach the rule on any copy.
	 */
	@Test
	void theServersCopyLandsWhenAndAsHardAsAClients() {
		boolean trace = System.getenv("RD_TRACE") != null;
		double worstRatio = 1.0;
		int worstTicks = 0;
		for (Scene s : scenes()) {
			Run server = follow(server(s), s.world);
			if (trace) {
				System.out.println("== " + s.name);
				System.out.println(line("server", server, 0));
			}
			for (Variant v : CLIENTS) {
				Run c = follow(client(s, v), s.world);
				if (trace) {
					System.out.println(line(v.name, c, v.lagTicks));
				}
				String who = s.name + "/" + v.name;
				switch (s.kind) {
					case FALL -> {
						if (server.peakAny() < Balance.CORPSE_IMPACT_MIN_SPEED) {
							break;
						}
						assertTrue(c.firstAny() >= 0, who + ": the server's copy landed hard and this one never did");
						// The client's copy started lagTicks of travel behind, so that much later is the same moment.
						int apart = Math.abs(c.firstAny() - v.lagTicks - server.firstAny());
						double ratio = Math.max(c.peakAny(), server.peakAny()) / Math.min(c.peakAny(), server.peakAny());
						worstTicks = Math.max(worstTicks, apart);
						worstRatio = Math.max(worstRatio, ratio);
						assertTrue(apart <= 2, who + ": the landing is within two ticks of the server's, was " + apart);
						assertTrue(ratio <= 1.125, who + ": the landing is within an eighth of the server's speed, was " + ratio);
						assertTrue(Math.abs(c.lost - server.lost) <= 1,
								who + ": the rule takes " + server.lost + " limbs off the server's copy and " + c.lost + " off this one");
					}
					case THROWN -> {
						// Not asserted, and that is the finding: see the method's comment.
					}
					case BLAST, QUIET -> {
						assertTrue(c.peakAny() < Balance.CORPSE_IMPACT_MIN_SPEED,
								who + ": struck at " + c.peakAny() + ", which the rule would look at");
						assertEquals(0, c.lost + c.broke, who + ": lost or broke something");
					}
				}
			}
			if (s.kind == Kind.THROWN) {
				assertTrue(server.peakAny() >= Balance.CORPSE_IMPACT_MIN_SPEED,
						s.name + ": the server's own copy does hit the wall hard, struck at " + server.peakAny());
			}
			if (s.kind == Kind.BLAST || s.kind == Kind.QUIET) {
				assertTrue(server.peakAny() < Balance.CORPSE_IMPACT_MIN_SPEED, s.name + ": the server's copy struck at " + server.peakAny());
				assertEquals(0, server.lost + server.broke, s.name + ": the server's copy lost or broke something");
			}
		}
		if (trace) {
			System.out.printf(Locale.ROOT, "falls, worst over every copy: landing %d ticks apart, %.3fx apart in speed%n", worstTicks, worstRatio);
		}
	}

	/** What the rule takes off the server's copy, case by case: the tuning the thresholds in Balance promise. */
	@Test
	void theRuleTakesWhatTheBalanceCommentsSay() {
		List<Scene> all = scenes();
		Run at20 = follow(server(all.get(0)), all.get(0).world);
		Run at30 = follow(server(all.get(1)), all.get(1).world);
		Run at40 = follow(server(all.get(2)), all.get(2).world);
		assertEquals(0, at20.lost + at20.broke, "a landing at 20 blocks a second (a six-block drop) is under every bar");
		assertEquals(1, at30.lost, "a landing at 30 takes one leg");
		assertEquals(1, at30.broke, "and breaks the other");
		assertTrue(at40.lost >= 2, "a landing at 40 takes both legs, took " + at40.lost);
	}

	/** Following a body costs little enough to do for a handful at once. */
	@Test
	void followingABodyIsCheap() {
		Scene s = scenes().get(6);
		for (int warm = 0; warm < 20; warm++) {
			follow(server(s), s.world);
		}
		long start = System.nanoTime();
		int runs = 50;
		for (int i = 0; i < runs; i++) {
			follow(server(s), s.world);
		}
		double microsPerTick = (System.nanoTime() - start) / 1000.0 / (runs * TICKS);
		if (System.getenv("RD_TRACE") != null) {
			System.out.printf(Locale.ROOT, "one followed body costs %.1f microseconds a tick%n", microsPerTick);
		}
		// A server tick is 50,000 microseconds. Generous, for a slow test machine.
		assertTrue(microsPerTick < 2000.0, "a followed body took " + microsPerTick + " microseconds a tick");
	}

	private static String line(String who, Run r, int lag) {
		StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-10s first %3d peak %5.1f rest %3d lost %d broke %d |", who,
				r.firstAny() < 0 ? -1 : r.firstAny() - lag, r.peakAny(), r.rest, r.lost, r.broke));
		for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
			sb.append(String.format(Locale.ROOT, " %s %3d/%5.1f", NAMES[i], r.firstHard[i] < 0 ? -1 : r.firstHard[i] - lag, r.peak[i]));
		}
		return sb.toString();
	}
}
