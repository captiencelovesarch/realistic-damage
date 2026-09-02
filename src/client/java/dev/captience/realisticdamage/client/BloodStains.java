package dev.captience.realisticdamage.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.systems.BloodFlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jspecify.annotations.Nullable;

/**
 * The book-keeping behind the marks on the world (1.5): a bounded field of
 * stains with oldest-first eviction and merging.
 *
 * <p>Two problems this exists to solve, both of which the particle engine alone
 * would get wrong.
 *
 * <p>The first is unbounded growth. Stains live for the best part of a minute,
 * and a critical wound produces eight drops a second — left alone that is
 * hundreds of live quads and no upper limit, which is precisely the "no
 * unbounded state" line the spec draws. So there is a hard capacity and the
 * oldest mark goes when a new one arrives.
 *
 * <p>The second is that a stationary player would spend the entire budget on
 * one square of floor. A drop landing within {@link Balance#STAIN_MERGE_DISTANCE}
 * of an existing mark feeds that mark instead of creating another, so standing
 * still grows a pool while walking lays a trail — which is the actual
 * information the player wanted out of this feature.
 */
public final class BloodStains {
	private BloodStains() {}

	/** The four splat sprites sit after the four drop sprites in {@code blood.json}. */
	private static final int SPLAT_SPRITE_OFFSET = 4;
	private static final int SPRITE_COUNT = 8;

	private static @Nullable SpriteSet sprites;
	/** Oldest first, so eviction is a poll from the head. */
	private static final Deque<BloodStain> live = new ArrayDeque<>();
	/** Merge candidates bucketed by the block they sit in — merges are always local. */
	private static final Map<Long, List<BloodStain>> byBlock = new HashMap<>();
	/** Only ever used to vary splat shape and roll, never to decide anything. */
	private static long counter;

	static void bindSprites(SpriteSet spriteSet) {
		sprites = spriteSet;
	}

	/**
	 * Records a drop landing on {@code face} at the given point. The point should
	 * already be on the surface; the offset away from it is applied here so every
	 * caller cannot get it subtly different and z-fight.
	 */
	public static void land(double x, double y, double z, Direction face, double dropScale) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		double strength = config.bloodIntensity() * config.bloodStainIntensity;
		if (strength <= 0.0 || sprites == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null) {
			return;
		}
		double radius = BloodFlow.stainSize(dropScale);
		var normal = face.getUnitVec3i();
		double sx = x + normal.getX() * Balance.STAIN_SURFACE_OFFSET;
		double sy = y + normal.getY() * Balance.STAIN_SURFACE_OFFSET;
		double sz = z + normal.getZ() * Balance.STAIN_SURFACE_OFFSET;

		BloodStain existing = findMergeTarget(sx, sy, sz, face);
		if (existing != null) {
			existing.grow(radius);
			return;
		}

		long seed = counter++;
		int variant = (int) (BloodFlow.jitter(seed, 3) * 4.0);
		BloodStain stain = new BloodStain(level, sx, sy, sz, face, radius, seed,
				sprites.get(Math.min(3, variant) + SPLAT_SPRITE_OFFSET, SPRITE_COUNT - 1));
		client.particleEngine.add(stain);
		register(stain, sx, sy, sz);
	}

	private static void register(BloodStain stain, double x, double y, double z) {
		while (live.size() >= Balance.STAIN_CAPACITY) {
			BloodStain oldest = live.poll();
			if (oldest != null) {
				oldest.remove();
			}
		}
		live.add(stain);
		byBlock.computeIfAbsent(BlockPos.asLong(
				(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)),
				key -> new ArrayList<>()).add(stain);
	}

	/**
	 * The nearest live mark on the same face within the merge radius, if any.
	 * Bucketed by block: the merge distance is well under one block, so a linear
	 * scan of the whole field would be doing several hundred distance checks to
	 * find a neighbour that can only ever be a few centimetres away.
	 */
	private static @Nullable BloodStain findMergeTarget(double x, double y, double z, Direction face) {
		List<BloodStain> bucket = byBlock.get(BlockPos.asLong(
				(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
		if (bucket == null) {
			return null;
		}
		BloodStain best = null;
		double bestDistance = Balance.STAIN_MERGE_DISTANCE * Balance.STAIN_MERGE_DISTANCE;
		for (int i = bucket.size() - 1; i >= 0; i--) {
			BloodStain stain = bucket.get(i);
			if (!stain.isAlive()) {
				bucket.remove(i);
				continue;
			}
			if (stain.face() != face) {
				continue;
			}
			double dx = stain.px() - x;
			double dy = stain.py() - y;
			double dz = stain.pz() - z;
			double distance = dx * dx + dy * dy + dz * dz;
			if (distance < bestDistance) {
				bestDistance = distance;
				best = stain;
			}
		}
		return best;
	}

	/** How many marks are currently on the world. Exists so tests can see them. */
	public static int count() {
		return live.size();
	}

	/** Drops references to dead stains so the buckets do not grow without bound. */
	public static void tick() {
		if (live.isEmpty()) {
			return;
		}
		live.removeIf(stain -> !stain.isAlive());
		byBlock.values().removeIf(bucket -> {
			bucket.removeIf(stain -> !stain.isAlive());
			return bucket.isEmpty();
		});
	}

	/** Dimension change, disconnect, or the player switching the effect off. */
	public static void reset() {
		for (BloodStain stain : live) {
			stain.remove();
		}
		live.clear();
		byBlock.clear();
		counter = 0;
	}
}
