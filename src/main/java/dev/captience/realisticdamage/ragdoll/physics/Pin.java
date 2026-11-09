package dev.captience.realisticdamage.ragdoll.physics;

import org.joml.Vector3d;

/**
 * A point on a body held to a spot in the world: a planted foot (stumbling
 * deaths, IK phase 0). This is what games mean by "foot planting": once a foot
 * is down it stays where it landed, whatever the rest of the body does, and
 * the body vaults over it instead of skating.
 *
 * <p>Horizontal by default. The ground already stops a foot going down, and a
 * foot rocking onto its toe as the body passes over it has to be free to lift,
 * so only sliding is held. Slightly soft ({@link #compliance}), so a body
 * pivoting about the edge of its sole does not fight the pin.
 */
public final class Pin {
	public final Body body;
	/** The pinned point, in the body's own frame. */
	public final Vector3d local = new Vector3d();
	/** Where it is held, world. */
	public final Vector3d world = new Vector3d();
	public boolean active;
	/** Hold only along the ground (x and z), not up and down. */
	public boolean horizontal = true;
	/** Blocks per unit of force: 0 is rigid. */
	public double compliance;

	public Pin(Body body) {
		this.body = body;
	}

	/** Holds the body's point {@code local} where it is now. */
	public void plant(Vector3d localPoint) {
		local.set(localPoint);
		body.toWorld(local.x, local.y, local.z, world);
		active = true;
	}

	/** How far the pinned point has drifted from its spot, blocks (horizontally, for a horizontal pin). */
	public double drift() {
		Vector3d now = new Vector3d();
		body.toWorld(local.x, local.y, local.z, now);
		double dx = now.x - world.x;
		double dy = horizontal ? 0.0 : now.y - world.y;
		double dz = now.z - world.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
