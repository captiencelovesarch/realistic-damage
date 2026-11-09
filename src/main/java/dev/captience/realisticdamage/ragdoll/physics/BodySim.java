package dev.captience.realisticdamage.ragdoll.physics;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * A set of rigid bodies held together by joints, falling through a world of
 * boxes (1.7, active ragdolls phase 0).
 *
 * <p>The method is XPBD, extended position-based dynamics for rigid bodies
 * (Müller et al. 2020, "Detailed Rigid Body Simulation with Extended Position
 * Based Dynamics"): each substep predicts the bodies forward under gravity,
 * moves them straight back onto every constraint (joints held together, joint
 * limits, the floor), reads the velocities off how far they actually moved,
 * and finishes with a velocity pass for friction, bounce and joint friction.
 * It is unconditionally stable, which a ragdoll needs: a ten-to-one mass ratio
 * between a torso and an arm, hit by a creeper, is the case that makes simpler
 * solvers explode. Many small substeps with one iteration each, as the paper
 * recommends, instead of few steps with many iterations.
 *
 * <p>Deterministic: fixed iteration order, no hash maps, no randomness, so two
 * clients given the same launch run the same fall.
 */
public final class BodySim {
	public final Body[] bodies;
	public final Joint[] joints;
	/** Self-collision pairs, packed: the points of bodies[i] against the box of bodies[i + 1]. */
	private final int[] pairs;

	/** Blocks a second squared. Minecraft's own (0.08 a tick squared), so a body falls with the world. */
	public double gravity = 32.0;
	public int substeps = 10;
	/**
	 * Constraint passes per substep. One, as the XPBD paper recommends (many
	 * substeps, one pass each): measured against two passes and against 16
	 * and 20 substeps, it settled bodies as well or better at two thirds of
	 * the cost.
	 */
	public int iterations = 1;
	/** Velocity lost per second in air; Minecraft loses 2% a tick, which is 0.4 a second. */
	public double linearDrag = 0.4;
	public double angularDrag = 0.6;
	public double staticFriction = 0.9;
	public double dynamicFriction = 0.7;
	public double restitution = 0.12;
	/** Self-collision boxes are shrunk this much, so limbs resting against the torso do not chatter. */
	public double selfMargin = 0.012;
	/**
	 * Rolling resistance: a body touching the ground and moving slower than
	 * {@link #restSpeed} loses this much of its motion per second. Flesh is soft
	 * and does not roll; without it a limp body lying on its side creeps and
	 * rolls by millimetres for ever, and never goes to sleep.
	 */
	public double restDamping = 10.0;
	public double restSpeed = 0.5;

	/** Upper bound on any body's speed, blocks a second; a corpse leaving the world helps nobody. */
	public double maxSpeed = 60.0;

	/**
	 * A body held upright for a moment (a stagger, phase 3): its local
	 * {@link #uprightAxis} is pulled toward world up with this stiffness,
	 * torque per radian. 0 is no hold.
	 */
	public @org.jspecify.annotations.Nullable Body upright;
	public final Vector3d uprightAxis = new Vector3d(0.0, -1.0, 0.0);
	public double uprightStiffness;

	private final BoxList boxes = new BoxList();

	private static final int MAX_CONTACTS = 1024;
	private int contactCount;
	private final int[] contactBody = new int[MAX_CONTACTS];
	private final int[] contactSample = new int[MAX_CONTACTS];
	private final double[] contactNormal = new double[MAX_CONTACTS * 3];
	private final double[] contactLambda = new double[MAX_CONTACTS];
	private final double[] contactVn = new double[MAX_CONTACTS];

	private final Vector3d t1 = new Vector3d();
	private final Vector3d t2 = new Vector3d();
	private final Vector3d t3 = new Vector3d();
	private final Vector3d t4 = new Vector3d();
	private final Quaterniond q1 = new Quaterniond();
	private final Quaterniond q2 = new Quaterniond();
	private final Vector3d swing = new Vector3d();
	private final double[] twist = new double[1];

	/** Set when a step produced a non-finite number; the owner should stop using this sim. */
	private boolean broken;

	public BodySim(Body[] bodies, Joint[] joints, int[] selfPairs) {
		this.bodies = bodies;
		this.joints = joints;
		this.pairs = selfPairs;
	}

	public boolean broken() {
		return broken;
	}

	/** One tick of {@code dt} seconds. */
	public void step(double dt, PhysicsWorld world) {
		if (broken) {
			return;
		}
		for (Body b : bodies) {
			b.tickX.set(b.x);
			b.tickQ.set(b.q);
		}
		gather(world, dt);
		fluids(world);
		double h = dt / substeps;
		for (int s = 0; s < substeps; s++) {
			substep(h);
		}
		for (Body b : bodies) {
			if (!b.finite()) {
				broken = true;
				for (Body r : bodies) {
					// Leave it where the last good tick had it.
					r.x.set(r.tickX);
					r.q.set(r.tickQ);
					r.v.zero();
					r.w.zero();
				}
				return;
			}
		}
	}

	/**
	 * Settles a body that has come to rest: a few passes of the joints and the
	 * world with no motion at all, so the two agree before it is frozen. In
	 * motion they share each substep's error, which a moving body never shows;
	 * a body that falls asleep keeps whatever share was left, and a limb pinned
	 * under it can keep a pixel's gap at the shoulder for good.
	 */
	public void relax(int passes) {
		if (broken) {
			return;
		}
		for (int pass = 0; pass < passes; pass++) {
			for (Body b : bodies) {
				b.prevX.set(b.x);
				b.prevQ.set(b.q);
				b.prevV.zero();
				b.prevW.zero();
			}
			contactCount = 0;
			// In order of what matters least to what matters most, since the last
			// word wins: limbs against each other (an arm a little inside the
			// torso is hidden by the torso), then the joints (a gap at the
			// shoulder shows), then the world (nothing in the ground). The
			// limits are left out: when a pinned limb cannot be inside its limit,
			// on the floor and on its shoulder all at once, a few degrees past
			// the limit cannot be seen.
			for (int i = 0; i < pairs.length; i += 2) {
				selfCollide(bodies[pairs[i]], bodies[pairs[i + 1]]);
			}
			for (Joint j : joints) {
				attach(j);
			}
			for (int i = 0; i < bodies.length; i++) {
				worldCollide(i);
			}
		}
		contactCount = 0;
		for (Body b : bodies) {
			b.v.zero();
			b.w.zero();
		}
	}

	/** The fastest any part of the body is moving, blocks a second (angular speed counted at a half-block lever). */
	public double maxMotion() {
		double m = 0.0;
		for (Body b : bodies) {
			m = Math.max(m, b.v.length());
			m = Math.max(m, b.w.length() * 0.5);
		}
		return m;
	}

	/** The world boxes around the whole body this tick, for the sleeping body's wake check. */
	public long worldDigest(PhysicsWorld world) {
		gather(world, 0.0);
		return boxes.digest();
	}

	private void gather(PhysicsWorld world, double dt) {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double minZ = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		double maxZ = Double.NEGATIVE_INFINITY;
		for (Body b : bodies) {
			b.worldExtent(t1);
			double reach = b.v.length() * dt + 0.25;
			minX = Math.min(minX, b.x.x - t1.x - reach);
			minY = Math.min(minY, b.x.y - t1.y - reach);
			minZ = Math.min(minZ, b.x.z - t1.z - reach);
			maxX = Math.max(maxX, b.x.x + t1.x + reach);
			maxY = Math.max(maxY, b.x.y + t1.y + reach);
			maxZ = Math.max(maxZ, b.x.z + t1.z + reach);
		}
		boxes.clear();
		world.collect(minX, minY, minZ, maxX, maxY, maxZ, boxes);
	}

	private void fluids(PhysicsWorld world) {
		for (Body b : bodies) {
			b.worldExtent(t1);
			PhysicsWorld.Fluid f = world.fluid(b.x.x, b.x.y, b.x.z);
			if (f.kind() == PhysicsWorld.Fluid.Kind.NONE) {
				f = world.fluid(b.x.x, b.x.y - t1.y * 0.8, b.x.z);
			}
			switch (f.kind()) {
				case NONE -> {
					b.submerged = 0.0;
					b.buoyancy = 0.0;
					b.fluidDrag = 0.0;
				}
				case WATER -> {
					b.submerged = clamp((f.surface() - (b.x.y - t1.y)) / (2.0 * t1.y), 0.0, 1.0);
					b.buoyancy = 1.12;
					b.fluidDrag = 3.0;
				}
				case LAVA -> {
					b.submerged = clamp((f.surface() - (b.x.y - t1.y)) / (2.0 * t1.y), 0.0, 1.0);
					b.buoyancy = 0.85;
					b.fluidDrag = 7.0;
				}
			}
		}
	}

	private void substep(double h) {
		for (Body b : bodies) {
			b.prevX.set(b.x);
			b.prevQ.set(b.q);
			b.prevV.set(b.v);
			b.prevW.set(b.w);
			b.v.y -= gravity * (1.0 - b.submerged * b.buoyancy) * h;
			double ld = 1.0 - (linearDrag + b.submerged * b.fluidDrag) * h;
			double ad = 1.0 - (angularDrag + b.submerged * b.fluidDrag) * h;
			b.v.mul(Math.max(0.0, ld));
			b.w.mul(Math.max(0.0, ad));
			b.x.fma(h, b.v);
			b.turn(b.w.x * h, b.w.y * h, b.w.z * h);
		}

		// Joints, limbs against each other, then the world, which is solved
		// last so nothing ends a pass in the ground. (Limbs against each other
		// after the joints: in motion that settles a body twice as fast as the
		// other way round; at rest, {@link #relax} turns it about.)
		for (int pass = 0; pass < iterations; pass++) {
			contactCount = 0;
			for (Joint j : joints) {
				attach(j);
				limit(j);
				if (pass == 0) {
					drive(j, h);
				}
			}
			for (int i = 0; i < pairs.length; i += 2) {
				selfCollide(bodies[pairs[i]], bodies[pairs[i + 1]]);
			}
			if (pass == 0) {
				holdUpright(h);
			}
			for (int i = 0; i < bodies.length; i++) {
				worldCollide(i);
			}
		}

		for (Body b : bodies) {
			b.v.set(b.x).sub(b.prevX).div(h);
			// dq = q * prevQ^-1
			b.prevQ.conjugate(q1);
			b.q.mul(q1, q2);
			double sign = q2.w < 0.0 ? -1.0 : 1.0;
			b.w.set(q2.x, q2.y, q2.z).mul(2.0 * sign / h);
			clampLength(b.v, maxSpeed);
			clampLength(b.w, maxSpeed);
		}

		for (int c = 0; c < contactCount; c++) {
			contactVelocity(c, h);
		}
		int lastBody = -1;
		for (int c = 0; c < contactCount; c++) {
			int bi = contactBody[c];
			if (bi == lastBody) {
				continue;
			}
			lastBody = bi;
			Body b = bodies[bi];
			if (b.settles && b.v.length() < restSpeed && b.w.length() * 0.25 < restSpeed) {
				double keep = Math.max(0.0, 1.0 - restDamping * h);
				b.v.mul(keep);
				b.w.mul(keep);
			}
		}
		for (Joint j : joints) {
			jointDamping(j, h);
		}
	}

	// --- Joints ---------------------------------------------------------------

	private void attach(Joint j) {
		Body a = j.parent;
		Body b = j.child;
		a.toWorld(j.anchorParent.x, j.anchorParent.y, j.anchorParent.z, t1);
		b.toWorld(j.anchorChild.x, j.anchorChild.y, j.anchorChild.z, t2);
		double dx = t2.x - t1.x;
		double dy = t2.y - t1.y;
		double dz = t2.z - t1.z;
		double c = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (c < 1.0e-9) {
			return;
		}
		double nx = dx / c;
		double ny = dy / c;
		double nz = dz / c;
		double rax = t1.x - a.x.x;
		double ray = t1.y - a.x.y;
		double raz = t1.z - a.x.z;
		double rbx = t2.x - b.x.x;
		double rby = t2.y - b.x.y;
		double rbz = t2.z - b.x.z;
		double wa = a.positionalInvMass(rax, ray, raz, nx, ny, nz, t3);
		double wb = b.positionalInvMass(rbx, rby, rbz, nx, ny, nz, t3);
		double lambda = c / (wa + wb);
		double px = nx * lambda;
		double py = ny * lambda;
		double pz = nz * lambda;
		a.applyPositional(px, py, pz, rax, ray, raz, 1.0, t3);
		b.applyPositional(px, py, pz, rbx, rby, rbz, -1.0, t3);
	}

	private void limit(Joint j) {
		j.relative(q1);
		Joint.decompose(q1, swing, twist);
		double sx = clamp(swing.x, j.swingXMin, j.swingXMax);
		double sz = clamp(swing.z, j.swingZMin, j.swingZMax);
		double tw = clamp(twist[0], j.twistMin, j.twistMax);
		if (sx == swing.x && sz == swing.z && tw == twist[0]) {
			return;
		}
		// The allowed rotation nearest this one, and the turn that gets there.
		Joint.compose(sx, sz, tw, q2);
		correctToward(j, q2, 0.0);
	}

	private void drive(Joint j, double h) {
		if (j.stiffness <= 0.0) {
			return;
		}
		correctToward(j, j.target, 1.0 / (j.stiffness * h * h));
	}

	/**
	 * Turns parent and child toward a relative rotation, sharing the turn by their
	 * inertia; {@code alphaTilde} is the compliance over h squared (0 is rigid).
	 */
	private void correctToward(Joint j, Quaterniond targetRel, double alphaTilde) {
		Body a = j.parent;
		Body b = j.child;
		// The turn, in the parent's frame: target * current^-1.
		j.relative(q1);
		q1.conjugate();
		targetRel.mul(q1, q1);
		Joint.rotationVector(q1.x, q1.y, q1.z, q1.w, t1);
		// Into the world.
		Body.rotate(a.q.x, a.q.y, a.q.z, a.q.w, t1.x, t1.y, t1.z, t1);
		double theta = t1.length();
		if (theta < 1.0e-9) {
			return;
		}
		double nx = t1.x / theta;
		double ny = t1.y / theta;
		double nz = t1.z / theta;
		double wa = a.angularInvMass(nx, ny, nz, t3);
		double wb = b.angularInvMass(nx, ny, nz, t3);
		double lambda = theta / (wa + wb + alphaTilde);
		b.applyAngular(nx * lambda, ny * lambda, nz * lambda, 1.0, t3);
		a.applyAngular(nx * lambda, ny * lambda, nz * lambda, -1.0, t3);
	}

	/** Pulls {@link #upright}'s axis toward world up, against the world (which does not turn). */
	private void holdUpright(double h) {
		Body b = upright;
		if (b == null || uprightStiffness <= 0.0) {
			return;
		}
		Body.rotate(b.q.x, b.q.y, b.q.z, b.q.w, uprightAxis.x, uprightAxis.y, uprightAxis.z, t1);
		// The turn taking the axis to up: about axis x up, by the angle between.
		t2.set(t1).cross(0.0, 1.0, 0.0);
		double sin = t2.length();
		if (sin < 1.0e-9) {
			return;
		}
		double angle = Math.atan2(sin, t1.y);
		double nx = t2.x / sin;
		double ny = t2.y / sin;
		double nz = t2.z / sin;
		double w = b.angularInvMass(nx, ny, nz, t3);
		double lambda = angle / (w + 1.0 / (uprightStiffness * h * h));
		b.applyAngular(nx * lambda, ny * lambda, nz * lambda, 1.0, t3);
	}

	private void jointDamping(Joint j, double h) {
		if (j.damping <= 0.0) {
			return;
		}
		Body a = j.parent;
		Body b = j.child;
		double dx = b.w.x - a.w.x;
		double dy = b.w.y - a.w.y;
		double dz = b.w.z - a.w.z;
		double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (d < 1.0e-9) {
			return;
		}
		double nx = dx / d;
		double ny = dy / d;
		double nz = dz / d;
		double wa = a.angularInvMass(nx, ny, nz, t3);
		double wb = b.angularInvMass(nx, ny, nz, t3);
		double p = d * Math.min(j.damping * h, 1.0) / (wa + wb);
		t3.set(nx * p, ny * p, nz * p);
		a.invInertia(t3, t4);
		a.w.add(t4);
		t3.set(nx * p, ny * p, nz * p);
		b.invInertia(t3, t4);
		b.w.sub(t4);
	}

	// --- Collision ------------------------------------------------------------

	private void worldCollide(int index) {
		Body b = bodies[index];
		if (boxes.size == 0) {
			return;
		}
		b.worldExtent(t4);
		double bminX = b.x.x - t4.x - 0.05;
		double bminY = b.x.y - t4.y - 0.05;
		double bminZ = b.x.z - t4.z - 0.05;
		double bmaxX = b.x.x + t4.x + 0.05;
		double bmaxY = b.x.y + t4.y + 0.05;
		double bmaxZ = b.x.z + t4.z + 0.05;
		double[] d = boxes.data;
		double[] s = b.samples;
		for (int k = 0; k < boxes.size; k++) {
			int o = k * 6;
			double minX = d[o];
			double minY = d[o + 1];
			double minZ = d[o + 2];
			double maxX = d[o + 3];
			double maxY = d[o + 4];
			double maxZ = d[o + 5];
			if (maxX < bminX || minX > bmaxX || maxY < bminY || minY > bmaxY || maxZ < bminZ || minZ > bmaxZ) {
				continue;
			}
			for (int i = 0; i < s.length; i += 3) {
				b.toWorld(s[i], s[i + 1], s[i + 2], t1);
				double px = t1.x;
				double py = t1.y;
				double pz = t1.z;
				if (px <= minX || px >= maxX || py <= minY || py >= maxY || pz <= minZ || pz >= maxZ) {
					continue;
				}
				b.toWorldPrev(s[i], s[i + 1], s[i + 2], t2);
				// Out through the face it came in by, if it came in by one: a point
				// sinking into a floor of many blocks must go up, not sideways into
				// the next block over.
				double best = Double.POSITIVE_INFINITY;
				double nx = 0.0;
				double ny = 1.0;
				double nz = 0.0;
				for (int pass = 0; pass < 2 && best == Double.POSITIVE_INFINITY; pass++) {
					boolean any = pass == 1;
					double depth;
					if ((any || t2.y >= maxY - 1.0e-4) && (depth = maxY - py) < best) {
						best = depth;
						nx = 0.0;
						ny = 1.0;
						nz = 0.0;
					}
					if ((any || t2.y <= minY + 1.0e-4) && (depth = py - minY) < best) {
						best = depth;
						nx = 0.0;
						ny = -1.0;
						nz = 0.0;
					}
					if ((any || t2.x >= maxX - 1.0e-4) && (depth = maxX - px) < best) {
						best = depth;
						nx = 1.0;
						ny = 0.0;
						nz = 0.0;
					}
					if ((any || t2.x <= minX + 1.0e-4) && (depth = px - minX) < best) {
						best = depth;
						nx = -1.0;
						ny = 0.0;
						nz = 0.0;
					}
					if ((any || t2.z >= maxZ - 1.0e-4) && (depth = maxZ - pz) < best) {
						best = depth;
						nx = 0.0;
						ny = 0.0;
						nz = 1.0;
					}
					if ((any || t2.z <= minZ + 1.0e-4) && (depth = pz - minZ) < best) {
						best = depth;
						nx = 0.0;
						ny = 0.0;
						nz = -1.0;
					}
				}
				resolveWorldContact(index, i / 3, px, py, pz, nx, ny, nz, best);
			}
		}
	}

	private void resolveWorldContact(int index, int sample, double px, double py, double pz,
			double nx, double ny, double nz, double depth) {
		Body b = bodies[index];
		double rx = px - b.x.x;
		double ry = py - b.x.y;
		double rz = pz - b.x.z;
		double w = b.positionalInvMass(rx, ry, rz, nx, ny, nz, t3);
		double lambda = depth / w;
		b.applyPositional(nx * lambda, ny * lambda, nz * lambda, rx, ry, rz, 1.0, t3);

		// Static friction: a point that is pressed into the floor stays where it
		// was along it, unless it is pushed harder than friction can hold.
		int si = sample * 3;
		b.toWorld(b.samples[si], b.samples[si + 1], b.samples[si + 2], t1);
		double dx = t1.x - t2.x;
		double dy = t1.y - t2.y;
		double dz = t1.z - t2.z;
		double dn = dx * nx + dy * ny + dz * nz;
		dx -= nx * dn;
		dy -= ny * dn;
		dz -= nz * dn;
		double dt = Math.sqrt(dx * dx + dy * dy + dz * dz);
		if (dt > 1.0e-9) {
			double tx = dx / dt;
			double ty = dy / dt;
			double tz = dz / dt;
			double rx2 = t1.x - b.x.x;
			double ry2 = t1.y - b.x.y;
			double rz2 = t1.z - b.x.z;
			double wt = b.positionalInvMass(rx2, ry2, rz2, tx, ty, tz, t3);
			double lt = dt / wt;
			if (lt < staticFriction * b.friction * lambda) {
				b.applyPositional(tx * lt, ty * lt, tz * lt, rx2, ry2, rz2, -1.0, t3);
			}
		}

		if (contactCount < MAX_CONTACTS) {
			int c = contactCount++;
			contactBody[c] = index;
			contactSample[c] = sample;
			contactNormal[c * 3] = nx;
			contactNormal[c * 3 + 1] = ny;
			contactNormal[c * 3 + 2] = nz;
			contactLambda[c] = lambda;
			// The point's approach speed at the start of the substep, for the bounce.
			double r0x = t2.x - b.prevX.x;
			double r0y = t2.y - b.prevX.y;
			double r0z = t2.z - b.prevX.z;
			double vx = b.prevV.x + b.prevW.y * r0z - b.prevW.z * r0y;
			double vy = b.prevV.y + b.prevW.z * r0x - b.prevW.x * r0z;
			double vz = b.prevV.z + b.prevW.x * r0y - b.prevW.y * r0x;
			contactVn[c] = vx * nx + vy * ny + vz * nz;
		}
	}

	private void contactVelocity(int c, double h) {
		Body b = bodies[contactBody[c]];
		int si = contactSample[c] * 3;
		Body.rotate(b.q.x, b.q.y, b.q.z, b.q.w, b.samples[si], b.samples[si + 1], b.samples[si + 2], t1);
		double rx = t1.x;
		double ry = t1.y;
		double rz = t1.z;
		double nx = contactNormal[c * 3];
		double ny = contactNormal[c * 3 + 1];
		double nz = contactNormal[c * 3 + 2];
		double vx = b.v.x + b.w.y * rz - b.w.z * ry;
		double vy = b.v.y + b.w.z * rx - b.w.x * rz;
		double vz = b.v.z + b.w.x * ry - b.w.y * rx;
		double vn = vx * nx + vy * ny + vz * nz;
		double tx = vx - nx * vn;
		double ty = vy - ny * vn;
		double tz = vz - nz * vn;
		double vt = Math.sqrt(tx * tx + ty * ty + tz * tz);
		double dvx = 0.0;
		double dvy = 0.0;
		double dvz = 0.0;
		if (vt > 1.0e-9) {
			double normalForce = contactLambda[c] / (h * h);
			double slow = Math.min(dynamicFriction * b.friction * normalForce * h, vt);
			dvx -= tx / vt * slow;
			dvy -= ty / vt * slow;
			dvz -= tz / vt * slow;
		}
		double vnPre = contactVn[c];
		double e = Math.abs(vnPre) <= 2.0 * gravity * h ? 0.0 : restitution;
		double targetVn = Math.max(-e * vnPre, 0.0);
		double dn = targetVn - vn;
		dvx += nx * dn;
		dvy += ny * dn;
		dvz += nz * dn;
		double dv = Math.sqrt(dvx * dvx + dvy * dvy + dvz * dvz);
		if (dv < 1.0e-12) {
			return;
		}
		double w = b.positionalInvMass(rx, ry, rz, dvx / dv, dvy / dv, dvz / dv, t3);
		double px = dvx / w;
		double py = dvy / w;
		double pz = dvz / w;
		b.v.add(px * b.invMass, py * b.invMass, pz * b.invMass);
		t3.set(ry * pz - rz * py, rz * px - rx * pz, rx * py - ry * px);
		b.invInertia(t3, t3);
		b.w.add(t3);
	}

	/** The points of body a pushed out of the box of body b, both of them moving. */
	private void selfCollide(Body a, Body b) {
		double hx = b.hx - selfMargin;
		double hy = b.hy - selfMargin;
		double hz = b.hz - selfMargin;
		double[] s = a.samples;
		for (int i = 0; i < s.length; i += 3) {
			a.toWorld(s[i], s[i + 1], s[i + 2], t1);
			double px = t1.x;
			double py = t1.y;
			double pz = t1.z;
			// Into b's frame.
			Body.rotate(-b.q.x, -b.q.y, -b.q.z, b.q.w, px - b.x.x, py - b.x.y, pz - b.x.z, t2);
			double lx = t2.x;
			double ly = t2.y;
			double lz = t2.z;
			if (Math.abs(lx) >= hx || Math.abs(ly) >= hy || Math.abs(lz) >= hz) {
				continue;
			}
			a.toWorldPrev(s[i], s[i + 1], s[i + 2], t2);
			Body.rotate(-b.prevQ.x, -b.prevQ.y, -b.prevQ.z, b.prevQ.w,
					t2.x - b.prevX.x, t2.y - b.prevX.y, t2.z - b.prevX.z, t2);
			double best = Double.POSITIVE_INFINITY;
			double nx = 0.0;
			double ny = 0.0;
			double nz = 0.0;
			for (int pass = 0; pass < 2 && best == Double.POSITIVE_INFINITY; pass++) {
				boolean any = pass == 1;
				double depth;
				if ((any || t2.x >= hx) && (depth = hx - lx) < best) {
					best = depth;
					nx = 1.0;
					ny = 0.0;
					nz = 0.0;
				}
				if ((any || t2.x <= -hx) && (depth = lx + hx) < best) {
					best = depth;
					nx = -1.0;
					ny = 0.0;
					nz = 0.0;
				}
				if ((any || t2.y >= hy) && (depth = hy - ly) < best) {
					best = depth;
					nx = 0.0;
					ny = 1.0;
					nz = 0.0;
				}
				if ((any || t2.y <= -hy) && (depth = ly + hy) < best) {
					best = depth;
					nx = 0.0;
					ny = -1.0;
					nz = 0.0;
				}
				if ((any || t2.z >= hz) && (depth = hz - lz) < best) {
					best = depth;
					nx = 0.0;
					ny = 0.0;
					nz = 1.0;
				}
				if ((any || t2.z <= -hz) && (depth = lz + hz) < best) {
					best = depth;
					nx = 0.0;
					ny = 0.0;
					nz = -1.0;
				}
			}
			// The normal back into the world.
			Body.rotate(b.q.x, b.q.y, b.q.z, b.q.w, nx, ny, nz, t2);
			nx = t2.x;
			ny = t2.y;
			nz = t2.z;
			double rax = px - a.x.x;
			double ray = py - a.x.y;
			double raz = pz - a.x.z;
			double rbx = px - b.x.x;
			double rby = py - b.x.y;
			double rbz = pz - b.x.z;
			double wa = a.positionalInvMass(rax, ray, raz, nx, ny, nz, t3);
			double wb = b.positionalInvMass(rbx, rby, rbz, nx, ny, nz, t3);
			double lambda = best / (wa + wb);
			a.applyPositional(nx * lambda, ny * lambda, nz * lambda, rax, ray, raz, 1.0, t3);
			b.applyPositional(nx * lambda, ny * lambda, nz * lambda, rbx, rby, rbz, -1.0, t3);
		}
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	private static void clampLength(Vector3d v, double max) {
		double l = v.length();
		if (l > max) {
			v.mul(max / l);
		}
	}
}
