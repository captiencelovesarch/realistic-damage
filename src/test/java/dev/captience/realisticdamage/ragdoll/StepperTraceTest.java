package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.Body;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/**
 * Not an assertion: a shoved test body, tick by tick, for tuning the stepper.
 * Runs only with {@code RD_STEP} set. {@code RD_SPEED} (blocks a second),
 * {@code RD_DIR} (back, forward, left, right: relative to the body),
 * {@code RD_SCENE} (flat, stairs_up, stairs_down, ledge, wall) and
 * {@code RD_FRAMES} (a file for every part's box every tick, for drawing).
 */
class StepperTraceTest {
	@Test
	void traceShove() throws IOException {
		if (System.getenv("RD_STEP") == null) {
			return;
		}
		double speed = Double.parseDouble(System.getenv().getOrDefault("RD_SPEED", "3"));
		String dir = System.getenv().getOrDefault("RD_DIR", "back");
		String scene = System.getenv().getOrDefault("RD_SCENE", "flat");
		HumanoidRagdollTest.FakeWorld world = StepperTest.scene(scene);
		HumanoidRagdoll r = StepperTest.shoved(world, speed, dir);
		Stand stand = (Stand) r.controller();
		PrintWriter frames = System.getenv("RD_FRAMES") == null ? null
				: new PrintWriter(Files.newBufferedWriter(Path.of(System.getenv("RD_FRAMES"))));
		int ticks = Integer.parseInt(System.getenv().getOrDefault("RD_TICKS", "160"));
		for (int t = 0; t < ticks; t++) {
			r.tick(world, 0.05);
			Stepper st = stand.stepper;
			Vector3d com = Balance.centreOfMass(r, new Vector3d());
			Vector3d cp = st.capturePoint();
			Vector3d rf = r.tip(HumanoidRagdoll.RIGHT_LEG);
			Vector3d lf = r.tip(HumanoidRagdoll.LEFT_LEG);
			System.out.printf(java.util.Locale.ROOT,
					"t%03d com (%5.2f %5.2f %5.2f) v %4.2f cp (%5.2f %5.2f) R (%5.2f %5.2f %5.2f) L (%5.2f %5.2f %5.2f) lean %5.1f steps %d %s%s drift %.3f%n",
					t, com.x, com.y, com.z, st.speed(), cp.x, cp.z, rf.x, rf.y, rf.z, lf.x, lf.y, lf.z, r.lean(),
					st.steps(), st.stepping() ? "SWING" : st.standing() ? "stand" : "-----",
					stand.holding() ? "" : " LET GO", st.worstDrift());
			if (frames != null) {
				for (int i = 0; i < HumanoidRagdoll.PARTS; i++) {
					Body b = r.body(i);
					frames.printf(java.util.Locale.ROOT, "%d %d %.5f %.5f %.5f %.6f %.6f %.6f %.6f %.5f %.5f %.5f%n",
							t, i, b.x.x, b.x.y, b.x.z, b.q.x, b.q.y, b.q.z, b.q.w, b.hx, b.hy, b.hz);
				}
			}
		}
		if (frames != null) {
			frames.close();
		}
	}
}
