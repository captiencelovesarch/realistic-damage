package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.damage.DeathKind;
import dev.captience.realisticdamage.ragdoll.physics.Body;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Not an assertion: a death on its feet, tick by tick, for tuning a
 * {@link StumbleStyle}. Runs only with {@code RD_STUMBLE} set.
 * {@code RD_STYLE} (grounded, off), {@code RD_SPEED} (how fast it was going,
 * the way it faces: 0, 4.3 walking, 5.6 sprinting), {@code RD_PHASE} (where in
 * its stride), {@code RD_KIND} (PROJECTILE, BLOW, FADE), {@code RD_VIOLENCE},
 * {@code RD_HIT} (the part struck: 0 head, 1 torso, 2 and 3 the arms, 4 and 5
 * the legs), {@code RD_FROM} (front, behind, left, right), {@code RD_BROKEN}
 * (parts, comma list), {@code RD_SEED}, {@code RD_SCENE} (as the stepper's),
 * {@code RD_TICKS}, {@code RD_FRAMES} (a file for every part's box every
 * tick, for drawing) and {@code RD_SET} (the style's numbers overridden:
 * {@code pushShare=0.2,feetScale=3}).
 *
 * <p>With {@code RD_SWEEP} set it prints one line a death instead, over
 * {@code RD_SPEEDS}, {@code RD_KINDS}, {@code RD_HITS}, {@code RD_FROMS},
 * {@code RD_PHASES} and {@code RD_SCENES} (comma lists).
 */
class StumbleTraceTest {
	@Test
	void traceDeath() throws IOException {
		if (System.getenv("RD_STUMBLE") == null || System.getenv("RD_SWEEP") != null || System.getenv("RD_GRID") != null) {
			return;
		}
		HumanoidRagdollTest.FakeWorld world = StepperTest.scene(System.getenv().getOrDefault("RD_SCENE", "flat"));
		StumbleTest.Death death = death();
		death.speed = Double.parseDouble(System.getenv().getOrDefault("RD_SPEED", "0"));
		death.phase = Double.parseDouble(System.getenv().getOrDefault("RD_PHASE", "0"));
		death.kind = DeathKind.valueOf(System.getenv().getOrDefault("RD_KIND", "PROJECTILE"));
		death.hit = Integer.parseInt(System.getenv().getOrDefault("RD_HIT", "1"));
		death.from = System.getenv().getOrDefault("RD_FROM", "front");
		HumanoidRagdoll r = death.start();
		Stumble stumble = r.controller() instanceof Stumble s ? s : null;
		System.out.printf(Locale.ROOT, "# %s%n", stumble == null ? "no stumble"
				: String.format(Locale.ROOT, "on its feet %.2f s, vigor %.2f, weak leg %d", stumble.onFeet, stumble.vigor,
						stumble.weakLeg));
		PrintWriter frames = System.getenv("RD_FRAMES") == null ? null
				: new PrintWriter(Files.newBufferedWriter(Path.of(System.getenv("RD_FRAMES"))));
		int ticks = Integer.parseInt(System.getenv().getOrDefault("RD_TICKS", "100"));
		for (int t = 0; t < ticks; t++) {
			r.tick(world, 0.05);
			Vector3d com = Balance.centreOfMass(r, new Vector3d());
			Vector3d v = Balance.velocity(r, new Vector3d());
			Vector3d rf = r.tip(HumanoidRagdoll.RIGHT_LEG);
			Vector3d lf = r.tip(HumanoidRagdoll.LEFT_LEG);
			Stepper st = stumble == null ? null : stumble.stepper;
			System.out.printf(Locale.ROOT,
					"t%03d com (%5.2f %5.2f %5.2f) v %4.2f R (%5.2f %5.2f %5.2f) L (%5.2f %5.2f %5.2f) lean %5.1f pitch %5.1f"
							+ " arms %3.0f %3.0f head %3.0f hip %4.2f headY %4.2f",
					t, com.x, com.y, com.z, Math.hypot(v.x, v.z), rf.x, rf.y, rf.z, lf.x, lf.y, lf.z, r.lean(), pitch(r),
					limb(r, HumanoidRagdoll.RIGHT_ARM), limb(r, HumanoidRagdoll.LEFT_ARM), limb(r, HumanoidRagdoll.HEAD),
					r.joint(HumanoidRagdoll.RIGHT_LEG, new Vector3d()).y, r.body(HumanoidRagdoll.HEAD).x.y);
			if (st != null) {
				System.out.printf(Locale.ROOT, " steps %d %s%s%s%s str %.2f bal %.2f slip %.2f%n", st.steps(),
						st.stepping() ? "SWING" + st.swingLeg() : st.standing() ? "stand" : "-----",
						st.planted(0) ? " R" : " .", st.planted(1) ? "L" : ".",
						stumble.holding() ? "" : " DOWN (" + stumble.why() + ")", st.strength, st.balance, st.slipped());
			} else {
				System.out.println();
			}
			if (frames != null) {
				for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
					Body b = r.body(i);
					frames.printf(Locale.ROOT, "%d %d %.5f %.5f %.5f %.6f %.6f %.6f %.6f %.5f %.5f %.5f%n",
							t, i, b.x.x, b.x.y, b.x.z, b.q.x, b.q.y, b.q.z, b.q.w, b.hx, b.hy, b.hz);
				}
			}
		}
		if (frames != null) {
			frames.close();
		}
	}

	@Test
	void sweep() {
		if (System.getenv("RD_SWEEP") == null || System.getenv("RD_STUMBLE") == null || System.getenv("RD_GRID") != null) {
			return;
		}
		String[] scenes = System.getenv().getOrDefault("RD_SCENES", "flat").split(",");
		String[] speeds = System.getenv().getOrDefault("RD_SPEEDS", "0,4.3,5.6").split(",");
		String[] kinds = System.getenv().getOrDefault("RD_KINDS", "PROJECTILE").split(",");
		String[] hits = System.getenv().getOrDefault("RD_HITS", "1").split(",");
		String[] froms = System.getenv().getOrDefault("RD_FROMS", "front").split(",");
		String[] phases = System.getenv().getOrDefault("RD_PHASES", "0,1.2,2.4,3.6").split(",");
		int ticks = Integer.parseInt(System.getenv().getOrDefault("RD_TICKS", "160"));
		System.out.printf("S %-11s %-10s %3s %-6s %3s %4s | %4s %5s %5s %-17s | %5s %5s %5s %5s | %5s %5s %5s %5s %5s | %7s%n",
				"scene", "kind", "hit", "from", "v", "ph", "feet", "steps", "down", "why", "went", "side", "leanX", "slip",
				"lean", "face", "endV", "pen", "sep", "strides");
		for (String scene : scenes) {
			for (String kind : kinds) {
				for (String hit : hits) {
					for (String from : froms) {
						for (String sp : speeds) {
							for (String ph : phases) {
								HumanoidRagdollTest.FakeWorld world = StepperTest.scene(scene);
								StumbleTest.Death death = death();
								death.kind = DeathKind.valueOf(kind);
								death.hit = Integer.parseInt(hit);
								death.from = from;
								death.speed = Double.parseDouble(sp);
								death.phase = Double.parseDouble(ph);
								HumanoidRagdoll r = death.start();
								Stumble stumble = r.controller() instanceof Stumble s ? s : null;
								Vector3d start = Balance.centreOfMass(r, new Vector3d());
								double leanMax = 0.0;
								double pen = 0.0;
								double sep = 0.0;
								for (int t = 0; t < ticks; t++) {
									r.tick(world, 0.05);
									if (stumble != null && stumble.holding()) {
										leanMax = Math.max(leanMax, r.lean());
									}
									pen = Math.max(pen, world.maxPenetration(r));
									sep = Math.max(sep, r.worstSeparation());
								}
								Vector3d went = Balance.centreOfMass(r, new Vector3d()).sub(start);
								System.out.printf(Locale.ROOT,
										"S %-11s %-10s %3s %-6s %3.1f %4.1f | %4.2f %5d %5.2f %-17s | %5.2f %5.2f %5.1f %5.2f | %5.1f %5.2f %5.2f %5.3f %5.3f | %7d%n",
										scene, kind, hit, from, death.speed, death.phase, stumble == null ? 0.0 : stumble.onFeet,
										stumble == null ? 0 : stumble.stepper.steps(), stumble == null ? 0.0 : stumble.letGoAt(),
										stumble == null ? "no stumble" : String.valueOf(stumble.why()), went.z, went.x, leanMax,
										stumble == null ? 0.0 : stumble.stepper.slipped(), r.lean(), r.facing().y,
										r.sim.maxMotion(), pen, sep, stumble == null ? 0 : stumble.stepper.strides());
							}
						}
					}
				}
			}
		}
	}

	/**
	 * With {@code RD_GRID} set ({@code brakeTone=0.8|1.5;overshoot=-0.2|0}): every
	 * combination of those numbers, a line each for bodies standing, walking and
	 * sprinting, over {@code RD_PHASES}, {@code RD_FROMS}, {@code RD_KINDS} and
	 * {@code RD_HITS}: how many steps (least, mean, most), when it went down, how
	 * far it got, how many ended face down and how many on their backs, and why
	 * they went down.
	 */
	@Test
	void grid() {
		String spec = System.getenv("RD_GRID");
		if (spec == null || System.getenv("RD_STUMBLE") == null) {
			return;
		}
		String[] axes = spec.split(";");
		String[] names = new String[axes.length];
		String[][] values = new String[axes.length][];
		int combos = 1;
		for (int i = 0; i < axes.length; i++) {
			String[] kv = axes[i].split("=");
			names[i] = kv[0].trim();
			values[i] = kv[1].split("\\|");
			combos *= values[i].length;
		}
		String[] speeds = System.getenv().getOrDefault("RD_SPEEDS", "0,4.3,5.6").split(",");
		String[] kinds = System.getenv().getOrDefault("RD_KINDS", "PROJECTILE").split(",");
		String[] hits = System.getenv().getOrDefault("RD_HITS", "1").split(",");
		String[] froms = System.getenv().getOrDefault("RD_FROMS", "front,behind").split(",");
		String[] phases = System.getenv().getOrDefault("RD_PHASES", "0,1.2,2.4,3.6").split(",");
		String scene = System.getenv().getOrDefault("RD_SCENE", "flat");
		int ticks = Integer.parseInt(System.getenv().getOrDefault("RD_TICKS", "140"));
		for (int c = 0; c < combos; c++) {
			StringBuilder label = new StringBuilder();
			String[] pick = new String[axes.length];
			int rest = c;
			for (int i = 0; i < axes.length; i++) {
				pick[i] = values[i][rest % values[i].length];
				rest /= values[i].length;
				label.append(names[i]).append('=').append(pick[i]).append(' ');
			}
			for (String sp : speeds) {
				int n = 0;
				int least = 99;
				int most = 0;
				double stepSum = 0.0;
				double downSum = 0.0;
				double wentSum = 0.0;
				double wentMin = 99.0;
				int faceDown = 0;
				int onBack = 0;
				double pen = 0.0;
				double endV = 0.0;
				java.util.Map<String, Integer> whys = new java.util.TreeMap<>();
				for (String kind : kinds) {
					for (String hit : hits) {
						for (String from : froms) {
							for (String ph : phases) {
								HumanoidRagdollTest.FakeWorld world = StepperTest.scene(scene);
								StumbleTest.Death death = death();
								for (int i = 0; i < axes.length; i++) {
									set(death.style, names[i], pick[i]);
								}
								death.kind = DeathKind.valueOf(kind);
								death.hit = Integer.parseInt(hit);
								death.from = from;
								death.speed = Double.parseDouble(sp);
								death.phase = Double.parseDouble(ph);
								HumanoidRagdoll r = death.start();
								Stumble stumble = r.controller() instanceof Stumble s ? s : null;
								Vector3d start = Balance.centreOfMass(r, new Vector3d());
								for (int t = 0; t < ticks; t++) {
									r.tick(world, 0.05);
									pen = Math.max(pen, world.maxPenetration(r));
								}
								Vector3d went = Balance.centreOfMass(r, new Vector3d()).sub(start);
								int steps = stumble == null ? 0 : stumble.stepper.steps();
								n++;
								least = Math.min(least, steps);
								most = Math.max(most, steps);
								stepSum += steps;
								downSum += stumble == null ? 0.0 : stumble.letGoAt();
								wentSum += went.z;
								wentMin = Math.min(wentMin, went.z);
								faceDown += r.facing().y < -0.5 ? 1 : 0;
								onBack += r.facing().y > 0.5 ? 1 : 0;
								endV = Math.max(endV, r.sim.maxMotion());
								whys.merge(stumble == null ? "none" : String.valueOf(stumble.why()), 1, Integer::sum);
							}
						}
					}
				}
				System.out.printf(Locale.ROOT, "G %-44s v%-3s steps %d %.1f %d  down %.2f  went %5.2f (min %5.2f)  face %d back %d of %d  pen %.3f endV %.2f  %s%n",
						label, sp, least, stepSum / n, most, downSum / n, wentSum / n, wentMin, faceDown, onBack, n, pen, endV, whys);
			}
		}
	}

	private static void set(StumbleStyle style, String name, String value) {
		try {
			StumbleStyle.class.getField(name).setDouble(style, Double.parseDouble(value.trim()));
		} catch (ReflectiveOperationException e) {
			throw new IllegalArgumentException("no such number in the style: " + name, e);
		}
	}

	/** The death the environment asks for, but for what the sweep varies. */
	private static StumbleTest.Death death() {
		StumbleTest.Death d = new StumbleTest.Death();
		d.style = StumbleStyle.byName(System.getenv().getOrDefault("RD_STYLE", "grounded"));
		d.violence = Double.parseDouble(System.getenv().getOrDefault("RD_VIOLENCE", "0.4"));
		d.seed = Integer.parseInt(System.getenv().getOrDefault("RD_SEED", "7"));
		String broken = System.getenv("RD_BROKEN");
		if (broken != null && !broken.isBlank()) {
			for (String part : broken.split(",")) {
				d.broken[Integer.parseInt(part.trim())] = true;
			}
		}
		String set = System.getenv("RD_SET");
		if (d.style != null && set != null && !set.isBlank()) {
			for (String pair : set.split(",")) {
				String[] kv = pair.split("=");
				try {
					StumbleStyle.class.getField(kv[0].trim()).setDouble(d.style, Double.parseDouble(kv[1].trim()));
				} catch (ReflectiveOperationException e) {
					throw new IllegalArgumentException("no such number in the style: " + kv[0], e);
				}
			}
		}
		return d;
	}

	/** The torso's lean the way it faces, degrees; negative leaning back. */
	private static double pitch(HumanoidRagdoll r) {
		Vector3d up = r.up();
		return Math.toDegrees(Math.atan2(up.z, up.y));
	}

	/** How far a limb is from hanging straight down the torso (the head: from straight up it), degrees. */
	private static double limb(HumanoidRagdoll r, int part) {
		if (!r.present(part)) {
			return 0.0;
		}
		Vector3d down = r.body(HumanoidRagdoll.TORSO).q.transform(new Vector3d(0.0, 1.0, 0.0));
		Vector3d it = r.body(part).q.transform(new Vector3d(0.0, 1.0, 0.0));
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, down.dot(it)))));
	}
}
