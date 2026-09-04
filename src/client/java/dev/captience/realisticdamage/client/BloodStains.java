package dev.captience.realisticdamage.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.captience.realisticdamage.body.BodyPart;
import dev.captience.realisticdamage.config.Balance;
import dev.captience.realisticdamage.damage.BodyHitResolver;
import dev.captience.realisticdamage.systems.BloodFlow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The book-keeping behind the marks on the world (1.5): a bounded field of
 * stains with oldest-first eviction, merging, and accretion.
 *
 * <p>Three problems this exists to solve, all of which the particle engine
 * alone would get wrong.
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
 *
 * <p>The third only showed up in play, and it is why 1.5's blood "made these
 * weird splatters" rather than pooling: a mark that had grown as large as it was
 * allowed to get <em>swallowed every further drop</em>. Standing still for a
 * minute over a moderate wound produced a hundred and forty-four drops and two
 * marks, both of them full within the first three seconds, and the other hundred
 * and thirty-two changed nothing at all. A full mark now sheds the drop sideways
 * instead: {@link #land} lays a new mark beside it and the pool widens by
 * accretion, until it holds {@link Balance#STAIN_POOL_MARKS_MAX} marks and stops
 * widening.
 */
public final class BloodStains {
	private BloodStains() {}

	/** The four splat sprites sit after the four drop sprites in {@code blood.json}. */
	private static final int SPLAT_SPRITE_OFFSET = 4;
	/** ...and the four pool sprites after those. */
	private static final int POOL_SPRITE_OFFSET = 8;
	private static final int SPRITE_VARIANTS = 4;

	private static @Nullable SpriteSet sprites;
	/**
	 * Least-recently-fed first, so eviction takes from the head.
	 *
	 * <p>A {@link LinkedHashSet} rather than a deque because a mark that is being
	 * fed has to move back to the tail. {@link BloodStain#grow} winds a fed mark's
	 * age back to zero, so a pool under a standing player is effectively immortal —
	 * but it was also the <em>first</em> thing inserted, so when the capacity bit,
	 * the pool the player was standing in was deleted before the trail that led to
	 * it. That is backwards, and it became reachable the moment a pool was allowed
	 * to consist of more than one mark.
	 */
	private static final Set<BloodStain> live = new LinkedHashSet<>();
	/** Merge candidates bucketed by the block they sit in — merges are always local. */
	private static final Map<Long, List<BloodStain>> byBlock = new HashMap<>();
	/** Only ever used to vary splat shape and roll, never to decide anything. */
	private static long counter;
	/** Test-visible proof that contact transfer and wall runs actually fired. */
	private static long contactTransfers;
	private static long wallRuns;
	// The local player can settle one movement quantum (about 5.6 cm) off a
	// server-placed wall after position reconciliation. Keep that visually
	// touching distance inside the transfer envelope without reaching as far as
	// even the narrowest intentional walkway gap.
	private static final double CONTACT_GAP = 0.075;
	private static final double CONTACT_INSET = 1.0e-4;

	private record Contact(double x, double y, double z, Direction face, double gap) {}

	static void bindSprites(SpriteSet spriteSet) {
		sprites = spriteSet;
	}

	/**
	 * Records a drop landing on {@code face} at the given point. The point should
	 * already be on the surface; the offset away from it is applied here so every
	 * caller cannot get it subtly different and z-fight.
	 *
	 * <p>Three outcomes, in order. If there is a neighbour with room left, the drop
	 * feeds it and the mark grows. If every neighbour within merging distance is
	 * full but the pool they belong to has not reached its mark limit, the drop
	 * lays a new mark of its own — the pool spreads outward rather than being
	 * thrown away, which is the whole of this fix. Only when the pool is at its
	 * limit <em>and</em> the drop landed on one of its marks does it stop adding
	 * anything but wetness. That last condition matters: a drop landing past the
	 * edge of a full pool still lays a mark, because otherwise a player walking
	 * away from where they stood would leave no trail until they were clear of it.
	 */
	public static void land(double x, double y, double z, Direction face, double dropScale) {
		land(x, y, z, face, dropScale, -1);
	}

	/** As {@link #land(double, double, double, Direction, double)}, retaining the blood's owner. */
	public static void land(double x, double y, double z, Direction face, double dropScale,
			int sourceEntityId) {
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

		BloodStain thirsty = findMergeTarget(sx, sy, sz, face, true);
		if (thirsty != null) {
			feed(thirsty, radius);
			return;
		}
		// Nothing nearby has room. Either widen the pool, or — if it is already as
		// wide as it is allowed to get — settle for keeping its nearest edge wet.
		if (clusterSize(sx, sy, sz, face) >= Balance.STAIN_POOL_MARKS_MAX) {
			BloodStain full = findMergeTarget(sx, sy, sz, face, false);
			if (full != null) {
				feed(full, radius);
				return;
			}
		}

		long seed = counter++;
		int variant = Math.min(SPRITE_VARIANTS - 1, (int) (BloodFlow.jitter(seed, 3) * SPRITE_VARIANTS));
		BloodStain stain = new BloodStain(level, sx, sy, sz, face, radius, seed, sourceEntityId,
				sprites.get(variant + SPLAT_SPRITE_OFFSET, BloodEmitter.SPRITE_COUNT - 1),
				sprites.get(variant + POOL_SPRITE_OFFSET, BloodEmitter.SPRITE_COUNT - 1));
		client.particleEngine.add(stain);
		register(stain, sx, sy, sz);
	}

	/**
	 * Transfers one visible bead from a bleeding body region to a wall its box is
	 * actually touching. Collision shapes are used rather than
	 * {@code horizontalCollision}: the latter only stays true while movement is
	 * being attempted, so a body that has gone still against stone would stop
	 * leaving the very mark this method exists to show.
	 */
	public static boolean transfer(Player player, BodyPart part, double dropScale, long seed) {
		ClientEffectsConfig config = ClientEffectsConfig.get();
		if (sprites == null || config.bloodIntensity() * config.bloodStainIntensity <= 0.0) {
			return false;
		}
		Contact contact = contact(player, part, seed);
		if (contact == null) {
			return false;
		}
		land(contact.x(), contact.y(), contact.z(), contact.face(), dropScale, player.getId());
		contactTransfers++;
		return true;
	}

	private static @Nullable Contact contact(Player player, BodyPart part, long seed) {
		AABB body = player.getBoundingBox();
		Vec3 region = BodyHitResolver.regionCenter(player, part);
		double y = Math.clamp(region.y + BloodFlow.jitterSigned(seed, 42) * 0.08,
				body.minY + CONTACT_INSET, body.maxY - CONTACT_INSET);
		Contact best = null;
		AABB search = body.inflate(CONTACT_GAP, 0.0, CONTACT_GAP);
		for (int x = (int) Math.floor(search.minX); x <= (int) Math.floor(search.maxX); x++) {
			for (int blockY = (int) Math.floor(search.minY); blockY <= (int) Math.floor(search.maxY); blockY++) {
				for (int z = (int) Math.floor(search.minZ); z <= (int) Math.floor(search.maxZ); z++) {
					BlockPos pos = new BlockPos(x, blockY, z);
					VoxelShape shape = player.level().getBlockState(pos)
							.getCollisionShape(player.level(), pos);
					for (AABB localBox : shape.toAabbs()) {
						best = nearer(best, body, region, y, seed, localBox.move(pos));
					}
				}
			}
		}
		return best;
	}

	private static @Nullable Contact nearer(@Nullable Contact best, AABB body,
			Vec3 region, double y, long seed, AABB wall) {
		if (y < wall.minY || y > wall.maxY) {
			return best;
		}
		double minZ = Math.max(body.minZ, wall.minZ);
		double maxZ = Math.min(body.maxZ, wall.maxZ);
		if (maxZ > minZ) {
			double z = inside(region.z + BloodFlow.jitterSigned(seed, 43) * 0.10, minZ, maxZ);
			best = nearer(best, wall.minX - body.maxX, wall.minX, y, z, Direction.WEST);
			best = nearer(best, body.minX - wall.maxX, wall.maxX, y, z, Direction.EAST);
		}
		double minX = Math.max(body.minX, wall.minX);
		double maxX = Math.min(body.maxX, wall.maxX);
		if (maxX > minX) {
			double x = inside(region.x + BloodFlow.jitterSigned(seed, 44) * 0.10, minX, maxX);
			best = nearer(best, wall.minZ - body.maxZ, x, y, wall.minZ, Direction.NORTH);
			best = nearer(best, body.minZ - wall.maxZ, x, y, wall.maxZ, Direction.SOUTH);
		}
		return best;
	}

	private static double inside(double value, double min, double max) {
		double inset = Math.min(CONTACT_INSET, (max - min) * 0.25);
		return Math.clamp(value, min + inset, max - inset);
	}

	private static @Nullable Contact nearer(@Nullable Contact best, double gap,
			double x, double y, double z, Direction face) {
		double distance = Math.abs(gap);
		if (distance > CONTACT_GAP) {
			return best;
		}
		if (best == null || distance < best.gap() - 1.0e-9
				|| Math.abs(distance - best.gap()) <= 1.0e-9 && face.ordinal() < best.face().ordinal()) {
			return new Contact(x, y, z, face, distance);
		}
		return best;
	}

	/**
	 * Grows a mark and moves it to the back of the eviction queue, which are the
	 * same event: {@code grow} resets the mark's age, so its insertion order stops
	 * being the truth about how old it is unless the queue is told.
	 */
	private static void feed(BloodStain stain, double incomingRadius) {
		stain.grow(incomingRadius);
		if (live.remove(stain)) {
			live.add(stain);
		}
	}

	private static void register(BloodStain stain, double x, double y, double z) {
		while (live.size() >= Balance.STAIN_CAPACITY) {
			Iterator<BloodStain> oldest = live.iterator();
			if (!oldest.hasNext()) {
				break;
			}
			BloodStain evicted = oldest.next();
			oldest.remove();
			evicted.remove();
		}
		live.add(stain);
		byBlock.computeIfAbsent(BlockPos.asLong(
				(int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)),
				key -> new ArrayList<>()).add(stain);
	}

	/**
	 * The nearest live mark on the same face within the merge radius, if any,
	 * optionally restricted to marks that still have room to grow.
	 *
	 * <p>Bucketed by block: the merge distance is well under one block, so a linear
	 * scan of the whole field would be doing several hundred distance checks to
	 * find a neighbour that can only ever be a few centimetres away. The scan
	 * covers the block neighbourhood rather than the one block the point falls in,
	 * because two marks two centimetres apart across a block boundary are
	 * neighbours by every measure except that one — which is why standing on the
	 * edge of a block used to leave four marks where standing in the middle of one
	 * left two (see {@link #neighbourhood}).
	 *
	 * <p>Preferring the nearest mark that is not yet full, rather than simply the
	 * nearest, is what makes a pool fill before it widens: it keeps the mark count
	 * (and so the particle count) down for the same amount of blood on the floor,
	 * and it is also what liquid does.
	 */
	private static @Nullable BloodStain findMergeTarget(
			double x, double y, double z, Direction face, boolean onlyWithRoom) {
		BloodStain best = null;
		double bestDistance = Balance.STAIN_MERGE_DISTANCE * Balance.STAIN_MERGE_DISTANCE;
		for (List<BloodStain> bucket : neighbourhood(x, y, z)) {
			for (int i = bucket.size() - 1; i >= 0; i--) {
				BloodStain stain = bucket.get(i);
				if (!stain.isAlive()) {
					bucket.remove(i);
					continue;
				}
				if (stain.face() != face || (onlyWithRoom && stain.saturated())) {
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
		}
		return best;
	}

	/**
	 * How many marks on this face are close enough to count as one pool. This is
	 * the number {@link Balance#STAIN_POOL_MARKS_MAX} bounds, and so it is what
	 * stops a player who stands still forever from spreading across the floor
	 * without limit.
	 */
	private static int clusterSize(double x, double y, double z, Direction face) {
		double reach = Balance.STAIN_POOL_RADIUS * Balance.STAIN_POOL_RADIUS;
		int found = 0;
		for (List<BloodStain> bucket : neighbourhood(x, y, z)) {
			for (BloodStain stain : bucket) {
				if (!stain.isAlive() || stain.face() != face) {
					continue;
				}
				double dx = stain.px() - x;
				double dy = stain.py() - y;
				double dz = stain.pz() - z;
				if (dx * dx + dy * dy + dz * dz < reach) {
					found++;
				}
			}
		}
		return found;
	}

	/**
	 * The buckets of the 3&times;3&times;3 block neighbourhood around a point.
	 *
	 * <p>All three axes rather than the two in the face's plane: a stain sits
	 * {@link Balance#STAIN_SURFACE_OFFSET} off the surface it marks, so a mark on
	 * the underside of a ceiling lands in the block below the one a mark on the
	 * floor above it lands in, and a wall stain a centimetre under a block boundary
	 * has neighbours on the other side of it. Twenty-seven lookups of a map that is
	 * nearly always almost empty; the face check in the callers is what actually
	 * decides membership.
	 */
	private static List<List<BloodStain>> neighbourhood(double x, double y, double z) {
		int bx = (int) Math.floor(x);
		int by = (int) Math.floor(y);
		int bz = (int) Math.floor(z);
		List<List<BloodStain>> found = new ArrayList<>(27);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					List<BloodStain> bucket = byBlock.get(BlockPos.asLong(bx + dx, by + dy, bz + dz));
					if (bucket != null && !bucket.isEmpty()) {
						found.add(bucket);
					}
				}
			}
		}
		return found;
	}

	/** How many marks are currently on the world. Exists so tests can see them. */
	public static int count() {
		return live.size();
	}

	/**
	 * Total quad area of the live marks, in square blocks.
	 *
	 * <p>Exists so tests can watch a pool grow, which {@link #count()} cannot show:
	 * merging deliberately keeps the count low, so counting marks reports the same
	 * number whether the field is pooling or has stalled. Ink is the measure that
	 * tells those apart. A mark's radius is a half-extent, hence 4r&sup2;.
	 */
	public static double inkArea() {
		double total = 0.0;
		for (BloodStain stain : live) {
			total += 4.0 * stain.radius() * stain.radius();
		}
		return total;
	}

	/**
	 * Radius of the largest live mark, in blocks. Exists so tests can assert that
	 * accumulation reaches a size a single drop could not have produced — the exact
	 * thing that was impossible while one constant capped both.
	 */
	public static double largestMark() {
		double largest = 0.0;
		for (BloodStain stain : live) {
			largest = Math.max(largest, stain.radius());
		}
		return largest;
	}

	/**
	 * How many live marks are drawn with the pool sprite.
	 *
	 * <p>Exists so tests can ask the thing {@link #count()}, {@link #inkArea()}
	 * and {@link #largestMark()} all miss: not how much blood is down or how big
	 * the biggest mark got, but whether a mark that two drops built is claiming to
	 * be a pool. Every one of those three reads the same on a trail of pools as on
	 * a trail of splats, which is how a moving player came to leave one pool per
	 * heartbeat with the suite green.
	 */
	public static int poolMarks() {
		int pools = 0;
		for (BloodStain stain : live) {
			if (stain.isPool()) {
				pools++;
			}
		}
		return pools;
	}

	/**
	 * How many live marks sit on the given face.
	 *
	 * <p>Exists so tests can tell a wall from a floor, which none of the four
	 * measures above can: blood that fell at the player's feet and blood that was
	 * thrown across a room and stopped against stone read identically as a count,
	 * as ink and as a largest mark. The face is the only thing that separates
	 * them, and "does a blow put blood on the wall behind you" is the whole of
	 * what the burst is for.
	 */
	public static int countOnFace(Direction face) {
		int found = 0;
		for (BloodStain stain : live) {
			if (stain.face() == face) {
				found++;
			}
		}
		return found;
	}

	public static long contactTransfers() {
		return contactTransfers;
	}

	public static long wallRuns() {
		return wallRuns;
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
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || sprites == null) {
			return;
		}
		for (BloodStain stain : live) {
			if (!stain.isAlive() || !stain.advanceWallRun()) {
				continue;
			}
			long seed = counter++;
			int variant = Math.min(BloodEmitter.DROP_SPRITE_COUNT - 1,
					(int) (BloodFlow.jitter(seed, 45) * BloodEmitter.DROP_SPRITE_COUNT));
			BloodDrop drop = new BloodDrop(level, stain.px(),
					stain.py() - stain.radius() - Balance.STAIN_SURFACE_OFFSET, stain.pz(),
					0.0, -0.02, 0.0, stain.sourceEntityId(),
					sprites.get(variant, BloodEmitter.SPRITE_COUNT - 1));
			client.particleEngine.add(drop);
			wallRuns++;
		}
	}

	/** Dimension change, disconnect, or the player switching the effect off. */
	public static void reset() {
		for (BloodStain stain : live) {
			stain.remove();
		}
		live.clear();
		byBlock.clear();
		counter = 0;
		contactTransfers = 0;
		wallRuns = 0;
	}
}
