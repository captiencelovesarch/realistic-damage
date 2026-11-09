package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.Joint;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

/** Not an assertion: prints a quiet death tick by tick, for tuning. Run with -Drd.trace=true. */
class RagdollTraceTest {
	@Test
	void traceQuietDeath() {
		if (System.getenv("RD_TRACE") == null) {
			return;
		}
		HumanoidRagdollTest.FakeWorld world = new HumanoidRagdollTest.FakeWorld().blockFloor(30, 0.0);
		HumanoidRagdoll r = new HumanoidRagdoll(HumanoidRagdoll.playerParts(false), new Vector3d(0.5, 0.0, 0.5), 0.0F,
				0.9375 / 16.0, new float[18]);
		dev.captience.realisticdamage.damage.DeathKind kind = dev.captience.realisticdamage.damage.DeathKind.valueOf(
				System.getenv().getOrDefault("RD_KIND", "BLOW"));
		r.setMuscles(Muscles.forDeath(Muscles.Input.healthy(kind,
				Double.parseDouble(System.getenv().getOrDefault("RD_VIOLENCE", "0.4")),
				Integer.parseInt(System.getenv().getOrDefault("RD_HIT", "-1")))));
		double rate = Double.parseDouble(System.getenv().getOrDefault("RD_RATE", "2.2"));
		r.spin(new Vector3d(1.0, 0.0, 0.0).mul(rate), new Vector3d(0.5, 0.0, 0.5));
		if (System.getenv("RD_BLAST") != null) {
			r.push(4.9, 10.0, -12.26);
			r.kick(HumanoidRagdoll.LEFT_LEG, 0.37 * 8.0, 0.0, -0.93 * 8.0);
		}
		String[] names = {"head", "rArm", "lArm", "rLeg", "lLeg"};
		Vector3d swing = new Vector3d();
		double[] twist = new double[1];
		for (int t = 0; t < 300; t++) {
			r.tick(world, 0.05);
			if (t % 10 != 9 && t > 40) {
				continue;
			}
			StringBuilder sb = new StringBuilder(String.format("t%03d lean %5.1f spread %5.2f gap %.3f motion %.3f asleep %s |", t, r.lean(), r.spread(), r.worstSeparation(), r.sim.maxMotion(), r.asleep()));
			Joint[] js = r.joints();
			for (int i = 0; i < js.length; i++) {
				Joint.decompose(js[i].relative(new Quaterniond()), swing, twist);
				sb.append(String.format(" %s x%+.2f z%+.2f t%+.2f", names[i], swing.x, swing.z, twist[0]));
			}
			System.out.println(sb);
		}
	}
}
