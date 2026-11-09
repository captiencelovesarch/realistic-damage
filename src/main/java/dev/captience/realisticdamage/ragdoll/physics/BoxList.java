package dev.captience.realisticdamage.ragdoll.physics;

import java.util.Arrays;

/** A growable flat list of axis-aligned boxes, six doubles each. No allocation once warm. */
public final class BoxList {
	double[] data = new double[6 * 32];
	int size;

	public void clear() {
		size = 0;
	}

	public int size() {
		return size;
	}

	public void add(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
		int i = size * 6;
		if (i + 6 > data.length) {
			data = Arrays.copyOf(data, data.length * 2);
		}
		data[i] = minX;
		data[i + 1] = minY;
		data[i + 2] = minZ;
		data[i + 3] = maxX;
		data[i + 4] = maxY;
		data[i + 5] = maxZ;
		size++;
	}

	/** How deep a point is inside the deepest box holding it, 0 when it is in none. */
	public double deepest(double x, double y, double z) {
		double worst = 0.0;
		for (int i = 0; i < size; i++) {
			int o = i * 6;
			if (x > data[o] && x < data[o + 3] && y > data[o + 1] && y < data[o + 4] && z > data[o + 2] && z < data[o + 5]) {
				double d = Math.min(Math.min(Math.min(x - data[o], data[o + 3] - x), Math.min(y - data[o + 1], data[o + 4] - y)),
						Math.min(z - data[o + 2], data[o + 5] - z));
				worst = Math.max(worst, d);
			}
		}
		return worst;
	}

	/** An order-sensitive digest of the boxes, to notice the world changing under a sleeping body. */
	public long digest() {
		long h = size;
		for (int i = 0; i < size * 6; i++) {
			h = h * 31 + Double.doubleToLongBits(data[i]);
		}
		return h;
	}
}
