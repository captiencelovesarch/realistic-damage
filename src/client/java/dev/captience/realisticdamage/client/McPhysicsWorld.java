package dev.captience.realisticdamage.client;

import dev.captience.realisticdamage.ragdoll.physics.BoxList;
import dev.captience.realisticdamage.ragdoll.physics.PhysicsWorld;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The client level as a ragdoll sees it: block collision shapes (stairs,
 * slabs, fences, carpets: whatever the blocks say their shape is) and fluids.
 * Entities, the world border and other bodies are not in it.
 */
final class McPhysicsWorld implements PhysicsWorld {
	private final ClientLevel level;
	private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

	McPhysicsWorld(ClientLevel level) {
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
