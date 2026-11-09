package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.Body;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Not an assertion: a shoved test body, tick by tick, for tuning the stepper.
 * Runs only with {@code RD_STEP} set. {@code RD_SPEED} (blocks a second),
 * {@code RD_DIR} (back, forward, left, right: relative to the body),
 * {@code RD_SCENE} (flat, stairs_up, stairs_down, ledge, wall, pit),
 * {@code RD_FRAMES} (a file for every part's box every tick, for drawing) and
 * {@code RD_SET} (the stepper's public numbers, and the stand's, overridden:
 * {@code uprightHold=1.5,brakeTone=3}) and {@code RD_FEET} (seconds on its
 * feet; 0, the default here, does not weaken).
 *
 * <p>With {@code RD_SWEEP} set it prints one line a shove instead, over
 * {@code RD_SPEEDS} and {@code RD_SCENES} (comma lists) and all four ways.
 */
class StepperTraceTest {
	private static final String[] DIRS = {"back", "forward", "left", "right"};

	@Test
	void traceShove() throws IOException {
		if (System.getenv("RD_STEP") == null || System.getenv("RD_SWEEP") != null) {
			return;
		}
		double speed = Double.parseDouble(System.getenv().getOrDefault("RD_SPEED", "3"));
		String dir = System.getenv().getOrDefault("RD_DIR", "back");
		String scene = System.getenv().getOrDefault("RD_SCENE", "flat");
		HumanoidRagdollTest.FakeWorld world = StepperTest.scene(scene);
		HumanoidRagdoll r = StepperTest.shoved(world, speed, dir, FEET);
		Stand stand = (Stand) r.controller();
		override(stand);
		Vector3d way = way(r, dir);
		PrintWriter frames = System.getenv("RD_FRAMES") == null ? null
				: new PrintWriter(Files.newBufferedWriter(Path.of(System.getenv("RD_FRAMES"))));
		int ticks = Integer.parseInt(System.getenv().getOrDefault("RD_TICKS", "160"));
		for (int t = 0; t < ticks; t++) {
			r.tick(world, 0.05);
			Stepper st = stand.stepper;
			Vector3d com = Balance.centreOfMass(r, new Vector3d());
			Vector3d rf = r.tip(HumanoidRagdoll.RIGHT_LEG);
			Vector3d lf = r.tip(HumanoidRagdoll.LEFT_LEG);
			System.out.printf(Locale.ROOT,
					"t%03d com (%5.2f %5.2f %5.2f) v %4.2f R (%5.2f %5.2f %5.2f) L (%5.2f %5.2f %5.2f) lean %5.1f along %5.1f"
							+ " arms %3.0f %3.0f head %3.0f hip %4.2f steps %d %s%s%s drift %.3f%n",
					t, com.x, com.y, com.z, st.speed(), rf.x, rf.y, rf.z, lf.x, lf.y, lf.z, r.lean(), along(r, way),
					limb(r, HumanoidRagdoll.RIGHT_ARM), limb(r, HumanoidRagdoll.LEFT_ARM), limb(r, HumanoidRagdoll.HEAD),
					r.joint(HumanoidRagdoll.RIGHT_LEG, new Vector3d()).y,
					st.steps(), st.stepping() ? "SWING" : st.standing() ? "stand" : "-----",
					stand.holding() ? "" : " LET GO", st.lost() == null ? "" : " (" + st.lost() + ")", st.worstDrift());
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
		if (System.getenv("RD_SWEEP") == null) {
			return;
		}
		String[] scenes = System.getenv().getOrDefault("RD_SCENES", "flat").split(",");
		String[] speeds = System.getenv().getOrDefault("RD_SPEEDS", "1,2,3,4,5,6").split(",");
		String[] dirs = System.getenv().getOrDefault("RD_DIRS", String.join(",", DIRS)).split(",");
		int ticks = Integer.parseInt(System.getenv().getOrDefault("RD_TICKS", "200"));
		System.out.printf("S %-11s %-7s %3s | %5s %5s %-19s %6s | %5s %5s %5s %5s | %5s | %6s %5s %5s %5s%n", "scene", "dir",
				"v", "steps", "held", "why", "travel", "lean1", "leanX", "arms", "head", "drift", "fell", "went", "endV",
				"pen");
		for (String scene : scenes) {
			for (String dir : dirs) {
				for (String sp : speeds) {
					double speed = Double.parseDouble(sp);
					HumanoidRagdollTest.FakeWorld world = StepperTest.scene(scene);
					HumanoidRagdoll r = StepperTest.shoved(world, speed, dir, FEET);
					Stand stand = (Stand) r.controller();
					override(stand);
					Vector3d way = way(r, dir);
					Vector3d from = Balance.centreOfMass(r, new Vector3d());
					double travel = 0.0;
					double leanSum = 0.0;
					int leanN = 0;
					double leanMax = 0.0;
					double arms = 0.0;
					double head = 0.0;
					double pen = 0.0;
					for (int t = 0; t < ticks; t++) {
						r.tick(world, 0.05);
						if (stand.holding()) {
							Vector3d com = Balance.centreOfMass(r, new Vector3d());
							travel = Math.hypot(com.x - from.x, com.z - from.z);
							double a = along(r, way);
							if (t < 20) {
								leanSum += a;
								leanN++;
							}
							leanMax = Math.max(leanMax, r.lean());
							arms = Math.max(arms, Math.max(limb(r, HumanoidRagdoll.RIGHT_ARM), limb(r, HumanoidRagdoll.LEFT_ARM)));
							head = Math.max(head, limb(r, HumanoidRagdoll.HEAD));
						}
						pen = Math.max(pen, world.maxPenetration(r));
					}
					Stepper st = stand.stepper;
					System.out.printf(Locale.ROOT,
							"S %-11s %-7s %3.0f | %5d %5.2f %-19s %6.2f | %5.1f %5.1f %5.0f %5.0f | %5.3f | %6.1f %5.2f %5.2f %5.3f%n",
							scene, dir, speed, st.steps(), stand.letGoAt(), st.lost() == null ? "stood" : st.lost(), travel,
							leanN == 0 ? 0.0 : leanSum / leanN, leanMax, arms, head, st.worstDrift(),
							r.lean() < 45.0 ? 0.0 : along(r, way),
							Balance.centreOfMass(r, new Vector3d()).sub(from).dot(way), r.sim.maxMotion(), pen);
				}
			}
		}
	}

	/** The way a shove went, along the ground. */
	private static Vector3d way(HumanoidRagdoll r, String dir) {
		Vector3d f = r.facing();
		Vector3d front = new Vector3d(f.x, 0.0, f.z).normalize();
		Vector3d right = new Vector3d(front).cross(0.0, 1.0, 0.0).normalize();
		return switch (dir) {
			case "forward" -> front;
			case "left" -> right.negate();
			case "right" -> right;
			default -> front.negate();
		};
	}

	/** The torso's lean the way it was shoved, degrees; negative leaning back against it. */
	private static double along(HumanoidRagdoll r, Vector3d way) {
		Vector3d up = r.up();
		return Math.toDegrees(Math.atan2(up.x * way.x + up.z * way.z, up.y));
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

	/** {@code RD_FEET}: seconds on its feet (0, the default here: at full strength until it has stood still). */
	private static final double FEET = Double.parseDouble(System.getenv().getOrDefault("RD_FEET", "0"));

	/** {@code RD_SET}: the stepper's public numbers, and the stand's, by name. */
	private static void override(Stand stand) {
		String set = System.getenv("RD_SET");
		if (set == null || set.isBlank()) {
			return;
		}
		for (String pair : set.split(",")) {
			String[] kv = pair.split("=");
			String name = kv[0].trim();
			double value = Double.parseDouble(kv[1].trim());
			try {
				try {
					Stepper.class.getField(name).setDouble(stand.stepper, value);
				} catch (NoSuchFieldException e) {
					Stand.class.getField(name).setDouble(stand, value);
				}
			} catch (ReflectiveOperationException e) {
				throw new IllegalArgumentException("no such number on the stepper or the stand: " + name, e);
			}
		}
	}
}
