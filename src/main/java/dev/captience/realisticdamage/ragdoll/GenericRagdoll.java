package dev.captience.realisticdamage.ragdoll;

import dev.captience.realisticdamage.ragdoll.physics.Body;
import dev.captience.realisticdamage.ragdoll.physics.BodySim;
import dev.captience.realisticdamage.ragdoll.physics.Joint;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import java.util.List;
import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * Any model's body as a ragdoll (active ragdolls, phase 4): a cow, a pig, a
 * wolf, a spider, a chicken, a creeper, an iron golem. Each top-level part of
 * the model is one rigid box (its decorations, ears and horns and beaks, ride
 * on it as the model already draws them), and every part hangs off the biggest
 * one, the torso, at the part's own pivot.
 *
 * <p>The humanoid gets {@link HumanoidRagdoll}: hand-set ranges of motion,
 * muscles, reflexes. This one knows nothing about what the parts are, so its
 * ranges are generic and measured from each part's resting angle (a cow's body
 * is modelled standing and turned flat, so its legs rest at a right angle to
 * it), and it only has the dead body's fading stiffness.
 *
 * <p>Frames as in {@link HumanoidRagdoll}: a model point {@code m} (pixels) is
 * in the world at {@code origin + Q * unit * m}; a part's frame is its pivot
 * and rest rotation; a body's frame is its part's frame moved to the centre of
 * its box.
 */
public final class GenericRagdoll {
	/**
	 * One body: a model part (or several sharing a pivot and a rest rotation),
	 * its pivot and rest rotation in the model, and the box around everything it
	 * draws, in its own frame, pixels.
	 */
	public record Part(double pivotX, double pivotY, double pivotZ, double xRot, double yRot, double zRot,
			double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		double volume() {
			return Math.max(0.5, maxX - minX) * Math.max(0.5, maxY - minY) * Math.max(0.5, maxZ - minZ);
		}
	}

	/** Swing and twist either side of rest, radians: loose, since nothing here knows what a part is. */
	private static final double SWING_X = 1.1;
	private static final double SWING_Z = 0.7;
	private static final double TWIST = 0.4;
	/** The stiffness a dead body has: firm at first, then gone. In multiples of each part's gravity torque. */
	private static final double TONE_START = 2.0;
	private static final double TONE_FLOOR = 0.15;
	private static final double TONE_FADE = 0.4;
	/** How long, after death, the parts' feet slip; and how much grip they keep meanwhile. */
	private static final double SLIP_SECONDS = 1.0;
	private static final double SLIP_FRICTION = 0.15;

	public final BodySim sim;
	private final Body[] bodies;
	private final Joint[] joints;
	private final int[] jointPart;
	private final double[] driveScale;
	public final int torso;
	public final double unit;
	private final Vector3d[] centre;
	private final Vector3d[] pivot;
	private final Quaterniond[] restRotation;
	private final Vector3d[] startX;
	private final Quaterniond[] startQ;
	private double age;
	private int stillTicks;
	private boolean asleep;
	private long sleepDigest;

	/**
	 * @param parts   the bodies, at least two
	 * @param origin  where the model's origin is in the world
	 * @param model   the model's rotation in the world (vanilla's Ry(180 - yaw) * Rz(180))
	 * @param unit    blocks per pixel
	 * @param density kilograms per cubic pixel
	 */
	public GenericRagdoll(List<Part> parts, Vector3d origin, Quaterniond model, double unit, double density) {
		this(parts, origin, model, unit, density, -1);
	}

	/**
	 * As above, with the part every other one hangs from given ({@code hub}),
	 * or -1 for the biggest. The biggest is not always the body: a villager's
	 * head outweighs its chest.
	 */
	public GenericRagdoll(List<Part> parts, Vector3d origin, Quaterniond model, double unit, double density,
			int hub) {
		int n = parts.size();
		this.unit = unit;
		this.bodies = new Body[n];
		this.centre = new Vector3d[n];
		this.pivot = new Vector3d[n];
		this.restRotation = new Quaterniond[n];
		this.startX = new Vector3d[n];
		this.startQ = new Quaterniond[n];
		int biggest = 0;
		for (int i = 0; i < n; i++) {
			if (parts.get(i).volume() > parts.get(biggest).volume()) {
				biggest = i;
			}
		}
		this.torso = hub >= 0 && hub < n ? hub : biggest;
		for (int i = 0; i < n; i++) {
			Part p = parts.get(i);
			double hx = Math.max(0.5, (p.maxX() - p.minX()) * 0.5) * unit;
			double hy = Math.max(0.5, (p.maxY() - p.minY()) * 0.5) * unit;
			double hz = Math.max(0.5, (p.maxZ() - p.minZ()) * 0.5) * unit;
			double mass = Math.max(0.2, p.volume() * density);
			bodies[i] = new Body(hx, hy, hz, mass);
			centre[i] = new Vector3d((p.minX() + p.maxX()) * 0.5, (p.minY() + p.maxY()) * 0.5, (p.minZ() + p.maxZ()) * 0.5);
			pivot[i] = new Vector3d(p.pivotX(), p.pivotY(), p.pivotZ());
			restRotation[i] = new Quaterniond().rotationZYX(p.zRot(), p.yRot(), p.xRot());
			Quaterniond q = new Quaterniond(model).mul(restRotation[i]);
			Vector3d m = restRotation[i].transform(new Vector3d(centre[i])).add(pivot[i]);
			Vector3d x = model.transform(new Vector3d(m).mul(unit)).add(origin);
			bodies[i].place(x, q);
			startX[i] = new Vector3d(x);
			startQ[i] = new Quaterniond(q);
		}
		Body t = bodies[torso];
		java.util.List<Joint> list = new java.util.ArrayList<>();
		java.util.List<Integer> owners = new java.util.ArrayList<>();
		java.util.List<Double> scales = new java.util.ArrayList<>();
		for (int i = 0; i < n; i++) {
			if (i == torso) {
				continue;
			}
			Body b = bodies[i];
			// The joint at the part's pivot, in each body's own frame.
			Vector3d pivotWorld = model.transform(new Vector3d(pivot[i]).mul(unit)).add(origin);
			Vector3d inTorso = t.q.conjugate(new Quaterniond()).transform(new Vector3d(pivotWorld).sub(t.x));
			Vector3d inPart = b.q.conjugate(new Quaterniond()).transform(new Vector3d(pivotWorld).sub(b.x));
			Joint j = new Joint(t, b, inTorso, inPart, -SWING_X, SWING_X, -SWING_Z, SWING_Z, -TWIST, TWIST);
			j.relative(j.rest);
			j.target.set(j.rest);
			j.damping = 2.5;
			list.add(j);
			owners.add(i);
			scales.add(b.mass * 32.0 * Math.max(b.hx, Math.max(b.hy, b.hz)) * 2.0);
		}
		this.joints = list.toArray(new Joint[0]);
		this.jointPart = owners.stream().mapToInt(Integer::intValue).toArray();
		this.driveScale = scales.stream().mapToDouble(Double::doubleValue).toArray();
		this.sim = new BodySim(bodies, joints, new int[0]);
		applyTone();
	}

	public int parts() {
		return bodies.length;
	}

	public Body body(int i) {
		return bodies[i];
	}

	// --- Launch ---------------------------------------------------------------------

	public void push(double vx, double vy, double vz) {
		for (Body b : bodies) {
			b.v.add(vx, vy, vz);
		}
	}

	public void spin(Vector3d axisTimesRate, Vector3d about) {
		for (Body b : bodies) {
			b.v.add(new Vector3d(axisTimesRate).cross(new Vector3d(b.x).sub(about)));
			b.w.add(axisTimesRate);
		}
	}

	public void kick(int part, double vx, double vy, double vz) {
		bodies[part].v.add(vx, vy, vz);
	}

	/**
	 * Keels the body over onto its side about a world axis (unit, the
	 * right-hand way) through {@code feet}: vanilla's death roll, but a fall.
	 * On four legs (or eight) a body has to be rolled past its outer feet to go
	 * over at all, and how hard depends on the shape: a narrow chicken goes
	 * with a nudge, a wide cow or a sprawling spider needs a real shove. So the
	 * roll is worked out from this body: just enough to carry its centre of
	 * mass over the feet on the side it falls to, with a margin.
	 */
	public void keel(Vector3d axis, Vector3d feet) {
		Vector3d a = new Vector3d(axis).normalize();
		Vector3d fall = new Vector3d(a).cross(0.0, 1.0, 0.0).normalize();
		Vector3d com = new Vector3d();
		double mass = 0.0;
		double lowest = Double.POSITIVE_INFINITY;
		for (Body b : bodies) {
			com.fma(b.mass, b.x);
			mass += b.mass;
		}
		com.div(mass);
		// How far the outermost foot (the lowest points) reaches toward the fall.
		double reach = 0.0;
		Vector3d corner = new Vector3d();
		for (Body b : bodies) {
			for (int k = 0; k < 8; k++) {
				corner.set((k & 1) == 0 ? -b.hx : b.hx, (k & 2) == 0 ? -b.hy : b.hy, (k & 4) == 0 ? -b.hz : b.hz);
				b.q.transform(corner).add(b.x);
				lowest = Math.min(lowest, corner.y);
			}
		}
		for (Body b : bodies) {
			for (int k = 0; k < 8; k++) {
				corner.set((k & 1) == 0 ? -b.hx : b.hx, (k & 2) == 0 ? -b.hy : b.hy, (k & 4) == 0 ? -b.hz : b.hz);
				b.q.transform(corner).add(b.x);
				if (corner.y < lowest + 0.1) {
					reach = Math.max(reach, new Vector3d(corner).sub(com).dot(fall));
				}
			}
		}
		double h = Math.max(0.05, com.y - lowest);
		double r = Math.sqrt(h * h + reach * reach);
		// Energy to lift the centre of mass over the edge, as a roll about it.
		double needed = Math.sqrt(2.0 * 32.0 * (r - h)) / r;
		double rate = Math.max(KEEL_RATE, needed * 1.35 + 0.4);
		Vector3d pivot = new Vector3d(com.x, lowest, com.z).fma(reach, fall);
		spin(new Vector3d(a).mul(rate), pivot);
	}

	private static final double KEEL_RATE = 2.0;

	// --- Running ----------------------------------------------------------------------

	public void tick(PhysicsWorld world, double dt) {
		age += dt;
		if (asleep) {
			for (Body b : bodies) {
				b.tickX.set(b.x);
				b.tickQ.set(b.q);
			}
			if (((int) Math.round(age / dt)) % 10 == 0 && sim.worldDigest(world) != sleepDigest) {
				asleep = false;
				stillTicks = 0;
			} else {
				return;
			}
		}
		applyTone();
		// A dead animal on four stiff legs would stand like a table: legs with
		// no knee cannot fold. For the first moment the feet slip, so the legs
		// splay and the body goes down.
		double slip = age < SLIP_SECONDS ? SLIP_FRICTION : 1.0;
		for (int i = 0; i < bodies.length; i++) {
			if (i != torso) {
				bodies[i].friction = slip;
			}
		}
		sim.step(dt, world);
		if (sim.maxMotion() < 0.12) {
			if (++stillTicks >= 20) {
				asleep = true;
				sim.relax(12);
				sleepDigest = sim.worldDigest(world);
			}
		} else {
			stillTicks = 0;
		}
	}

	private void applyTone() {
		double tone = TONE_FLOOR + (TONE_START - TONE_FLOOR) * Math.exp(-age / TONE_FADE);
		for (int i = 0; i < joints.length; i++) {
			joints[i].stiffness = tone * driveScale[i];
		}
	}

	public boolean asleep() {
		return asleep;
	}

	public boolean broken() {
		return sim.broken();
	}

	// --- Reading it back ----------------------------------------------------------------

	public Quaterniond orientation(int i, float partial, Quaterniond out) {
		return out.set(bodies[i].tickQ).slerp(bodies[i].q, partial);
	}

	/** Where a part's frame is (its pivot), world, between the last two ticks. */
	public Vector3d partOrigin(int i, float partial, Vector3d out) {
		Quaterniond q = orientation(i, partial, new Quaterniond());
		Vector3d x = new Vector3d(bodies[i].tickX).lerp(bodies[i].x, partial);
		return q.transform(new Vector3d(centre[i]).negate().mul(unit), out).add(x);
	}

	/** The model's own frame, as the torso now lies: its rotation in the world. */
	public Quaterniond modelRotation(float partial, Quaterniond out) {
		orientation(torso, partial, out);
		return out.mul(new Quaterniond(restRotation[torso]).conjugate());
	}

	/** The model's origin in the world, as the torso now lies. */
	public Vector3d modelOrigin(float partial, Quaterniond modelRotation, Vector3d out) {
		partOrigin(torso, partial, out);
		return out.sub(modelRotation.transform(new Vector3d(pivot[torso]).mul(unit)));
	}

	public Vector3d centre() {
		return new Vector3d(bodies[torso].x);
	}

	/**
	 * Degrees the body's up has turned from straight up: 0 standing, 90 on its
	 * side. The model's up, not the torso box's: a quadruped's body is modelled
	 * upright and turned flat, so the box's own axes lie along its spine.
	 */
	public double lean() {
		Quaterniond model = new Quaterniond(bodies[torso].q).mul(new Quaterniond(restRotation[torso]).conjugate());
		Vector3d up = model.transform(new Vector3d(0.0, -1.0, 0.0));
		return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, up.y))));
	}

	public double spread() {
		double sum = 0.0;
		Vector3d swing = new Vector3d();
		double[] twist = new double[1];
		Quaterniond rel = new Quaterniond();
		for (Joint j : joints) {
			j.relative(rel);
			j.rest.conjugate(new Quaterniond()).mul(rel, rel);
			Joint.decompose(rel, swing, twist);
			sum += Math.abs(swing.x) + Math.abs(swing.z) + Math.abs(twist[0]) * 0.5;
		}
		return sum;
	}

	public double worstSeparation() {
		double worst = 0.0;
		for (Joint j : joints) {
			Vector3d a = j.parent.q.transform(j.anchorParent, new Vector3d()).add(j.parent.x);
			Vector3d b = j.child.q.transform(j.anchorChild, new Vector3d()).add(j.child.x);
			worst = Math.max(worst, a.distance(b));
		}
		return worst;
	}

	/** Where a point on the standing body is on this one: carried by the part it was on, or nearest. */
	public Vector3d place(Vector3d upright) {
		int best = torso;
		double bestOut = Double.POSITIVE_INFINITY;
		Vector3d local = new Vector3d();
		Vector3d bestLocal = new Vector3d();
		for (int i = 0; i < bodies.length; i++) {
			Body b = bodies[i];
			startQ[i].conjugate(new Quaterniond()).transform(new Vector3d(upright).sub(startX[i]), local);
			double ox = Math.max(0.0, Math.abs(local.x) - b.hx);
			double oy = Math.max(0.0, Math.abs(local.y) - b.hy);
			double oz = Math.max(0.0, Math.abs(local.z) - b.hz);
			double out = ox * ox + oy * oy + oz * oz;
			if (out < bestOut) {
				bestOut = out;
				best = i;
				bestLocal.set(local);
			}
		}
		return bodies[best].q.transform(bestLocal, new Vector3d()).add(bodies[best].x);
	}

	/** Which part each joint moves; for the tests. */
	public int jointPart(int i) {
		return jointPart[i];
	}

	public Joint[] joints() {
		return joints;
	}
}
