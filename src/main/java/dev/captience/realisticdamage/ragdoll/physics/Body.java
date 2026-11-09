package dev.captience.realisticdamage.ragdoll.physics;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * One rigid box of a ragdoll (1.7, active ragdolls phase 0).
 *
 * <p>Units are blocks, seconds and kilograms. The body's own frame has its
 * origin at the centre of mass, which is the centre of the box: a Minecraft
 * limb is one uniform cube, so there is no better guess to make.
 *
 * <p>Plain Java on purpose (no Minecraft types): the whole solver runs in the
 * unit tests against a fake world, so it can be broken and fixed without a
 * game window. See {@link BodySim} for the method.
 */
public final class Body {
	/** Centre of mass, world. */
	public final Vector3d x = new Vector3d();
	/** Orientation, world from local. */
	public final Quaterniond q = new Quaterniond();
	/** Linear velocity, blocks a second. */
	public final Vector3d v = new Vector3d();
	/** Angular velocity, world axes, radians a second. */
	public final Vector3d w = new Vector3d();

	/** The pose at the start of the current substep. */
	final Vector3d prevX = new Vector3d();
	final Quaterniond prevQ = new Quaterniond();
	/** The velocities at the start of the current substep (restitution reads them). */
	final Vector3d prevV = new Vector3d();
	final Vector3d prevW = new Vector3d();

	/** The pose at the end of the previous tick, for render interpolation. */
	public final Vector3d tickX = new Vector3d();
	public final Quaterniond tickQ = new Quaterniond();

	/** Half extents of the box, local axes, blocks. */
	public final double hx;
	public final double hy;
	public final double hz;
	public final double mass;
	final double invMass;
	/** Inverse inertia, local principal axes. */
	final double iix;
	final double iiy;
	final double iiz;

	/** Points on the box that collide with the world, local, packed xyz. */
	final double[] samples;

	/** This tick's fluid: the fraction under the surface, and how it pushes. */
	double submerged;
	double buoyancy;
	double fluidDrag;

	public Body(double hx, double hy, double hz, double mass) {
		this.hx = hx;
		this.hy = hy;
		this.hz = hz;
		this.mass = mass;
		this.invMass = 1.0 / mass;
		double sx = 2.0 * hx;
		double sy = 2.0 * hy;
		double sz = 2.0 * hz;
		this.iix = 12.0 / (mass * (sy * sy + sz * sz));
		this.iiy = 12.0 / (mass * (sx * sx + sz * sz));
		this.iiz = 12.0 / (mass * (sx * sx + sy * sy));
		this.samples = samplePoints(hx, hy, hz);
	}

	/**
	 * A lattice over the box's surface, spaced no wider than {@link #SAMPLE_SPACING}:
	 * corners and edges always, faces filled in on the big ones. A block edge can
	 * still slip between two points on a long face, which is why the spacing is
	 * under a quarter of a block.
	 */
	static final double SAMPLE_SPACING = 0.24;

	private static double[] samplePoints(double hx, double hy, double hz) {
		int nx = Math.max(1, (int) Math.ceil(2.0 * hx / SAMPLE_SPACING));
		int ny = Math.max(1, (int) Math.ceil(2.0 * hy / SAMPLE_SPACING));
		int nz = Math.max(1, (int) Math.ceil(2.0 * hz / SAMPLE_SPACING));
		double[] tmp = new double[(nx + 1) * (ny + 1) * (nz + 1) * 3];
		int n = 0;
		for (int i = 0; i <= nx; i++) {
			for (int j = 0; j <= ny; j++) {
				for (int k = 0; k <= nz; k++) {
					boolean surface = i == 0 || i == nx || j == 0 || j == ny || k == 0 || k == nz;
					if (!surface) {
						continue;
					}
					tmp[n++] = -hx + 2.0 * hx * i / nx;
					tmp[n++] = -hy + 2.0 * hy * j / ny;
					tmp[n++] = -hz + 2.0 * hz * k / nz;
				}
			}
		}
		return java.util.Arrays.copyOf(tmp, n);
	}

	public int sampleCount() {
		return samples.length / 3;
	}

	/** Places the body and zeroes its motion; the interpolation history starts here too. */
	public void place(Vector3d position, Quaterniond orientation) {
		x.set(position);
		q.set(orientation).normalize();
		tickX.set(x);
		tickQ.set(q);
		prevX.set(x);
		prevQ.set(q);
	}

	/** out = (world inverse inertia) * in. {@code in} and {@code out} may be the same vector. */
	void invInertia(Vector3d in, Vector3d out) {
		// Into the body frame, scale by the diagonal, back out.
		double qx = -q.x;
		double qy = -q.y;
		double qz = -q.z;
		double qw = q.w;
		rotate(qx, qy, qz, qw, in.x, in.y, in.z, out);
		out.set(out.x * iix, out.y * iiy, out.z * iiz);
		rotate(q.x, q.y, q.z, q.w, out.x, out.y, out.z, out);
	}

	/** The generalized inverse mass for a push along unit n at world offset r from the centre. */
	double positionalInvMass(double rx, double ry, double rz, double nx, double ny, double nz, Vector3d tmp) {
		tmp.set(ry * nz - rz * ny, rz * nx - rx * nz, rx * ny - ry * nx);
		double cx = tmp.x;
		double cy = tmp.y;
		double cz = tmp.z;
		invInertia(tmp, tmp);
		return invMass + cx * tmp.x + cy * tmp.y + cz * tmp.z;
	}

	/** The generalized inverse mass for a turn about unit n. */
	double angularInvMass(double nx, double ny, double nz, Vector3d tmp) {
		tmp.set(nx, ny, nz);
		invInertia(tmp, tmp);
		return nx * tmp.x + ny * tmp.y + nz * tmp.z;
	}

	/** Applies positional impulse sign*p at world offset r: moves the centre and turns the body. */
	void applyPositional(double px, double py, double pz, double rx, double ry, double rz, double sign, Vector3d tmp) {
		x.add(px * invMass * sign, py * invMass * sign, pz * invMass * sign);
		tmp.set((ry * pz - rz * py) * sign, (rz * px - rx * pz) * sign, (rx * py - ry * px) * sign);
		invInertia(tmp, tmp);
		turn(tmp.x, tmp.y, tmp.z);
	}

	/** Applies angular impulse sign*p. */
	void applyAngular(double px, double py, double pz, double sign, Vector3d tmp) {
		tmp.set(px * sign, py * sign, pz * sign);
		invInertia(tmp, tmp);
		turn(tmp.x, tmp.y, tmp.z);
	}

	/** q += 0.5 * (r, 0) * q, then renormalised: a small rotation by the world rotation vector r. */
	void turn(double rx, double ry, double rz) {
		double qx = q.x;
		double qy = q.y;
		double qz = q.z;
		double qw = q.w;
		q.x = qx + 0.5 * (rx * qw + ry * qz - rz * qy);
		q.y = qy + 0.5 * (-rx * qz + ry * qw + rz * qx);
		q.z = qz + 0.5 * (rx * qy - ry * qx + rz * qw);
		q.w = qw + 0.5 * (-rx * qx - ry * qy - rz * qz);
		q.normalize();
	}

	/** out = x + q * local. */
	void toWorld(double lx, double ly, double lz, Vector3d out) {
		rotate(q.x, q.y, q.z, q.w, lx, ly, lz, out);
		out.add(x);
	}

	/** out = prevX + prevQ * local: where that point was at the start of the substep. */
	void toWorldPrev(double lx, double ly, double lz, Vector3d out) {
		rotate(prevQ.x, prevQ.y, prevQ.z, prevQ.w, lx, ly, lz, out);
		out.add(prevX);
	}

	/** out = rotation (x, y, z, w) applied to vector (vx, vy, vz). */
	static void rotate(double x, double y, double z, double w, double vx, double vy, double vz, Vector3d out) {
		// v + 2w(u x v) + 2u x (u x v), u = (x, y, z)
		double tx = 2.0 * (y * vz - z * vy);
		double ty = 2.0 * (z * vx - x * vz);
		double tz = 2.0 * (x * vy - y * vx);
		out.set(vx + w * tx + (y * tz - z * ty),
				vy + w * ty + (z * tx - x * tz),
				vz + w * tz + (x * ty - y * tx));
	}

	/** Half the body's world-axis extent along x, y, z (its world AABB about the centre). */
	void worldExtent(Vector3d out) {
		Vector3d a = new Vector3d();
		rotate(q.x, q.y, q.z, q.w, hx, 0, 0, a);
		double ex = Math.abs(a.x);
		double ey = Math.abs(a.y);
		double ez = Math.abs(a.z);
		rotate(q.x, q.y, q.z, q.w, 0, hy, 0, a);
		ex += Math.abs(a.x);
		ey += Math.abs(a.y);
		ez += Math.abs(a.z);
		rotate(q.x, q.y, q.z, q.w, 0, 0, hz, a);
		ex += Math.abs(a.x);
		ey += Math.abs(a.y);
		ez += Math.abs(a.z);
		out.set(ex, ey, ez);
	}

	boolean finite() {
		return Double.isFinite(x.x) && Double.isFinite(x.y) && Double.isFinite(x.z)
				&& Double.isFinite(q.x) && Double.isFinite(q.y) && Double.isFinite(q.z) && Double.isFinite(q.w)
				&& Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z)
				&& Double.isFinite(w.x) && Double.isFinite(w.y) && Double.isFinite(w.z);
	}
}
