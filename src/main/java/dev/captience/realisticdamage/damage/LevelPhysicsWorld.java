package dev.captience.realisticdamage.damage;

import dev.captience.realisticdamage.ragdoll.physics.BoxList;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A level as a ragdoll sees it, on the server (2.0): block collision shapes and
 * fluids, read exactly the way the client's {@code McPhysicsWorld} reads them,
 * so the server's copy of a falling body lands on the same stairs, slabs and
 * water as everyone's. Entities, the world border and other bodies are not in it.
 */
final class LevelPhysicsWorld implements PhysicsWorld {
	private final Level level;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	LevelPhysicsWorld(Level level) {
		this.level = level;
	}

	@Override
	public void collect(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, BoxList out) {
		for (VoxelShape shape : level.getBlockCollisions(null, new AABB(minX, minY, minZ, maxX, maxY, maxZ))) {
			shape.forAllBoxes(out::add);
		}
	}

	@Override
	public Fluid fluid(double x, double y, double z) {
		cursor.set(x, y, z);
		FluidState state = level.getFluidState(cursor);
		if (state.isEmpty()) {
			return Fluid.NONE;
		}
		double surface = cursor.getY() + state.getHeight(level, cursor);
		if (y >= surface) {
			return Fluid.NONE;
		}
		// A full block of fluid with more above: the surface is higher up. A body
		// is two blocks at most, so a few steps are enough.
		boolean lava = state.is(FluidTags.LAVA);
		for (int up = 0; up < 3 && state.getHeight(level, cursor) >= 1.0F; up++) {
			cursor.move(0, 1, 0);
			FluidState above = level.getFluidState(cursor);
			if (above.isEmpty() || above.is(FluidTags.LAVA) != lava) {
				break;
			}
			state = above;
			surface = cursor.getY() + above.getHeight(level, cursor);
		}
		return new Fluid(lava ? Fluid.Kind.LAVA : Fluid.Kind.WATER, surface);
	}
}
